/*
 * The MIT License
 *
 * Copyright 2026 CloudBees, Inc.
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

import java.util.ArrayList;
import java.util.List;

/**
 * Serializable data for MemoryImprint to store in Hazelcast.
 * <p>
 * Contains simplified Entry data without complex object references.
 * Uses Compact Serialization for cross-JVM compatibility in sidecar deployments.
 *
 */
public class MemoryImprintData {

    private String eventJson;  // JSON representation of GerritTriggeredEvent
    private List<EntryData> entries;

    /**
     * Default constructor.
     */
    public MemoryImprintData() {
        this.entries = new ArrayList<>();
    }

    /**
     * Constructor with parameters.
     *
     * @param eventJson serialized event
     * @param entries list of entry data
     */
    public MemoryImprintData(String eventJson, List<EntryData> entries) {
        this.eventJson = eventJson;
        if (entries != null) {
            this.entries = entries;
        } else {
            this.entries = new ArrayList<>();
        }
    }

    /**
     * Gets the serialized event JSON.
     *
     * @return event JSON string
     */
    public String getEventJson() {
        return eventJson;
    }

    /**
     * Sets the serialized event JSON.
     *
     * @param eventJson event JSON string
     */
    public void setEventJson(String eventJson) {
        this.eventJson = eventJson;
    }

    /**
     * Gets the list of entries.
     *
     * @return list of entry data
     */
    public List<EntryData> getEntries() {
        return entries;
    }

    /**
     * Sets the list of entries.
     *
     * @param entries list of entry data
     */
    public void setEntries(List<EntryData> entries) {
        this.entries = entries;
    }

    /**
     * Adds an entry to the list.
     *
     * @param entry the entry to add
     */
    public void addEntry(EntryData entry) {
        this.entries.add(entry);
    }
}
