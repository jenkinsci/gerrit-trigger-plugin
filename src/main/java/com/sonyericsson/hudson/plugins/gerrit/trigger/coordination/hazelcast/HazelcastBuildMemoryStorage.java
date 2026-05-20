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
 *
 * @see HazelcastCoordinationProvider
 */
public class HazelcastBuildMemoryStorage extends BuildMemoryStorage {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastBuildMemoryStorage.class);

    /**
     * Hazelcast map name for distributed build memory.
     */
    private static final String MAP_NAME = "gerrit-trigger-build-memory";

    /**
     * Gson instance for JSON serialization of events.
     */
    private static final Gson GSON = new GsonBuilder().create();

    /**
     * Distributed mode storage (coordination mode).
     * Lazy-initialized when first accessed.
     */
    private transient IMap<BuildMemoryKey, MemoryImprintData> distributedMemory = null;

    /**
     * Gets or initializes the distributed memory map.
     *
     * @return distributed memory map, or null if Hazelcast unavailable
     */
    private IMap<BuildMemoryKey, MemoryImprintData> getDistributedMemory() {
        if (distributedMemory == null) {
            HazelcastInstance hz = HazelcastInstanceProvider.getInstance();
            if (hz != null) {
                distributedMemory = hz.getMap(MAP_NAME);
                logger.debug("Initialized distributed BuildMemory map: {}", MAP_NAME);
            } else {
                logger.warn("Hazelcast unavailable, distributed memory not available");
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
            return GSON.toJson(event);
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
            return GSON.fromJson(eventJson, GerritTriggeredEvent.class);
        } catch (Exception e) {
            logger.error("Failed to deserialize event from JSON", e);
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
        MemoryImprintData data = map.get(key);

        if (data == null) {
            // Create new memory imprint data
            data = new MemoryImprintData();
            data.setEventJson(serializeEvent(event));
        }

        // Add entry for triggered project
        EntryData entryData = new EntryData();
        entryData.setProjectFullName(project.getFullName());
        data.addEntry(entryData);

        map.put(key, data);
        logger.trace("Triggered event stored in distributed memory: {}", key);
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

        // ATOMIC OPERATION - Executes on partition owner, prevents race conditions
        Boolean found = map.executeOnKey(key, new BuildStartedProcessor(projectFullName, buildId));

        if (!found) {
            logger.warn("Build started without being registered first (distributed mode).");
        }
        logger.trace("Build started event stored in distributed memory: {}", key);
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

        // ATOMIC OPERATION - Executes on partition owner, prevents race conditions
        Boolean found = map.executeOnKey(key, new BuildCompletedProcessor(projectFullName, buildId));

        if (!found) {
            logger.debug("Build completed without being registered first (distributed mode).");
        }
        logger.trace("Build completed event stored in distributed memory: {}", key);
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
        MemoryImprintData data = map.get(key);

        if (data == null) {
            // Create new memory imprint data
            data = new MemoryImprintData();
            data.setEventJson(serializeEvent(event));

            if (otherBuilds != null) {
                // Populate with old build info
                for (Run build : otherBuilds) {
                    EntryData entryData = new EntryData();
                    entryData.setProjectFullName(build.getParent().getFullName());
                    entryData.setBuildId(build.getId());
                    entryData.setBuildCompleted(!build.isBuilding());
                    data.addEntry(entryData);
                }
            }
        }

        // Reset the retriggered project (clear build info)
        String projectFullName = project.getFullName();
        boolean found = false;

        if (data.getEntries() != null) {
            for (EntryData entry : data.getEntries()) {
                if (projectFullName.equals(entry.getProjectFullName())) {
                    // Reset this entry
                    entry.setBuildId(null);
                    entry.setBuildCompleted(false);
                    entry.setStartedTimestamp(null);
                    entry.setCompletedTimestamp(null);
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            // Add new entry for retriggered project
            EntryData entryData = new EntryData();
            entryData.setProjectFullName(projectFullName);
            data.addEntry(entryData);
        }

        map.put(key, data);
        logger.trace("Retriggered event stored in distributed memory: {}", key);
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

        // ATOMIC OPERATION - Executes on partition owner, prevents race conditions
        Boolean found = map.executeOnKey(key, new BuildCancelledProcessor(projectFullName));

        if (!found) {
            logger.debug("Build cancelled without being registered first (distributed mode).");
        }
        logger.trace("Cancelled event stored in distributed memory: {}", key);
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

        // ATOMIC OPERATION - Executes on partition owner, prevents race conditions
        map.executeOnKey(key, new SetCancellingProcessor(projectFullName));

        logger.trace("Cancelling flag set in distributed memory for event: {}", key);
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

        // Iterate over all entries in distributed memory
        for (Map.Entry<BuildMemoryKey, MemoryImprintData> mapEntry : map.entrySet()) {
            MemoryImprintData data = mapEntry.getValue();
            if (data.getEntries() != null) {
                // Remove entries matching this project
                boolean removed = data.getEntries().removeIf(
                    entry -> projectFullName.equals(entry.getProjectFullName())
                );

                // If we removed anything, update the map
                if (removed) {
                    map.put(mapEntry.getKey(), data);
                    logger.trace("Removed project {} from distributed memory entry: {}",
                        projectFullName, mapEntry.getKey());
                }
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
        IMap<BuildMemoryKey, MemoryImprintData> map = getDistributedMemory();
        if (map == null) {
            logger.warn("Cannot set custom URL - Hazelcast unavailable");
            return;
        }

        BuildMemoryKey key = new BuildMemoryKey(event);
        String projectFullName = r.getParent().getFullName();

        // ATOMIC OPERATION - Executes on partition owner, prevents race conditions
        Boolean found = map.executeOnKey(key, new SetCustomUrlProcessor(projectFullName, customUrl));

        if (found) {
            logger.trace("Recording custom URL for {}: {}", event, customUrl);
        } else {
            logger.warn("Could not set custom URL - event not found: {}", event);
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

        // ATOMIC OPERATION - Executes on partition owner, prevents race conditions
        Boolean found = map.executeOnKey(key,
                new SetUnsuccessfulMessageProcessor(projectFullName, unsuccessfulMessage));

        if (found) {
            logger.trace("Recording unsuccessful message for {}: {}", event, unsuccessfulMessage);
        } else {
            logger.warn("Could not set unsuccessful message - event not found: {}", event);
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
            GerritTriggeredEvent event = deserializeEvent(entry.getValue().getEventJson());
            if (event != null) {
                MemoryImprint imprint = reconstructMemoryImprint(event, entry.getValue());
                result.put(event, imprint);
            }
        }
        return result;
    }
}
