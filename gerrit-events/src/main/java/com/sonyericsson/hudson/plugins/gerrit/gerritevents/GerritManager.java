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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.handler.AbstractHandler;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.handler.RabbitmqHandler;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.handler.SshHandler;

/**
 * Manager class for user. Contains connection thread and event handling management.
 *
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public class GerritManager {

    private static final String HANDLERTHREAD_NAME = "Gerrit handler thread";

    private Thread handlerThread;
    private AbstractHandler handler;

    /**
     * Creates a GerritManager with the specified values.
     *
     * @param config the configuration containing the connection values.
     */
    public GerritManager(GerritConnectionConfig config) {
        if (config.isEnableGerritRabbitmq()) {
            handler = new RabbitmqHandler(config);
        } else {
            handler = new SshHandler(config);
        }
        handlerThread = null;
    }

    /**
     * Get event handler.
     *
     * @return instance of GerritEventHandler.
     */
    public GerritEventHandler getEventHandler() {
        return (GerritEventHandler)handler;
    }

    /**
     * Get connection handler.
     *
     * @return instance of ConnectionHandler.
     */
    public ConnectionHandler getConnectionHandler() {
        return (ConnectionHandler)handler;
    }

    /**
     * Start handler for connecting and reading Gerrit JSON Events and dispatching them to Workers..
     */
    public void start() {
        if (handlerThread == null) {
            handlerThread = new Thread(handler, HANDLERTHREAD_NAME);
            handlerThread.start();
        }
    }

    /**
     * Awaits handler thread terminated.
     *
     * @param timeoutMillis the timeout milliseconds.
     * @return true if handler thread is terminated.
     * @throws InterruptedException if interrupted while awaiting
     */
    public boolean awaitTermination(long timeoutMillis) throws InterruptedException {
        if (handlerThread == null || !handlerThread.isAlive()) {
            handlerThread = null;
            return true;
        } else {
            handlerThread.join(timeoutMillis);
            if (handlerThread.isAlive()) {
                return false;
            }
            handlerThread = null;
            return true;
        }
    }

    /**
     * Await handler thread terminated with no timeout.
     *
     * @throws InterruptedException if interrupted while awaiting
     */
    public void awaitTermination() throws InterruptedException {
        awaitTermination(0);
    }

    /**
     * "Triggers" an event by adding it to the internal queue and be taken by one of the worker threads. This way it
     * will be put into the normal flow of events as if it was coming from the stream-events command.
     *
     * @param event the event to trigger.
     */
    public void triggerEvent(GerritEvent event) {
        handler.triggerEvent(event);
    }

    /**
     * The gerrit version we are connected to.
     * @return the gerrit version.
     */
    public String getGerritVersion() {
        return handler.getGerritVersion();
    }
}
