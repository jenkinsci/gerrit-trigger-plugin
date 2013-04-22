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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeMerged;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeRestored;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.DraftPublished;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.RefUpdated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.runner.GerritEventRunner;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.runner.StreamEventsStringRunner;

import net.sf.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

//CS IGNORE LineLength FOR NEXT 1 LINES. REASON: static import.
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_NR_OF_RECEIVING_WORKER_THREADS;

/**
 * Main class for this module. Contains the main loop for connecting and reading streamed events from Gerrit.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritHandler implements Handler {

    private static final int TIMEOUT_SHUTDOWN_MILLIS = 30000;
    private static final Logger logger = LoggerFactory.getLogger(GerritHandler.class);
    private ExecutorService executorService;
    private int numberOfWorkerThreads;
    private String ignoreEMail;
    private final Set<GerritEventListener> gerritEventListeners = new CopyOnWriteArraySet<GerritEventListener>();
    private final Set<ConnectionListener> connectionListeners = new CopyOnWriteArraySet<ConnectionListener>();

    /**
     * Creates a GerritHandler with default number of worker threads.
     *
     * @see GerritDefaultValues#DEFAULT_NR_OF_RECEIVING_WORKER_THREADS
     */
    public GerritHandler() {
        this(DEFAULT_NR_OF_RECEIVING_WORKER_THREADS, null);
    }

    /**
     * Creates a GerritHandler with the specified values.
     *
     * @param numberOfWorkerThreads the number of event threads.
     * @param ignoreEMail the e-mail to ignore events from.
     */
    public GerritHandler(int numberOfWorkerThreads, String ignoreEMail) {
        this.numberOfWorkerThreads = numberOfWorkerThreads;
        this.ignoreEMail = ignoreEMail;
        this.executorService = Executors.newFixedThreadPool(numberOfWorkerThreads);
    }

    @Override
    public void post(String data) {
        try {
            executorService.execute(new StreamEventsStringRunner(this, data));
        } catch (IOException ex) {
            logger.error(ex.getMessage());
        }
    }

    @Override
    public void post(JSONObject json) {
        try {
            executorService.execute(new GerritEventRunner(this, GerritJsonEventFactory.getEvent(json)));
        } catch (IOException ex) {
            logger.error(ex.getMessage());
        }
    }

    @Override
    public void post(JSONObject json) {
        try {
            executorService.execute(new GerritEventRunner(this, GerritJsonEventFactory.getEvent(json)));
        } catch (IOException ex) {
            logger.error(ex.getMessage());
        }
    }

    /**
     * Add a GerritEventListener to the list of listeners.
     *
     * @param listener the listener to add.
     */
    public void addListener(GerritEventListener listener) {
        synchronized (this) {
            if (!gerritEventListeners.add(listener)) {
                logger.warn("The listener was doubly-added: {}", listener);
            }
        }
    }

    /**
     * Adds all the provided listeners to the internal list of listeners.
     *
     * @param listeners the listeners to add.
     */
    public void addEventListeners(Collection<? extends GerritEventListener> listeners) {
        synchronized (this) {
            gerritEventListeners.addAll(listeners);
        }
    }

    /**
     * Removes a GerritEventListener from the list of listeners.
     *
     * @param listener the listener to remove.
     */
    public void removeListener(GerritEventListener listener) {
        synchronized (this) {
            gerritEventListeners.remove(listener);
        }
    }

    /**
     * Removes all event listeners and returns those that where removed.
     *
     * @return the former list of listeners.
     */
    public Collection<GerritEventListener> removeAllEventListeners() {
        synchronized (this) {
            HashSet<GerritEventListener> listeners = new HashSet<GerritEventListener>(gerritEventListeners);
            gerritEventListeners.clear();
            return listeners;
        }
    }

    /**
     * The number of added e{@link GerritEventListener}s.
     * @return the size.
     */
    public int getEventListenersCount() {
        return gerritEventListeners.size();
    }

    /**
     * Add a ConnectionListener to the list of listeners. Return the current connection status so that listeners that
     * are added later than a connectionestablished/ connectiondown will get the current connection status.
     *
     * @param listener the listener to add.
     */
    public void addListener(ConnectionListener listener) {
        connectionListeners.add(listener);
    }

    /**
     * Add all ConnectionListeners to the list of listeners.
     *
     * @param listeners the listeners to add.
     */
    public void addConnectionListeners(Collection<? extends ConnectionListener> listeners) {
        connectionListeners.addAll(listeners);
    }

    /**
     * Removes a ConnectionListener from the list of listeners.
     *
     * @param listener the listener to remove.
     */
    public void removeListener(ConnectionListener listener) {
        connectionListeners.remove(listener);
    }

    /**
     * Removes all connection listeners and returns those who where removed.
     *
     * @return the list of former listeners.
     */
    public Collection<ConnectionListener> removeAllConnectionListeners() {
        Set<ConnectionListener> listeners = new HashSet<ConnectionListener>(connectionListeners);
        connectionListeners.clear();
        return listeners;
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
        if (this.numberOfWorkerThreads != numberOfWorkerThreads) {
            this.numberOfWorkerThreads = numberOfWorkerThreads;
            updateWorkerThreads();
        }
    }

    /**
     * Updates worker threads safely.
     */
    public void updateWorkerThreads() {
        ExecutorService disposedExecutorService = executorService;
        executorService = Executors.newFixedThreadPool(numberOfWorkerThreads);
        try {
            boolean terminated = false;
            while (!terminated) {
                terminated = disposedExecutorService.awaitTermination(TIMEOUT_SHUTDOWN_MILLIS, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException ex) {
            logger.error("Interrupted while shutdown handler.", ex);
        }
    }

    /**
     * Gets ignore e-mail.
     * @return the e-mail.
     */
    public String getIgnoreEMail() {
        return ignoreEMail;
    }

    /**
     * Sets ignore e-mail.
     * @param ignoreEMail an e-mail.
     */
    public void setIgnoreEMail(String ignoreEMail) {
        this.ignoreEMail = ignoreEMail;
    }

    /**
     * Notifies all listeners of a Gerrit event. This method is meant to be called by one of the Worker Threads {@link
     * com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.EventThread} and not on this Thread which would
     * defeat the purpose of having workers.
     *
     * @param event the event.
     */
    @Override
    public void handleEvent(GerritEvent event) {
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

    @Override
    public void handleEvent(GerritConnectionEvent event) {
        for (ConnectionListener listener : connectionListeners) {
            try {
                switch(event) {
                case GERRIT_CONNECTION_ESTABLISHED:
                    listener.connectionEstablished();
                    break;
                case GERRIT_CONNECTION_DOWN:
                    listener.connectionDown();
                    break;
                default:
                    break;
                }
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
        try {
            logger.debug("Internally trigger event: {}", event);
            logger.trace("putting work on queue.");
            executorService.execute(new GerritEventRunner(this, event));
        } catch (IOException ex) {
            logger.error(ex.getMessage());
        }
    }

    /**
     * Shutdown handler.
     * @param join true if wait termination.
     */
    public void shutdown(boolean join) {
        executorService.shutdown();
        if (join) {
            try {
                boolean terminated = false;
                while (!terminated) {
                    terminated = executorService.awaitTermination(TIMEOUT_SHUTDOWN_MILLIS, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException ex) {
                logger.error("Interrupted while shutdown handler.", ex);
            }
            removeAllEventListeners();
            removeAllConnectionListeners();
        }
    }
}
