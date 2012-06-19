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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ConnectionListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritHandler;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritSendCommandQueue;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContextConverter;
import hudson.Plugin;
import hudson.model.Hudson;
import hudson.model.Items;
import hudson.model.Run;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private static final Logger logger = LoggerFactory.getLogger(PluginImpl.class);
    private transient GerritHandler gerritEventManager;
    private transient GerritProjectListUpdater projectListUpdater;
    private static PluginImpl instance;
    private IGerritHudsonTriggerConfig config;
    private transient Collection<GerritEventListener> savedEventListeners;
    private transient Collection<ConnectionListener> savedConnectionListeners;

    /**
     * Constructor.
     */
    public PluginImpl() {
        instance = this;
    }

    /**
     * Gets the global config.
     *
     * @return the config.
     */
    public IGerritHudsonTriggerConfig getConfig() {
        return config;
    }

    /**
     * Returns this singleton instance.
     *
     * @return the singleton.
     */
    public static PluginImpl getInstance() {
        return instance;
    }

    @Override
    public void start() throws Exception {
        logger.info("Starting");
        doXStreamRegistrations();
        loadConfig();
        projectListUpdater = new GerritProjectListUpdater();
        projectListUpdater.start();
        //Starts the send-command-queue
        GerritSendCommandQueue.getInstance(config);
        //do not try to connect to gerrit unless there is a URL or a hostname in the text fields
        if (!config.hasDefaultValues()) {
            startManager();
            logger.info("Started");
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

    /**
     * Loads the configuration from disk.
     *
     * @throws IOException if the unfortunate happens.
     */
    private void loadConfig() throws IOException {
        logger.trace("loadConfig");
        load();

        if (config == null) {
            config = new Config();
        }
    }

    @Override
    public void stop() throws Exception {
        logger.info("Shutting down...");
        projectListUpdater.shutdown();
        projectListUpdater.join();
        if (gerritEventManager != null) {
            gerritEventManager.shutdown(false);
            //TODO save to register listeners?
            gerritEventManager = null;
        }
        GerritSendCommandQueue.shutdown();
    }

    /**
     * Starts the GerritEventManager
     */
    private void startManager() {
        logger.debug("starting Gerrit manager");
        createManager();
        gerritEventManager.start();
    }

    /**
     * Creates the GerritEventManager
     */
    private void createManager() {
        gerritEventManager = new GerritHandler(config);
        //Add any event/connectionlisteners that were created while the connection was down.
        if (savedConnectionListeners != null) {
            gerritEventManager.addConnectionListeners(savedConnectionListeners);
            savedConnectionListeners = null;
        }
        if (savedEventListeners != null) {
            gerritEventManager.addEventListeners(savedEventListeners);
            savedEventListeners = null;
        }
    }

    /**
     * Adds a listener to the EventManager.  The listener will receive all events from Gerrit.
     *
     * @param listener the listener.
     * @see GerritHandler#addListener(com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener)
     */
    public void addListener(GerritEventListener listener) {
        if (gerritEventManager != null) {
            gerritEventManager.addListener(listener);
        } else {
            //If the eventmanager isn't created yet, save the eventlistener so it can be added once
            //the eventmanager is created.
            if (savedEventListeners == null) {
                savedEventListeners = Collections.synchronizedSet(new HashSet<GerritEventListener>());
            }
            savedEventListeners.add(listener);
        }
    }

    /**
     * Removes a listener from the manager.
     *
     * @param listener the listener to remove.
     * @see GerritHandler#removeListener(com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener)
     */
    public void removeListener(GerritEventListener listener) {
        if (gerritEventManager != null) {
            gerritEventManager.removeListener(listener);
        } else {
            if (savedEventListeners != null) {
                savedEventListeners.remove(listener);
            }
        }
    }

    /**
     * Removes a connection listener from the manager.
     *
     * @param listener the listener to remove.
     */
    public void removeListener(ConnectionListener listener) {
        if (gerritEventManager != null) {
            gerritEventManager.removeListener(listener);
        } else {
            if (savedConnectionListeners != null) {
                savedConnectionListeners.remove(listener);
            }
        }
    }

    /**
     * Starts the connection to Gerrit stream of events.
     *
     * @throws Exception if it is so unfortunate.
     */
    public synchronized void startConnection() throws Exception {
        if (gerritEventManager == null) {
            createManager();
            if (savedEventListeners != null) {
                gerritEventManager.addEventListeners(savedEventListeners);
                savedEventListeners = null;
            }
            if (savedConnectionListeners != null) {
                gerritEventManager.addConnectionListeners(savedConnectionListeners);
                savedConnectionListeners = null;
            }
            gerritEventManager.start();
        } else {
            logger.warn("Already started!");
        }
    }

    /**
     * Stops the connection to Gerrit stream of events.
     *
     * @throws Exception if it is so unfortunate.
     */
    public synchronized void stopConnection() throws Exception {
        if (gerritEventManager != null) {
            savedEventListeners = null;
            savedConnectionListeners = null;
            gerritEventManager.shutdown(true);

            savedEventListeners = gerritEventManager.removeAllEventListeners();
            savedConnectionListeners = gerritEventManager.removeAllConnectionListeners();
            gerritEventManager = null;
        } else {
            logger.warn("Was told to shutdown again!?");
        }
    }

    /**
     * Restarts the connection to Gerrit stream of events.
     *
     * @throws Exception if it is so unfortunate.
     */
    public void restartConnection() throws Exception {
        stopConnection();
        startConnection();
    }

    /**
     * Adds a Connection Listener to the manager.
     * Return the current connection status so that listeners that
     * are added later than a connectionestablished/ connectiondown
     * will get the current connection status.
     *
     * @param listener the listener.
     * @return the connection status.
     */
    public boolean addListener(ConnectionListener listener) {
        boolean connected = false;
        if (gerritEventManager != null) {
            connected = gerritEventManager.addListener(listener);
        } else {
            //If the eventmanager isn't created yet, save the connectionlistener so it can be added once
            //the eventmanager is created.
            if (savedConnectionListeners == null) {
                savedConnectionListeners = Collections.synchronizedSet(new HashSet<ConnectionListener>());
            }
            savedConnectionListeners.add(listener);
        }
        return connected;
    }

    /**
     * Returns a list of Gerrit projects.
     *
     * @return list of gerrit projects
     */
    public List<String> getGerritProjects() {
        if (projectListUpdater != null) {
            return projectListUpdater.getGerritProjects();
        } else {
            return new ArrayList<String>();
        }
    }

    /**
     * Adds the given event to the stream of events.
     * It gets added to the same event queue as any event coming from the stream-events command in Gerrit.
     *
     * @param event the event.
     * @see GerritHandler#triggerEvent(com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent)
     */
    public void triggerEvent(GerritEvent event) {
        if (gerritEventManager != null) {
            gerritEventManager.triggerEvent(event);
        } else {
            throw new IllegalStateException("Manager not started!");
        }
    }

    /**
     * Returns the current Gerrit version.
     * If we are connected to Gerrit, otherwise null is returned.
     *
     * @return the current gerrit version as a String.
     */
    public String getGerritVersion() {
        if (gerritEventManager != null) {
            return gerritEventManager.getGerritVersion();
        } else {
            return null;
        }
    }
}
