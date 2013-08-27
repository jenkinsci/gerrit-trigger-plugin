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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Provider;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshAuthenticationException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectionFactory;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.StreamWatchdog;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData;


//CS IGNORE LineLength FOR NEXT 7 LINES. REASON: static import.
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_AUTH_KEY_FILE;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_AUTH_KEY_FILE_PASSWORD;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_NAME;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_HOSTNAME;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_SSH_PORT;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_PROXY;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_USERNAME;

/**
 * Main class for connection. Contains the main loop for connecting to Gerrit.
 *
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public class GerritConnection extends Thread implements Connector {

    /**
     * Time to wait between connection attempts.
     */
    public static final int CONNECT_SLEEP = 2000;
    private static final String CMD_STREAM_EVENTS = "gerrit stream-events";
    private static final String GERRIT_VERSION_PREFIX = "gerrit version ";
    private static final String GERRIT_PROTOCOL_NAME = "ssh";
    private static final Logger logger = LoggerFactory.getLogger(GerritConnection.class);
    private String gerritName;
    private String gerritHostName;
    private int gerritSshPort;
    private String gerritProxy;
    private Authentication authentication;
    private SshConnection sshConnection;
    private volatile boolean shutdownInProgress = false;
    private volatile boolean connected = false;
    private String gerritVersion = null;
    private int watchdogTimeoutSeconds;
    private WatchTimeExceptionData exceptionData;
    private StreamWatchdog watchdog;
    private int reconnectCallCount = 0;
    private GerritHandler handler;

    /**
     * Creates a GerritHandler with all the default values set.
     *
     * @see GerritDefaultValues#DEFAULT_GERRIT_NAME
     * @see GerritDefaultValues#DEFAULT_GERRIT_HOSTNAME
     * @see GerritDefaultValues#DEFAULT_GERRIT_SSH_PORT
     * @see GerritDefaultValues#DEFAULT_GERRIT_PROXY
     * @see GerritDefaultValues#DEFAULT_GERRIT_USERNAME
     * @see GerritDefaultValues#DEFAULT_GERRIT_AUTH_KEY_FILE
     * @see GerritDefaultValues#DEFAULT_GERRIT_AUTH_KEY_FILE_PASSWORD
     */
    public GerritConnection() {
        this(DEFAULT_GERRIT_NAME,
                DEFAULT_GERRIT_HOSTNAME,
                DEFAULT_GERRIT_SSH_PORT,
                DEFAULT_GERRIT_PROXY,
                new Authentication(DEFAULT_GERRIT_AUTH_KEY_FILE,
                        DEFAULT_GERRIT_USERNAME,
                        DEFAULT_GERRIT_AUTH_KEY_FILE_PASSWORD));
    }

    /**
     * Creates a GerritHandler with the specified values.
     *
     * @param gerritName the name of the gerrit server.
     * @param gerritHostName the hostName
     * @param gerritSshPort  the ssh port that the gerrit server listens to.
     * @param authentication the authentication credentials.
     */
    public GerritConnection(String gerritName,
                         String gerritHostName,
                         int gerritSshPort,
                         Authentication authentication) {
        this(gerritName,
                gerritHostName,
                gerritSshPort,
                DEFAULT_GERRIT_PROXY,
                authentication);
    }

    /**
     * Standard Constructor.
     *
     * @param gerritName the name of the gerrit server.
     * @param config the configuration containing the connection values.
     */
    public GerritConnection(String gerritName, GerritConnectionConfig config) {
        this(gerritName, config, DEFAULT_GERRIT_PROXY, 0, null);
    }

    /**
     * Standard Constructor.
     *
     * @param gerritName the name of the gerrit server.
     * @param config the configuration containing the connection values.
     */
    public GerritConnection(String gerritName, GerritConnectionConfig2 config) {
        this(gerritName, config, config.getGerritProxy(), config.getWatchdogTimeoutSeconds(), config.getExceptionData());
    }

    /**
     * Creates a GerritHandler with the specified values.
     *
     * @param gerritName             the name of the gerrit server.
     * @param config                 the configuration containing the connection values.
     * @param gerritProxy            the URL of gerrit proxy.
     * @param watchdogTimeoutSeconds number of seconds before the connection watch dog restarts the connection set to 0
     *                               or less to disable it.
     * @param exceptionData          time info for when the watch dog's timeout should not be in effect. set to null to
     *                               disable the watch dog.
     */
    public GerritConnection(String gerritName,
                         GerritConnectionConfig config,
                         String gerritProxy,
                         int watchdogTimeoutSeconds,
                         WatchTimeExceptionData exceptionData) {
        this(gerritName,
                config.getGerritHostName(),
                config.getGerritSshPort(),
                gerritProxy,
                config.getGerritAuthentication(),
                watchdogTimeoutSeconds, exceptionData);
    }

    /**
     * Standard Constructor.
     *
     * @param gerritName            the name of the gerrit server.
     * @param gerritHostName        the hostName for gerrit.
     * @param gerritSshPort         the ssh port that the gerrit server listens to.
     * @param gerritProxy           the proxy url socks5|http://host:port.
     * @param authentication        the authentication credentials.
     */
    public GerritConnection(String gerritName,
                         String gerritHostName,
                         int gerritSshPort,
                         String gerritProxy,
                         Authentication authentication) {
        this(gerritName, gerritHostName, gerritSshPort, gerritProxy, authentication, 0, null);
    }

    /**
     * Standard Constructor.
     *
     * @param gerritName              the name of the gerrit server.
     * @param gerritHostName          the hostName for gerrit.
     * @param gerritSshPort             the ssh port that the gerrit server listens to.
     * @param gerritProxy              the proxy url socks5|http://host:port.
     * @param authentication            the authentication credentials.
     * @param watchdogTimeoutSeconds number of seconds before the connection watch dog restarts the connection
     *                               set to 0 or less to disable it.
     * @param exceptionData           time info for when the watch dog's timeout should not be in effect.
     *                                  set to null to disable the watch dog.
     */
    public GerritConnection(String gerritName,
                         String gerritHostName,
                         int gerritSshPort,
                         String gerritProxy,
                         Authentication authentication,
                         int watchdogTimeoutSeconds,
                         WatchTimeExceptionData exceptionData) {
        this.gerritName = gerritName;
        this.gerritHostName = gerritHostName;
        this.gerritSshPort = gerritSshPort;
        this.gerritProxy = gerritProxy;
        this.authentication = authentication;
        this.watchdogTimeoutSeconds = watchdogTimeoutSeconds;
        this.exceptionData = exceptionData;
    }

    /**
     * Sets gerrit handler.
     *
     * @param handler the handler.
     */
    public void setHandler(GerritHandler handler) {
        this.handler = handler;
    }

    /**
     * Gets gerrit handler.
     *
     * @return the handler.
     */
    public GerritHandler getHandler() {
        return handler;
    }

    /**
     * The gerrit version we are connected to.
     *
     * @return the gerrit version.
     */
    public String getGerritVersion() {
        return gerritVersion;
    }

    /**
     * Main loop for connecting and reading Gerrit JSON Events and dispatching them to Workers.
     */
    @Override
    public void run() {
        logger.info("Starting Up " + gerritName);
        do {
            sshConnection = connect();
            if (sshConnection == null) {
                return;
            }
            if (watchdogTimeoutSeconds > 0 && exceptionData != null) {
                watchdog = new StreamWatchdog(this, watchdogTimeoutSeconds, exceptionData);
            }

            BufferedReader br = null;
            try {
                logger.trace("Executing stream-events command.");
                Reader reader = sshConnection.executeCommandReader(CMD_STREAM_EVENTS);
                br = new BufferedReader(reader);
                String line = "";
                Provider provider = new Provider(
                        gerritName,
                        gerritHostName,
                        String.valueOf(gerritSshPort),
                        GERRIT_PROTOCOL_NAME,
                        DEFAULT_GERRIT_HOSTNAME,
                        getGerritVersionString());
                logger.info("Ready to receive data from Gerrit: " + gerritName);
                notifyConnectionEstablished();
                do {
                    logger.debug("Data-line from Gerrit: {}", line);
                    if (line != null && line.length() > 0) {
                        if (handler != null) {
                            handler.post(line, provider);
                        }
                    }
                    logger.trace("Reading next line.");
                    line = br.readLine();
                    if (watchdog != null) {
                        watchdog.signal();
                    }
                } while (line != null);
            } catch (IOException ex) {
                logger.error("Stream events command error. ", ex);
            } finally {
                logger.trace("Connection closed, ended read loop.");
                try {
                    sshConnection.disconnect();
                } catch (Exception ex) {
                    logger.warn("Error when disconnecting sshConnection.", ex);
                }
                sshConnection = null;
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException ex) {
                        logger.warn("Could not close events reader.", ex);
                    }
                }
                notifyConnectionDown();
            }
        } while (!shutdownInProgress);
        handler = null;
        logger.debug("End of GerritHandler Thread.");
    }

    /**
     * Connects to the Gerrit server and authenticates as the specified user.
     *
     * @return not null if everything is well, null if connect and reconnect failed.
     */
    private SshConnection connect() {
        while (!shutdownInProgress) {
            SshConnection ssh = null;
            try {
                logger.debug("Connecting...");
                ssh = SshConnectionFactory.getConnection(gerritHostName, gerritSshPort, gerritProxy, authentication);
                gerritVersion  = formatVersion(ssh.executeCommand("gerrit version"));
                logger.debug("connection seems ok, returning it.");
                return ssh;
            } catch (SshConnectException sshConEx) {
                logger.error("Could not connect to Gerrit server! "
                        + "Host: {} Port: {}", gerritHostName, gerritSshPort);
                logger.error(" Proxy: {}", gerritProxy);
                logger.error(" User: {} KeyFile: {}", authentication.getUsername(), authentication.getPrivateKeyFile());
                logger.error("ConnectionException: ", sshConEx);
            } catch (SshAuthenticationException sshAuthEx) {
                logger.error("Could not authenticate to Gerrit server!"
                        + "\n\tUsername: {}\n\tKeyFile: {}\n\tPassword: {}",
                        new Object[]{authentication.getUsername(),
                                authentication.getPrivateKeyFile(),
                                authentication.getPrivateKeyFilePassword(), });
                logger.error("AuthenticationException: ", sshAuthEx);
            } catch (IOException ex) {
                logger.error("Could not connect to Gerrit server! "
                        + "Host: {} Port: {}", gerritHostName, gerritSshPort);
                logger.error(" Proxy: {}", gerritProxy);
                logger.error(" User: {} KeyFile: {}", authentication.getUsername(), authentication.getPrivateKeyFile());
                logger.error("IOException: ", ex);
            }

            if (ssh != null) {
                logger.trace("Disconnecting bad connection.");
                try {
                    //The ssh lib used is starting at least one thread for each connection.
                    //The thread isn't shutdown properly when the connection goes down,
                    //so we need to close it "manually"
                    ssh.disconnect();
                } catch (Exception ex) {
                    logger.warn("Error when disconnecting bad connection.", ex);
                } finally {
                    ssh = null;
                }
            }

            if (!shutdownInProgress) {
                //If we end up here, sleep for a while and then go back up in the loop.
                logger.trace("Sleeping for a bit.");
                try {
                    Thread.sleep(CONNECT_SLEEP);
                } catch (InterruptedException ex) {
                    logger.warn("Got interrupted while sleeping.", ex);
                }
            }
        }
        return null;
    }

    /**
     * Removes the "gerrit version " from the start of the response from gerrit.
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

    /**
     * The authentication credentials for ssh connection.
     *
     * @return the credentials.
     */
    public Authentication getAuthentication() {
        return authentication;
    }

    /**
     * The authentication credentials for ssh connection.
     *
     * @param authentication the credentials.
     */
    public void setAuthentication(Authentication authentication) {
        this.authentication = authentication;
    }

    /**
     * gets the hostname where Gerrit is running.
     *
     * @return the hostname.
     */
    public String getGerritHostName() {
        return gerritHostName;
    }

    /**
     * Sets the hostname where Gerrit is running.
     *
     * @param gerritHostName the hostname.
     */
    public void setGerritHostName(String gerritHostName) {
        this.gerritHostName = gerritHostName;
    }

    /**
     * Gets the port for gerrit ssh commands.
     *
     * @return the port nr.
     */
    public int getGerritSshPort() {
        return gerritSshPort;
    }

    /**
     * Sets the port for gerrit ssh commands.
     *
     * @param gerritSshPort the port nr.
     */
    public void setGerritSshPort(int gerritSshPort) {
        this.gerritSshPort = gerritSshPort;
    }

    /**
     * Gets the proxy for gerrit ssh commands.
     *
     * @return the proxy url.
     */
    public String getGerritProxy() {
        return gerritProxy;
    }

    /**
     * Sets the proxy for gerrit ssh commands.
     *
     * @param gerritProxy the port nr.
     */
    public void setGerritProxy(String gerritProxy) {
        this.gerritProxy = gerritProxy;
    }

    /**
     * Sets the shutdown flag.
     */
    private void setShutdownInProgress() {
            this.shutdownInProgress = true;
    }

    /**
     * If the system is shutting down. I.e. the shutdown method has been called.
     *
     * @return true if so.
     */
    public boolean isShutdownInProgress() {
            return shutdownInProgress;
    }

    /**
     * If already connected.
     * @return true if already connected.
     */
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void reconnect() {
        reconnectCallCount++;
        if (watchdog != null) {
            watchdog.shutdown();
        }
        sshConnection.disconnect();
    }


    /**
     * Count how many times {@link #reconnect()} has been called since object creation.
     *
     * @return the count.
     */
    public int getReconnectCallCount() {
        return reconnectCallCount;
    }

    /**
     * Closes the connection.
     *
     * @param join if the method should wait for the thread to finish before returning.
     */
    public void shutdown(boolean join) {
        setShutdownInProgress();
        if (watchdog != null) {
            watchdog.shutdown();
        }
        if (sshConnection != null) {
            logger.info("Shutting down the ssh connection.");
            try {
                sshConnection.disconnect();
            } catch (Exception ex) {
                logger.warn("Error when disconnecting sshConnection.", ex);
            }
        }
        if (join) {
            try {
                this.join();
            } catch (InterruptedException ex) {
                logger.warn("Got interrupted while waiting for shutdown.", ex);
            }
        }
    }

    /**
     * Notifies all ConnectionListeners that the connection is down.
     */
    protected void notifyConnectionDown() {
        connected = false;
        if (handler != null) {
            handler.notifyConnectionDown();
        }
    }

    /**
     * Notifies all ConnectionListeners that the connection is established.
     */
    protected void notifyConnectionEstablished() {
        connected = true;
        if (handler != null) {
            handler.notifyConnectionEstablished();
        }
    }
}
