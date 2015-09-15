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

import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

class TeamCityPluginExtension {

    String version = '9.0'

    File homeDir

    File dataDir

    File javaHome

    String downloadBaseUrl = 'http://download.jetbrains.com/teamcity'

    String downloadUrl

    String downloadDir = 'downloads'

    String downloadFile

    private AgentPluginConfiguration agent

    private ServerPluginConfiguration server

    private Project project

    TeamCityPluginExtension(Project project) {
        this.project = project
        this.agent = new AgentPluginConfiguration(project.copySpec {})
        this.server = new ServerPluginConfiguration()
    }

    def getAgent() {
        return agent
    }

    def agent(Closure closure) {
        ConfigureUtil.configure(closure, agent)
    }

    def getServer() {
        return server
    }

    def server(Closure closure) {
        ConfigureUtil.configure(closure, server)
    }

    def setDescriptor(Object descriptor) {
        if (project.plugins.hasPlugin(TeamCityAgentPlugin)) {
            agent.descriptor = descriptor
        } else {
            server.descriptor = descriptor
        }
    }

    def getDescriptor() {
        if (project.plugins.hasPlugin(TeamCityAgentPlugin)) {
            return agent.descriptor
        } else {
            return server.descriptor
        }
    }

    def descriptor(Closure closure) {
        if (project.plugins.hasPlugin(TeamCityAgentPlugin)) {
            agent.descriptor(closure)
        } else {
            server.descriptor(closure)
        }
    }

    def getDownloadUrl() {
        if (!downloadUrl) {
            downloadUrl = "${downloadBaseUrl}/TeamCity-${version}.tar.gz"
        }
        return downloadUrl
    }

    def getDownloadFile() {
        if (!downloadFile) {
            downloadFile = "${downloadDir}/TeamCity-${version}.tar.gz"
        }
        return downloadFile
    }
}
