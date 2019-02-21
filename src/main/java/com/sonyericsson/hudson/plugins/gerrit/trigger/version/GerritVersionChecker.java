/*
 *  The MIT License
 *
 *  Copyright 2012 Sony Mobile Communications Inc. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.version;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import hudson.util.VersionNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class used to determine if the current Gerrit version is high enough to run a specific feature..
 *
 * @author Tomas Westling &lt;thomas.westling@sonyericsson.com&gt;
 */
public final class GerritVersionChecker {

    private static final Logger logger = LoggerFactory.getLogger(GerritVersionChecker.class);

    /**
     * The feature version we want to compare the current Gerrit version with.
     */
    public static enum Feature {
        // Skipping WIPStateChanged & PrivateStateChanged version check due to not necessary for now.
        /**
         * Triggering on files, added in Gerrit 2.3.
         */
        fileTrigger("Trigger on files", "2.3"),

        /**
         * Triggering on draft change published, added in Gerrit 2.5.
         */
        triggerOnDraftPublished("Trigger on draft published", "2.5"),

        /**
         * Replication events, added in Gerrit 2.9.
         */
        replicationEvents("Replication events", "2.9"),

        /**
         * Project created events, added in Gerrit 2.12.
         */
        projectCreatedEvents("Project created events", "2.12"),


        /**
         * Gerrit CommentAdded always contains approval information, added in Gerrit 2.13.
         */
        commentAlwaysApproval("CommentAdded always contains approval", "2.13");


        private final String displayName;
        private final String version;
        private final VersionNumber versionNumber;

        /**
         * Standard constructor.
         *
         * @param displayName human readable name.
         * @param version     the version number.
         */
        Feature(String displayName, String version) {
            this.displayName = displayName;
            this.version = version;
            versionNumber = new VersionNumber(version);
        }

        /**
         * Human readable name.
         *
         * @return the display name.
         */
        public String getDisplayName() {
            return displayName;
        }

        /**
         * The required version string.
         *
         * @return the version.
         */
        public String getVersion() {
            return version;
        }
    }

    /**
     * Private constructor to prevent instantiation of the util class.
     */
    private GerritVersionChecker() {
    }

    /**
     * Tells us if we are running the correct version for a particular feature.
     *
     * @param feature the feature we want to check.
     * @param serverName the name of the Gerrit server.
     * @return true if the Gerrit version is high enough for us to use this feature.
     */
    public static boolean isCorrectVersion(Feature feature, String serverName) {
        if (PluginImpl.getInstance() != null) {
            if (serverName == null || serverName.isEmpty() || GerritServer.ANY_SERVER.equals(serverName)) {
                for (GerritServer server : PluginImpl.getServers_()) {
                    GerritVersionNumber gerritVersion
                            = createVersionNumber(server.getGerritVersion());
                    if (isCorrectVersion(gerritVersion, feature)) {
                        return true;
                    }
                }
                return false;
            } else {
                GerritVersionNumber gerritVersion
                    = createVersionNumber(getGerritVersion(serverName));
                return isCorrectVersion(gerritVersion, feature);
            }
        } else {
            return false;
        }
    }

    /**
     * Tells us if we are running the correct version for a particular feature.
     *
     * @param feature the feature we want to check.
     * @param serverName the name of the Gerrit server.
     * @param excludeSnapshotVersions exclude snapshot versions from feature checks.
     * @return true if the Gerrit version is high enough for us to use this feature.
     */
    public static boolean isCorrectVersion(Feature feature, String serverName,
            boolean excludeSnapshotVersions) {
        if (PluginImpl.getInstance() != null) {
            if (serverName == null || serverName.isEmpty()
                    || GerritServer.ANY_SERVER.equals(serverName)) {
                for (GerritServer server : PluginImpl.getServers_()) {
                    GerritVersionNumber gerritVersion
                            = createVersionNumber(server.getGerritVersion());
                    if (isCorrectVersion(gerritVersion, feature, excludeSnapshotVersions)) {
                        return true;
                    }
                }
                return false;
            } else {
                GerritVersionNumber gerritVersion
                    = createVersionNumber(getGerritVersion(serverName));
                return isCorrectVersion(gerritVersion, feature, excludeSnapshotVersions);
            }
        } else {
            return false;
        }
    }

    /**
     *Returns the current Gerrit version.
     *@param serverName the name of the server.
     *@return the current Gerrit version as a String if connected, or null otherwise.
    */
    private static String getGerritVersion(String serverName) {
        GerritServer server = PluginImpl.getServer_(serverName);
        if (server != null) {
            String version = server.getGerritVersion();
            if (version != null) {
                return version;
            } else {
                logger.error("Could not find the Gerrit version for {}", serverName);
            }
        } else {
            logger.error("Could not find the server {}", serverName);
        }
        return null;
    }

    /**
     * Tells us if we are running the correct version for a particular feature.
     *
     * @param gerritVersion the version of Gerrit we are running.
     * @param feature       the feature we want to check.
     * @param excludeSnapshotVersions exclude snapshots from feature check.
     * @return true if the Gerrit version is high enough for us to use this feature.
     */
    public static boolean isCorrectVersion(GerritVersionNumber gerritVersion,
            Feature feature, boolean excludeSnapshotVersions) {
        if (excludeSnapshotVersions) {
            return !feature.versionNumber.isNewerThan(gerritVersion);
        } else {
            return (gerritVersion.isSnapshot() || !feature.versionNumber.isNewerThan(gerritVersion));
        }
    }

    /**
     * Tells us if we are running the correct version for a particular feature.
     *
     * @param gerritVersion the version of Gerrit we are running.
     * @param feature       the feature we want to check.
     * @return true if the Gerrit version is high enough for us to use this feature.
     */
    public static boolean isCorrectVersion(GerritVersionNumber gerritVersion, Feature feature) {
        return isCorrectVersion(gerritVersion, feature, false);
    }

    /**
     * Creates a new VersionNumber from the response of the gerrit server.
     *
     * @param version the version as a String.
     * @return the version as a versionNumber.
     */
    public static GerritVersionNumber createVersionNumber(String version) {
        if (version == null || version.isEmpty()) {
            logger.error("Gerrit version number is null or the empty string.");
            return new HighestVersionNumber();
        }
        return GerritVersionNumber.getGerritVersionNumber(version);
    }
}
