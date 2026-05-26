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

import java.util.Map;

/**
 * Hazelcast Entry Processor to atomically set the "cancelling" flag for a project entry.
 * <p>
 * This processor is used when the build cancellation policy decides a build should be cancelled,
 * marking the intent before Jenkins actually processes the cancellation.
 * The "cancelling" flag prevents the same build from being reconsidered for cancellation
 * in future policy checks.
 * <p>
 * Thread-safe atomic operation.
 *
 */
public class SetCancellingProcessor implements EntryProcessor<BuildMemoryKey, MemoryImprintData, Object> {

    private static final long serialVersionUID = 1L;

    private final String projectFullName;

    /**
     * Constructor.
     *
     * @param projectFullName the full name of the project being marked for cancellation
     */
    public SetCancellingProcessor(String projectFullName) {
        this.projectFullName = projectFullName;
    }

    @Override
    public Object process(Map.Entry<BuildMemoryKey, MemoryImprintData> entry) {
        MemoryImprintData data = entry.getValue();
        if (data == null) {
            return null;
        }

        // Find the entry for the project and set cancelling flag
        boolean updated = false;
        for (EntryData entryData : data.getEntries()) {
            if (projectFullName.equals(entryData.getProjectFullName())) {
                // Only set cancelling if not already completed, cancelling, or cancelled
                if (!entryData.isBuildCompleted() && !entryData.isCancelling() && !entryData.isCancelled()) {
                    entryData.setCancelling(true);
                    updated = true;
                }
            }
        }

        // Save changes if we updated anything
        if (updated) {
            entry.setValue(data);
        }

        return null;
    }
}
