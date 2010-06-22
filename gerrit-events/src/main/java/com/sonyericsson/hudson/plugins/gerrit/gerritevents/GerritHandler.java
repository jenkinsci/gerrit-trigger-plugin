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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshAuthenticationException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.Coordinator;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.EventThread;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.*;

/**
 * Main class for this module.
 * Contains the main loop for connecting and reading streamed events from Gerrit.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritHandler extends Thread implements Coordinator {

    /**
     * Time to wait between connection attempts.
     */
    public static final int CONNECT_SLEEP = 2000;
    private static final String CMD_STREAM_EVENTS = "gerrit stream-events";
    private static final Logger logger = LoggerFactory.getLogger(GerritHandler.class);
    private BlockingQueue<JSONObject> workQueue;
    private String gerritHostName;
    private int gerritSshPort;
    private Authentication authentication;
    private int numberOfWorkerThreads;
    private final List<GerritEventListener> gerritEventListeners;
    private final List<ConnectionListener> connectionListeners;
    private final List<EventThread> workers;
    private SshConnection sshConnection;
    private boolean shutdownInProgress = false;
    private boolean connecting = false;

    /**
     * Creates a GerritHandler with all the default values set.
     * @see #DEFAULT_GERRIT_HOSTNAME
     * @see #DEFAULT_GERRIT_SSH_PORT
     * @see #DEFAULT_GERRIT_USERNAME
     * @see #DEFAULT_AUTH_KEY_FILE
     * @see #DEFAULT_AUTH_KEY_FILE_PASSWORD
     * @see #DEFAULT_NR_OF_WORKER_THREADS
     */
    public GerritHandler() {
        this(DEFAULT_GERRIT_HOSTNAME,
             DEFAULT_GERRIT_SSH_PORT,
             new Authentication(DEFAULT_GERRIT_AUTH_KEY_FILE,
                                DEFAULT_GERRIT_USERNAME,
                                DEFAULT_GERRIT_AUTH_KEY_FILE_PASSWORD),
             DEFAULT_NR_OF_WORKER_THREADS);
    }

    /**
     * Creates a GerritHandler with the specified values and default number of worker threads.
     * @param gerritHostName the hostName
     * @param gerritSshPort the ssh port that the gerrit server listens to.
     * @param authentication the authentication credentials.
     */
    public GerritHandler(String gerritHostName,
                         int gerritSshPort,
                         Authentication authentication) {
        this(gerritHostName,
             gerritSshPort,
             authentication,
             DEFAULT_NR_OF_WORKER_THREADS);
    }

    /**
     * Creates a GerritHandler with the specified values.
     * @param gerritHostName the hostName for gerrit.
     * @param gerritSshPort the ssh port that the gerrit server listens to.
     * @param authentication the authentication credentials.
     * @param numberOfWorkerThreads the number of eventthreads.
     */
    public GerritHandler(String gerritHostName,
                         int gerritSshPort,
                         Authentication authentication,
                         int numberOfWorkerThreads) {
        super("Gerrit Events Reader");
        this.gerritHostName = gerritHostName;
        this.gerritSshPort = gerritSshPort;
        this.authentication = authentication;
        this.numberOfWorkerThreads = numberOfWorkerThreads;

        workQueue = new LinkedBlockingQueue<JSONObject>();
        gerritEventListeners = new LinkedList<GerritEventListener>();
        connectionListeners = new LinkedList<ConnectionListener>();
        workers = new ArrayList<EventThread>(numberOfWorkerThreads);
        for (int i = 0; i < numberOfWorkerThreads; i++) {
            workers.add(new EventThread(this, "Gerrit Worker EventThread_" + i));
        }
    }

    /**
     * Main loop for connecting and reading Gerrit JSON Events and dispatching them to Workers.
     */
    @Override
    public void run() {
        logger.info("Starting Up...");
        //Start the workers
        for (EventThread worker : workers) {
            //TODO what if nr of workers are increased/decreased in runtime.
            worker.start();
        }
        do {
            sshConnection = connect();
            if (sshConnection == null) {
                //should mean unrecoverable error
                for (EventThread worker : workers) {
                    worker.shutdown();
                }
                return;
            }

            BufferedReader br = null;
            try {
                Reader reader = sshConnection.executeCommandReader(CMD_STREAM_EVENTS);
                br = new BufferedReader(reader);
                String line = "";
                logger.info("Ready to receive data from Gerrit");
                notifyConnectionEstablished();
                do {
                    logger.debug("Data-line from Gerrit: {}", line);
                    JSONObject obj = GerritJsonEventFactory.getJsonObjectIfInterestingAndUsable(line);
                    if (obj != null) {
                        try {
                            workQueue.put(obj);
                        } catch (InterruptedException ex) {
                            logger.warn("Interrupted while putting work on queue!", ex);
                            //TODO check if shutdown
                            //TODO try again since it is important
                        }
                    }
                    line = br.readLine();
                } while (line != null);
            } catch (IOException ex) {
                logger.error("Stream events command error. ", ex);
            } finally {
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
        } while (!shutdownInProgress);

        for (EventThread worker : workers) {
            worker.shutdown();
        }
    }

    /**
     * Connects to the Gerrit server and authenticates as a "Hudson user".
     * @return not null if everything is well, null if connect and reconnect failed.
     */
    private SshConnection connect() {
        connecting = true;
        while (true) { //TODO do not go on forever.
            if (shutdownInProgress) {
                connecting = false;
                return null;
            }
            try {
                SshConnection ssh = new SshConnection(gerritHostName, gerritSshPort, authentication);
                notifyConnectionEstablished();
                connecting = false;
                return ssh;
            } catch (SshConnectException sshConEx) {
                logger.error("Could not connect to Gerrit server! "
                        + "Host: {} Port: {}", gerritHostName, gerritSshPort);
                logger.error(" User: {} KeyFile: {}", authentication.getUsername(), authentication.getPrivateKeyFile());
                logger.error("ConnectionException: ", sshConEx);
                notifyConnectionDown();
            } catch (SshAuthenticationException sshAuthEx) {
                logger.error("Could not authenticate to gerrit server!"
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

            if (shutdownInProgress) {
                connecting = false;
                return null;
            }

            //If we end up here, sleep for a while and then go back up in the loop.
            try {
                Thread.sleep(CONNECT_SLEEP);
            } catch (InterruptedException ex) {
                logger.warn("Got interrupted while sleeping.", ex);
            }
        }
    }

    /**
     * Add a GerritEventListener to the list of listeners.
     * @param listener the listener to add.
     */
    public void addListener(GerritEventListener listener) {
        synchronized (gerritEventListeners) {
            gerritEventListeners.add(listener);
        }
    }

    /**
     * Adds all the provided listeners to the internal list of listeners.
     * @param listeners the listeners to add.
     */
    public void addEventListeners(Collection<GerritEventListener> listeners) {
        synchronized (gerritEventListeners) {
            gerritEventListeners.addAll(listeners);
        }
    }

    /**
     * Removes a GerritEventListener from the list of listeners.
     * @param listener the listener to remove.
     */
    public void removeListener(GerritEventListener listener) {
        synchronized (gerritEventListeners) {
            gerritEventListeners.remove(listener);
        }
    }

    /**
     * Removes all event listeners and returns those that where removed.
     * @return the former list of listeners.
     */
    public List<GerritEventListener> removeAllEventListeners() {
        synchronized (gerritEventListeners) {
            List<GerritEventListener> listeners = new LinkedList<GerritEventListener>(gerritEventListeners);
            gerritEventListeners.clear();
            return listeners;
        }
    }

    /**
     * Add a ConnectionListener to the list of listeners.
     * @param listener the listener to add.
     */
    public void addListener(ConnectionListener listener) {
        synchronized (connectionListeners) {
            connectionListeners.add(listener);
        }
    }

    /**
     * Add all ConnectionListeners to the list of listeners.
     * @param listeners the listeners to add.
     */
    public void addConnectionListeners(Collection<ConnectionListener> listeners) {
        synchronized (connectionListeners) {
            connectionListeners.addAll(listeners);
        }
    }

    /**
     * Removes a ConnectionListener from the list of listeners.
     * @param listener the listener to remove.
     */
    public void removeListener(ConnectionListener listener) {
        synchronized (connectionListeners) {
            connectionListeners.remove(listener);
        }
    }

    /**
     * Removes all connection listeners and returns those who where remooved.
     * @return the list of former listeners.
     */
    public List<ConnectionListener> removeAllConnectionListeners() {
        synchronized (connectionListeners) {
            List<ConnectionListener> listeners = new LinkedList<ConnectionListener>(connectionListeners);
            connectionListeners.clear();
            return listeners;
        }
    }

    /**
     * The authentication credentials for ssh connection.
     * @return the credentials.
     */
    public Authentication getAuthentication() {
        return authentication;
    }

    /**
     * The authentication credentials for ssh connection.
     * @param authentication the credentials.
     */
    public void setAuthentication(Authentication authentication) {
        this.authentication = authentication;
    }

    /**
     * gets the hostname where Gerrit is running.
     * @return the hostname.
     */
    public String getGerritHostName() {
        return gerritHostName;
    }

    /**
     * Sets the hostname where Gerrit is running.
     * @param gerritHostName the hostname.
     */
    public void setGerritHostName(String gerritHostName) {
        this.gerritHostName = gerritHostName;
    }

    /**
     * Gets the port for gerrit ssh commands.
     * @return the port nr.
     */
    public int getGerritSshPort() {
        return gerritSshPort;
    }

    /**
     * Sets the port for gerrit ssh commands.
     * @param gerritSshPort the port nr.
     */
    public void setGerritSshPort(int gerritSshPort) {
        this.gerritSshPort = gerritSshPort;
    }

    /**
     * Gets the number of event worker threads.
     * @return the number of threads.
     */
    public int getNumberOfWorkerThreads() {
        return numberOfWorkerThreads;
    }

    /**
     * Sets the number of worker event threads.
     * @param numberOfWorkerThreads the number of threads
     */
    public void setNumberOfWorkerThreads(int numberOfWorkerThreads) {
        this.numberOfWorkerThreads = numberOfWorkerThreads;
        //TODO what if nr of workers are increased/decreased in runtime.
    }

    @Override
    public BlockingQueue<JSONObject> getWorkQueue() {
        return workQueue;
    }

    /**
     * Notifies all listeners of a Gerrit event.
     * This method is meant to be called by one of the Worker Threads
     * {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.EventThread}
     * and not on this Thread which would defeat the purpous of having workers.
     * @param event the event.
     */
    @Override
    public void notifyListeners(GerritEvent event) {
        synchronized (gerritEventListeners) {
            //Take the performance penalty for synchronization security.
            for (GerritEventListener listener : gerritEventListeners) {
                notifyListener(listener, event);
            }
        }
    }

    /**
     * Sub method of {@link #notifyListeners(com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent) }.
     * This is where most of the reflection magic in the event notification is done.
     * @param listener the listener to notify
     * @param event the event.
     */
    private void notifyListener(GerritEventListener listener, GerritEvent event) {
        logger.debug("Notifying listener {} of event {}", listener, event);
        try {
            if (event instanceof PatchsetCreated) {
                listener.gerritEvent((PatchsetCreated) event);
            } else if (event instanceof ChangeAbandoned) {
                listener.gerritEvent((ChangeAbandoned) event);
            } else {
                listener.gerritEvent(event);
            }
        } catch (Exception ex) {
            logger.error("Exception thrown during event handling.", ex);
        }
    }

    /**
     * Sub-method of
     * {@link #notifyListener(com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener,
     * com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent) .
     * It is a convenience method so there is no need to try catch for every occurence.
     * @param listener the listener to notify
     * @param event the event to fire.
     */
    private void notifyListenerDefaultMethod(GerritEventListener listener, GerritEvent event) {
        try {
            listener.gerritEvent(event);
        } catch (Exception ex) {
            logger.error("Exception thrown during event handling.", ex);
        }
    }

    /**
     * Closes the connection.
     * @param join if the method should wait for the thread to finish before returning.
     */
    public void shutdown(boolean join) {
        if (sshConnection != null) {
            logger.info("Shutting down the ssh connection.");
            this.shutdownInProgress = true;
            sshConnection.disconnect();
            if (join) {
                try {
                    this.join();
                } catch (InterruptedException ex) {
                    logger.warn("Got interrupted while waiting for shutdown.", ex);
                }
            }
        } else if (connecting) {
            this.shutdownInProgress = true;
            if (join) {
                try {
                    this.join();
                } catch (InterruptedException ex) {
                    logger.warn("Got interrupted while waiting for shutdown.", ex);
                }
            }
        } else {
            logger.warn("Was told to shutdown without a connection.");
        }
    }

    /**
     * Notifies all ConnectionListeners that the connection is down.
     */
    protected void notifyConnectionDown() {
        synchronized (connectionListeners) {
            for (ConnectionListener listener : connectionListeners) {
                try {
                    listener.connectionDown();
                } catch (Exception ex) {
                    logger.error("ConnectionListener threw Exception. ", ex);
                }
            }
        }
    }

    /**
     * Notifies all ConnectionListeners that the connection is established.
     */
    protected void notifyConnectionEstablished() {
        synchronized (connectionListeners) {
            for (ConnectionListener listener : connectionListeners) {
                try {
                    listener.connectionEstablished();
                } catch (Exception ex) {
                    logger.error("ConnectionListener threw Exception. ", ex);
                }
            }
        }
    }
}
