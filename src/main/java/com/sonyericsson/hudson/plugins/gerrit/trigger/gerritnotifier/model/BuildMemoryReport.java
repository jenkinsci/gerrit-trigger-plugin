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

package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model;

import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import hudson.model.ModelObject;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Contains a snapshot clone of a {@link BuildMemory}.
 *
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 * @see BuildMemory#report()
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.GerritManagement.Diagnostics
 */
public class BuildMemoryReport implements Map<GerritTriggeredEvent, List<BuildMemory.MemoryImprint.Entry>>, ModelObject {

    private final Map<GerritTriggeredEvent, List<BuildMemory.MemoryImprint.Entry>> internal;

    /**
     * Default Constructor.
     */
    public BuildMemoryReport() {
        internal = new TreeMap<GerritTriggeredEvent, List<BuildMemory.MemoryImprint.Entry>>();
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
