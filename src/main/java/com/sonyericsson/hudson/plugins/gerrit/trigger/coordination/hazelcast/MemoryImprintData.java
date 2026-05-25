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
 * Serializable data transfer object for BuildMemory storage in Hazelcast distributed maps.
 * <p>
 * <b>Design Rationale - Why separate from MemoryImprint?</b>
 * <p>
 * This class exists alongside
 * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint}
 * to separate API concerns from serialization concerns:
 * <ul>
 *   <li><b>MemoryImprint</b>: The main API class used by business logic throughout the plugin.
 *       Contains Jenkins objects ({@link hudson.model.Job}, {@link hudson.model.Run},
 *       {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent})
 *       which are not serializable or cross-JVM compatible.</li>
 *   <li><b>MemoryImprintData</b>: Serialization-optimized data structure for Hazelcast storage.
 *       Contains only primitives and strings (event JSON, project full names, build IDs)
 *       which can be safely serialized across JVM boundaries.</li>
 * </ul>
 * <p>
 * <b>Key Benefits of This Design:</b>
 * <ul>
 *   <li><b>Cross-JVM Compatibility</b>: Uses Hazelcast Compact Serialization which works across
 *       different JVMs and classloaders (critical for sidecar deployment scenarios)</li>
 *   <li><b>API Stability</b>: MemoryImprint API remains unchanged, preserving backward compatibility
 *       with existing code throughout the plugin</li>
 *   <li><b>No Object References</b>: Avoids serializing Jenkins objects which may not exist on
 *       remote replicas or may change between serialization/deserialization</li>
 *   <li><b>Explicit Conversion</b>: Forces explicit conversion at storage boundaries, making
 *       the serialization strategy visible and testable</li>
 * </ul>
 * <p>
 * <b>Conversion Strategy:</b>
 * <ul>
 *   <li><b>Storage</b>: {@link HazelcastBuildMemoryStorage} converts MemoryImprint to MemoryImprintData
 *       by serializing events to JSON and extracting string identifiers (project names, build IDs)</li>
 *   <li><b>Retrieval</b>: {@link HazelcastBuildMemoryStorage#reconstructMemoryImprint} converts
 *       MemoryImprintData back to MemoryImprint by deserializing events and looking up Jenkins
 *       objects via {@link jenkins.model.Jenkins#getItemByFullName}</li>
 * </ul>
 * <p>
 * <b>Alternative Considered and Rejected:</b>
 * Making MemoryImprint directly serializable was rejected because:
 * <ul>
 *   <li>Jenkins objects (Job, Run) are not reliably serializable across replicas</li>
 *   <li>GerritTriggeredEvent requires custom polymorphic serialization</li>
 *   <li>Would break in sidecar scenarios where classloaders differ</li>
 *   <li>Would tightly couple the API to Hazelcast serialization details</li>
 * </ul>
 *
 * @see HazelcastBuildMemoryStorage
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint
 * @see MemoryImprintDataSerializer
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
