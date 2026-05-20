/*
 *  The MIT License
 *
 *  Copyright (c) 2010, 2014 Sony Mobile Communications Inc. All rights reserved.
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
 * Hazelcast EntryProcessor for atomically setting a custom URL for a build.
 * Executes on the partition owner to prevent race conditions.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class SetCustomUrlProcessor implements EntryProcessor<BuildMemoryKey, MemoryImprintData, Boolean> {
    private static final long serialVersionUID = 1L;

    private final String projectFullName;
    private final String customUrl;

    /**
     * Constructor.
     *
     * @param projectFullName the full name of the project
     * @param customUrl the custom URL to set
     */
    public SetCustomUrlProcessor(String projectFullName, String customUrl) {
        this.projectFullName = projectFullName;
        this.customUrl = customUrl;
    }

    @Override
    public Boolean process(Map.Entry<BuildMemoryKey, MemoryImprintData> entry) {
        MemoryImprintData data = entry.getValue();
        if (data == null || data.getEntries() == null) {
            return false;
        }

        // Find and update the entry for this project
        for (EntryData entryData : data.getEntries()) {
            if (projectFullName.equals(entryData.getProjectFullName())) {
                entryData.setCustomUrl(customUrl);
                // Save the modified data back atomically
                entry.setValue(data);
                return true;
            }
        }

        return false;
    }
}
