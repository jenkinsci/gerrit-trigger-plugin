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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ConnectionListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritSendCommandQueue;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Displays a warning message in /manage if the Gerrit connection is down or some other warning.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@Extension
public class GerritAdministrativeMonitor extends AdministrativeMonitor implements ConnectionListener {

    private static final Logger logger = LoggerFactory.getLogger(GerritAdministrativeMonitor.class);
    private boolean connected = true;

    /**
     * Default constructor.
     * Adds this as a ConnectionListener to PluginImpl by calling {@link #addThisAsConnectionListener()}.
     *
     * @see PluginImpl#addListener(com.sonyericsson.hudson.plugins.gerrit.gerritevents.ConnectionListener)
     */
    public GerritAdministrativeMonitor() {
        addThisAsConnectionListener();
    }

    /**
     * Adds this monitor as a connection listener to PluginImpl.
     * If PluginImpl hasn't started yet, a separate Thread will be started that tries again in a little while.
     *
     * @see PluginImpl#addListener(com.sonyericsson.hudson.plugins.gerrit.gerritevents.ConnectionListener)
     */
    protected void addThisAsConnectionListener() {
        if (PluginImpl.getInstance() != null) {
            PluginImpl.getInstance().addListener(this);
        } else {
            //We where created first... let's wait without disrupting the flow.
            Runnable runner = new Runnable() {

                private static final int SLEEP_INTERVAL = 500;

                @Override
                public void run() {
                    while (PluginImpl.getInstance() == null) {
                        try {
                            Thread.sleep(SLEEP_INTERVAL);
                        } catch (InterruptedException ex) {
                            logger.debug("Got interrupted while sleeping...", ex);
                        }
                    }
                    PluginImpl plugin = PluginImpl.getInstance();
                    if (plugin != null) {
                        plugin.addListener(GerritAdministrativeMonitor.this);
                    } else {
                        logger.error("Unable to register GerritAdministrativeMonitor");
                    }
                }
            };
            Thread thread = new Thread(runner);
            thread.setDaemon(true);
            thread.start();
        }
    }

    /**
     * Tells if there is a warning with the send-commands-queue.
     * Utility method for the jelly page,
     *
     * @return true if so.
     */
    public boolean isSendQueueWarning() {
        return getSendQueueSize() >= GerritSendCommandQueue.SEND_QUEUE_SIZE_WARNING_THRESHOLD;
    }

    /**
     * Gets the current send-command queue size.
     * Utility method for the jelly page,
     *
     * @return the amount of jobs in the queue.
     */
    public int getSendQueueSize() {
        return GerritSendCommandQueue.getQueueSize();
    }

    /**
     * Tells if there is a connection warning.
     * Utility method for the jelly page,
     *
     * @return true if so.
     */
    @SuppressWarnings("unused")
    //called from jelly
    public boolean isConnectionWarning() {
        return !connected;
    }

    @Override
    public boolean isActivated() {
        return !connected || isSendQueueWarning();
    }

    @Override
    public void connectionEstablished() {
        connected = true;
    }

    @Override
    public void connectionDown() {
        connected = false;
    }
}
