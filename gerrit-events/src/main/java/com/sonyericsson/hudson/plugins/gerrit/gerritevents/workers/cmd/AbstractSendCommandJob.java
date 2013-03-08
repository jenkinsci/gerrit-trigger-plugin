/*
 * The MIT License
 *
 * Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.cmd;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritCmdRunner;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritConnectionConfig;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritConnectionConfig2;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstract Job implementation
 * to be scheduled on {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritSendCommandQueue}.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public abstract class AbstractSendCommandJob implements Runnable, GerritCmdRunner {

    /**
     * An instance of a logger for sub-classes to use.
     */
    protected static Logger logger = LoggerFactory.getLogger(AbstractSendCommandJob.class);

    private GerritConnectionConfig2 config;

    /**
     * Standard constructor taking the latest configuration.
     * @param config the connection config.
     */
    protected AbstractSendCommandJob(GerritConnectionConfig2 config) {
        this.config = config;
    }

    /**
     * Gets the connection config used when sending a command.
     * @return the config.
     */
    public GerritConnectionConfig getConfig() {
        return config;
    }

    /**
     * Sends a command to the Gerrit server.
     * @param command the command.
     * @return true if there were no exceptions when sending.
     */
    @Override
    public boolean sendCommand(String command) {
        try {
            SshConnection ssh = SshConnectionFactory.getConnection(config.getGerritHostName(),
                    config.getGerritSshPort(), config.getGerritProxy(), config.getGerritAuthentication());
            ssh.executeCommand(command);
            ssh.disconnect();
            return true;
        } catch (Exception ex) {
            logger.error("Could not run command " + command, ex);
            return false;
        }
    }

    /**
     * Sends a command to the Gerrit server, returning the output from the command.
     * @param command the command.
     * @return the output from the command.
     */
    @Override
    public String sendCommandStr(String command) {
        try {
            SshConnection ssh = SshConnectionFactory.getConnection(config.getGerritHostName(),
                    config.getGerritSshPort(), config.getGerritProxy(), config.getGerritAuthentication());
            String str = ssh.executeCommand(command);
            ssh.disconnect();
            return str;
        } catch (Exception ex) {
            logger.error("Could not run command " + command, ex);
            return null;
        }
    }
}
