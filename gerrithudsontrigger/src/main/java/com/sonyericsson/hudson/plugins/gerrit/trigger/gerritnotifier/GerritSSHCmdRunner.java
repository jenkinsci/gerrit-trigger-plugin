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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier;

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs SSH commands on the Gerrit server.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritSSHCmdRunner {

    private static final Logger logger = LoggerFactory.getLogger(GerritSSHCmdRunner.class);

    private IGerritHudsonTriggerConfig config;

    /**
     * Constructor.
     * @param config the global configuration.
     */
    public GerritSSHCmdRunner(IGerritHudsonTriggerConfig config) {
        this.config = config;
    }

    /**
     * Runs a command on the gerrit ssh server.
     * Any exception thet could happen is hidded but logged.
     * @param cmd the sommand.
     * @return true if the command was successful, false otherwise.
     */
    public boolean runCmd(String cmd) {
        try {
            SshConnection ssh = new SshConnection(config.getGerritHostName(),
                    config.getGerritSshPort(), config.getGerritAuthentication());
                ssh.executeCommand(cmd);
                return true;
        } catch (Exception ex) {
            logger.error("Could not run command " + cmd, ex);
            return false;
        }
    }
}
