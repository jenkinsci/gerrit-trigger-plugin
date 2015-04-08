/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
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

import com.sonymobile.tools.gerrit.gerritevents.ConnectionListener;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class responsible for providing the Config object with a list of all
 * available Gerrit projects. Executed periodically on a timely basis
 * and every time the connection to the Gerrit server has been restored.
 *
 * @author Gustaf Lundh &lt;Gustaf.Lundh@sonyericsson.com&gt;
 */
public class GerritProjectListUpdater extends Thread implements ConnectionListener {
    /**
     * The command for fetching projects.
     */
    public static final String GERRIT_LS_PROJECTS = "gerrit ls-projects";

    private boolean connected = false;
    private boolean shutdown = false;
    private static final Logger logger = LoggerFactory.getLogger(GerritProjectListUpdater.class);
    private List<String> gerritProjects;
    private String serverName;

    /**
     * Standard constructor.
     *
     * @param serverName the name of the Gerrit server.
     */
    public GerritProjectListUpdater(String serverName) {
        this.setName(this.getClass().getName() + " for " + serverName + " Thread");
        this.setDaemon(true);
        this.serverName = serverName;
        addThisAsListener();
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
            server.addListener(this);
            connected = server.isConnected();
        } else {
            logger.error("Could not find the server {}", serverName);
        }
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

    /**
     * Shutdown the thread.
     */
    public synchronized void shutdown() {
        shutdown = true;
        notify();
    }

    @Override
    public void run() {
        // Zero or negative value, never query this Gerrit-server for project list
        if (getConfig().getProjectListRefreshInterval() <= 0) {
            return;
        }

        boolean loadProjectList = getConfig().isLoadProjectListOnStartup();
        while (!shutdown) {
            try {
                if (loadProjectList) {
                    if (isConnected()) {
                        IGerritHudsonTriggerConfig activeConfig = getConfig();
                        SshConnection sshConnection = SshConnectionFactory.getConnection(
                                activeConfig.getGerritHostName(),
                                activeConfig.getGerritSshPort(),
                                activeConfig.getGerritProxy(),
                                activeConfig.getGerritAuthentication()
                        );
                        setGerritProjects(readProjects(sshConnection.executeCommandReader(GERRIT_LS_PROJECTS)));
                        sshConnection.disconnect();
                    }
                } else {
                    loadProjectList = true;
                }
            } catch (SshException ex) {
                 logger.warn("Could not connect to Gerrit server when updating Gerrit project list: ", ex);
            } catch (IOException ex) {
                logger.error("Could not read stream with Gerrit projects: ", ex);
            }

            try {
                synchronized (this) {
                    long startTime = System.nanoTime();
                    // Continuously refetching the refresh interval allows us to pick up configuration
                    // changes on the fly, keeping us from getting stuck on accidentally entered very
                    // high values.
                    while (System.nanoTime() - startTime
                            < TimeUnit.SECONDS.toNanos(getConfig().getProjectListRefreshInterval())
                            && !shutdown) {
                        wait(TimeUnit.SECONDS.toMillis(1));
                    }
                }
            } catch (InterruptedException ex) {
                logger.warn("InterruptedException: ", ex);
                break;
            }
        }
        GerritServer server = PluginImpl.getServer_(serverName);
        if (server != null) {
             server.removeListener(this);
        } else {
            logger.error("Could not find server {}", serverName);
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
        return connected;
    }

    /**
     * @param connected the connected to set.
     */
    public synchronized void setConnected(boolean connected) {
        this.connected = connected;
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
}

