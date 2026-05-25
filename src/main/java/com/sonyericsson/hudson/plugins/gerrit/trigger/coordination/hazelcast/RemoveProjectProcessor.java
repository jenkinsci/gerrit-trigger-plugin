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

import java.io.Serializable;
import java.util.Map;

/**
 * Hazelcast EntryProcessor for atomic project removal operation.
 * <p>
 * Removes all EntryData entries matching the specified project name from
 * a single MemoryImprintData entry. If after removal the MemoryImprintData
 * becomes empty (no entries left), it signals for map entry deletion by
 * returning {@code true}.
 * <p>
 * This processor ensures atomicity - even if multiple replicas attempt
 * concurrent removal operations, the updates won't overwrite each other.
 *
 * @see HazelcastBuildMemoryStorage#removeProject
 */
public class RemoveProjectProcessor implements EntryProcessor<BuildMemoryKey, MemoryImprintData, Boolean>,
        Serializable {

    private static final long serialVersionUID = 1L;

    private final String projectFullName;

    /**
     * Constructor for RemoveProjectProcessor.
     *
     * @param projectFullName the full name of the project to remove
     */
    public RemoveProjectProcessor(String projectFullName) {
        this.projectFullName = projectFullName;
    }

    @Override
    public Boolean process(Map.Entry<BuildMemoryKey, MemoryImprintData> entry) {
        MemoryImprintData data = entry.getValue();

        if (data == null || data.getEntries() == null) {
            // No data or no entries - nothing to remove
            return false;
        }

        // Remove matching entries
        boolean removed = data.getEntries().removeIf(entryData ->
            projectFullName.equals(entryData.getProjectFullName())
        );

        if (removed) {
            // Check if MemoryImprintData is now empty
            if (data.getEntries() == null || data.getEntries().isEmpty()) {
                // Signal that this map entry should be deleted
                return true;
            } else {
                // Update the entry with modified data
                entry.setValue(data);
                return false;
            }
        }

        // Nothing was removed
        return false;
    }
}
