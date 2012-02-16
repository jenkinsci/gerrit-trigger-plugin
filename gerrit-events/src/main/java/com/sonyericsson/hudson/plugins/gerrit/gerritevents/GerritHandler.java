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
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeMerged;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.RefUpdated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshAuthenticationException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectionFactory;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.Coordinator;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.EventThread;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.GerritEventWork;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.StreamEventsStringWork;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.Work;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.*;

/**
 * Main class for this module. Contains the main loop for connecting and reading streamed events from Gerrit.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritHandler extends Thread implements Coordinator {

    /**
     * Time to wait between connection attempts.
     */
    public static final int CONNECT_SLEEP = 2000;
    private static final String CMD_STREAM_EVENTS = "gerrit stream-events";
    /**
     * The amount of milliseconds to pause when brute forcing the shutdown flag to true.
     */
    protected static final int PAUSE_SECOND = 1000;
    /**
     * How many times to try and set the shutdown flag to true. Noticed during unit tests there seems to be a timing
     * issue or something so sometimes the {@shutdownInProgress} flag is not set properly. Setting it a number of times
     * with some delay helped.
     *
     * @see #shutdown(boolean)
     * @see #PAUSE_SECOND
     */
    protected static final int BRUTE_FORCE_TRIES = 10;
    private static final Logger logger = LoggerFactory.getLogger(GerritHandler.class);
    private BlockingQueue<Work> workQueue;
    private String gerritHostName;
    private int gerritSshPort;
    private Authentication authentication;
    private int numberOfWorkerThreads;
    private Map<Integer, GerritEventListener> gerritEventListeners;
    private Map<Integer, ConnectionListener> connectionListeners;
    private final List<EventThread> workers;
    private SshConnection sshConnection;
    private boolean shutdownInProgress = false;
    private final Object shutdownInProgressSync = new Object();
    private boolean connecting = false;
    private boolean connected = false;

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
    public GerritHandler() {
        this(DEFAULT_GERRIT_HOSTNAME,
                DEFAULT_GERRIT_SSH_PORT,
                new Authentication(DEFAULT_GERRIT_AUTH_KEY_FILE,
                        DEFAULT_GERRIT_USERNAME,
                        DEFAULT_GERRIT_AUTH_KEY_FILE_PASSWORD),
                DEFAULT_NR_OF_RECEIVING_WORKER_THREADS);
    }

    /**
     * Creates a GerritHandler with the specified values and default number of worker threads.
     *
     * @param gerritHostName the hostName
     * @param gerritSshPort  the ssh port that the gerrit server listens to.
     * @param authentication the authentication credentials.
     */
    public GerritHandler(String gerritHostName,
                         int gerritSshPort,
                         Authentication authentication) {
        this(gerritHostName,
                gerritSshPort,
                authentication,
                DEFAULT_NR_OF_RECEIVING_WORKER_THREADS);
    }

    /**
     * Creates a GerritHandler with the specified values.
     *
     * @param config the configuration containing the connection values.
     */
    public GerritHandler(GerritConnectionConfig config) {
        this(config.getGerritHostName(),
                config.getGerritSshPort(),
                config.getGerritAuthentication(),
                config.getNumberOfReceivingWorkerThreads());
    }

    /**
     * Creates a GerritHandler with the specified values.
     *
     * @param gerritHostName        the hostName for gerrit.
     * @param gerritSshPort         the ssh port that the gerrit server listens to.
     * @param authentication        the authentication credentials.
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

        workQueue = new LinkedBlockingQueue<Work>();
        gerritEventListeners = new HashMap<Integer, GerritEventListener>();
        connectionListeners = new HashMap<Integer, ConnectionListener>();
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
                logger.trace("Executing stream-events command.");
                Reader reader = sshConnection.executeCommandReader(CMD_STREAM_EVENTS);
                br = new BufferedReader(reader);
                String line = "";
                logger.info("Ready to receive data from Gerrit");
                notifyConnectionEstablished();
                do {
                    logger.debug("Data-line from Gerrit: {}", line);
                    if (line != null && line.length() > 0) {
                        try {
                            StreamEventsStringWork work = new StreamEventsStringWork(line);
                            logger.trace("putting work on queue: {}", work);
                            workQueue.put(work);
                        } catch (InterruptedException ex) {
                            logger.warn("Interrupted while putting work on queue!", ex);
                            //TODO check if shutdown
                            //TODO try again since it is important
                        }
                    }
                    logger.trace("Reading next line.");
                    line = br.readLine();
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
                notifyConnectionDown();
                if (br != null) {
                    try {
                        br.close();
                    } catch (IOException ex) {
                        logger.warn("Could not close events reader.", ex);
                    }
                }
            }
        } while (!isShutdownInProgress());

        for (EventThread worker : workers) {
            worker.shutdown();
        }
        logger.debug("End of GerritHandler Thread.");
    }

    /**
     * Connects to the Gerrit server and authenticates as the specified user.
     *
     * @return not null if everything is well, null if connect and reconnect failed.
     */
    private SshConnection connect() {
        connecting = true;
        while (true) { //TODO do not go on forever.
            if (isShutdownInProgress()) {
                connecting = false;
                return null;
            }
            SshConnection ssh = null;
            try {
                logger.debug("Connecting...");
                ssh = SshConnectionFactory.getConnection(gerritHostName, gerritSshPort, authentication);
                notifyConnectionEstablished();
                connecting = false;
                logger.debug("connection seems ok, returning it.");
                return ssh;
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

            if (isShutdownInProgress()) {
                connecting = false;
                return null;
            }

            //If we end up here, sleep for a while and then go back up in the loop.
            logger.trace("Sleeping for a bit.");
            try {
                Thread.sleep(CONNECT_SLEEP);
            } catch (InterruptedException ex) {
                logger.warn("Got interrupted while sleeping.", ex);
            }
        }
    }

    /**
     * Add a GerritEventListener to the list of listeners.
     *
     * @param listener the listener to add.
     */
    public synchronized void addListener(GerritEventListener listener) {
        GerritEventListener old = gerritEventListeners.put(listener.hashCode(), listener);
        if (old != null) {
            logger.warn("The listener was replaced by a previous old value: {}", old);
        }
    }

    /**
     * Adds all the provided listeners to the internal list of listeners.
     *
     * @param listeners the listeners to add.
     */
    public synchronized void addEventListeners(Map<Integer, GerritEventListener> listeners) {
        gerritEventListeners.putAll(listeners);
    }

    /**
     * Removes a GerritEventListener from the list of listeners.
     *
     * @param listener the listener to remove.
     */
    public synchronized void removeListener(GerritEventListener listener) {
        gerritEventListeners.remove(listener.hashCode());
    }

    /**
     * Removes all event listeners and returns those that where removed.
     *
     * @return the former list of listeners.
     */
    public synchronized HashMap<Integer, GerritEventListener> removeAllEventListeners() {
        HashMap<Integer, GerritEventListener> listeners =
                new HashMap<Integer, GerritEventListener>(gerritEventListeners);
        gerritEventListeners.clear();
        return listeners;
    }

    /**
     * The number of added e{@link GerritEventListener}s.
     * @return the size.
     */
    public synchronized int getEventListenersCount() {
        return gerritEventListeners.size();
    }

    /**
     * Add a ConnectionListener to the list of listeners. Return the current connection status so that listeners that
     * are added later than a connectionestablished/ connectiondown will get the current connection status.
     *
     * @param listener the listener to add.
     * @return the connection status
     */
    public synchronized boolean addListener(ConnectionListener listener) {
        connectionListeners.put(listener.hashCode(), listener);
        return connected;
    }

    /**
     * Add all ConnectionListeners to the list of listeners.
     *
     * @param listeners the listeners to add.
     */
    public synchronized void addConnectionListeners(Map<Integer, ConnectionListener> listeners) {
        connectionListeners.putAll(listeners);
    }

    /**
     * Removes a ConnectionListener from the list of listeners.
     *
     * @param listener the listener to remove.
     */
    public synchronized void removeListener(ConnectionListener listener) {
        connectionListeners.remove(listener);
    }

    /**
     * Removes all connection listeners and returns those who where remooved.
     *
     * @return the list of former listeners.
     */
    public synchronized Map<Integer, ConnectionListener> removeAllConnectionListeners() {
        Map<Integer, ConnectionListener> listeners =
                new HashMap<Integer, ConnectionListener>(connectionListeners);
        connectionListeners.clear();
        return listeners;
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
     * Gets the number of event worker threads.
     *
     * @return the number of threads.
     */
    public int getNumberOfWorkerThreads() {
        return numberOfWorkerThreads;
    }

    /**
     * Sets the number of worker event threads.
     *
     * @param numberOfWorkerThreads the number of threads
     */
    public void setNumberOfWorkerThreads(int numberOfWorkerThreads) {
        this.numberOfWorkerThreads = numberOfWorkerThreads;
        //TODO what if nr of workers are increased/decreased in runtime.
    }

    @Override
    public BlockingQueue<Work> getWorkQueue() {
        return workQueue;
    }

    /**
     * Notifies all listeners of a Gerrit event. This method is meant to be called by one of the Worker Threads {@link
     * com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.EventThread} and not on this Thread which would
     * defeat the purpose of having workers.
     *
     * @param event the event.
     */
    @Override
    public synchronized void notifyListeners(GerritEvent event) {
        //Take the performance penalty for synchronization security.

        //Notify lifecycle listeners.
        if (event instanceof PatchsetCreated) {
            try {
                ((PatchsetCreated)event).fireTriggerScanStarting();
            } catch (Exception ex) {
                logger.error("Error when notifying LifecycleListeners. ", ex);
            }
        }

        //The real deed.
        for (GerritEventListener listener : gerritEventListeners.values()) {
            try {
                notifyListener(listener, event);
            } catch (Exception ex) {
                logger.error("When notifying listener: {} about event: {}", listener, event);
                logger.error("Notify-error: ", ex);
            }
        }

        ////Notify lifecycle listeners.
        if (event instanceof PatchsetCreated) {
            try {
                ((PatchsetCreated)event).fireTriggerScanDone();
            } catch (Exception ex) {
                logger.error("Error when notifying LifecycleListeners. ", ex);
            }
        }
    }

    /**
     * Sub method of {@link #notifyListeners(com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent) }.
     * This is where most of the reflection magic in the event notification is done.
     *
     * @param listener the listener to notify
     * @param event    the event.
     */
    private void notifyListener(GerritEventListener listener, GerritEvent event) {
        logger.debug("Notifying listener {} of event {}", listener, event);
        try {
            if (event instanceof PatchsetCreated) {
                listener.gerritEvent((PatchsetCreated)event);
            } else if (event instanceof ChangeAbandoned) {
                listener.gerritEvent((ChangeAbandoned)event);
            } else if (event instanceof ChangeMerged) {
              listener.gerritEvent((ChangeMerged)event);
            } else if (event instanceof CommentAdded) {
                listener.gerritEvent((CommentAdded)event);
            } else if (event instanceof RefUpdated) {
                listener.gerritEvent((RefUpdated)event);
            } else {
                listener.gerritEvent(event);
            }
        } catch (Exception ex) {
            logger.error("Exception thrown during event handling.", ex);
        }
    }

    /**
     * Sub-method of {@link #notifyListener(com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener,
     * com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent) . It is a convenience method so there is no
     * need to try catch for every occurence.
     *
     * @param listener the listener to notify
     * @param event    the event to fire.
     */
    private void notifyListenerDefaultMethod(GerritEventListener listener, GerritEvent event) {
        try {
            listener.gerritEvent(event);
        } catch (Exception ex) {
            logger.error("Exception thrown during event handling.", ex);
        }
    }

    /**
     * Sets the shutdown flag.
     *
     * @param isIt true if shutdown is in progress.
     */
    private void setShutdownInProgress(boolean isIt) {
        synchronized (shutdownInProgressSync) {
            this.shutdownInProgress = isIt;
        }
    }

    /**
     * If the system is shutting down. I.e. the shutdown method has been called.
     *
     * @return true if so.
     */
    public boolean isShutdownInProgress() {
        synchronized (shutdownInProgressSync) {
            return this.shutdownInProgress;
        }
    }


    /**
     * Closes the connection.
     *
     * @param join if the method should wait for the thread to finish before returning.
     */
    public void shutdown(boolean join) {
        if (sshConnection != null) {
            logger.info("Shutting down the ssh connection.");
            //For some reason the shutdown flag is not correctly set.
            //So we'll try the brute force way.... and it actually works.
            for (int i = 0; i < BRUTE_FORCE_TRIES; i++) {
                setShutdownInProgress(true);
                if (!isShutdownInProgress()) {
                    try {
                        Thread.sleep(PAUSE_SECOND);
                    } catch (InterruptedException e) {
                        logger.debug("Interrupted while pausing in the shutdown flag set.");
                    }
                } else {
                    break;
                }
            }
            //Fail terribly if we still couldn't
            if (!isShutdownInProgress()) {
                throw new RuntimeException("Failed to set the shutdown flag!");
            }
            sshConnection.disconnect();
            if (join) {
                try {
                    this.join();
                } catch (InterruptedException ex) {
                    logger.warn("Got interrupted while waiting for shutdown.", ex);
                }
            }
        } else if (connecting) {
            setShutdownInProgress(true);
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
    protected synchronized void notifyConnectionDown() {
        connected = false;
        for (ConnectionListener listener : connectionListeners.values()) {
            try {
                listener.connectionDown();
            } catch (Exception ex) {
                logger.error("ConnectionListener threw Exception. ", ex);
            }
        }
    }

    /**
     * Notifies all ConnectionListeners that the connection is established.
     */
    protected synchronized void notifyConnectionEstablished() {
        connected = true;
        for (ConnectionListener listener : connectionListeners.values()) {
            try {
                listener.connectionEstablished();
            } catch (Exception ex) {
                logger.error("ConnectionListener threw Exception. ", ex);
            }
        }
    }

    /**
     * "Triggers" an event by adding it to the internal queue and be taken by one of the worker threads. This way it
     * will be put into the normal flow of events as if it was coming from the stream-events command.
     *
     * @param event the event to trigger.
     */
    public void triggerEvent(GerritEvent event) {
        logger.debug("Internally trigger event: {}", event);
        try {
            logger.trace("putting work on queue.");
            workQueue.put(new GerritEventWork(event));
        } catch (InterruptedException ex) {
            logger.error("Interrupted while putting work on queue!", ex);
        }
    }
}
