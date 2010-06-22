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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple ssh client connection with private key.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class SshConnection {

    private static final Logger logger = LoggerFactory.getLogger(SshConnection.class);
    private final JSch client;
    private Session connectSession;
    private Channel currentSession;

    //CS IGNORE RedundantThrows FOR NEXT 10 LINES. REASON: Informative
    /**
     * Creates and opens a SshConnection.
     * @param host the host to connect to.
     * @param port the port.
     * @param authentication the authentication-info
     * @throws SshException if something happens - usually due to bad config.
     * @throws IOException if the unfortunate happens.
     */
    public SshConnection(String host, int port, Authentication authentication) throws SshException, IOException {
        logger.debug("connecting...");
        try {
            client = new JSch();
            client.addIdentity(authentication.getPrivateKeyFile().getAbsolutePath(),
                               authentication.getPrivateKeyFilePassword());
            client.setHostKeyRepository(new BlindHostKeyRepository());
            connectSession = client.getSession(authentication.getUsername(), host, port);
            connectSession.connect();
            logger.debug("Connected: {}", connectSession.isConnected());
        } catch (JSchException ex) {
            throw new SshException(ex);
        }
    }

    /**
     * Is the connection connected.
     * @return true if it is so.
     */
    public synchronized boolean isConnected() {
        //Cannot distinguish connected or authenticated with the "new" API
        return isAuthenticated();
    }

    /**
     * Is the connection authenticated.
     * @return true if it is so.
     */
    public synchronized boolean isAuthenticated() {
        return client != null && connectSession != null && connectSession.isConnected();
    }

    /**
     * Returns if there already is an open session on this connection.
     * @return true if it is so.
     */
    public synchronized boolean isSessionOpen() {
        return currentSession != null && currentSession.isConnected() && !currentSession.isEOF();
    }

    /**
     * Execute an ssh command on the server.
     * @param command the command to execute.
     * @throws SshException if so.
     */
    public synchronized void executeCommand(String command) throws SshException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected!");
        }
        logger.debug("Executing command: \"{}\"", command);
        try {
            Channel channel = connectSession.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);
            channel.connect();
        } catch (JSchException ex) {
            throw new SshException(ex);
        }
    }

    //CS IGNORE RedundantThrows FOR NEXT 11 LINES. REASON: Informative.
    /**
     * Execute an ssh command on the server, without closing the session
     * so that a Reader can be returned with streaming data from the server.
     * @param command the command to execute.
     * @return a Reader with streaming data from the server.
     * @throws IOException if it is so.
     * @throws SshException if there are any ssh problems.
     */
    public synchronized Reader executeCommandReader(String command) throws SshException, IOException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected!");
        }
        try {
            Channel channel = connectSession.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);
            InputStreamReader reader = new InputStreamReader(channel.getInputStream());
            channel.connect();
            return reader;
        } catch (JSchException ex) {
            throw new SshException(ex);
        }
    }

    /**
     * Disconnects the connection.
     */
    public synchronized void disconnect() {
        if (isSessionOpen()) {
            logger.debug("Closing ssh session.");

            currentSession.disconnect();
            currentSession = null;
        }
        if (isConnected()) {
            logger.debug("Disconnecting client connection.");
            connectSession.disconnect();
            connectSession = null;
        }
    }

    /**
     * A KnownHosts repository that blindly exepts any host fingerprint as OK.
     */
    static class BlindHostKeyRepository implements HostKeyRepository {
        private static final HostKey[] EMPTY = new HostKey[0];

        @Override
        public int check(String host, byte[] key) {
            return HostKeyRepository.OK;
        }

        @Override
        public void add(HostKey hostkey, UserInfo ui) {
        }

        @Override
        public void remove(String host, String type) {
        }

        @Override
        public void remove(String host, String type, byte[] key) {
        }

        @Override
        public String getKnownHostsRepositoryID() {
            return "";
        }

        @Override
        public HostKey[] getHostKey() {
            return EMPTY;
        }

        @Override
        public HostKey[] getHostKey(String host, String type) {
            return EMPTY;
        }

    }
}
