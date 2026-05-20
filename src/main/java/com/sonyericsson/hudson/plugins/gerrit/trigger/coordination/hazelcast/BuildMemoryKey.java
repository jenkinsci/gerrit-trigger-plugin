/*
 * The MIT License
 *
 *
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

import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import java.io.Serializable;
import java.util.Objects;

/**
 * Key class for BuildMemory entries in Hazelcast.
 * <p>
 * Uses event ID instead of event object for serialization efficiency.
 * The event ID is deterministic (same event on different replicas produces same ID).
 *
 * @author CloudBees, Inc.
 */
public class BuildMemoryKey implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String eventId;

    /**
     * Constructor from GerritTriggeredEvent.
     *
     * @param event the Gerrit event
     */
    public BuildMemoryKey(GerritTriggeredEvent event) {
        this.eventId = EventIdentifier.generateEventId(event);
    }

    /**
     * Constructor from event ID string.
     *
     * @param eventId the event identifier
     */
    public BuildMemoryKey(String eventId) {
        this.eventId = eventId;
    }

    /**
     * Gets the event identifier.
     *
     * @return event ID
     */
    public String getEventId() {
        return eventId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BuildMemoryKey that = (BuildMemoryKey)o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "BuildMemoryKey{eventId='" + eventId + "'}";
    }
}
