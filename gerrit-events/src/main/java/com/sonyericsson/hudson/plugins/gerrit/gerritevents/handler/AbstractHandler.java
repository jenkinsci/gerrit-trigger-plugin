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

//CS IGNORE LineLength FOR NEXT 1 LINES. REASON: static import.
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_NR_OF_RECEIVING_WORKER_THREADS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ConnectionHandler;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ConnectionListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritConnectionConfig;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventHandler;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeMerged;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeRestored;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.DraftPublished;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.RefUpdated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.Coordinator;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.EventThread;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.GerritEventWork;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.Work;

/**
 * Abstract handler class for Gerrit stream events. Contains the work queue management.
 *
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public abstract class AbstractHandler implements Runnable, GerritEventHandler, ConnectionHandler, Coordinator {

    /**
     * Time to wait between connection attempts.
     */
    protected static final int CONNECT_SLEEP = 2000;

    private static final Logger logger = LoggerFactory.getLogger(AbstractHandler.class);
    private BlockingQueue<Work> workQueue;
    private final Set<GerritEventListener> gerritEventListeners = new CopyOnWriteArraySet<GerritEventListener>();
    private final Set<ConnectionListener> connectionListeners = new CopyOnWriteArraySet<ConnectionListener>();
    private final List<EventThread> workers;
    private volatile boolean connected = false;
    private String gerritVersion = null;
    private String ignoreEMail;

    /**
     * shutdown flag.
     */
    protected volatile boolean shutdownRequested = false;

    /**
     * Creates a AbstractHandler with all the default values set.
     *
     * @see GerritDefaultValues#DEFAULT_NR_OF_RECEIVING_WORKER_THREADS
     */
    public AbstractHandler() {
        this(DEFAULT_NR_OF_RECEIVING_WORKER_THREADS, null);
    }

    /**
     * Creates a AbstractHandler with the specified value.
     * Also generate work queue.
     *
     * @param numberOfWorkerThreads the number of eventthreads.
     * @param ignoreEMail           the e-mail to ignore events from.
     */
    public AbstractHandler(int numberOfWorkerThreads, String ignoreEMail) {
        this.ignoreEMail = ignoreEMail;
        this.workQueue = new LinkedBlockingQueue<Work>();
        this.workers = new ArrayList<EventThread>(numberOfWorkerThreads);
        for (int i = 0; i < numberOfWorkerThreads; i++) {
            workers.add(new EventThread(this, "Gerrit Worker EventThread_" + i));
        }
    }

    /**
     * Creates a AbstractHandler with the specified value.
     *
     * @param config the configuration containing the connection values.
     */
    public AbstractHandler(GerritConnectionConfig config) {
        this(config.getNumberOfReceivingWorkerThreads(), config.getGerritEMail());
    }

    /**
     * Sets gerrit version.
     *
     * @param gerritVersion the gerrit version.
     */
    protected void setGerritVersion(String gerritVersion) {
        this.gerritVersion = gerritVersion;
    }

    /**
     * Gets gerrit version.
     *
     * @return the gerrit version.
     */
    public String getGerritVersion() {
        return gerritVersion;
    }

    @Override
    public BlockingQueue<Work> getWorkQueue() {
        return workQueue;
    }

    // Event Handler
    @Override
    public void addEventListener(GerritEventListener listener) {
        synchronized (this) {
            if (!gerritEventListeners.add(listener)) {
                logger.warn("The listener was doubly-added: {}", listener);
            }
        }
    }

    @Override
    public void addEventListeners(
            Collection<? extends GerritEventListener> listeners) {
        synchronized (this) {
            gerritEventListeners.addAll(listeners);
        }
    }

    @Override
    public void removeEventListener(GerritEventListener listener) {
        synchronized (this) {
            gerritEventListeners.remove(listener);
        }
    }

    @Override
    public Collection<GerritEventListener> removeAllEventListeners() {
        synchronized (this) {
            HashSet<GerritEventListener> listeners = new HashSet<GerritEventListener>(gerritEventListeners);
            gerritEventListeners.clear();
            return listeners;
        }
    }

    @Override
    public int getEventListenersCount() {
        return gerritEventListeners.size();
    }

    @Override
    public void notifyListeners(GerritEvent event) {
        //Notify lifecycle listeners.
        if (event instanceof PatchsetCreated) {
            try {
                ((PatchsetCreated)event).fireTriggerScanStarting();
            } catch (Exception ex) {
                logger.error("Error when notifying LifecycleListeners. ", ex);
            }
        }

        //The real deed.
        if (event instanceof CommentAdded) {
            if (ignoreEvent((CommentAdded)event)) {
                logger.trace("CommentAdded ignored");
                return;
            }
        }
        for (GerritEventListener listener : gerritEventListeners) {
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
            } else if (event instanceof DraftPublished) {
                listener.gerritEvent((DraftPublished)event);
            } else if (event instanceof ChangeAbandoned) {
                listener.gerritEvent((ChangeAbandoned)event);
            } else if (event instanceof ChangeMerged) {
              listener.gerritEvent((ChangeMerged)event);
            } else if (event instanceof ChangeRestored) {
                listener.gerritEvent((ChangeRestored)event);
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
     * Checks if the event should be ignored, due to a circular CommentAdded.
     *
     * @param event the event to check.
     * @return true if it should be ignored, false if not.
     */
    private boolean ignoreEvent(CommentAdded event) {
        Account account = event.getAccount();
        if (account == null) {
            return false;
        }
        String eMail = account.getEmail();
        if (ignoreEMail != null && ignoreEMail.equals(eMail)) {
            return true;
        }
        return false;
    }

    // Connection Handler

    @Override
    public void addConnectionListener(ConnectionListener listener) {
        synchronized (this) {
            connectionListeners.add(listener);
        }
    }

    @Override
    public void addConnectionListeners(
            Collection<? extends ConnectionListener> listeners) {
        synchronized (this) {
            connectionListeners.addAll(listeners);
        }
    }

    @Override
    public void removeConnectionListener(ConnectionListener listener) {
        synchronized (this) {
            connectionListeners.remove(listener);
        }
    }

    @Override
    public Collection<ConnectionListener> removeAllConnectionListeners() {
        synchronized (this) {
            Set<ConnectionListener> listeners = new HashSet<ConnectionListener>(connectionListeners);
            connectionListeners.clear();
            return listeners;
        }
    }

    @Override
    public int getConnectionListenersCount() {
        return connectionListeners.size();
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    /**
     * Notifies all ConnectionListeners that the connection is down.
     */
    protected void notifyConnectionDown() {
        connected = false;
        for (ConnectionListener listener : connectionListeners) {
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
    protected void notifyConnectionEstablished() {
        connected = true;
        for (ConnectionListener listener : connectionListeners) {
            try {
                listener.connectionEstablished();
            } catch (Exception ex) {
                logger.error("ConnectionListener threw Exception. ", ex);
            }
        }
    }

    /**
     * Starts event workers.
     */
    protected void startWorkers() {
        for (EventThread worker : workers) {
            worker.start();
        }
    }

    /**
     * Shutdown event workers.
     */
    protected void shutdownWorkers() {
        for (EventThread worker : workers) {
            worker.shutdown();
        }
    }

    @Override
    public void shutdown() {
        logger.debug("Shutdown requested.");
        this.shutdownRequested = true;
        Thread.currentThread().interrupt();
    }

    @Override
    public void run() {
        logger.info("Starting Up...");
        //Start the workers
        startWorkers();
        do {
            listenEvent();
            try {
                Thread.sleep(CONNECT_SLEEP);
            } catch (InterruptedException e) {
                logger.warn("Got interrupted while sleeping.", e);
            }
        } while (!shutdownRequested);
        shutdownWorkers();
        logger.debug("End of GerritHandler Thread.");
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

    /**
     * Listens event from connection.
     */
    protected abstract void listenEvent();
}
