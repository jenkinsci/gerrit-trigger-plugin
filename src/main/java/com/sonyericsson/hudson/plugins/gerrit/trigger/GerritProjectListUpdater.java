/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Mobile Communications Inc.
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

import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil;
import com.sonymobile.tools.gerrit.gerritevents.ConnectionListener;
import com.sonymobile.tools.gerrit.gerritevents.GerritEventListener;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ProjectCreated;
import com.sonymobile.tools.gerrit.gerritevents.ssh.SshConnection;
import com.sonymobile.tools.gerrit.gerritevents.ssh.SshConnectionFactory;
import com.sonymobile.tools.gerrit.gerritevents.ssh.SshException;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class responsible for providing the Config object with a list of all
 * available Gerrit projects. Executed periodically on a timely basis
 * and every time the connection to the Gerrit server has been restored.
 *
 * @author Gustaf Lundh &lt;Gustaf.Lundh@sonyericsson.com&gt;
 */
public class GerritProjectListUpdater extends Thread implements ConnectionListener, NamedGerritEventListener {
    /**
     * The command for fetching projects.
     */
    public static final String GERRIT_LS_PROJECTS = "gerrit ls-projects";
    private static final int MAX_WAIT_TIME = 64;

    private AtomicBoolean connected = new AtomicBoolean(false);
    private boolean shutdown = false;
    private static final Logger logger = LoggerFactory.getLogger(GerritProjectListUpdater.class);
    private List<String> gerritProjects;
    private String serverName;

    /**
     * Default constructor.
     * @param serverName the name of the Gerrit server.
     */
    public GerritProjectListUpdater(String serverName) {
        this.setName(this.getClass().getName() + " for " + serverName + " Thread");
        this.setDaemon(true);
        this.serverName = serverName;
        addThisAsListener();
    }

    /**
     * The name of the {@link GerritServer} this listener is working for.
     *
     * @return the {@link GerritServer#getName()}.
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Add the current list updater as a listener to the GerritServer object.
     */
    private void addThisAsListener() {
        PluginImpl plugin = PluginImpl.getInstance();
        if (plugin == null) {
            return;
        }
        GerritServer server = plugin.getServer(serverName);
        if (server != null) {
            server.addListener(this.connectionListener());
            connected.set(server.isConnected());
        } else {
            logger.error("Could not find the server {}", serverName);
        }
    }

    /**
     * This as ConnectionListener.
     * @return This as ConnectionListener.
     */
    private ConnectionListener connectionListener() {
        return this;
    }

    /**
     * This as GerritEventListener.
     * @return This as GerritEventListener.
     */
    private GerritEventListener gerritEventListener() {
        return this;
    }

    @Override
    public synchronized void connectionEstablished() {
        setConnected(true);
        notify();
    }

    @Override
    public synchronized void connectionDown() {
        setConnected(false);
    }

    @Override
    public void gerritEvent(GerritEvent gerritEvent) {
    }

    /**
     * OverLoaded gerritEvent(GerritEvent gerritEvent).
     * @param gerritEvent the event.
     */
    public void gerritEvent(ProjectCreated gerritEvent) {
        addGerritProject(gerritEvent.getProjectName());
        logger.debug("Added project {} to project lists", gerritEvent.getProjectName());
    }

    /**
     * Shutdown the thread.
     */
    public synchronized void shutdown() {
        shutdown = true;
        notify();
    }

    @Override
    public void run() {
        // Never query this Gerrit-server for project list.
        if (!getConfig().isEnableProjectAutoCompletion()) {
            return;
        }
        if (getConfig().getProjectListFetchDelay() == 0) {
            tryLoadProjectList();
        } else {
            waitFor(getConfig().getProjectListFetchDelay());
            tryLoadProjectList();
        }

        if (listenToProjectCreatedEvents()) {
            logger.info("ProjectCreated events supported by Gerrit Server {}. "
                    + "Will now listen for new projects...", serverName);
        } else {
            while (!shutdown) {
                waitFor(getConfig().getProjectListRefreshInterval());
                tryLoadProjectList();
            }
        }
    }

    /**
     * Add this as GerritEventListener if project events supported.
     * @return true is project created events are supported and listener is added.
     */
    private boolean listenToProjectCreatedEvents() {
        // Listen to project-created events.
        PluginImpl plugin = PluginImpl.getInstance();
        if (plugin != null) {
            GerritServer server = plugin.getServer(serverName);
            if (server != null) {
                if (server.isProjectCreatedEventsSupported()) {
                    // If run was called before.
                    server.removeListener(this.gerritEventListener());
                    server.addListener(this.gerritEventListener());
                    return true;
                }
            } else {
                logger.error("Could not find the server {} to add GerritEventListener.", serverName);
            }
        }
        return false;
    }

    /**
     * Wait for 'delay' seconds.
     * @param delay seconds to wait.
     */
    private void waitFor(long delay) {
        try {
            synchronized (this) {
                long startTime = System.nanoTime();
                // Continuously refetching the refresh interval allows us to pick up configuration
                // changes on the fly, keeping us from getting stuck on accidentally entered very
                // high values.
                while (System.nanoTime() - startTime
                        < TimeUnit.SECONDS.toNanos(delay)
                        && !shutdown) {
                    wait(TimeUnit.SECONDS.toMillis(1));
                }
            }
        } catch (InterruptedException ex) {
            logger.warn("InterruptedException: ", ex);
        }
    }

    /**
     * Try to load entire project list from Gerrit server.
     */
    private void tryLoadProjectList() {
        int interval = 1;
        while (!isConnected() && !shutdown) {
            logger.info("Not connected to {}, waiting for {} second(s)", serverName, interval);
            waitFor(interval);
            if (interval < MAX_WAIT_TIME) {
                interval = interval * 2;
            }
        }
        try {
            if (isConnected()) {
                logger.info("Trying to load project list.");
                IGerritHudsonTriggerConfig activeConfig = getConfig();
                SshConnection sshConnection = SshConnectionFactory.getConnection(
                        activeConfig.getGerritHostName(),
                        activeConfig.getGerritSshPort(),
                        activeConfig.getGerritProxy(),
                        activeConfig.getGerritAuthentication()
                );
                List<String> projects = readProjects(sshConnection.executeCommandReader(GERRIT_LS_PROJECTS));
                if (projects.size() > 0) {
                    setGerritProjects(projects);
                    logger.info("Project list from {} contains {} entries", serverName, projects.size());
                } else {
                    logger.warn("Project list from {} contains 0 projects", serverName);
                }
                sshConnection.disconnect();
            } else {
                logger.warn("Could not connect to Gerrit server when updating Gerrit project list: "
                    + "Server is not connected (timeout)");
            }
        } catch (SshException ex) {
            logger.warn("Could not connect to Gerrit server when updating Gerrit project list: ", ex);
        } catch (IOException ex) {
            logger.error("Could not read stream with Gerrit projects: ", ex);
        }
    }

    /**
     * Get the the server config.
     * @return the server config or null if config not found.
     */
    private IGerritHudsonTriggerConfig getConfig() {
        PluginImpl plugin = PluginImpl.getInstance();
        if (plugin != null) {
            GerritServer server = plugin.getServer(serverName);
            if (server != null) {
                IGerritHudsonTriggerConfig config = server.getConfig();
                if (config != null) {
                    return config;
                } else {
                    logger.error("Could not find the server config");
                }
            } else {
                logger.error("Could not find server {}", serverName);
            }
        }
        return null;
    }

    /**
     * Reads a list of Gerrit projects from a Reader, one project per line. As from command: gerrit ls-projects.
     * @param commandReader the Reader.
     * @return a list of projects
     * @throws IOException if something unfortunate happens.
     */
    public static List<String> readProjects(Reader commandReader) throws IOException {
        List<String> projects = new ArrayList<String>();
        BufferedReader br = new BufferedReader(commandReader);
        String line = br.readLine();

        while (line != null) {
            projects.add(line);
            line = br.readLine();
        }
        return projects;
    }

    /**
     * @return if connected to Gerrit.
     */
    public synchronized boolean isConnected() {
        return connected.get();
    }

    /**
     * @param connected the connected to set.
     */
    public synchronized void setConnected(boolean connected) {
        this.connected.set(connected);
    }

    /**
     * Adds a Gerrit project to this.gerritProjects.
     * @param gerritProject the Gerrit project to add.
     */
    public synchronized void addGerritProject(String gerritProject) {
        gerritProjects.add(gerritProject);
    }

    /**
     * Sets the internal Gerrit project list.
     * @param projects The list of projects
     */
    public synchronized void setGerritProjects(List<String> projects) {
        gerritProjects = projects;
    }

    /**
     * Returns a string list of Gerrit projects.
     * @return list of gerrit projects
     */
    public synchronized List<String> getGerritProjects() {
        if (gerritProjects == null) {
            gerritProjects = new ArrayList<String>();
        }
        return gerritProjects;
    }

    @Override
    public String getDisplayName() {
        return StringUtil.getDefaultDisplayNameForSpecificServer(this, getServerName());
    }
}
