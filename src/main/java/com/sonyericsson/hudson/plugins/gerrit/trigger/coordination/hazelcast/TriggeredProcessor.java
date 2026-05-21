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
 * Hazelcast EntryProcessor for atomically recording a triggered build.
 * <p>
 * This processor prevents the "lost update" race condition that occurs when multiple
 * projects are triggered by the same Gerrit event simultaneously. Without atomic operations,
 * concurrent threads can overwrite each other's entries, causing some project entries to be lost
 * from BuildMemory.
 * <p>
 * Executes on the partition owner to ensure atomicity across distributed Hazelcast cluster.
 *
 */
public class TriggeredProcessor implements EntryProcessor<BuildMemoryKey, MemoryImprintData, Boolean> {
    private static final long serialVersionUID = 1L;

    private final String projectFullName;
    private final String eventJson;

    /**
     * Constructor.
     *
     * @param projectFullName the full name of the project
     * @param eventJson the serialized JSON representation of the event
     */
    public TriggeredProcessor(String projectFullName, String eventJson) {
        this.projectFullName = projectFullName;
        this.eventJson = eventJson;
    }

    @Override
    public Boolean process(Map.Entry<BuildMemoryKey, MemoryImprintData> entry) {
        MemoryImprintData data = entry.getValue();

        // Create new data if this is the first project triggered by this event
        if (data == null) {
            data = new MemoryImprintData();
            data.setEventJson(eventJson);
        }

        // Check if this project is already recorded (idempotency check)
        boolean found = false;
        if (data.getEntries() != null) {
            for (EntryData entryData : data.getEntries()) {
                if (projectFullName.equals(entryData.getProjectFullName())) {
                    found = true;
                    break;
                }
            }
        }

        // Add entry for this project if not already present
        if (!found) {
            EntryData newEntry = new EntryData();
            newEntry.setProjectFullName(projectFullName);
            data.addEntry(newEntry);
        }

        // Save the modified data back atomically
        entry.setValue(data);
        return !found; // Return true if this was a new entry, false if already existed
    }
}
