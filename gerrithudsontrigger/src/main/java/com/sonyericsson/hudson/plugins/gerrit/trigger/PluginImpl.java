/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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

import hudson.Plugin;

import java.io.IOException;
import java.util.List;




import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ConnectionListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Plugin entrance.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class PluginImpl extends Plugin {

    /**
     * What to call this plugin to humans.
     */
    public static final String DISPLAY_NAME = "Gerrit Hudson Trigger";
    private static final Logger logger = LoggerFactory.getLogger(PluginImpl.class);
    private transient GerritHandler gerritEventManager;
    private static PluginImpl instance;
    private IGerritHudsonTriggerConfig config;
    private transient List<GerritEventListener> savedEventListeners;
    private transient List<ConnectionListener> savedConnectionListeners;

    /**
     * Constructor.
     */
    public PluginImpl() {
        instance = this;
    }

    /**
     * Gets the global config.
     * @return the config.
     */
    public IGerritHudsonTriggerConfig getConfig() {
        return config;
    }

    /**
     * Returns this singelton instance.
     * @return the singelton.
     */
    public static PluginImpl getInstance() {
        return instance;
    }

    @Override
    public void start() throws Exception {
        logger.info("Starting");
        loadConfig();
        startManager();
        logger.info("Started");
    }

    /**
     * Loads the configuration from disk.
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
        gerritEventManager.shutdown(false);
        //TODO save to regegister listeners?
        gerritEventManager = null;
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
        gerritEventManager = new GerritHandler(
                config.getGerritHostName(),
                config.getGerritSshPort(),
                config.getGerritAuthentication(),
                config.getNumberOfWorkerThreads());
    }

    /**
     * Adds a listener to the EventManager the listener will receive all events from Gerrit.
     * @param listener the listener.
     * @see GerritHandler#addListener(com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener)
     */
    public void addListener(GerritEventListener listener) {
        if (gerritEventManager != null) {
            gerritEventManager.addListener(listener);
        } else {
            throw new IllegalStateException("Manager not started!");
        }
    }

    /**
     * Removes a listener from the manager.
     * @param listener the listener to remove.
     * @see GerritHandler#removeListener(com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener)
     */
    public void removeListener(GerritEventListener listener) {
        if (gerritEventManager != null) {
            gerritEventManager.removeListener(listener);
        } else {
            throw new IllegalStateException("Manager not started!");
        }
    }

    /**
     * Starts the connection to Gerrit stream of events.
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
     * @throws Exception if it is so unfortunate.
     */
    public synchronized void stopConnection() throws Exception {
        savedEventListeners = null;
        savedConnectionListeners = null;
        if (gerritEventManager != null) {
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
     * @throws Exception if it is so unfortunate.
     */
    public void restartConnection() throws Exception {
        stopConnection();
        startConnection();
    }

    /**
     * Adds a Connection Listener to the manager.
     * @param listener the listener.
     */
    public void addListener(ConnectionListener listener) {
        if (gerritEventManager != null) {
            gerritEventManager.addListener(listener);
        } else {
            throw new IllegalStateException("Manager not started!");
        }
    }
}
