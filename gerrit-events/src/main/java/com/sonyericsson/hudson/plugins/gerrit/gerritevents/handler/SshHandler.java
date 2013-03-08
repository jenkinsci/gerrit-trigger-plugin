/*
 *  The MIT License
 *
 *  Copyright 2013 rinrinne <rinrin.ne@gmail.com>
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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.handler;

//CS IGNORE LineLength FOR NEXT 6 LINES. REASON: static import.
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_AUTH_KEY_FILE;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_AUTH_KEY_FILE_PASSWORD;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_HOSTNAME;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_SSH_PORT;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_USERNAME;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_NR_OF_RECEIVING_WORKER_THREADS;

//CS IGNORE LineLength FOR NEXT 6 LINES. REASON: static import.
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PROVIDER;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.NAME;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.HOST;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PROTO;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PORT;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.VERSION;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritConnectionConfig;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshAuthenticationException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectionFactory;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.StreamEventsStringWork;

/**
 * Handler class for Gerrit stream events. Contains the main loop for connecting and
 * reading gerrit stream events.
 *
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public final class SshHandler extends AbstractHandler {
    private static final String CMD_STREAM_EVENTS = "gerrit stream-events";
    private static final String CMD_VERSION = "gerrit version";
    private static final String GERRIT_NAME = "gerrit";
    private static final String GERRIT_PROTOCOL_NAME = "ssh";
    private static final String GERRIT_VERSION_PREFIX = "gerrit version ";

    private static final Logger logger = LoggerFactory.getLogger(SshHandler.class);
    private final String gerritHostName;
    private final int gerritSshPort;
    private final Authentication authentication;

    private SshConnection sshConnection;

    /**
     * Creates a GerritHandler with all the default values set.
     *
     * @see GerritDefaultValues#DEFAULT_GERRIT_HOSTNAME
     * @see GerritDefaultValues#DEFAULT_GERRIT_SSH_PORT
     * @see GerritDefaultValues#DEFAULT_GERRIT_USERNAME
     * @see GerritDefaultValues#DEFAULT_GERRIT_AUTH_KEY_FILE
     * @see GerritDefaultValues#DEFAULT_GERRIT_AUTH_KEY_FILE_PASSWORD
     * @see GerritDefaultValues#DEFAULT_NR_OF_RECEIVING_WORKER_THREADS
     */
    public SshHandler() {
        this(
                DEFAULT_GERRIT_HOSTNAME,
                DEFAULT_GERRIT_SSH_PORT,
                new Authentication(DEFAULT_GERRIT_AUTH_KEY_FILE,
                        DEFAULT_GERRIT_USERNAME,
                        DEFAULT_GERRIT_AUTH_KEY_FILE_PASSWORD),
                DEFAULT_NR_OF_RECEIVING_WORKER_THREADS,
                "");
    }

    /**
     * Creates a SshHandler with the specified value.
     *
     * @param config the configuration containing the connection values.
     */
    public SshHandler(GerritConnectionConfig config) {
        this(
                config.getGerritHostName(),
                config.getGerritSshPort(),
                config.getGerritAuthentication(),
                config.getNumberOfReceivingWorkerThreads(),
                config.getGerritEMail());
    }

    /**
     * Creates a GerritHandler with the specified values.
     *
     * @param gerritHostName        the hostName for gerrit.
     * @param gerritSshPort         the ssh port that the gerrit server listens to.
     * @param authentication        the authentication credentials.
     * @param numberOfWorkerThreads the number of eventthreads.
     * @param ignoreEMail           the e-mail to ignore events from.
     */
    public SshHandler(
            String gerritHostName,
            int gerritSshPort,
            Authentication authentication,
            int numberOfWorkerThreads,
            String ignoreEMail) {
        super(numberOfWorkerThreads, ignoreEMail);
        this.gerritHostName = gerritHostName;
        this.gerritSshPort = gerritSshPort;
        this.authentication = authentication;
    }

    @Override
    protected void listenEvent() {
        sshConnection = connect();
        if (sshConnection == null) {
            return;
        }

        BufferedReader br = null;
        try {
            logger.trace("Executing stream-events command.");
            Reader reader = sshConnection.executeCommandReader(CMD_STREAM_EVENTS);
            br = new BufferedReader(reader);
            String line = "";
            Map<String, String> providerValueMap = new LinkedHashMap<String, String>() {
                {
                    put(NAME, GERRIT_NAME);
                    put(HOST, gerritHostName);
                    put(PORT, String.valueOf(gerritSshPort));
                    put(PROTO, GERRIT_PROTOCOL_NAME);
                    put(VERSION, getGerritVersionString());
                }
            };
            logger.info("Ready to receive data from Gerrit");
            notifyConnectionEstablished();
            do {
                logger.debug("Data-line from Gerrit: {}", line);
                if (line != null && line.length() > 0) {
                    try {
                        StreamEventsStringWork work = new StreamEventsStringWork(line, PROVIDER, providerValueMap);
                        logger.trace("putting work on queue: {}", work);
                        getWorkQueue().put(work);
                    } catch (InterruptedException ex) {
                        logger.warn("Interrupted while putting work on queue!", ex);
                        if (shutdownRequested) {
                            throw ex;
                        }
                    }
                }
                logger.trace("Reading next line.");
                line = br.readLine();
            } while (!shutdownRequested || line != null);
        } catch (IOException ex) {
            logger.error("Stream events command error. ", ex);
        } catch (InterruptedException ex) {
            logger.warn("Shutdown aleady requested when interrupted while putting work on queue!", ex);
        } finally {
            if (shutdownRequested) {
                logger.warn("Shutdown requested.");
            }
            logger.trace("Connection closed, ended read loop.");
            try {
                sshConnection.disconnect();
            } catch (Exception ex) {
                logger.warn("Error when disconnecting sshConnection.", ex);
            }
            sshConnection = null;
            notifyConnectionDown();
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ex) {
                    logger.warn("Could not close events reader.", ex);
                }
            }
        }
    }

    /**
     * Connects to the Gerrit.
     *
     * @return not null if everything is well, null if connect and reconnect failed.
     */
    private SshConnection connect() {
        SshConnection ssh = null;
        boolean connectionSucceeded = false;

        while (!shutdownRequested) {
            try {
                logger.debug("Connecting...");
                ssh = SshConnectionFactory.getConnection(gerritHostName, gerritSshPort, authentication);
                notifyConnectionEstablished();
                setGerritVersion(formatVersion(ssh.executeCommand(CMD_VERSION)));
                connectionSucceeded = true;
                logger.debug("connection seems ok, returning it.");
            } catch (SshConnectException sshConEx) {
                logger.error("Could not connect to Gerrit server! "
                        + "Host: {} Port: {}", gerritHostName, gerritSshPort);
                logger.error(" User: {} KeyFile: {}", authentication.getUsername(), authentication.getPrivateKeyFile());
                logger.error("ConnectionException: ", sshConEx);
                notifyConnectionDown();
            } catch (SshAuthenticationException sshAuthEx) {
                logger.error("Could not authenticate to Gerrit server!"
                        + "\n\tUsername: {}\n\tKeyFile: {}\n\tPassword: {}",
                        new Object[]{authentication.getUsername(),
                                authentication.getPrivateKeyFile(),
                                authentication.getPrivateKeyFilePassword(), });
                logger.error("AuthenticationException: ", sshAuthEx);
                notifyConnectionDown();
            } catch (IOException ex) {
                logger.error("Could not connect to Gerrit server! "
                        + "Host: {} Port: {}", gerritHostName, gerritSshPort);
                logger.error(" User: {} KeyFile: {}", authentication.getUsername(), authentication.getPrivateKeyFile());
                logger.error("IOException: ", ex);
                notifyConnectionDown();
            }

            if (!connectionSucceeded) {
                logger.trace("Disconnecting bad connection.");
                try {
                    //The ssh lib used is starting at least one thread for each connection.
                    //The thread isn't shutdown properly when the connection goes down,
                    //so we need to close it "manually"
                    if (ssh != null) {
                        ssh.disconnect();
                    }
                } catch (Exception ex) {
                    logger.warn("Error when disconnecting bad connection.", ex);
                } finally {
                    ssh = null;
                }
            } else {
                break;
            }

            //If we end up here, sleep for a while and then go back up in the loop.
            logger.trace("Sleeping for a bit.");
            try {
                Thread.sleep(CONNECT_SLEEP);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                logger.warn("Got interrupted while sleeping.", ex);
                if (shutdownRequested) {
                    return null;
                }
            }
        }
        return ssh;
    }

    /**
     * Removes the "gerrit version " from the start of the response from gerrit.
     *
     * @param version the response from gerrit.
     * @return the input string with "gerrit version " removed.
     */
    private String formatVersion(String version) {
        if (version == null) {
            return version;
        }
        String[] split = version.split(GERRIT_VERSION_PREFIX);
        if (split.length < 2) {
            return version.trim();
        }
        return split[1].trim();
    }

    /**
     * Gets the gerrit version.
     * @return the gerrit version as valid string.
     */
    private String getGerritVersionString() {
        String version = getGerritVersion();
        if (version == null) {
            version = "";
        }
        return version;
    }
}
