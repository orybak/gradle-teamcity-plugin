package example.teamcity;

import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.apache.log4j.Logger;

public class Example1ServerPlugin extends BuildServerAdapter {

    private static Logger logger = Logger.getLogger("jetbrains.buildServer.SERVER");

    private final SBuildServer server;

    private final PluginDescriptor descriptor;

    public Example1ServerPlugin(SBuildServer server, PluginDescriptor descriptor) {
        this.server = server;
        this.descriptor = descriptor;
    }

    public void register() {
        server.addListener(this);
        String name = getClass().getSimpleName();
        logger.info(String.format("%s: Plugin name: %s, version: %s", name, descriptor.getPluginName(), descriptor.getPluginVersion()));
        logger.info(String.format("%s: Plugin parameter: param=%s", name, descriptor.getParameterValue("param")));
    }
}
