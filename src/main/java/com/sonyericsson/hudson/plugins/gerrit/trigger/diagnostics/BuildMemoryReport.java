/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 CloudBees Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics;

import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.RefUpdated;
import hudson.model.ModelObject;

import javax.annotation.Nonnull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Contains a snapshot clone of a {@link BuildMemory}.
 *
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 * @see BuildMemory#report()
 * @see Diagnostics
 */
public class BuildMemoryReport implements Map<GerritTriggeredEvent, List<BuildMemory.MemoryImprint.Entry>>, ModelObject {

    private final Map<GerritTriggeredEvent, List<BuildMemory.MemoryImprint.Entry>> internal;

    /**
     * The format used to display timestamps.
     *
     * A variant of ISO 8601 with the 'T' replaced by a space for simpler ocular parsing.
     */
    public static final ThreadLocal<DateFormat> TS_FORMAT = new ThreadLocal<DateFormat>() {
        @Override
        public DateFormat get() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ");
        }
      };

    /**
     * Default Constructor.
     */
    public BuildMemoryReport() {
        internal = new TreeMap<GerritTriggeredEvent, List<BuildMemory.MemoryImprint.Entry>>(
                new Comparator<GerritTriggeredEvent>() {
            @Override
            public int compare(GerritTriggeredEvent a, GerritTriggeredEvent b) {
                int to = a.getEventCreatedOn().compareTo(b.getEventCreatedOn()) * -1;
                if (to == 0) {
                    return Integer.valueOf(a.hashCode()).compareTo(b.hashCode()) * -1;
                }
                return to;
            }
        });
    }

    /**
     * Gets a sorted list of the contents from {@link #entrySet()}.
     * The sorting is based on the inverse comparison of {@link GerritTriggeredEvent#getEventCreatedOn()}.
     *
     * @return a sorted list of this report's entries.
     */
    public List<Map.Entry<GerritTriggeredEvent, List<BuildMemory.MemoryImprint.Entry>>> getSortedEntrySet() {
        List<Map.Entry<GerritTriggeredEvent, List<BuildMemory.MemoryImprint.Entry>>> entries =
                new LinkedList<Entry<GerritTriggeredEvent,
                        List<BuildMemory.MemoryImprint.Entry>>>(entrySet());
        Collections.sort(entries, new Comparator<Entry<GerritTriggeredEvent,
                List<BuildMemory.MemoryImprint.Entry>>>() {
            @Override
            public int compare(Map.Entry<GerritTriggeredEvent, List<BuildMemory.MemoryImprint.Entry>> a,
                               Map.Entry<GerritTriggeredEvent, List<BuildMemory.MemoryImprint.Entry>> b) {
                return a.getKey().getEventCreatedOn().compareTo(b.getKey().getEventCreatedOn()) * -1;
            }
        });
        return entries;
    }

    /**
     * Generates a one liner display name for the event.
     *
     * For {@link ChangeBasedEvent}s: "type change#/patchSet# @ timestamp".
     * For {@link RefUpdated} events: "type project @ timestamp".
     * Default: "type @ timestamp"
     *
     * @param event the event
     * @return a name to display
     */
    public String getDisplayNameFor(GerritTriggeredEvent event) {
        StringBuilder display = new StringBuilder(event.getEventType().getTypeValue());
        if (event instanceof ChangeBasedEvent) {
            display.append(' ');
            display.append(((ChangeBasedEvent)event).getChange().getNumber());
            display.append('/');
            display.append(((ChangeBasedEvent)event).getPatchSet().getNumber());
        } else if (event instanceof RefUpdated) {
            display.append(' ');
            display.append(((RefUpdated)event).getRefUpdate().getProject());
        }
        display.append(" @ ");
        display.append(TS_FORMAT.get().format(event.getEventCreatedOn()));
        return display.toString();
    }

    @Override
    public int size() {
        return internal.size();
    }

    @Override
    public boolean isEmpty() {
        return internal.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return internal.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return internal.containsValue(value);
    }

    @Override
    public List<BuildMemory.MemoryImprint.Entry> get(Object key) {
        return internal.get(key);
    }

    @Override
    public List<BuildMemory.MemoryImprint.Entry> put(GerritTriggeredEvent key,
                                                     List<BuildMemory.MemoryImprint.Entry> value) {
        return internal.put(key, value);
    }

    @Override
    public List<BuildMemory.MemoryImprint.Entry> remove(Object key) {
        return internal.remove(key);
    }

    @Override
    public void putAll(Map<? extends GerritTriggeredEvent, ? extends List<BuildMemory.MemoryImprint.Entry>> m) {
        internal.putAll(m);
    }

    @Override
    public void clear() {
        internal.clear();
    }

    @Override
    @Nonnull
    public Set<GerritTriggeredEvent> keySet() {
        return internal.keySet();
    }

    @Override
    @Nonnull
    public Collection<List<BuildMemory.MemoryImprint.Entry>> values() {
        return internal.values();
    }

    @Override
    @Nonnull
    public Set<Entry<GerritTriggeredEvent, List<BuildMemory.MemoryImprint.Entry>>> entrySet() {
        return internal.entrySet();
    }

    @Override
    public String getDisplayName() {
        return Messages.BuildMemoryReport_DisplayName();
    }
}
