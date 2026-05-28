/*
 *  The MIT License
 *
 *  Copyright 2026 CloudBees, Inc.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.hazelcast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.BuildMemoryReport;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.BuildMemoryStorage;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Hazelcast-backed implementation of BuildMemoryStorage for HA/HS deployments.
 * <p>
 * Uses distributed IMap for storing build memory across multiple Jenkins replicas.
 * All operations use atomic EntryProcessor to prevent race conditions.
 * <p>
 * This implementation is automatically selected when:
 * <ul>
 *   <li>Coordination mode is set to 'hazelcast' via system property</li>
 *   <li>Hazelcast instance is available and running</li>
 * </ul>
 * <p>
 * <b>Serialization Strategy (MemoryImprint ↔ MemoryImprintData):</b>
 * <p>
 * This class handles conversion between the API type
 * ({@link com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint})
 * and the serialization type ({@link MemoryImprintData}):
 * <ul>
 *   <li><b>Write Path</b>: Business logic → MemoryImprint → (convert) → MemoryImprintData → Hazelcast IMap</li>
 *   <li><b>Read Path</b>: Hazelcast IMap → MemoryImprintData → (reconstruct) → MemoryImprint → Business logic</li>
 * </ul>
 * <p>
 * <b>Conversion Details:</b>
 * <ul>
 *   <li><b>Event Serialization</b>: {@link #serializeEvent} converts GerritTriggeredEvent to JSON
 *       using {@link PolymorphicEventTypeAdapter} for type preservation</li>
 *   <li><b>Entry Data Extraction</b>: EntryProcessors extract string identifiers (project full names,
 *       build IDs) from Jenkins objects before storage</li>
 *   <li><b>Reconstruction</b>: {@link #reconstructMemoryImprint} deserializes JSON to events and
 *       looks up Jenkins objects via {@link jenkins.model.Jenkins#getItemByFullName}</li>
 * </ul>
 * <p>
 * This conversion happens only at storage boundaries, keeping the rest of the plugin
 * unaware of serialization concerns.
 *
 * @see HazelcastCoordinationProvider
 * @see MemoryImprintData
 * @see PolymorphicEventTypeAdapter
 */
public class HazelcastBuildMemoryStorage extends BuildMemoryStorage {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastBuildMemoryStorage.class);

    /**
     * Hazelcast map name for distributed build memory.
     */
    private static final String MAP_NAME = "gerrit-trigger-build-memory";

    /**
     * Gson instance for JSON serialization of events.
     * Configured to handle polymorphic event types by including runtime type information.
     */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(GerritTriggeredEvent.class, new PolymorphicEventTypeAdapter())
            .create();

    /**
     * The Hazelcast instance to use for distributed storage.
     */
    private final HazelcastInstance hazelcastInstance;

    /**
     * Distributed mode storage (coordination mode).
     * Lazy-initialized when first accessed.
     * Marked volatile for thread-safe double-checked locking pattern.
     */
    private transient volatile IMap<BuildMemoryKey, MemoryImprintData> distributedMemory = null;

    /**
     * Constructor.
     *
     * @param hazelcastInstance the Hazelcast instance to use
     */
    public HazelcastBuildMemoryStorage(@NonNull HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * Gets or initializes the distributed memory map using thread-safe double-checked locking.
     * <p>
     * Uses volatile field and synchronized block to ensure only one thread initializes
     * the map while avoiding synchronization overhead on subsequent accesses.
     *
     * @return distributed memory map, or null if Hazelcast unavailable
     */
    private IMap<BuildMemoryKey, MemoryImprintData> getDistributedMemory() {
        // First check (no locking) - fast path for already-initialized case
        if (distributedMemory == null) {
            synchronized (this) {
                // Second check (with locking) - ensures only one thread initializes
                if (distributedMemory == null) {
                    if (hazelcastInstance != null) {
                        distributedMemory = hazelcastInstance.getMap(MAP_NAME);
                        logger.debug("Initialized distributed BuildMemory map: {} (size: {})",
                                MAP_NAME, distributedMemory.size());
                    } else {
                        logger.warn("Hazelcast unavailable, distributed memory not available");
                    }
                }
            }
        }
        return distributedMemory;
    }

    /**
     * Serializes a GerritTriggeredEvent to JSON.
     *
     * @param event the event to serialize
     * @return JSON string, or null if serialization fails
     */
    private String serializeEvent(GerritTriggeredEvent event) {
        try {
            // IMPORTANT: Must explicitly specify GerritTriggeredEvent.class to ensure
            // the PolymorphicEventTypeAdapter is used, even when event is a concrete subclass
            String json = GSON.toJson(event, GerritTriggeredEvent.class);
            if (json != null) {
                logger.trace("Serialized event {} to JSON (length: {})", event, json.length());
            } else {
                logger.trace("Serialized event {} to JSON (length: 0)", event);
            }
            return json;
        } catch (Exception e) {
            logger.error("Failed to serialize event to JSON: " + event, e);
            return null;
        }
    }

    /**
     * Deserializes a GerritTriggeredEvent from JSON.
     *
     * @param eventJson the JSON string
     * @return deserialized event, or null if deserialization fails
     */
    private GerritTriggeredEvent deserializeEvent(String eventJson) {
        try {
            if (eventJson == null) {
                logger.warn("Cannot deserialize null eventJson");
                return null;
            }
            GerritTriggeredEvent event = GSON.fromJson(eventJson, GerritTriggeredEvent.class);
            logger.trace("Deserialized JSON (length: {}) to event: {}", eventJson.length(), event);
            return event;
        } catch (Exception e) {
            if (eventJson != null) {
                logger.error("Failed to deserialize event from JSON (length: " + eventJson.length() + ")", e);
            } else {
                logger.error("Failed to deserialize event from NULL JSON", e);
            }
            return null;
        }
    }

    /**
     * Reconstructs a MemoryImprint from distributed data.
     *
     * @param event the event
     * @param data the serialized data
     * @return reconstructed MemoryImprint
     */
    private MemoryImprint reconstructMemoryImprint(GerritTriggeredEvent event, MemoryImprintData data) {
        MemoryImprint imprint = new MemoryImprint(event);

        if (data.getEntries() != null) {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null) {
                logger.warn("Jenkins instance not available, cannot reconstruct MemoryImprint");
                return imprint;
            }

            for (EntryData entryData : data.getEntries()) {
                String projectFullName = entryData.getProjectFullName();
                Job project = jenkins.getItemByFullName(projectFullName, Job.class);

                logger.info("[HZ-DIAG] reconstruct: project='{}' found={} buildId='{}' completed={} cancelled={}",
                        projectFullName, project != null, entryData.getBuildId(),
                        entryData.isBuildCompleted(), entryData.isCancelled());

                if (project != null) {
                    if (entryData.getBuildId() != null) {
                        Run build = project.getBuild(entryData.getBuildId());
                        String buildResult = "N/A";
                        if (build != null) {
                            buildResult = String.valueOf(build.getResult());
                        }
                        logger.info("[HZ-DIAG] reconstruct: project.getBuild('{}') = {} result={}",
                                entryData.getBuildId(), build != null, buildResult);
                        if (build != null) {
                            imprint.set(project, build, entryData.isBuildCompleted());
                        } else {
                            // Build not found, but project exists - add entry without build
                            imprint.set(project);
                        }
                    } else {
                        // No build ID - project triggered but not started
                        imprint.set(project);
                    }

                    // Restore additional entry data
                    MemoryImprint.Entry entry = imprint.getEntry(project);
                    if (entry != null) {
                        entry.setBuildCompleted(entryData.isBuildCompleted());
                        entry.setCancelling(entryData.isCancelling());
                        entry.setCancelled(entryData.isCancelled());
                        entry.setCustomUrl(entryData.getCustomUrl());
                        entry.setUnsuccessfulMessage(entryData.getUnsuccessfulMessage());
                    }
                }
            }
        }

        return imprint;
    }

    // ===== Implement BuildMemoryStorage abstract methods =====

    @Override
    @CheckForNull
    public synchronized MemoryImprint getMemoryImprint(@NonNull GerritTriggeredEvent event) {
        IMap<BuildMemoryKey, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            return null;
        }

        BuildMemoryKey key = new BuildMemoryKey(event);
        MemoryImprintData data = map.get(key);
        int entryCount = -1;
        if (data != null && data.getEntries() != null) {
            entryCount = data.getEntries().size();
        }
        logger.info("[HZ-DIAG] getMemoryImprint key={} dataFound={} entries={}",
                key, data != null, entryCount);
        if (data != null) {
            return reconstructMemoryImprint(event, data);
        }
        return null;
    }

    @Override
    public synchronized void triggered(@NonNull GerritTriggeredEvent event, @NonNull Job project) {
        IMap<BuildMemoryKey, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot record triggered - Hazelcast unavailable");
            return;
        }

        BuildMemoryKey key = new BuildMemoryKey(event);
        String projectFullName = project.getFullName();
        String eventJson = serializeEvent(event);

        logger.info("[HZ-DIAG] triggered key={} project={}", key, projectFullName);

        // ATOMIC OPERATION - Distributed lock ensures only one replica modifies this entry at a time.
        // This is critical when multiple projects are triggered by the same event simultaneously.
        // Without atomic operations, concurrent threads can overwrite each other's entries,
        // causing some project entries to be lost from BuildMemory (which breaks cancellation logic).
        // Note: EntryProcessor is NOT used here because in client mode the processor class would need
        // to exist on the Hazelcast sidecar member's classpath, causing ClassNotFoundException.
        map.lock(key);
        try {
            MemoryImprintData data = map.get(key);
            if (data == null) {
                data = new MemoryImprintData();
                data.setEventJson(eventJson);
            }
            boolean found = false;
            if (data.getEntries() != null) {
                for (EntryData entryData : data.getEntries()) {
                    if (projectFullName.equals(entryData.getProjectFullName())) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                EntryData newEntry = new EntryData();
                newEntry.setProjectFullName(projectFullName);
                data.addEntry(newEntry);
            }
            map.put(key, data);
            int triggeredEntries = 0;
            if (data.getEntries() != null) {
                triggeredEntries = data.getEntries().size();
            }
            logger.info("[HZ-DIAG] triggered stored: key={} found={} totalEntries={}",
                    key, found, triggeredEntries);
            if (!found) {
                logger.trace("Triggered event stored in distributed memory: {} for project: {}", key, projectFullName);
            } else {
                logger.trace("Project {} already triggered for event: {}", projectFullName, key);
            }
        } catch (Exception e) {
            logger.error("Failed to store triggered event in distributed memory for project: {} event: {}",
                    projectFullName, key, e);
        } finally {
            map.unlock(key);
        }
    }

    @Override
    public synchronized void started(@NonNull GerritTriggeredEvent event, @NonNull Run build) {
        IMap<BuildMemoryKey, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot mark started - Hazelcast unavailable");
            return;
        }

        BuildMemoryKey key = new BuildMemoryKey(event);
        String projectFullName = build.getParent().getFullName();
        String buildId = build.getId();

        // ATOMIC OPERATION - Distributed lock. EntryProcessor not used (ClassNotFoundException in client mode).
        long startedTimestamp = System.currentTimeMillis();
        map.lock(key);
        try {
            MemoryImprintData data = map.get(key);
            if (data == null) {
                data = new MemoryImprintData();
            }
            boolean found = false;
            if (data.getEntries() != null) {
                for (EntryData entryData : data.getEntries()) {
                    if (projectFullName.equals(entryData.getProjectFullName())) {
                        entryData.setBuildId(buildId);
                        entryData.setStartedTimestamp(startedTimestamp);
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                EntryData newEntry = new EntryData();
                newEntry.setProjectFullName(projectFullName);
                newEntry.setBuildId(buildId);
                newEntry.setStartedTimestamp(startedTimestamp);
                data.addEntry(newEntry);
            }
            map.put(key, data);
            if (!found) {
                logger.warn("Build started without being registered first (distributed mode).");
            }
            logger.trace("Build started event stored in distributed memory: {}", key);
        } catch (Exception e) {
            logger.error("Failed to mark build started in distributed memory: project={}, build={}, event={}",
                    projectFullName, buildId, key, e);
        } finally {
            map.unlock(key);
        }
    }

    @Override
    public synchronized void completed(@NonNull GerritTriggeredEvent event, @NonNull Run build) {
        IMap<BuildMemoryKey, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot mark completed - Hazelcast unavailable");
            return;
        }

        BuildMemoryKey key = new BuildMemoryKey(event);
        String projectFullName = build.getParent().getFullName();
        String buildId = build.getId();

        logger.info("[HZ-DIAG] completed key={} project={} buildId={} result={}",
                key, projectFullName, buildId, build.getResult());

        // ATOMIC OPERATION - Distributed lock. EntryProcessor not used (ClassNotFoundException in client mode).
        long completedTimestamp = System.currentTimeMillis();
        map.lock(key);
        try {
            MemoryImprintData data = map.get(key);
            if (data == null) {
                data = new MemoryImprintData();
            }
            boolean found = false;
            if (data.getEntries() != null) {
                for (EntryData entryData : data.getEntries()) {
                    if (projectFullName.equals(entryData.getProjectFullName())) {
                        if (entryData.getBuildId() == null) {
                            entryData.setBuildId(buildId);
                        }
                        entryData.setCompletedTimestamp(completedTimestamp);
                        entryData.setBuildCompleted(true);
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                EntryData newEntry = new EntryData();
                newEntry.setProjectFullName(projectFullName);
                newEntry.setBuildId(buildId);
                newEntry.setCompletedTimestamp(completedTimestamp);
                newEntry.setBuildCompleted(true);
                data.addEntry(newEntry);
            }
            map.put(key, data);
            int completedEntries = 0;
            if (data.getEntries() != null) {
                completedEntries = data.getEntries().size();
            }
            logger.info("[HZ-DIAG] completed stored: key={} found={} totalEntries={} buildCompleted={}",
                    key, found, completedEntries, true);
            if (!found) {
                logger.debug("Build completed without being registered first (distributed mode).");
            }
            logger.trace("Build completed event stored in distributed memory: {}", key);
        } catch (Exception e) {
            logger.error("Failed to mark build completed in distributed memory: project={}, build={}, event={}",
                    projectFullName, buildId, key, e);
        } finally {
            map.unlock(key);
        }
    }

    @Override
    public synchronized void retriggered(@NonNull GerritTriggeredEvent event, @NonNull Job project,
                            @CheckForNull List<Run> otherBuilds) {
        IMap<BuildMemoryKey, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot record retriggered - Hazelcast unavailable");
            return;
        }

        BuildMemoryKey key = new BuildMemoryKey(event);
        String projectFullName = project.getFullName();
        String eventJson = serializeEvent(event);

        // ATOMIC OPERATION - Distributed lock. EntryProcessor not used (ClassNotFoundException in client mode).
        map.lock(key);
        try {
            MemoryImprintData data = map.get(key);
            if (data == null) {
                data = new MemoryImprintData();
                data.setEventJson(eventJson);
                if (otherBuilds != null) {
                    for (Run otherBuild : otherBuilds) {
                        EntryData entryData = new EntryData();
                        entryData.setProjectFullName(otherBuild.getParent().getFullName());
                        entryData.setBuildId(otherBuild.getId());
                        entryData.setBuildCompleted(!otherBuild.isBuilding());
                        data.addEntry(entryData);
                    }
                }
            }
            boolean found = false;
            if (data.getEntries() != null) {
                for (EntryData entryData : data.getEntries()) {
                    if (projectFullName.equals(entryData.getProjectFullName())) {
                        entryData.setBuildId(null);
                        entryData.setBuildCompleted(false);
                        entryData.setStartedTimestamp(null);
                        entryData.setCompletedTimestamp(null);
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                EntryData newEntry = new EntryData();
                newEntry.setProjectFullName(projectFullName);
                data.addEntry(newEntry);
            }
            map.put(key, data);
            logger.trace("Retriggered event stored in distributed memory: {}", key);
        } catch (Exception e) {
            logger.error("Failed to record retriggered in distributed memory: project={}, event={}",
                    projectFullName, key, e);
        } finally {
            map.unlock(key);
        }
    }

    @Override
    public synchronized void cancelled(@NonNull GerritTriggeredEvent event, @NonNull Job project) {
        IMap<BuildMemoryKey, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot mark cancelled - Hazelcast unavailable");
            return;
        }

        BuildMemoryKey key = new BuildMemoryKey(event);
        String projectFullName = project.getFullName();

        // ATOMIC OPERATION - Distributed lock. EntryProcessor not used (ClassNotFoundException in client mode).
        long cancelledTimestamp = System.currentTimeMillis();
        map.lock(key);
        try {
            MemoryImprintData data = map.get(key);
            if (data == null) {
                data = new MemoryImprintData();
            }
            boolean found = false;
            boolean modified = false;
            if (data.getEntries() != null) {
                for (EntryData entryData : data.getEntries()) {
                    if (projectFullName.equals(entryData.getProjectFullName())) {
                        found = true;
                        // Only mark as cancelled if the build never started (buildId is null)
                        // OR was actively being cancelled by a new patchset (cancelling flag was set).
                        // If buildId is already set but cancelling is false, this is a cross-replica
                        // queue deduplication: the actual build is running on another replica.
                        // Writing cancelled=true in that case poisons the shared map and causes
                        // premature "No Builds Executed" feedback.
                        if (entryData.getBuildId() == null || entryData.isCancelling()) {
                            entryData.setCancelled(true);
                            entryData.setCancelling(false);
                            entryData.setCompletedTimestamp(cancelledTimestamp);
                            entryData.setBuildCompleted(true);
                            modified = true;
                        } else {
                            logger.debug("Skipping cancelled() for project={} event={}: "
                                    + "buildId already set by another replica (cross-replica queue dedup)",
                                    projectFullName, key);
                        }
                        break;
                    }
                }
            }
            if (!found) {
                EntryData newEntry = new EntryData();
                newEntry.setProjectFullName(projectFullName);
                newEntry.setCancelled(true);
                newEntry.setCancelling(false);
                newEntry.setCompletedTimestamp(cancelledTimestamp);
                newEntry.setBuildCompleted(true);
                data.addEntry(newEntry);
                modified = true;
            }
            if (modified) {
                map.put(key, data);
            }
            if (!found) {
                logger.debug("Build cancelled without being registered first (distributed mode).");
            }
            logger.trace("Cancelled event stored in distributed memory: {}", key);
        } catch (Exception e) {
            logger.error("Failed to mark cancelled in distributed memory: project={}, event={}",
                    projectFullName, key, e);
        } finally {
            map.unlock(key);
        }
    }

    @Override
    public synchronized void setCancelling(@NonNull GerritTriggeredEvent event, @NonNull Job project) {
        IMap<BuildMemoryKey, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot mark cancelling - Hazelcast unavailable");
            return;
        }

        BuildMemoryKey key = new BuildMemoryKey(event);
        String projectFullName = project.getFullName();

        // ATOMIC OPERATION - Distributed lock. EntryProcessor not used (ClassNotFoundException in client mode).
        map.lock(key);
        try {
            MemoryImprintData data = map.get(key);
            if (data != null && data.getEntries() != null) {
                boolean updated = false;
                for (EntryData entryData : data.getEntries()) {
                    if (projectFullName.equals(entryData.getProjectFullName())) {
                        if (!entryData.isBuildCompleted() && !entryData.isCancelling() && !entryData.isCancelled()) {
                            entryData.setCancelling(true);
                            updated = true;
                        }
                    }
                }
                if (updated) {
                    map.put(key, data);
                }
            }
            logger.trace("Cancelling flag set in distributed memory for event: {}", key);
        } catch (Exception e) {
            logger.error("Failed to set cancelling flag in distributed memory: project={}, event={}",
                    projectFullName, key, e);
        } finally {
            map.unlock(key);
        }
    }

    @Override
    public synchronized void forget(@NonNull GerritTriggeredEvent event) {
        IMap<BuildMemoryKey, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            return;
        }

        BuildMemoryKey key = new BuildMemoryKey(event);
        map.remove(key);
        logger.trace("Forgot event from distributed memory: {}", key);
    }

    @Override
    public synchronized void removeProject(@NonNull Job project) {
        String projectFullName = project.getFullName();

        IMap<BuildMemoryKey, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            return;
        }

        // ATOMIC OPERATION - Distributed lock per key. EntryProcessor not used (ClassNotFoundException in client mode).
        // Collect keys first to avoid ConcurrentModificationException
        java.util.Set<BuildMemoryKey> keys = new java.util.HashSet<>(map.keySet());

        for (BuildMemoryKey key : keys) {
            map.lock(key);
            try {
                MemoryImprintData data = map.get(key);
                if (data == null || data.getEntries() == null) {
                    continue;
                }
                boolean removed = data.getEntries().removeIf(
                        entryData -> projectFullName.equals(entryData.getProjectFullName()));
                if (removed) {
                    if (data.getEntries().isEmpty()) {
                        map.delete(key);
                        logger.trace("Removed empty entry for project {} from distributed memory: {}",
                            projectFullName, key);
                    } else {
                        map.put(key, data);
                        logger.trace("Removed project {} from distributed memory entry: {}",
                            projectFullName, key);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to remove project from distributed memory entry: project={}, key={}",
                        projectFullName, key, e);
                // Continue processing other keys
            } finally {
                map.unlock(key);
            }
        }
    }

    @Override
    public synchronized boolean isAllBuildsCompleted(@NonNull GerritTriggeredEvent event) {
        MemoryImprint imprint = getMemoryImprint(event);
        boolean result = imprint != null && imprint.isAllBuildsCompleted();
        logger.info("[HZ-DIAG] isAllBuildsCompleted: imprintNull={} result={}",
                imprint == null, result);
        if (imprint != null) {
            logger.info("[HZ-DIAG] isAllBuildsCompleted: wereAllBuildsSuccessful={} wereAnyBuildsFailed={}"
                    + " wereAllBuildsNotBuilt={}",
                    imprint.wereAllBuildsSuccessful(), imprint.wereAnyBuildsFailed(),
                    imprint.wereAllBuildsNotBuilt());
        }
        return result;
    }

    @Override
    public synchronized boolean isAllBuildsStarted(@NonNull GerritTriggeredEvent event) {
        MemoryImprint imprint = getMemoryImprint(event);
        return imprint != null && imprint.isAllBuildsSet();
    }

    @Override
    @CheckForNull
    public synchronized BuildsStartedStats getBuildsStartedStats(@NonNull GerritTriggeredEvent event) {
        MemoryImprint imprint = getMemoryImprint(event);
        if (imprint != null) {
            return imprint.getBuildsStartedStats();
        }
        return null;
    }

    @Override
    @CheckForNull
    public synchronized String getStatusReport(@NonNull GerritTriggeredEvent event) {
        MemoryImprint imprint = getMemoryImprint(event);
        if (imprint != null) {
            return imprint.getStatusReport();
        }
        return null;
    }

    @Override
    public synchronized boolean isTriggered(@NonNull GerritTriggeredEvent event, @NonNull Job project) {
        MemoryImprint imprint = getMemoryImprint(event);
        if (imprint == null) {
            return false;
        }
        String fullName = project.getFullName();
        for (MemoryImprint.Entry entry : imprint.getEntries()) {
            if (entry.isProject(fullName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean isBuilding(@NonNull GerritTriggeredEvent event, @NonNull Job project) {
        MemoryImprint imprint = getMemoryImprint(event);
        if (imprint == null) {
            return false;
        }
        String fullName = project.getFullName();
        for (MemoryImprint.Entry entry : imprint.getEntries()) {
            if (entry.isProject(fullName)) {
                if (entry.getBuild() != null) {
                    return !entry.isBuildCompleted();
                } else {
                    return !entry.isCancelling() && !entry.isCancelled();
                }
            }
        }
        return false;
    }

    @Override
    public synchronized boolean isBuilding(@NonNull GerritTriggeredEvent event) {
        MemoryImprint imprint = getMemoryImprint(event);
        return imprint != null;
    }

    @Override
    @CheckForNull
    public synchronized List<Run> getBuilds(@NonNull GerritTriggeredEvent event) {
        MemoryImprint imprint = getMemoryImprint(event);
        if (imprint != null) {
            List<Run> list = new LinkedList<>();
            for (MemoryImprint.Entry entry : imprint.getEntries()) {
                if (entry.getBuild() != null) {
                    list.add(entry.getBuild());
                }
            }
            return list;
        }
        return null;
    }

    @Override
    public void setEntryCustomUrl(@NonNull GerritTriggeredEvent event, @NonNull Run r,
                                   @CheckForNull String customUrl) {
        IMap<BuildMemoryKey, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot set custom URL - Hazelcast unavailable");
            return;
        }

        BuildMemoryKey key = new BuildMemoryKey(event);
        String projectFullName = r.getParent().getFullName();

        // ATOMIC OPERATION - Distributed lock. EntryProcessor not used (ClassNotFoundException in client mode).
        map.lock(key);
        try {
            MemoryImprintData data = map.get(key);
            if (data == null || data.getEntries() == null) {
                logger.warn("Could not set custom URL - event not found: {}", event);
                return;
            }
            boolean found = false;
            for (EntryData entryData : data.getEntries()) {
                if (projectFullName.equals(entryData.getProjectFullName())) {
                    entryData.setCustomUrl(customUrl);
                    found = true;
                    break;
                }
            }
            if (found) {
                map.put(key, data);
                logger.trace("Recording custom URL for {}: {}", event, customUrl);
            } else {
                logger.warn("Could not set custom URL - event not found: {}", event);
            }
        } catch (Exception e) {
            logger.error("Failed to set custom URL in distributed memory: project={}, event={}, url={}",
                    projectFullName, key, customUrl, e);
        } finally {
            map.unlock(key);
        }
    }

    @Override
    public void setEntryUnsuccessfulMessage(@NonNull GerritTriggeredEvent event, @NonNull Run r,
                                            @CheckForNull String unsuccessfulMessage) {
        IMap<BuildMemoryKey, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot set unsuccessful message - Hazelcast unavailable");
            return;
        }

        BuildMemoryKey key = new BuildMemoryKey(event);
        String projectFullName = r.getParent().getFullName();

        // ATOMIC OPERATION - Distributed lock. EntryProcessor not used (ClassNotFoundException in client mode).
        map.lock(key);
        try {
            MemoryImprintData data = map.get(key);
            if (data == null || data.getEntries() == null) {
                logger.warn("Could not set unsuccessful message - event not found: {}", event);
                return;
            }
            boolean found = false;
            for (EntryData entryData : data.getEntries()) {
                if (projectFullName.equals(entryData.getProjectFullName())) {
                    entryData.setUnsuccessfulMessage(unsuccessfulMessage);
                    found = true;
                    break;
                }
            }
            if (found) {
                map.put(key, data);
                logger.trace("Recording unsuccessful message for {}: {}", event, unsuccessfulMessage);
            } else {
                logger.warn("Could not set unsuccessful message - event not found: {}", event);
            }
        } catch (Exception e) {
            logger.error("Failed to set unsuccessful message in distributed memory: project={}, event={}, message={}",
                    projectFullName, key, unsuccessfulMessage, e);
        } finally {
            map.unlock(key);
        }
    }

    @Override
    @NonNull
    public synchronized BuildMemoryReport report() {
        BuildMemoryReport report = new BuildMemoryReport();

        IMap<BuildMemoryKey, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            return report;
        }

        // Read all entries from distributed memory
        for (Map.Entry<BuildMemoryKey, MemoryImprintData> mapEntry : map.entrySet()) {
            MemoryImprintData data = mapEntry.getValue();
            GerritTriggeredEvent event = deserializeEvent(data.getEventJson());

            if (event != null) {
                MemoryImprint imprint = reconstructMemoryImprint(event, data);
                List<MemoryImprint.Entry> triggered = new LinkedList<MemoryImprint.Entry>();
                for (MemoryImprint.Entry tr : imprint.getEntries()) {
                    triggered.add(tr.clone());
                }
                report.put(event, triggered);
            }
        }
        return report;
    }

    @Override
    @NonNull
    public synchronized Map<GerritTriggeredEvent, MemoryImprint> getAllEvents() {
        Map<GerritTriggeredEvent, MemoryImprint> result = new HashMap<>();

        IMap<BuildMemoryKey, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            return result;
        }

        // Convert all entries
        for (Map.Entry<BuildMemoryKey, MemoryImprintData> entry : map.entrySet()) {
            MemoryImprintData data = entry.getValue();
            if (data != null) {
                GerritTriggeredEvent event = deserializeEvent(data.getEventJson());
                if (event != null) {
                    MemoryImprint imprint = reconstructMemoryImprint(event, data);
                    result.put(event, imprint);
                }
            }
        }

        logger.trace("Returning {} events from distributed memory", result.size());
        return result;
    }

    @Override
    public boolean eventsMatch(@NonNull GerritTriggeredEvent event1, @NonNull GerritTriggeredEvent event2) {
        // In distributed mode, use logical comparison via EventIdentifier
        // because events may be deserialized from Hazelcast, creating new object instances
        String id1 = EventIdentifier.generateEventId(event1);
        String id2 = EventIdentifier.generateEventId(event2);
        return id1.equals(id2);
    }
}
