/*
 *  The MIT License
 *
 *  Copyright 2026 CloudBees, Inc.
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

import com.hazelcast.map.EntryProcessor;

import java.util.Map;

/**
 * Hazelcast EntryProcessor for atomically marking a build as cancelled.
 * Executes on the partition owner to prevent race conditions.
 *
 */
public class BuildCancelledProcessor implements EntryProcessor<BuildMemoryKey, MemoryImprintData, Boolean> {
    private static final long serialVersionUID = 1L;

    private final String projectFullName;

    /**
     * Constructor.
     *
     * @param projectFullName the full name of the project
     */
    public BuildCancelledProcessor(String projectFullName) {
        this.projectFullName = projectFullName;
    }

    @Override
    public Boolean process(Map.Entry<BuildMemoryKey, MemoryImprintData> entry) {
        MemoryImprintData data = entry.getValue();

        // If no data exists, create it (shouldn't happen)
        if (data == null) {
            data = new MemoryImprintData();
        }

        // Find and update the entry for this project
        boolean found = false;
        if (data.getEntries() != null) {
            for (EntryData entryData : data.getEntries()) {
                if (projectFullName.equals(entryData.getProjectFullName())) {
                    entryData.setCancelled(true);
                    entryData.setCancelling(false);  // Clear cancelling flag
                    entryData.setBuildCompleted(true);  // Cancelled builds are also completed
                    found = true;
                    break;
                }
            }
        }

        // If project not found, add it
        if (!found) {
            EntryData newEntry = new EntryData();
            newEntry.setProjectFullName(projectFullName);
            newEntry.setCancelled(true);
            newEntry.setCancelling(false);
            newEntry.setBuildCompleted(true);  // Cancelled builds are also completed
            data.addEntry(newEntry);
        }

        // Save the modified data back atomically
        entry.setValue(data);
        return found;
    }
}
