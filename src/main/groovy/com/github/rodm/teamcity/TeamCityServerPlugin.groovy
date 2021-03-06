/*
 * Copyright 2015 Rod MacKenzie
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rodm.teamcity

import com.github.rodm.teamcity.tasks.GenerateServerPluginDescriptor
import com.github.rodm.teamcity.tasks.InstallTeamCity
import com.github.rodm.teamcity.tasks.ProcessDescriptor
import com.github.rodm.teamcity.tasks.StartAgent
import com.github.rodm.teamcity.tasks.StartServer
import com.github.rodm.teamcity.tasks.StopAgent
import com.github.rodm.teamcity.tasks.StopServer
import com.github.rodm.teamcity.tasks.Unpack
import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Zip

class TeamCityServerPlugin extends TeamCityPlugin {

    public static final String PLUGIN_DEFINITION_PATTERN = "META-INF/build-server-plugin*.xml"

    public static final String SERVER_PLUGIN_DESCRIPTOR_DIR = PLUGIN_DESCRIPTOR_DIR + '/server'

    @Override
    void configureTasks(Project project, TeamCityPluginExtension extension) {
        configureServerPluginTasks(project, extension)
        configureEnvironmentTasks(project, extension)
    }

    void configureServerPluginTasks(Project project, TeamCityPluginExtension extension) {
        project.plugins.withType(JavaPlugin) {
            project.afterEvaluate {
                project.dependencies {
                    provided "org.jetbrains.teamcity:server-api:${extension.version}"

                    testCompile "org.jetbrains.teamcity:tests-support:${extension.version}"
                }
            }
        }

        configureJarTask(project, PLUGIN_DEFINITION_PATTERN)

        def packagePlugin = project.tasks.create('serverPlugin', Zip)
        packagePlugin.description = 'Package TeamCity plugin'
        packagePlugin.group = 'TeamCity'
        packagePlugin.with {
            baseName = "${project.rootProject.name}"
            into('server') {
                project.plugins.withType(JavaPlugin) {
                    def jar = project.tasks[JavaPlugin.JAR_TASK_NAME]
                    from(jar)
                    from(project.configurations.runtime - project.configurations.provided)
                }
                from(project.configurations.server)
            }
            into('agent') {
                if (project.plugins.hasPlugin(TeamCityAgentPlugin)) {
                    def agentPlugin = project.tasks['agentPlugin']
                    from(agentPlugin)
                } else {
                    from(project.configurations.agent)
                }
            }
            into('') {
                from {
                    new File(project.getBuildDir(), SERVER_PLUGIN_DESCRIPTOR_DIR + "/" + PLUGIN_DESCRIPTOR_FILENAME)
                }
                rename {
                    PLUGIN_DESCRIPTOR_FILENAME
                }
            }
        }
        def descriptorFile = new File(project.getBuildDir(), SERVER_PLUGIN_DESCRIPTOR_DIR + '/' + PLUGIN_DESCRIPTOR_FILENAME)
        packagePlugin.doLast(new PluginDescriptorValidationAction('teamcity-server-plugin-descriptor.xsd', descriptorFile))
        packagePlugin.with(extension.server.files)
        packagePlugin.onlyIf { extension.server.descriptor != null }

        def assemble = project.tasks['assemble']
        assemble.dependsOn packagePlugin

        def processDescriptor = project.tasks.create('processServerDescriptor', ProcessDescriptor) {
            conventionMapping.descriptor = { extension.server.descriptor instanceof File ? extension.server.descriptor : null }
            conventionMapping.tokens = { extension.server.tokens }
        }
        processDescriptor.destinationDir = new File(project.buildDir, SERVER_PLUGIN_DESCRIPTOR_DIR)
        processDescriptor.onlyIf { extension.server.descriptor != null && extension.server.descriptor instanceof File }
        packagePlugin.dependsOn processDescriptor

        def generateDescriptor = project.tasks.create('generateServerDescriptor', GenerateServerPluginDescriptor)
        generateDescriptor.onlyIf { extension.server.descriptor != null && extension.server.descriptor instanceof ServerPluginDescriptor }
        packagePlugin.dependsOn generateDescriptor

        project.artifacts.add('plugin', packagePlugin)

        project.afterEvaluate {
            Zip serverPlugin = (Zip) project.tasks.getByPath('serverPlugin')
            if (extension.server.archiveName) {
                serverPlugin.archiveName = extension.server.archiveName
            }
        }
    }

    private static void configureEnvironmentTasks(Project project, TeamCityPluginExtension extension) {
        project.afterEvaluate(new ConfigureEnvironmentTasksAction(extension))
    }

    static class ConfigureEnvironmentTasksAction implements Action<Project> {

        private TeamCityPluginExtension extension

        ConfigureEnvironmentTasksAction(TeamCityPluginExtension extension) {
            this.extension = extension
        }

        @Override
        void execute(Project project) {
            ServerPluginConfiguration server = extension.server
            def build = project.tasks.getByName('build')
            server.environments.each { environment ->
                defaultMissingProperties(project, server, environment)

                String name = environment.name.capitalize()
                def download = project.tasks.create(String.format("download%s", name), Download)
                download.src { environment.downloadUrl }
                download.dest { project.file("${server.downloadsDir}/${toFilename(environment.downloadUrl)}") }

                def unpack = project.tasks.create(String.format("unpack%s", name), Unpack) {
                    conventionMapping.map('source') {
                        project.file("${server.downloadsDir}/${toFilename(environment.downloadUrl)}")
                    }
                    conventionMapping.map('target') { environment.homeDir }
                }
                unpack.dependsOn download
                def install = project.tasks.create(String.format("install%s", name), InstallTeamCity)
                install.dependsOn unpack

                def deployPlugin = project.tasks.create(String.format('deployTo%s', name), Copy) {
                    from { environment.plugins }
                    into { environment.pluginsDir }
                }
                deployPlugin.dependsOn build

                def undeployPlugin = project.tasks.create(String.format('undeployFrom%s', name), Delete) {
                    delete { deployPlugin.outputs.files }
                }

                def startServer = project.tasks.create(String.format('start%sServer', name), StartServer) {
                    conventionMapping.map('homeDir') { environment.homeDir }
                    conventionMapping.map('dataDir') { environment.dataDir }
                    conventionMapping.map('javaHome') { environment.javaHome }
                    conventionMapping.map('serverOptions') { environment.serverOptions }
                }
                startServer.dependsOn deployPlugin

                def stopServer = project.tasks.create(String.format('stop%sServer', name), StopServer) {
                    conventionMapping.map('homeDir') { environment.homeDir }
                    conventionMapping.map('javaHome') { environment.javaHome }
                }
                stopServer.finalizedBy undeployPlugin

                project.tasks.create(String.format('start%sAgent', name), StartAgent) {
                    conventionMapping.map('homeDir') { environment.homeDir }
                    conventionMapping.map('javaHome') { environment.javaHome }
                    conventionMapping.map('agentOptions') { environment.agentOptions }
                }

                project.tasks.create(String.format('stop%sAgent', name), StopAgent) {
                    conventionMapping.map('homeDir') { environment.homeDir }
                    conventionMapping.map('javaHome') { environment.javaHome }
                }
            }
        }

        private static void defaultMissingProperties(Project project, ServerPluginConfiguration server, TeamCityEnvironment environment) {
            environment.with {
                downloadUrl = downloadUrl ?: "${server.baseDownloadUrl}/TeamCity-${version}.tar.gz"
                homeDir = homeDir ?: project.file("${server.baseHomeDir}/TeamCity-${version}")
                dataDir = dataDir ?: project.file("${server.baseDataDir}/" + (version =~ (/(\d+\.\d+).*/))[0][1])
                javaHome = javaHome ?: project.file(System.properties['java.home'])

                if (plugins.isEmpty()) {
                    def serverPlugin = project.tasks.getByName('serverPlugin')
                    if (serverPlugin) {
                        plugins serverPlugin.archivePath
                    }
                }
            }
        }

        private static String toFilename(String url) {
            return url[(url.lastIndexOf('/') + 1)..-1]
        }
    }
}
