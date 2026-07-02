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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.EntryData;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.MemoryImprintData;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Hazelcast Compact Serializer for {@link MemoryImprintData}.
 * <p>
 * Compact Serialization is schema-based and doesn't require class definitions
 * on the Hazelcast server (sidecar container). This enables cross-JVM serialization
 * without classloading issues.
 * <p>
 * The {@link GerritTriggeredEvent} held by {@link MemoryImprintData} is a live object; this
 * serializer is the boundary where it is turned into (and restored from) a JSON string on the
 * wire, using {@link PolymorphicEventTypeAdapter} to preserve the concrete event subtype. Keeping
 * this concern here means {@link MemoryImprintData} stays a plain, storage-agnostic DTO.
 *
 */
public class MemoryImprintDataSerializer implements CompactSerializer<MemoryImprintData> {

    private static final Logger logger = LoggerFactory.getLogger(MemoryImprintDataSerializer.class);

    /**
     * Type name for schema registration.
     * Uses fully-qualified name to prevent conflicts in shared Hazelcast clusters.
     */
    private static final String TYPE_NAME = "com.sonyericsson.gerrit.trigger.MemoryImprintData";

    /**
     * Gson instance for JSON serialization of events.
     * Configured to handle polymorphic event types by including runtime type information.
     */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(GerritTriggeredEvent.class, new PolymorphicEventTypeAdapter())
            .create();

    @Override
    @NonNull
    public MemoryImprintData read(@NonNull CompactReader reader) {
        String eventJson = reader.readString("eventJson");
        GerritTriggeredEvent event = deserializeEvent(eventJson);

        // Read entries array using Compact Serialization array support
        EntryData[] entriesArray = reader.readArrayOfCompact("entries", EntryData.class);
        List<EntryData> entries = new ArrayList<>();
        if (entriesArray != null) {
            for (EntryData entry : entriesArray) {
                entries.add(entry);
            }
        }

        return new MemoryImprintData(event, entries);
    }

    @Override
    public void write(@NonNull CompactWriter writer, @NonNull MemoryImprintData data) {
        writer.writeString("eventJson", serializeEvent(data.getEvent()));

        // Write entries array using Compact Serialization array support
        List<EntryData> entries = data.getEntries();
        EntryData[] entriesArray = null;
        if (entries != null && !entries.isEmpty()) {
            entriesArray = entries.toArray(new EntryData[0]);
        }
        writer.writeArrayOfCompact("entries", entriesArray);
    }

    /**
     * Serializes a GerritTriggeredEvent to JSON.
     *
     * @param event the event to serialize, may be null
     * @return JSON string, or null if the event is null or serialization fails
     */
    private static String serializeEvent(GerritTriggeredEvent event) {
        if (event == null) {
            return null;
        }
        try {
            // IMPORTANT: Must explicitly specify GerritTriggeredEvent.class to ensure
            // the PolymorphicEventTypeAdapter is used, even when event is a concrete subclass.
            return GSON.toJson(event, GerritTriggeredEvent.class);
        } catch (Exception e) {
            logger.error("Failed to serialize event to JSON: " + event, e);
            return null;
        }
    }

    /**
     * Deserializes a GerritTriggeredEvent from JSON.
     *
     * @param eventJson the JSON string, may be null
     * @return deserialized event, or null if the JSON is null or deserialization fails
     */
    private static GerritTriggeredEvent deserializeEvent(String eventJson) {
        if (eventJson == null) {
            return null;
        }
        try {
            return GSON.fromJson(eventJson, GerritTriggeredEvent.class);
        } catch (Exception e) {
            logger.error("Failed to deserialize event from JSON (length: " + eventJson.length() + ")", e);
            return null;
        }
    }

    @Override
    @NonNull
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    @NonNull
    public Class<MemoryImprintData> getCompactClass() {
        return MemoryImprintData.class;
    }
}
