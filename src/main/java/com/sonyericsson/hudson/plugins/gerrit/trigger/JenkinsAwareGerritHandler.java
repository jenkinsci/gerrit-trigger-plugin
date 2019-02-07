/*
 *  The MIT License
 *
 *  Copyright 2013 Ericsson
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
package com.sonyericsson.hudson.plugins.gerrit.trigger;

import hudson.security.ACL;
import hudson.security.ACLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonymobile.tools.gerrit.gerritevents.GerritHandler;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.workers.EventThread;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.lifecycle.GerritEventLifecycle;

/**
 * Specialization of GerritHandler that supports gerrit event's
 * lifecycle and takes care of custom EventThread creation.
 *
 * @author Hugo Arès &lt;hugo.ares@ericsson.com&gt;
 */
public class JenkinsAwareGerritHandler extends GerritHandler {

    private static final Logger logger = LoggerFactory.getLogger(JenkinsAwareGerritHandler.class);

    /**
     * Standard Constructor.
     *
     * @param numberOfWorkerThreads
     *            the number of event threads.
     */
    public JenkinsAwareGerritHandler(int numberOfWorkerThreads) {
        super(numberOfWorkerThreads);
    }

    /**
     * Here we override the EventThread creation with
     * one that impersonates System.
     * @param threadName name of thread.
     * @return new EventThread.
     */
    @Override
    protected EventThread createEventThread(String threadName) {
        return new SystemEventThread(this, threadName);
    }

    @Override
    public void notifyListeners(GerritEvent event) {
        // Notify lifecycle listeners.
        if (event instanceof GerritEventLifecycle) {
            try {
                ((GerritEventLifecycle)event).fireTriggerScanStarting();
            } catch (Exception ex) {
                logger.error("Error when notifying LifecycleListeners. ", ex);
            }
        }

        try (ACLContext ctx = ACL.as(ACL.SYSTEM)) {
            // The read deal
            super.notifyListeners(event);
        }

        // //Notify lifecycle listeners.
        if (event instanceof GerritEventLifecycle) {
            try {
                ((GerritEventLifecycle)event).fireTriggerScanDone();
            } catch (Exception ex) {
                logger.error("Error when notifying LifecycleListeners. ", ex);
            }
        }
    }
}
