/*
 * The MIT License
 *
 * Copyright 2011 Sony Ericsson Mobile Communications. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import com.jcraft.jsch.ProxySOCKS5;
import com.jcraft.jsch.ProxyHTTP;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;

/**
 * A simple ssh client connection with private key.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class SshConnectionImpl implements SshConnection {

    private static final Logger logger = LoggerFactory.getLogger(SshConnectionImpl.class);
    /**
     * Keep-alive interval [msec]
     */
    private static final int ALIVE_INTERVAL = 30 * 1000;
    /**
     * SSH Command to open an "exec channel".
     */
    protected static final String CMD_EXEC = "exec";
    /**
     * The str length of "://" used for proxy parsing.
     */
    protected static final int PROTO_HOST_DELIM_LENGTH = 3;
    private final JSch client;
    private Session connectSession;

    //CS IGNORE RedundantThrows FOR NEXT 30 LINES. REASON: Informative

    /**
     * Creates and opens a SshConnection.
     *
     * @param host           the host to connect to.
     * @param port           the port.
     * @param authentication the authentication-info
     * @throws IOException if the unfortunate happens.
     */
    protected SshConnectionImpl(String host, int port,
                                Authentication authentication) throws IOException {
        this(host, port, GerritDefaultValues.DEFAULT_GERRIT_PROXY, authentication);
    }

    /**
     * Creates and opens a SshConnection.
     *
     * @param host           the host to connect to.
     * @param port           the port.
     * @param proxy          the proxy url.
     * @param authentication the authentication-info
     * @throws IOException   if the unfortunate happens.
     */
    protected SshConnectionImpl(String host, int port, String proxy,
                                Authentication authentication) throws IOException {
        logger.debug("connecting...");
        try {
            client = new JSch();
            client.addIdentity(authentication.getPrivateKeyFile().getAbsolutePath(),
                    authentication.getPrivateKeyFilePassword());
            client.setHostKeyRepository(new BlindHostKeyRepository());
            connectSession = client.getSession(authentication.getUsername(), host, port);
            if (proxy != null && !proxy.isEmpty()) {
                String[] splitted = proxy.split(":");
                if (splitted.length > 2 && splitted[1].length() >= PROTO_HOST_DELIM_LENGTH) {
                    String pproto = splitted[0];
                    String phost = splitted[1].substring(2);
                    int pport = Integer.parseInt(splitted[2]);
                    if (pproto.equals("socks5") || pproto.equals("http")) {
                        if (pproto.equals("socks5")) {
                             connectSession.setProxy(new ProxySOCKS5(phost, pport));
                        } else {
                             connectSession.setProxy(new ProxyHTTP(phost, pport));
                        }
                    } else {
                        throw new MalformedURLException("Only http and socks5 protocols are supported");
                    }
                } else {
                    throw new MalformedURLException(proxy);
                }
            }
            connectSession.connect();
            logger.debug("Connected: {}", connectSession.isConnected());
            connectSession.setServerAliveInterval(ALIVE_INTERVAL);
        } catch (JSchException ex) {
            throw new SshException(ex);
        }
    }

    /**
        * Is the connection connected.
        *
        * @return true if it is so.
        */
    @Override
    public synchronized boolean isConnected() {
        //Cannot distinguish connected or authenticated with the "new" API
        return isAuthenticated();
    }

    /**
        * Is the connection authenticated.
        *
        * @return true if it is so.
        */
    @Override
    public synchronized boolean isAuthenticated() {
        return client != null && connectSession != null && connectSession.isConnected();
    }

    /**
        * Execute an ssh command on the server.
        * After the command is sent the used channel is disconnected.
        *
        * @param command the command to execute.
        * @return a String containing the output from the command.
         * @throws SshException if so.
         */
    @Override
    public synchronized String executeCommand(String command) throws SshException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected!");
        }
        try {
            logger.debug("Opening channel");
            Channel channel = connectSession.openChannel(CMD_EXEC);
            ((ChannelExec)channel).setCommand(command);

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(channel.getInputStream()));
            logger.debug("connecting channel.");
            channel.connect();

            // Seems like Gerrit does not like when you disconnect directly after the command has been sent.
            // For instance, we have seen effects of mails not being sent out. This is the reason for
            // receiving all the incoming data.
            String incomingLine = null;
            StringBuilder commandOutput = new StringBuilder();
            while ((incomingLine = bufferedReader.readLine()) != null) {
                commandOutput.append(incomingLine);
                commandOutput.append('\n');
                logger.trace("Incoming line: {}", incomingLine);
            }
            logger.trace("Closing reader.");
            bufferedReader.close();
            logger.trace("disconnecting channel.");
            channel.disconnect();

            return commandOutput.toString();
        } catch (JSchException ex) {
            throw new SshException(ex);
        } catch (IOException ex) {
            throw new SshException(ex);
        }
    }

    //CS IGNORE RedundantThrows FOR NEXT 14 LINES. REASON: Informative.

    /**
     * Execute an ssh command on the server, without closing the session
     * so that a Reader can be returned with streaming data from the server.
     *
     * @param command the command to execute.
     * @return a Reader with streaming data from the server.
     * @throws IOException  if it is so.
     * @throws SshException if there are any ssh problems.
     */
    @Override
    public synchronized Reader executeCommandReader(String command) throws SshException, IOException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected!");
        }
        try {
            Channel channel = connectSession.openChannel("exec");
            ((ChannelExec)channel).setCommand(command);
            InputStreamReader reader = new InputStreamReader(channel.getInputStream());
            channel.connect();
            return reader;
        } catch (JSchException ex) {
            throw new SshException(ex);
        }
    }

    //CS IGNORE RedundantThrows FOR NEXT 15 LINES. REASON: Informative.

    /**
     * This version takes a command to run, and then returns a wrapper instance
     * that exposes all the standard state of the channel (stdin, stdout,
     * stderr, exit status, etc).
     *
     * @param command the command to execute.
     * @return a Channel with access to all streams and the exit code.
     * @throws IOException  if it is so.
     * @throws SshException if there are any ssh problems.
     * @see #executeCommandReader(String)
     */
    @Override
    public synchronized ChannelExec executeCommandChannel(String command) throws SshException, IOException {
        if (!isConnected()) {
            throw new IOException("Not connected!");
        }
        try {
            ChannelExec channel = (ChannelExec)connectSession.openChannel("exec");
            channel.setCommand(command);
            channel.connect();
            return channel;
        } catch (JSchException ex) {
            throw new SshException(ex);
        }
    }

    /**
        * Disconnects the connection.
        */
    @Override
    public synchronized void disconnect() {
        if (connectSession != null) {
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
