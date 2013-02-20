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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A basic event thread.
 * The idea is to split up as much work as possible to be able to quickly handle the next event from Gerrit
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class EventThread extends Thread {

    private static final Logger logger = LoggerFactory.getLogger(EventThread.class);

    private Coordinator coordinator;
    private boolean shutdown = false;

    /**
     * Constructs an Event thread worker.
     * @param coordinator the master.
     */
    public EventThread(Coordinator coordinator) {
        this(coordinator, "Gerrit Worker EventThread");
    }

    /**
     * Constructs an Event thread worker.
     * @param coordinator the master.
     * @param name the name of the thread.
     */
    public EventThread(Coordinator coordinator, String name) {
        super(name);
        this.coordinator = coordinator;
    }

    @Override
    public void run() {
        //TODO implement shutdown functionality.
        while (!shutdown) {
            try {
                Work work = coordinator.getWorkQueue().take();
                work.perform(coordinator);
            } catch (InterruptedException ex) {
                logger.debug("Got interrupted while polling work queue", ex);
            }
        }
    }

    /**
     * Ends this worker's reign and ends the thread.
     */
    public void shutdown() {
        logger.debug("Shutting down worker: {}", this);
        shutdown = true;
        this.interrupt();
    }
}
