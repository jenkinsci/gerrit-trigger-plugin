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

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContextConverter;

import hudson.Plugin;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Items;
import hudson.model.Run;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;


/**
 * Main Plugin entrance.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class PluginImpl extends Plugin {

    /**
     * What to call this plug-in to humans.
     */
    public static final String DISPLAY_NAME = "Gerrit Trigger";

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
            Hudson.ADMINISTER);
    /**
     * The permission that allows users to perform the
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.RetriggerAction}.
     */
    public static final Permission RETRIGGER = new Permission(PERMISSION_GROUP,
            "Retrigger",
            Messages._RetriggerPermissionDescription(),
            AbstractProject.BUILD);

    private static final Logger logger = LoggerFactory.getLogger(PluginImpl.class);
    private static PluginImpl instance;
    private LinkedList<GerritServer> servers;

    // the old config field is left as deprecated and transient so that data in previous format can be read in but
    // not written back into the XML.
    @Deprecated
    private transient IGerritHudsonTriggerConfig config;

    /**
     * the default server name.
     */
    public static final String DEFAULT_SERVER_NAME = "defaultServer";

    /**
     * Constructor.
     */
    public PluginImpl() {
        instance = this;
    }

    /**
     * Returns this singleton instance.
     *
     * @return the singleton.
     */
    public static PluginImpl getInstance() {
        return instance;
    }

    /**
     * Get the list of Gerrit servers.
     *
     * @return the list as a LinkedList of GerritServers
     */
    public synchronized LinkedList<GerritServer> getServers() {
        if (servers == null) {
            servers = new LinkedList<GerritServer>();
        }
        return servers;
    }

    /**
     * Get the list of Gerrit server names.
     *
     * @return the list of server names as a list.
     */
    public synchronized LinkedList<String> getServerNames() {
        LinkedList<String> names = new LinkedList<String>();
        for (GerritServer s : getServers()) {
            names.add(s.getName());
        }
        return names;
    }

    /**
     * Get a GerritServer object by its name.
     *
     * @param name the name of the server to get.
     * @return the GerritServer object to get, or null if no server has this name.
     */
    public synchronized GerritServer getServer(String name) {
        for (GerritServer s : servers) {
            if (s.getName().equals(name)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Set the list of Gerrit servers.
     *
     * @param servers the list to be set.
     */
    public synchronized void setServers(LinkedList<GerritServer> servers) {
        this.servers = servers;
    }

    /**
     * Add a server to the list.
     *
     * @param s the server to be added.
     * @return the list after adding the server.
     */
    public synchronized LinkedList<GerritServer> addServer(GerritServer s) {
        servers.add(s);
        return servers;
    }

    /**
     * Remove a server from the list.
     *
     * @param s the server to be removed.
     * @return the list after removing the server.
     */
    public synchronized LinkedList<GerritServer> removeServer(GerritServer s) {
        servers.remove(s);
        return servers;
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
            }
        }
        return contains;
    }


    /**
     * Return the list of jobs configured with a server.
     *
     * @param serverName the name of the Gerrit server.
     * @return the list of jobs configured with this server.
     */
    public List<AbstractProject> getConfiguredJobs(String serverName) {
        LinkedList<AbstractProject> configuredJobs = new LinkedList<AbstractProject>();
        for (AbstractProject<?, ?> project : Hudson.getInstance().getItems(AbstractProject.class)) { //get the jobs
            GerritTrigger gerritTrigger = project.getTrigger(GerritTrigger.class);

            //if the job has a gerrit trigger, check whether the trigger has selected this server:
            if (gerritTrigger != null && gerritTrigger.getServerName().equals(serverName)) {
                configuredJobs.add(project); //job has selected this server, add it to the list
            }
        }
        return configuredJobs;
    }

    @Override
    public void start() throws Exception {
        logger.info("Starting Gerrit-Trigger Plugin");
        doXStreamRegistrations();
        logger.trace("Loading configs");
        load();
        for (GerritServer s : servers) {
            s.start();
        }
    }

    @Override
    public void load() throws IOException {
        super.load();
        if (servers == null || servers.isEmpty()) {
            servers = new LinkedList<GerritServer>();
            if (config != null) { //have loaded data in old format, so add a new server with the old config to the list.
                GerritServer defaultServer = new GerritServer(DEFAULT_SERVER_NAME);
                defaultServer.setConfig(config);
                servers.add(defaultServer);
            }
            setServers(servers);
            save();
        }
    }

    /**
     * Registers XStream alias and converters to handle backwards compatibility with old data.
     */
    protected static void doXStreamRegistrations() {
        logger.trace("doing XStream alias registrations.");

        //Register it in all known XStreams just to be sure.
        Items.XSTREAM.registerConverter(new TriggerContextConverter());
        Hudson.XSTREAM.registerConverter(new TriggerContextConverter());
        //This is where the problems where, reading builds.
        Run.XSTREAM.registerConverter(new TriggerContextConverter());

        logger.trace("XStream alias registrations done.");
    }

    @Override
    public void stop() throws Exception {
        for (GerritServer s : servers) {
            s.stop();
        }
    }
}
