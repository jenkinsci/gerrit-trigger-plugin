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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritSendCommandQueue;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.version.GerritVersionChecker;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;

import java.util.LinkedList;
import java.util.List;

/**
 * Displays a warning message in /manage if the Gerrit connection is down or some other warning.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@Extension
public class GerritAdministrativeMonitor extends AdministrativeMonitor {

    private LinkedList<GerritConnectionListener> warningList;
    private LinkedList<GerritConnectionListener> errorList;
    private LinkedList<GerritConnectionListener> snapshotList;
    private LinkedList<GerritConnectionListener> disabledFeaturesList;

    /**
     * Default constructor.
     */
    public GerritAdministrativeMonitor() {
        warningList = new LinkedList<GerritConnectionListener>();
        errorList = new LinkedList<GerritConnectionListener>();
        snapshotList = new LinkedList<GerritConnectionListener>();
        disabledFeaturesList = new LinkedList<GerritConnectionListener>();
    }

    @Override
    public boolean isActivated() {
        return isConnectionWarning() || isConnectionError() || isSendQueueWarning()
                || isGerritSnapshotVersion() || hasDisabledFeatures();
    }

    /**
     * Tells if there is at least one connection warning. Utility method for the jelly page.
     *
     * @return true if warning, false otherwise.
     */
    //called from jelly
    public boolean isConnectionWarning() {
        //Show a warning if at least one server config does not have a value for the front end URL.
        //Remind the user to add a frontend URL.
        warningList.clear();
        for (GerritServer s : PluginImpl.getInstance().getServers()) {
            GerritConnectionListener listener = s.getGerritConnectionListener();
            boolean connected = listener != null && listener.isConnected();
            if (!connected && s.getConfig().hasDefaultValues()) {
                warningList.add(listener);
            }
        }
        return !warningList.isEmpty();
    }

    /**
     * Returns the names of the servers with a connection warning.
     *
     * @return the names, or an empty list if no connection warning.
     */
    public LinkedList<String> getConnectionWarningServers() {
        LinkedList<String> warningServers = new LinkedList<String>();
        for (GerritConnectionListener l : warningList) {
            warningServers.add(l.getName());
        }
        return warningServers;
    }

    /**
     * Tells if there is at least one connection error. Utility method for the jelly page,
     *
     * @return true if error, false otherwise.
     */
    @SuppressWarnings("unused")
    //called from jelly
    public boolean isConnectionError() {
        //Show an error if at least one server connection could not be established with the configured front end URL
        errorList.clear();
        for (GerritServer s : PluginImpl.getInstance().getServers()) {
            if (!s.isPseudoMode()) {
                GerritConnectionListener listener = s.getGerritConnectionListener();
                boolean connected = listener != null && listener.isConnected();
                if (!connected && !s.getConfig().hasDefaultValues()) {
                    errorList.add(listener);
                }
            }
        }
        return !errorList.isEmpty();
    }

    /**
     * Returns the names of the servers with a connection error.
     *
     * @return the names, or an empty list if no connection error.
     */
    public LinkedList<String> getConnectionErrorServers() {
        LinkedList<String> errorServers = new LinkedList<String>();
        for (GerritConnectionListener l : errorList) {
            errorServers.add(l.getName());
        }
        return errorServers;
    }

    /**
     * Tells if there is a warning with the send-commands-queue. Utility method for the jelly page.
     *
     * @return true if so, false otherwise.
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
     * If at least one connected Gerrit is a snapshot version.
     *
     * @return true if so, false otherwise.
     */
    //called from jelly
    public boolean isGerritSnapshotVersion() {
        snapshotList.clear();
        for (GerritServer s : PluginImpl.getInstance().getServers()) {
            if (!s.isPseudoMode()) {
                GerritConnectionListener listener = s.getGerritConnectionListener();
                if (listener != null && listener.isSnapShotGerrit()) {
                    snapshotList.add(listener);
                }
            }
        }
        return !snapshotList.isEmpty();
    }

    /**
     * Returns the names of the Gerrit servers with a snapshot version.
     *
     * @return the names, or an empty list if no snapshot servers.
     */
    public LinkedList<String> getSnapshotServers() {
        LinkedList<String> snapshotServers = new LinkedList<String>();
        for (GerritConnectionListener l : snapshotList) {
            snapshotServers.add(l.getName());
        }
        return snapshotServers;
    }

    /**
     * If there is at least one server with features disabled due to old Gerrit version.
     *
     * @return true if so, false otherwise.
     */
    public boolean hasDisabledFeatures() {
        disabledFeaturesList.clear();
        for (GerritServer s : PluginImpl.getInstance().getServers()) {
            if (!s.isPseudoMode()) {
                GerritConnectionListener listener = s.getGerritConnectionListener();
                if (listener != null) {
                    List<GerritVersionChecker.Feature> disabledFeatures = listener.getDisabledFeatures();
                    if (disabledFeatures != null && !disabledFeatures.isEmpty()) {
                        disabledFeaturesList.add(listener);
                    }
                }
            }
        }
        return !disabledFeaturesList.isEmpty();
    }

    /**
     * Returns the names of the servers with disabled features.
     *
     * @return the names, or an empty list if no servers have disabled features.
     */
    public LinkedList<String> getDisabledFeaturesServers() {
        LinkedList<String> disabledFeaturesServers = new LinkedList<String>();
        for (GerritConnectionListener l : disabledFeaturesList) {
            disabledFeaturesServers.add(l.getName());
        }
        return disabledFeaturesServers;
    }

    /**
     * Returns the list of disabled features for a specific server.
     *
     * @param serverName the name of the Gerrit server
     * @return the list of disabled features or empty list if listener not found
     */
    public List<GerritVersionChecker.Feature> getDisabledFeatures(String serverName) {
        for (GerritConnectionListener listener : disabledFeaturesList) {
            if (listener.getName().equals(serverName)) {
                return listener.getDisabledFeatures();
            }
        }
        return new LinkedList<GerritVersionChecker.Feature>();
    }
}
