/*
 * The MIT License
 *
 *  Copyright 2026 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.hazelcast;

import com.hazelcast.map.EntryProcessor;
import hudson.model.Run;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Hazelcast EntryProcessor for atomic retriggered operation.
 * <p>
 * Handles the case where a job is retriggered for the same event.
 * Resets the retriggered project's build information while preserving
 * other builds from the previous trigger context.
 * <p>
 * This processor ensures atomicity - even if multiple replicas attempt
 * concurrent retriggered operations, the updates won't overwrite each other.
 *
 * @see HazelcastBuildMemoryStorage#retriggered
 */
public class RetriggeredProcessor implements EntryProcessor<BuildMemoryKey, MemoryImprintData, Void>, Serializable {

    private static final long serialVersionUID = 1L;

    private final String projectFullName;
    private final String eventJson;
    private final List<BuildInfo> otherBuildsList;

    /**
     * Constructor for RetriggeredProcessor.
     *
     * @param projectFullName the full name of the project being retriggered
     * @param eventJson JSON serialization of the event
     * @param otherBuilds list of other builds from previous trigger context (can be null)
     */
    public RetriggeredProcessor(String projectFullName, String eventJson, List<Run> otherBuilds) {
        this.projectFullName = projectFullName;
        this.eventJson = eventJson;

        // Convert Run objects to serializable BuildInfo
        // (Run objects are not serializable, so we extract the needed data)
        if (otherBuilds != null && !otherBuilds.isEmpty()) {
            this.otherBuildsList = new java.util.ArrayList<>(otherBuilds.size());
            for (Run build : otherBuilds) {
                this.otherBuildsList.add(new BuildInfo(
                    build.getParent().getFullName(),
                    build.getId(),
                    !build.isBuilding()
                ));
            }
        } else {
            this.otherBuildsList = null;
        }
    }

    @Override
    public Void process(Map.Entry<BuildMemoryKey, MemoryImprintData> entry) {
        MemoryImprintData data = entry.getValue();

        if (data == null) {
            // Create new memory imprint data
            data = new MemoryImprintData();
            data.setEventJson(eventJson);

            if (otherBuildsList != null) {
                // Populate with old build info
                for (BuildInfo buildInfo : otherBuildsList) {
                    EntryData entryData = new EntryData();
                    entryData.setProjectFullName(buildInfo.projectFullName);
                    entryData.setBuildId(buildInfo.buildId);
                    entryData.setBuildCompleted(buildInfo.completed);
                    data.addEntry(entryData);
                }
            }
        }

        // Reset the retriggered project (clear build info)
        boolean found = false;

        if (data.getEntries() != null) {
            for (EntryData entryData : data.getEntries()) {
                if (projectFullName.equals(entryData.getProjectFullName())) {
                    // Reset this entry
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
            // Add new entry for retriggered project
            EntryData entryData = new EntryData();
            entryData.setProjectFullName(projectFullName);
            data.addEntry(entryData);
        }

        // Update the entry value
        entry.setValue(data);

        return null;
    }

    /**
     * Serializable wrapper for Build information.
     * Used to transfer build data across Hazelcast cluster without serializing Run objects.
     */
    private static class BuildInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String projectFullName;
        private final String buildId;
        private final boolean completed;

        /**
         * Constructor for BuildInfo.
         *
         * @param projectFullName the full name of the project
         * @param buildId the build ID
         * @param completed true if the build is completed
         */
        BuildInfo(String projectFullName, String buildId, boolean completed) {
            this.projectFullName = projectFullName;
            this.buildId = buildId;
            this.completed = completed;
        }
    }
}
