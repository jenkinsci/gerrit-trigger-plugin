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

import java.util.LinkedList;
import java.util.List;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonymobile.tools.gerrit.gerritevents.ConnectionListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.version.GerritVersionChecker;
import com.sonyericsson.hudson.plugins.gerrit.trigger.version.GerritVersionNumber;

import javax.annotation.CheckForNull;

/**
 * Every instance of this class is a connection listener to a specific Gerrit server.
 * It keeps track of the connection status and information about the version and features of the Gerrit server.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 *
 */
public class GerritConnectionListener implements ConnectionListener {

    private static final Logger logger = LoggerFactory.getLogger(GerritConnectionListener.class);
    private String serverName;
    private boolean connected;
    private boolean gerritSnapshotVersion;
    private List<GerritVersionChecker.Feature> disabledFeatures;

    /**
     * Default constructor.
     * Initializes serverName and performs a Gerrit version check
     *
     * @param serverName the name of the Gerrit server.
     */
    public GerritConnectionListener(String serverName) {
        this.serverName = serverName;
    }

    /**
     * Return the name of the server to which this listener is attached.
     *
     * @return the name of the server.
     */
    public String getName() {
        return serverName;
    }
    /**
     * Return whether the listener is connected.
     *
     * @return whether it is connected
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Set the connection status.
     *
     * @param connected the connection status
     */
    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    /**
     * Return whether the Gerrit server is a snapshot version.
     *
     * @return whether the Gerrit server is a snapshot version
     */
    public boolean isSnapShotGerrit() {
        return gerritSnapshotVersion;
    }

    /**
     * A list of the features that have been disabled due to old Gerrit version of a specific Gerrit server.
     *
     * @return the list.
     */
    public List<GerritVersionChecker.Feature> getDisabledFeatures() {
        return disabledFeatures;
    }

    /**
     * @see ConnectionListener#connectionEstablished()
     */
    @Override
    public void connectionEstablished() {
        connected = true;
        checkGerritVersionFeatures();
    }

    /**
     * @see ConnectionListener#connectionDown()
     */
    @Override
    public void connectionDown() {
        connected = false;
        checkGerritVersionFeatures();
    }

    /**
     * Get the version of the GerritServer as a String.
     *
     * @return the Gerrit version as a String, or null if server not found.
     */
    @CheckForNull
    private String getVersionString() {
        PluginImpl plugin = PluginImpl.getInstance();
        if (plugin == null) {
            logger.error("INITIALIZATION Error, the plugin instance couldn't be found!");
            return null;
        }
        GerritServer server = plugin.getServer(serverName);
        if (server != null) {
            return server.getGerritVersion();
        } else {
            logger.error("server does not exist");
            return null;
        }
    }

    /**
     * Checks the Gerrit version that we are connected to.
     * If it is a snapshot or if any features will be disabled because of this.
     * It should be called whenever we got some new connection status.
     */
    public void checkGerritVersionFeatures() {
        if (connected) {
            GerritVersionNumber version =
                    GerritVersionChecker.createVersionNumber(getVersionString());
            List<GerritVersionChecker.Feature> list = new LinkedList<GerritVersionChecker.Feature>();
            for (GerritVersionChecker.Feature f : GerritVersionChecker.Feature.values()) {
                if (!GerritVersionChecker.isCorrectVersion(version, f)) {
                    list.add(f);
                }
            }
            disabledFeatures = list;
            gerritSnapshotVersion = version.isSnapshot();
        } else {
            disabledFeatures = null;
            gerritSnapshotVersion = false;
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        int tmp;
        if (serverName == null) {
            tmp = 0;
        } else {
            tmp = serverName.hashCode();
        }
        result = prime * result + tmp;
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        GerritConnectionListener other = (GerritConnectionListener)obj;
        if (serverName == null) {
            if (other.serverName != null) {
                return false;
            }
        } else if (!serverName.equals(other.serverName)) {
            return false;
        }
        return true;
    }

}
