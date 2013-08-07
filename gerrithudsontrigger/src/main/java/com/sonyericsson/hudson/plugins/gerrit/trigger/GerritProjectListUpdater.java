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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ConnectionListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectionFactory;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshException;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * Time to wait between refresh attempts
     */
    private static final int UPDATE_DELAY = 3600 * 1000;
    private boolean connected = false;
    private boolean shutdown = false;
    private static final Logger logger = LoggerFactory.getLogger(GerritProjectListUpdater.class);
    private List<String> gerritProjects;

    /**
     * Standard constructor.
     */
    public GerritProjectListUpdater() {
        this.setName(this.getClass().getName() + " Thread");
        this.setDaemon(true);

        connected = PluginImpl.getInstance().addListener(this);
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
        while (!shutdown) {
            try {
                if (PluginImpl.getInstance() != null && PluginImpl.getInstance().getConfig() != null && isConnected()) {
                    IGerritHudsonTriggerConfig activeConfig = PluginImpl.getInstance().getConfig();
                    SshConnection sshConnection = SshConnectionFactory.getConnection(
                            activeConfig.getGerritHostName(),
                            activeConfig.getGerritSshPort(),
                            activeConfig.getGerritProxy(),
                            activeConfig.getGerritAuthentication()
                    );
                    setGerritProjects(readProjects(sshConnection.executeCommandReader(GERRIT_LS_PROJECTS)));
                    sshConnection.disconnect();
                }
            } catch (SshException ex) {
                 logger.warn("Could not connect to Gerrit server when updating Gerrit project list: ", ex);
            } catch (IOException ex) {
                logger.error("Could not read stream with Gerrit projects: ", ex);
            }

            try {
                synchronized (this) {
                    wait(UPDATE_DELAY);
                }
            } catch (InterruptedException ex) {
                logger.warn("InterruptedException: ", ex);
                break;
            }
        }
        PluginImpl.getInstance().removeListener(this);
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

