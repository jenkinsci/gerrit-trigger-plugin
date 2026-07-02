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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model;

import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain data transfer object mirroring the state of a
 * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint}.
 * <p>
 * It holds the triggering {@link GerritTriggeredEvent} together with a list of {@link EntryData}.
 * Conversion to and from {@code MemoryImprint} is a straight structural copy provided by
 * {@link BuildMemory.MemoryImprint#toData()} and {@link BuildMemory.MemoryImprint#fromData(MemoryImprintData)};
 * neither performs any Jenkins lookups.
 * <p>
 * <b>Serialization is not this type's concern.</b> The event is kept as a live object here.
 * Turning it into a storage/wire representation (for example JSON, for distributed backends)
 * is the responsibility of the storage layer's serializer, so this DTO stays free of any
 * storage-technology dependency and is easy to reuse or extract.
 *
 * @see EntryData
 * @see BuildMemory.MemoryImprint
 */
public class MemoryImprintData {

    private GerritTriggeredEvent event;
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
     * @param event   the triggering event
     * @param entries list of entry data
     */
    public MemoryImprintData(GerritTriggeredEvent event, List<EntryData> entries) {
        this.event = event;
        if (entries != null) {
            this.entries = entries;
        } else {
            this.entries = new ArrayList<>();
        }
    }

    /**
     * Gets the triggering event.
     *
     * @return the event
     */
    public GerritTriggeredEvent getEvent() {
        return event;
    }

    /**
     * Sets the triggering event.
     *
     * @param event the event
     */
    public void setEvent(GerritTriggeredEvent event) {
        this.event = event;
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
