/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.dependency.DependencyQueueTaskDispatcher;
import com.sonyericsson.hudson.plugins.gerrit.trigger.replication.ReplicationQueueTaskDispatcher;
import com.sonymobile.tools.gerrit.gerritevents.GerritHandler;
import com.sonymobile.tools.gerrit.gerritevents.GerritSendCommandQueue;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.PluginConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContextConverter;

import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Provider;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.XmlFile;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.init.TermMilestone;
import hudson.init.Terminator;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Api;
import hudson.model.Items;
import hudson.model.Run;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import jenkins.model.GlobalConfiguration;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jenkins.model.Jenkins;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Main Plugin entrance.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@ExportedBean
@Extension
@Symbol(PluginImpl.SYMBOL_NAME)
public class PluginImpl extends GlobalConfiguration {

    /**
     * What to call this plug-in to humans.
     */
    public static final String DISPLAY_NAME = "Gerrit Trigger";

    /**
     * Machine readable plugin name.
     */
    public static final String SYMBOL_NAME = "gerrit-trigger";

    /**
     * Any special permissions needed by this plugin are grouped into this.
     */
    public static final PermissionGroup PERMISSION_GROUP =
            new PermissionGroup(PluginImpl.class, Messages._GerritPermissionGroup());
    /**
     * The permission that allows users to perform the
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.manual.ManualTriggerAction}.
     */
    public static final Permission MANUAL_TRIGGER = new Permission(PERMISSION_GROUP,
            "ManualTrigger",
            Messages._ManualTriggerPermissionDescription(),
            Jenkins.ADMINISTER,
            PermissionScope.JENKINS);
    /**
     * The permission that allows users to perform the
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.RetriggerAction}.
     */
    public static final Permission RETRIGGER = new Permission(PERMISSION_GROUP,
            "Retrigger",
            Messages._RetriggerPermissionDescription(),
            Item.BUILD,
            PermissionScope.JENKINS);

    private static final Logger logger = LoggerFactory.getLogger(PluginImpl.class);
    private final List<GerritServer> servers = new CopyOnWriteArrayList<>();
    private transient GerritHandler gerritEventManager;
    private transient volatile boolean active = false;

    // the old config field is left as deprecated and transient so that data in previous format can be read in but
    // not written back into the XML.
    @Deprecated
    private transient IGerritHudsonTriggerConfig config;

    private PluginConfig pluginConfig;

    /**
     * the default server name.
     */
    public static final String DEFAULT_SERVER_NAME = "defaultServer";

    /**
     * System property used during testing to replace the location of the public key for mock connections.
     */
    public static final String TEST_SSH_KEYFILE_LOCATION_PROPERTY = PluginImpl.class.getName() + "_test_ssh_key_file";

    /**
     * Gets api.
     * @return the api.
     */
    public Api getApi() {
        return new Api(this);
    }

    /**
     * Returns the instance of this class.
     * If {@link Jenkins#getInstanceOrNull()} ()} isn't available
     * or the plugin class isn't registered null will be returned.
     *
     * @return the instance.
     */
    @CheckForNull
    public static PluginImpl getInstance() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            return GlobalConfiguration.all().get(PluginImpl.class);
        } else {
            logger.debug("Error, Jenkins could not be found, so no plugin!");
            return null;
        }
    }

    /**
     * Check if this plugin is active.
     *
     * @return true if active.
     */
    public boolean isActive() {
        return active;
    }
    /**
     * Get the list of Gerrit servers.
     *
     * @return the list of GerritServers
     */
    @Exported
    public List<GerritServer> getServers() {
        return servers;
    }

    /**
     * Get the list of Gerrit servers.
     * Static shorthand for {@link #getServers()}.
     * If the plugin instance is not available, and empty list is returned.
     *
     * @return the list of GerritServers
     */
    @NonNull
    //CS IGNORE MethodName FOR NEXT 1 LINES. REASON: Static equivalent marker.
    public static List<GerritServer> getServers_() {
        PluginImpl plugin = getInstance();
        if (plugin == null) {
            logger.debug("PluginImpl instance not found!");
            return Collections.emptyList();
        }
        return plugin.getServers();
    }


    /**
     * Get the list of Gerrit server names.
     *
     * @return the list of server names as a list.
     */
    public List<String> getServerNames() {
        LinkedList<String> names = new LinkedList<>();
        for (GerritServer s : getServers()) {
            names.add(s.getName());
        }
        return names;
    }

    /**
     * Static shorthand for {@link #getServerNames()}.
     *
     * @return the list of server names.
     */
    @NonNull
    //CS IGNORE MethodName FOR NEXT 1 LINES. REASON: Static equivalent marker.
    public static List<String> getServerNames_() {
        PluginImpl plugin = getInstance();
        if (plugin == null) {
            logger.debug("PluginImpl instance not found!");
            return Collections.emptyList();
        }
        return plugin.getServerNames();
    }

    /**
     * Get a GerritServer object by its name.
     *
     * @param name the name of the server to get.
     * @return the GerritServer object to get, or null if no server has this name.
     */
    public GerritServer getServer(String name) {
        for (GerritServer s : servers) {
            if (s.getName().equals(name)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Get a GerritServer object by its name.
     *
     * Static short for {@link #getServer(String)}.
     *
     * @param name the name of the server to get.
     * @return the GerritServer object to get, or null if no server has this name.
     * @see #getServer(String)
     */
    @CheckForNull
    //CS IGNORE MethodName FOR NEXT 1 LINES. REASON: Static equivalent marker.
    public static GerritServer getServer_(String name) {
        PluginImpl plugin = getInstance();
        if (plugin == null) {
            logger.debug("Error, plugin instance could not be found!");
            return null;
        }
        return plugin.getServer(name);
    }

    /**
     * Returns the Gerrit Server based on the GerritTriggerEvent.
     *
     * @param event The GerritTriggerEvent.
     * @return GerritServer or null if no server could be found.
     */
    public GerritServer getServer(GerritTriggeredEvent event) {
        Provider provider = event.getProvider();
        if (provider == null) {
            logger.warn("The event {} has no provider specified. BUG!", event);
            return null;
        }
        GerritServer gerritServer = getServer_(provider.getName());
        if (gerritServer == null) {
            logger.warn("Could not find server config for {} - no such server.", provider.getName());
            return null;
        }
        return gerritServer;
    }

    /**
     * Returns a GerritServer object based on a GerritTriggerEvent.
     *
     * Static short for {@link #getServer(GerritTriggeredEvent)}.
     *
     * @param event The GerritTriggerEvent.
     * @return GerritServer or null if no server could be found.
     */
    @CheckForNull
    //CS IGNORE MethodName FOR NEXT 1 LINES. REASON: Static equivalent marker.
    public static GerritServer getServer_(GerritTriggeredEvent event) {
        PluginImpl plugin = getInstance();
        if (plugin == null) {
            logger.debug("Error, plugin instance could not be found!");
            return null;
        }
        return plugin.getServer(event);
    }

    /**
     * Gets the first server in the server list. Or null if there are no servers.
     *
     * @return the server.
     */
    @CheckForNull
    public GerritServer getFirstServer() {
        if (!servers.isEmpty()) {
            return servers.get(0);
        }
        return null;
    }

    /**
     * Static shorthand for {@link #getFirstServer()}.
     *
     * @return the server if any.
     */
    @CheckForNull
    //CS IGNORE MethodName FOR NEXT 1 LINES. REASON: Static equivalent marker.
    public static GerritServer getFirstServer_() {
        PluginImpl plugin = getInstance();
        if (plugin == null) {
            logger.debug("Error, plugin instance could not be found!");
            return null;
        }
        return plugin.getFirstServer();
    }

    /**
     * Set the list of Gerrit servers.
     *
     * @param servers the list to be set.
     */
    public void setServers(List<GerritServer> servers) {
        checkAdmin();

        // Mimicking GerritManagement#doAddNewServer

        List<String> serverNames = new ArrayList<>();
        for (GerritServer server : servers) {
            String name = server.getName();
            if (serverNames.contains(name)) {
                throw new IllegalArgumentException("Multiple gerrit servers with name: " + name);
            }
            serverNames.add(name);
        }
        if (serverNames.contains(GerritServer.ANY_SERVER)) {
            throw new IllegalArgumentException("Illegal gerrit server name: " + GerritServer.ANY_SERVER);
        }

        if (this.servers != servers) {
            this.servers.clear();
            this.servers.addAll(servers);
        }
    }

    /**
     * Add a server to the list.
     *
     * @param s the server to be added.
     * @return the list after adding the server.
     */
    public List<GerritServer> addServer(GerritServer s) {
        checkAdmin();
        servers.add(s);
        return servers;
    }

    /**
     * Remove a server from the list.
     *
     * @param s the server to be removed.
     * @return the list after removing the server.
     */
    public List<GerritServer> removeServer(GerritServer s) {
        checkAdmin();
        servers.remove(s);
        return servers;
    }

    /**
     * Checks that the current user has {@link Jenkins#ADMINISTER} permission.
     * If Jenkins is currently active.
     */
    private void checkAdmin() {
        final Jenkins jenkins = Jenkins.getInstanceOrNull(); //Hoping this method doesn't change meaning again
        if (jenkins != null) {
            //If Jenkins is not alive then we are not started, so no unauthorised user might do anything
            jenkins.checkPermission(Jenkins.ADMINISTER);
        }
    }


    /**
     * Check whether the list of servers contains a GerritServer object of a specific name.
     *
     * @param serverName to check.
     * @return whether the list contains a server with the given name.
     */
    public boolean containsServer(String serverName) {
        boolean contains = false;
        for (GerritServer s : getServers()) {
            if (s.getName().equals(serverName)) {
                contains = true;
                break;
            }
        }
        return contains;
    }

    /**
     * Static shorthand for {@link #containsServer(String)}.
     * @param serverName to check.
     * @return whether the list contains a server with the given name.
     */
    //CS IGNORE MethodName FOR NEXT 1 LINES. REASON: Static equivalent marker.
    public static boolean containsServer_(String serverName) {
        PluginImpl plugin = getInstance();
        if (plugin == null) {
            logger.debug("Error, plugin instance could not be found!");
            return false;
        }
        return plugin.containsServer(serverName);
    }

    /**
     * Finds the server config for the event's provider.
     *
     * @param event the event
     * @return the config or null if no server could be found.
     * @see com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent#getProvider()
     */
    public static IGerritHudsonTriggerConfig getServerConfig(GerritTriggeredEvent event) {
        Provider provider = event.getProvider();
        if (provider != null) {
            GerritServer gerritServer = getServer_(provider.getName());
            if (gerritServer != null) {
                return gerritServer.getConfig();
            } else {
                logger.warn("Could not find server config for {} - no such server.", provider.getName());
            }
        } else {
            logger.warn("The event {} has no provider specified. BUG!", event);
        }
        return null;
    }

    /**
     * Gets the global config.
     *
     * @return the config.
     */
    public synchronized PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    /**
     * Set plugin config.
     *
     * @param pluginConfig New config to set.
     */
    public synchronized void setPluginConfig(PluginConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    /**
     * Gets the global config.
     * Static short hand for {@link #getPluginConfig()}.
     *
     * @return the config.
     */
    @CheckForNull
    //CS IGNORE MethodName FOR NEXT 1 LINES. REASON: Static equivalent marker.
    public static PluginConfig getPluginConfig_() {
        PluginImpl plugin = getInstance();
        if (plugin == null) {
            logger.debug("Error, plugin instance could not be found!");
            return null;
        }
        return plugin.getPluginConfig();
    }

    /**
     * Static shorthand for {@link hudson.Plugin#save()}.
     *
     * @throws IOException if save does so.
     */
    //CS IGNORE MethodName FOR NEXT 1 LINES. REASON: Static equivalent marker.
    public static void save_() throws IOException {
        PluginImpl plugin = getInstance();
        if (plugin == null) {
            logger.debug("Error, plugin instance could not be found!");
            return;
        }
        plugin.save();
    }

    // Reading from the location where the data used to be back when this implemented hudson.Plugin
    @Override
    protected XmlFile getConfigFile() {
        return new XmlFile(Jenkins.XSTREAM, new File(Jenkins.get().getRootDir(), "gerrit-trigger.xml"));
    }

    /**
     * Returns the GerritHandler object.
     *
     * @return gerritEventManager
     */
    @NonNull
    public GerritHandler getHandler() {
        if (gerritEventManager == null) {
            throw new IllegalStateException("Plugin is not started yet, or it is stopped already");
        }
        return gerritEventManager;
    }

    /**
     * Static shorthand for {@link #getHandler()}.
     *
     * @return gerritEventManager
     */
    @CheckForNull
    //CS IGNORE MethodName FOR NEXT 1 LINES. REASON: Static equivalent marker.
    public static GerritHandler getHandler_() {
        PluginImpl plugin = getInstance();
        if (plugin == null) {
            logger.debug("Error, plugin instance could not be found!");
            return null;
        }
        return plugin.getHandler();
    }

    /**
     * Return the list of jobs configured with a server.
     *
     * @param serverName the name of the Gerrit server.
     * @return the list of jobs configured with this server.
     */
    public List<Job> getConfiguredJobs(String serverName) {
        LinkedList<Job> configuredJobs = new LinkedList<>();
        for (Job<?, ?> project : Jenkins.get().getItems(Job.class)) { //get the jobs
            GerritTrigger gerritTrigger = GerritTrigger.getTrigger(project);

            //if the job has a gerrit trigger, check whether the trigger has selected this server:
            if (gerritTrigger != null && gerritTrigger.getServerName().equals(serverName)) {
                configuredJobs.add(project); //job has selected this server, add it to the list
            }
        }
        return configuredJobs;
    }

    /**
     * Static shorthand for {@link #getConfiguredJobs(String)}.
     * Will return an empty list if plugin instance is null.
     *
     * @param serverName the name of the Gerrit server.
     * @return the list of jobs configured with this server.
     */
    @NonNull
    //CS IGNORE MethodName FOR NEXT 1 LINES. REASON: Static equivalent marker.
    public static List<Job> getConfiguredJobs_(String serverName) {
        PluginImpl plugin = getInstance();
        if (plugin == null) {
            logger.debug("Error, plugin instance could not be found!");
            return Collections.emptyList();
        }
        return plugin.getConfiguredJobs(serverName);
    }

    /**
     * Start the plugin.
     */
    public void start() {
        logger.info("Starting Gerrit-Trigger Plugin");
        logger.trace("Loading configs");
        load();
        GerritSendCommandQueue.initialize(pluginConfig);
        gerritEventManager = new JenkinsAwareGerritHandler(pluginConfig.getNumberOfReceivingWorkerThreads());
        for (GerritServer s : servers) {
            s.start();
        }
        active = true;
    }

    /**
     * Forces initialization of the Dispatchers.
     *
     * They need to register and listen to GerritEvents. Normally, it is lazy loaded when the first build is started.
     */
    @Initializer(after = InitMilestone.SYSTEM_CONFIG_ADAPTED)
    public static void initializeDispatchers() {
        // Call the following method for force initialization of the Dispatchers because
        // it needs to register and listen to GerritEvent. Normally, it is lazy loaded when the first build is started.
        try {
            ExtensionList.lookupSingleton(ReplicationQueueTaskDispatcher.class);
        } catch (IllegalStateException e) {
            logger.warn("Failed to initialize Replication Queue.", e);
        }
        try {
            ExtensionList.lookupSingleton(DependencyQueueTaskDispatcher.class);
        } catch (IllegalStateException e) {
            logger.warn("Failed to initialize Dependency Queue.", e);
        }
    }

    /**
     * Load plugin config.
     */
    @Override
    public void load() {
        super.load();
        if (pluginConfig == null) {
            PluginConfig conf = new PluginConfig();
            if (config != null) {
                conf.setNumberOfReceivingWorkerThreads(config.getNumberOfReceivingWorkerThreads());
                conf.setNumberOfSendingWorkerThreads(config.getNumberOfSendingWorkerThreads());
            }
            pluginConfig = conf;
        }
        if (servers.isEmpty()) {
            if (config != null) { //have loaded data in old format, so add a new server with the old config to the list.
                GerritServer defaultServer = new GerritServer(DEFAULT_SERVER_NAME);
                defaultServer.setConfig(config);
                servers.add(defaultServer);
            }
            save();
        }
        pluginConfig.updateEventFilter();
        //For unit/integration testing only...
        if (System.getProperty(TEST_SSH_KEYFILE_LOCATION_PROPERTY) != null && !servers.isEmpty()) {
            File location = new File(System.getProperty(TEST_SSH_KEYFILE_LOCATION_PROPERTY));
            for (GerritServer server : servers) {
                ((Config)server.getConfig()).setGerritAuthKeyFile(location);
            }
        }
    }

    /**
     * Registers XStream alias and converters to handle backwards compatibility with old data.
     */
    protected static void doXStreamRegistrations() {
        logger.trace("doing XStream alias registrations.");

        //Register it in all known XStreams just to be sure.
        Items.XSTREAM.registerConverter(new TriggerContextConverter());
        Jenkins.XSTREAM.registerConverter(new TriggerContextConverter());
        //This is where the problems where, reading builds.
        Run.XSTREAM.registerConverter(new TriggerContextConverter());
        Run.XSTREAM2.addCompatibilityAlias(
            "com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ManualPatchsetCreated",
            ManualPatchsetCreated.class);

        Items.XSTREAM.aliasPackage("com.sonyericsson.hudson.plugins.gerrit.gerritevents",
                "com.sonymobile.tools.gerrit.gerritevents");
        Jenkins.XSTREAM.aliasPackage("com.sonyericsson.hudson.plugins.gerrit.gerritevents",
                "com.sonymobile.tools.gerrit.gerritevents");
        Run.XSTREAM.aliasPackage("com.sonyericsson.hudson.plugins.gerrit.gerritevents",
                "com.sonymobile.tools.gerrit.gerritevents");

        logger.trace("XStream alias registrations done.");
    }

    /**
     * Stop the plugin.
     */
    public void stop() {
        active = false;
        for (GerritServer s : servers) {
            s.stop();
        }
        if (gerritEventManager != null) {
            gerritEventManager.shutdown(false);
            //TODO save to registered listeners?
            gerritEventManager = null;
        }
        GerritSendCommandQueue.shutdown();
        servers.clear();
    }

    /**
     * Startup hook.
     */
    @Initializer(
            after = InitMilestone.PLUGINS_STARTED, // Requires extensions registered
            before = InitMilestone.EXTENSIONS_AUGMENTED // Need to be done before jobs start loading
    )
    @Restricted(DoNotUse.class)
    public static void gerritStart() {
        PluginImpl instance = PluginImpl.getInstance();
        if (instance == null) {
            throw new IllegalStateException("Jenkins is not up");
        }
        instance.start();
    }

    /**
     * Shutdown hook.
     */
    @Terminator(after = TermMilestone.COMPLETED)
    @Restricted(DoNotUse.class)
    public static void gerritStop() {
        PluginImpl instance = PluginImpl.getInstance();
        if (instance == null) {
            throw new IllegalStateException("Jenkins is not up");
        }
        instance.stop();
    }

    static {
        doXStreamRegistrations();
    }
}
