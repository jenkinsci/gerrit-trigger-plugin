/*
 *  The MIT License
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.playback;

import com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.InstanceIdentity;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.XmlFile;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.OptionalLong;

/**
 * Persists missed-events playback timestamps as one file per JVM instance, rather than one
 * shared file per Gerrit server.
 *
 * <p>Layout: {@code $JENKINS_HOME/gerrit-server-event-data/<serverName>/instances/<instanceId>.xml}.
 * This allows multiple Jenkins controller JVMs sharing one NFS-mounted {@code JENKINS_HOME} to
 * each persist their own last-known timestamp without contending for the same file - NFS does
 * not support reliable file locking, so a single shared file would risk corruption or silent
 * data loss under concurrent writers.</p>
 *
 * <p>A store is scoped to one Gerrit server (mirroring the pre-existing per-server directory
 * layout, {@code gerrit-server-event-data/<serverName>/}) and this JVM instance, since one
 * {@link GerritMissedEventsPlaybackManager} - and therefore one store - already exists per
 * configured server. {@code serverName} is deliberately NOT interchangeable between stores:
 * the persisted timestamp is later used verbatim as the catch-up query's lower bound against
 * that specific server's own front-end URL, so mixing servers would silently skip or re-trigger
 * events for whichever server's watermark got overwritten.</p>
 *
 * <p>{@link #computeMaxTimestampAcrossInstances()} falls back to the legacy single-file path
 * ({@link #getLegacyConfigXml()}) when the instances directory is empty, so upgrades from a
 * pre-existing single-JVM deployment are not treated as "no known timestamp."</p>
 */
public class InstanceTimestampStore {

    private static final Logger logger = LoggerFactory.getLogger(InstanceTimestampStore.class);

    private static final String GERRIT_SERVER_EVENT_DATA_FOLDER = "/gerrit-server-event-data/";
    private static final String GERRIT_TRIGGER_SERVER_TIMESTAMPS_XML = "gerrit-trigger-server-timestamps.xml";
    private static final String INSTANCES_SUBDIR = "instances";
    private static final String INSTANCE_FILE_SUFFIX = ".xml";

    private final String serverName;
    private final String instanceId;

    /**
     * @param serverName the Gerrit server this store persists timestamps for.
     */
    public InstanceTimestampStore(@NonNull String serverName) {
        this(serverName, InstanceIdentity.get());
    }

    /**
     * @param serverName the Gerrit server this store persists timestamps for.
     * @param instanceId the JVM instance identifier to use (test/package-visible override; use
     *         {@link #InstanceTimestampStore(String)} otherwise).
     */
    InstanceTimestampStore(@NonNull String serverName, @NonNull String instanceId) {
        this.serverName = serverName;
        this.instanceId = instanceId;
    }

    /**
     * @return the per-server data directory, or null if Jenkins is not available.
     */
    @CheckForNull
    public File getServerDataDir() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return null;
        }
        File dataDir = new File(jenkins.getRootDir(), GERRIT_SERVER_EVENT_DATA_FOLDER);
        return new File(dataDir, serverName);
    }

    /**
     * @return the per-server directory holding one file per JVM instance, or null if Jenkins is
     *         not available.
     */
    @CheckForNull
    public File getInstancesDir() {
        File serverDataDir = getServerDataDir();
        if (serverDataDir == null) {
            return null;
        }
        return new File(serverDataDir, INSTANCES_SUBDIR);
    }

    /**
     * @return the XmlFile for this instance's timestamp, or null if Jenkins is not available.
     */
    @CheckForNull
    public XmlFile getInstanceConfigXml() {
        File instancesDir = getInstancesDir();
        if (instancesDir == null) {
            return null;
        }
        instancesDir.mkdirs();
        File xmlFile = new File(instancesDir, instanceId + INSTANCE_FILE_SUFFIX);
        return new XmlFile(Jenkins.XSTREAM, xmlFile);
    }

    /**
     * The pre-existing single-file-per-server location, kept for upgrade compatibility (read
     * fallback in {@link #computeMaxTimestampAcrossInstances()}) and as the effective storage
     * location in single-JVM local mode where a directory of instance files would only ever
     * contain one entry anyway.
     *
     * @return the legacy XmlFile, or null if Jenkins is not available.
     */
    @CheckForNull
    public XmlFile getLegacyConfigXml() {
        File serverDataDir = getServerDataDir();
        if (serverDataDir == null) {
            return null;
        }
        serverDataDir.mkdirs();
        File xmlFile = new File(serverDataDir, GERRIT_TRIGGER_SERVER_TIMESTAMPS_XML);
        return new XmlFile(Jenkins.XSTREAM, xmlFile);
    }

    /**
     * @return this instance's persisted timestamp, or null if none exists or Jenkins is
     *         unavailable.
     * @throws IOException if the file exists but cannot be unmarshalled.
     */
    @CheckForNull
    public EventTimeSlice readTimestamp() throws IOException {
        XmlFile xml = getInstanceConfigXml();
        if (xml != null && xml.exists()) {
            return (EventTimeSlice)xml.unmarshal(null);
        }
        return null;
    }

    /**
     * @param slice the timestamp to persist.
     * @throws IOException if the file cannot be written.
     */
    public void writeTimestamp(EventTimeSlice slice) throws IOException {
        XmlFile xml = getInstanceConfigXml();
        if (xml == null) {
            logger.error("Cannot resolve instance timestamp file for server {} instance {};"
                    + " Jenkins not available.", serverName, instanceId);
            return;
        }
        xml.write(slice);
    }

    /**
     * Deletes this instance's own persisted timestamp file, e.g. when playback support is lost.
     * Never touches any other instance's file.
     *
     * @throws IOException if the file exists but cannot be deleted.
     */
    public void deleteTimestamp() throws IOException {
        XmlFile xml = getInstanceConfigXml();
        if (xml != null) {
            xml.delete();
        }
    }

    /**
     * Computes the most advanced (maximum) timestamp across all known instance files for this
     * server. Each JVM keeps its own live Gerrit connection and event processing is already
     * deduplicated across JVMs via the distributed event-claim strategy, so cross-instance
     * catch-up only matters when all JVMs were simultaneously unable to process events (e.g. a
     * full outage) - the maximum watermark identifies the point closest to when that outage
     * started, minimizing redundant reprocessing while still covering the true gap.
     *
     * <p>Corrupt or unreadable instance files are logged and skipped rather than failing the
     * whole computation - a directory listing on NFS is subject to client-side attribute
     * caching, so a just-written file from another JVM may occasionally not be visible yet; this
     * is accepted as a self-healing eventual-consistency risk, since every live instance rewrites
     * its file on its own persistence cycle.</p>
     *
     * @return the maximum known timestamp, or empty if no instance file (nor the legacy
     *         single-file fallback) has one.
     */
    public OptionalLong computeMaxTimestampAcrossInstances() {
        File instancesDir = getInstancesDir();
        long max = Long.MIN_VALUE;
        boolean found = false;
        if (instancesDir != null && instancesDir.isDirectory()) {
            File[] files = instancesDir.listFiles((dir, name) -> name.endsWith(INSTANCE_FILE_SUFFIX));
            if (files != null) {
                for (File file : files) {
                    try {
                        XmlFile xml = new XmlFile(Jenkins.XSTREAM, file);
                        EventTimeSlice slice = (EventTimeSlice)xml.unmarshal(null);
                        if (slice != null && slice.getTimeSlice() > max) {
                            max = slice.getTimeSlice();
                            found = true;
                        }
                    } catch (IOException e) {
                        logger.warn("Skipping unreadable instance timestamp file {}", file, e);
                    }
                }
            }
        }
        if (found) {
            return OptionalLong.of(max);
        }
        try {
            XmlFile legacy = getLegacyConfigXml();
            if (legacy != null && legacy.exists()) {
                EventTimeSlice slice = (EventTimeSlice)legacy.unmarshal(null);
                if (slice != null) {
                    return OptionalLong.of(slice.getTimeSlice());
                }
            }
        } catch (IOException e) {
            logger.warn("Skipping unreadable legacy timestamp file for server {}", serverName, e);
        }
        return OptionalLong.empty();
    }

    /**
     * Deletes instance files that have not been rewritten in more than {@code maxAgeMillis},
     * indicating a permanently decommissioned JVM - orphaned files would otherwise accumulate
     * forever. Never deletes this store's own instance file, regardless of its age. Also deletes
     * the legacy single-file fallback ({@link #getLegacyConfigXml()}) once it is equally stale,
     * since it is no longer written to by current code and would otherwise linger forever as a
     * read-fallback candidate in {@link #computeMaxTimestampAcrossInstances()}.
     *
     * @param maxAgeMillis the staleness threshold, based on the file's last-modified time.
     * @return the number of files removed.
     */
    public int pruneStaleInstanceFiles(long maxAgeMillis) {
        File instancesDir = getInstancesDir();
        int removed = 0;
        long now = System.currentTimeMillis();
        if (instancesDir != null && instancesDir.isDirectory()) {
            File[] files = instancesDir.listFiles((dir, name) -> name.endsWith(INSTANCE_FILE_SUFFIX));
            if (files != null) {
                String currentFileName = instanceId + INSTANCE_FILE_SUFFIX;
                for (File file : files) {
                    if (file.getName().equals(currentFileName)) {
                        continue;
                    }
                    if (now - file.lastModified() > maxAgeMillis) {
                        if (file.delete()) {
                            removed++;
                        } else {
                            logger.warn("Failed to delete stale instance timestamp file {}", file);
                        }
                    }
                }
            }
        }
        XmlFile legacy = getLegacyConfigXml();
        if (legacy != null && legacy.exists()) {
            File legacyFile = legacy.getFile();
            if (now - legacyFile.lastModified() > maxAgeMillis) {
                if (legacyFile.delete()) {
                    removed++;
                } else {
                    logger.warn("Failed to delete stale legacy timestamp file {}", legacyFile);
                }
            }
        }
        return removed;
    }
}
