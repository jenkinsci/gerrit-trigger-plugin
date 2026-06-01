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
import com.hazelcast.map.listener.EntryAddedListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.BuildMemoryReport;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.BuildMemoryStorage;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
     * Hazelcast map name for cross-replica build abort requests.
     * <p>
     * When a replica needs to abort a build running on another replica, it puts an entry
     * {@code "jobFullName:buildNumber"} into this map. Each Jenkins replica has a listener
     * on this map and will abort any matching build running on its local executors.
     * <p>
     * This approach works in Hazelcast CLIENT mode where {@code executeOnMember()} would
     * target Hazelcast server sidecars (not Jenkins replicas) and fail with
     * {@code ClassNotFoundException} because Jenkins classes are absent from the sidecar JVM.
     */
    private static final String ABORT_INBOX_MAP_NAME = "gerrit-trigger-abort-inbox";

    /**
     * TTL for abort inbox entries in seconds (1 minute).
     * Entries expire automatically to prevent memory leaks in case the target build
     * has already finished or is not found on any replica.
     */
    private static final long ABORT_INBOX_TTL_SECONDS = 60;

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
     * Uses String keys (event IDs) instead of BuildMemoryKey to avoid Hazelcast classloader
     * issues when deserializing plugin-specific key classes via Java serialization.
     */
    private transient volatile IMap<String, MemoryImprintData> distributedMemory = null;

    /**
     * Constructor.
     *
     * @param hazelcastInstance the Hazelcast instance to use
     */
    public HazelcastBuildMemoryStorage(@NonNull HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
        registerAbortInboxListener();
    }

    /**
     * Registers a listener on the abort inbox IMap so this replica can receive and process
     * cross-replica build abort requests submitted by other Jenkins replicas.
     * <p>
     * The listener fires in this JVM (which has Jenkins on its classpath), so it can safely
     * look up jobs and interrupt executors. This is the correct approach for Hazelcast CLIENT
     * mode where {@code executeOnMember()} would instead run code on the Hazelcast sidecar.
     */
    private void registerAbortInboxListener() {
        if (hazelcastInstance == null) {
            return;
        }
        IMap<String, Long> abortInbox = hazelcastInstance.getMap(ABORT_INBOX_MAP_NAME);
        abortInbox.addEntryListener((EntryAddedListener<String, Long>)event -> {
            String abortKey = event.getKey();
            int lastColon = abortKey.lastIndexOf(':');
            if (lastColon < 0) {
                logger.warn("Abort-inbox: invalid key (no colon separator): {}", abortKey);
                return;
            }
            String jobName = abortKey.substring(0, lastColon);
            String buildId = abortKey.substring(lastColon + 1);
            handleAbortRequest(jobName, buildId);
        }, false);
        logger.debug("Registered abort-inbox listener on map: {}", ABORT_INBOX_MAP_NAME);
    }

    /**
     * Handles an abort request received via the abort inbox IMap.
     * Looks up the build on this replica and interrupts it if still running.
     *
     * @param jobName full name of the job
     * @param buildId build number as string
     */
    private static void handleAbortRequest(String jobName, String buildId) {
        try (ACLContext ignored = ACL.as(ACL.SYSTEM)) {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null) {
                return;
            }
            Job<?, ?> job = jenkins.getItemByFullName(jobName, Job.class);
            if (job == null) {
                return;
            }
            Run<?, ?> build = job.getBuildByNumber(Integer.parseInt(buildId));
            if (build == null || !build.isBuilding()) {
                return;
            }
            Executor executor = build.getExecutor();
            if (executor != null) {
                executor.interrupt(Result.ABORTED);
                logger.info("Abort-inbox: interrupted job={} build={}", jobName, buildId);
            }
        } catch (Exception e) {
            logger.error("Abort-inbox: failed to abort job={} build={}", jobName, buildId, e);
        }
    }

    /**
     * Gets or initializes the distributed memory map using thread-safe double-checked locking.
     * <p>
     * Uses volatile field and synchronized block to ensure only one thread initializes
     * the map while avoiding synchronization overhead on subsequent accesses.
     *
     * @return distributed memory map, or null if Hazelcast unavailable
     */
    private IMap<String, MemoryImprintData> getDistributedMemory() {
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

                if (project != null) {
                    if (entryData.getBuildId() != null) {
                        Run build = project.getBuild(entryData.getBuildId());
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
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            return null;
        }

        String key = EventIdentifier.generateEventId(event);
        MemoryImprintData data = map.get(key);
        if (data != null) {
            return reconstructMemoryImprint(event, data);
        }
        return null;
    }

    @Override
    public synchronized void triggered(@NonNull GerritTriggeredEvent event, @NonNull Job project) {
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot record triggered - Hazelcast unavailable");
            return;
        }

        String key = EventIdentifier.generateEventId(event);
        String projectFullName = project.getFullName();
        String eventJson = serializeEvent(event);

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
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot mark started - Hazelcast unavailable");
            return;
        }

        String key = EventIdentifier.generateEventId(event);
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
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot mark completed - Hazelcast unavailable");
            return;
        }

        String key = EventIdentifier.generateEventId(event);
        String projectFullName = build.getParent().getFullName();
        String buildId = build.getId();

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
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot record retriggered - Hazelcast unavailable");
            return;
        }

        String key = EventIdentifier.generateEventId(event);
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
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot mark cancelled - Hazelcast unavailable");
            return;
        }

        String key = EventIdentifier.generateEventId(event);
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
                        // Only mark as completed when our own code explicitly flagged this entry
                        // for cancellation (setCancelling was called by cancelOutdatedEvents).
                        // External queue cancellations (e.g. QueueLoadBalancer moving the item to
                        // another replica) must be skipped: setting completed=true here would cause
                        // allBuildsCompleted() to call forget(), removing the event from the shared
                        // IMap before the other replica has a chance to run cancelOutdatedEvents().
                        if (entryData.isCancelling()) {
                            entryData.setCancelled(true);
                            entryData.setCancelling(false);
                            entryData.setCompletedTimestamp(cancelledTimestamp);
                            entryData.setBuildCompleted(true);
                            modified = true;
                        } else {
                            logger.debug("Skipping cancelled() for project={} event={}: "
                                    + "isCancelling=false, buildId={}. Likely external cancellation "
                                    + "(e.g. QueueLoadBalancer); not marking as completed.",
                                    projectFullName, key, entryData.getBuildId());
                        }
                        break;
                    }
                }
            }
            if (!found) {
                // No entry for this project - skip. If the entry was explicitly cancelled
                // (isCancelling was set), it would have been found because setCancelling()
                // only updates existing entries. This path is an untracked external cancellation.
                logger.debug("cancelled() called for untracked project={} event={}: skipping.",
                        projectFullName, key);
            }
            if (modified) {
                map.put(key, data);
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
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot mark cancelling - Hazelcast unavailable");
            return;
        }

        String key = EventIdentifier.generateEventId(event);
        String projectFullName = project.getFullName();

        // ATOMIC OPERATION - Distributed lock. EntryProcessor not used (ClassNotFoundException in client mode).
        List<String> buildIdsToAbort = new ArrayList<>();
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
                            String buildId = entryData.getBuildId();
                            if (buildId != null) {
                                buildIdsToAbort.add(buildId);
                            }
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

        // Put abort requests into the distributed abort inbox for cross-replica cancellation.
        // Each Jenkins replica has a listener on this map (registered in the constructor) and will
        // abort any matching build running on its local executors.
        // NOTE: executeOnMember() cannot be used in Hazelcast CLIENT mode because it runs the task
        // on the Hazelcast sidecar JVM, which does not have Jenkins classes on its classpath.
        if (!buildIdsToAbort.isEmpty()) {
            IMap<String, Long> abortInbox = hazelcastInstance.getMap(ABORT_INBOX_MAP_NAME);
            for (String buildId : buildIdsToAbort) {
                String abortKey = projectFullName + ":" + buildId;
                abortInbox.put(abortKey, System.currentTimeMillis(), ABORT_INBOX_TTL_SECONDS, TimeUnit.SECONDS);
                logger.info("Queued cross-replica abort: job={} build={}", projectFullName, buildId);
            }
        }
    }

    @Override
    public synchronized void forget(@NonNull GerritTriggeredEvent event) {
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            return;
        }

        String key = EventIdentifier.generateEventId(event);
        map.remove(key);
        logger.trace("Forgot event from distributed memory: {}", key);
    }

    @Override
    public synchronized void removeProject(@NonNull Job project) {
        String projectFullName = project.getFullName();

        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            return;
        }

        // ATOMIC OPERATION - Distributed lock per key. EntryProcessor not used (ClassNotFoundException in client mode).
        // Collect keys first to avoid ConcurrentModificationException
        java.util.Set<String> keys = new java.util.HashSet<>(map.keySet());

        for (String key : keys) {
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
        return imprint != null && imprint.isAllBuildsCompleted();
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
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot set custom URL - Hazelcast unavailable");
            return;
        }

        String key = EventIdentifier.generateEventId(event);
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
        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot set unsuccessful message - Hazelcast unavailable");
            return;
        }

        String key = EventIdentifier.generateEventId(event);
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

        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            return report;
        }

        // Read all entries from distributed memory
        for (Map.Entry<String, MemoryImprintData> mapEntry : map.entrySet()) {
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

        IMap<String, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            return result;
        }

        // Convert all entries
        for (Map.Entry<String, MemoryImprintData> entry : map.entrySet()) {
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
