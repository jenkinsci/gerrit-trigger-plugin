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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ConnectionListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritSendCommandQueue;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.version.GerritVersionChecker;
import com.sonyericsson.hudson.plugins.gerrit.trigger.version.GerritVersionNumber;
import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

/**
 * Displays a warning message in /manage if the Gerrit connection is down or some other warning.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@Extension
public class GerritAdministrativeMonitor extends AdministrativeMonitor implements ConnectionListener {

    private static final Logger logger = LoggerFactory.getLogger(GerritAdministrativeMonitor.class);
    private boolean connected = false;
    private boolean gerritSnapshotVersion;
    private List<GerritVersionChecker.Feature> disabledFeatures;

    /**
     * Default constructor. Adds this as a ConnectionListener to PluginImpl by calling {@link
     * #addThisAsConnectionListener()}.
     *
     * @see PluginImpl#addListener(com.sonyericsson.hudson.plugins.gerrit.gerritevents.ConnectionListener)
     */
    public GerritAdministrativeMonitor() {
        addThisAsConnectionListener();
    }

    /**
     * Adds this monitor as a connection listener to PluginImpl. If PluginImpl hasn't started yet, a separate Thread
     * will be started that tries again in a little while.
     *
     * @see PluginImpl#addListener(com.sonyericsson.hudson.plugins.gerrit.gerritevents.ConnectionListener)
     */
    protected void addThisAsConnectionListener() {
        if (PluginImpl.getInstance() != null) {
            PluginImpl.getInstance().addListener(this);
            connected = PluginImpl.getInstance().isConnected();
            checkGerritVersionFeatures();
        } else {
            //We were created first... let's wait without disrupting the flow.
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
                        connected = PluginImpl.getInstance().isConnected();
                        checkGerritVersionFeatures();
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
     * Tells if there is a warning with the send-commands-queue. Utility method for the jelly page.
     *
     * @return true if so.
     */
    public boolean isSendQueueWarning() {
        return getSendQueueSize() >= GerritSendCommandQueue.SEND_QUEUE_SIZE_WARNING_THRESHOLD;
    }

    /**
     * Gets the current send-command queue size. Utility method for the jelly page.
     *
     * @return the amount of jobs in the queue.
     */
    public int getSendQueueSize() {
        return GerritSendCommandQueue.getQueueSize();
    }

    /**
     * Tells if there is a connection warning. Utility method for the jelly page.
     *
     * @return true if so.
     */
    @SuppressWarnings("unused")
    //called from jelly
    public boolean isConnectionWarning() {
        //If the frontend URL does not have a value, don't try to connect and show a warning
        //reminding the user to add a frontend URL.
        return !connected
                && PluginImpl.getInstance().getConfig().hasDefaultValues();
    }

    /**
     * Tells if there is a connection error. Utility method for the jelly page,
     *
     * @return true if so.
     */
    @SuppressWarnings("unused")
    //called from jelly
    public boolean isConnectionError() {
        //If the frontend URL has a value, try to connect to it and if it is not possible,
        //show an error to the user
        return !connected
                && !PluginImpl.getInstance().getConfig().hasDefaultValues();
    }

    @Override
    public boolean isActivated() {
        return isConnectionWarning() || isConnectionError() || isSendQueueWarning()
                || isGerritSnapshotVersion() || hasDisabledFeatures();
    }

    /**
     * If the connected Gerrit is a snapshot version.
     *
     * @return true if so.
     */
    @SuppressWarnings("unused")
    //called from jelly
    public boolean isGerritSnapshotVersion() {
        return gerritSnapshotVersion;
    }

    /**
     * A list of the features that has been disabled due to old Gerrit version.
     *
     * @return the list.
     */
    @SuppressWarnings("unused")
    //called from jelly
    public List<GerritVersionChecker.Feature> getDisabledFeatures() {
        return disabledFeatures;
    }

    /**
     * If there are features disabled due to old Gerrit version.
     *
     * @return true if so.
     */
    public boolean hasDisabledFeatures() {
        return disabledFeatures != null && !disabledFeatures.isEmpty();
    }

    @Override
    public void connectionEstablished() {
        connected = true;
        checkGerritVersionFeatures();
    }

    @Override
    public void connectionDown() {
        connected = false;
        checkGerritVersionFeatures();
    }

    /**
     * Checks the Gerrit version that we are connected to.
     * If it is a snapshot or if any features will be disabled because of this.
     * It should be called whenever we got some new connection status.
     */
    private void checkGerritVersionFeatures() {
        try {
            if (connected) {
                GerritVersionNumber version =
                        GerritVersionChecker.createVersionNumber(PluginImpl.getInstance().getGerritVersion());
                List<GerritVersionChecker.Feature> list = new LinkedList<GerritVersionChecker.Feature>();
                for (GerritVersionChecker.Feature f : GerritVersionChecker.Feature.values()) {
                    if (!GerritVersionChecker.isCorrectVersion(version, f)) {
                        list.add(f);
                    }
                }
                this.disabledFeatures = list;
                this.gerritSnapshotVersion = version.isSnapshot();
            } else {
                this.disabledFeatures = null;
                this.gerritSnapshotVersion = false;
            }
        } catch (Exception ex) {
            logger.warn("Failed to calculate version info! ", ex);
        }
    }

}
