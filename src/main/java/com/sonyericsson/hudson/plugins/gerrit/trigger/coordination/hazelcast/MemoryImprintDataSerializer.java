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

import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Hazelcast Compact Serializer for {@link MemoryImprintData}.
 * <p>
 * Compact Serialization is schema-based and doesn't require class definitions
 * on the Hazelcast server (sidecar container). This enables cross-JVM serialization
 * without classloading issues.
 *
 * @author CloudBees, Inc.
 */
public class MemoryImprintDataSerializer implements CompactSerializer<MemoryImprintData> {

    /**
     * Type name for schema registration.
     * Must be unique across all compact serialized types.
     */
    private static final String TYPE_NAME = "MemoryImprintData";

    @Override
    @NonNull
    public MemoryImprintData read(@NonNull CompactReader reader) {
        String eventJson = reader.readString("eventJson");

        // Read entries array using Compact Serialization array support
        EntryData[] entriesArray = reader.readArrayOfCompact("entries", EntryData.class);
        List<EntryData> entries = new ArrayList<>();
        if (entriesArray != null) {
            for (EntryData entry : entriesArray) {
                entries.add(entry);
            }
        }

        return new MemoryImprintData(eventJson, entries);
    }

    @Override
    public void write(@NonNull CompactWriter writer, @NonNull MemoryImprintData data) {
        writer.writeString("eventJson", data.getEventJson());

        // Write entries array using Compact Serialization array support
        List<EntryData> entries = data.getEntries();
        EntryData[] entriesArray = null;
        if (entries != null && !entries.isEmpty()) {
            entriesArray = entries.toArray(new EntryData[0]);
        }
        writer.writeArrayOfCompact("entries", entriesArray);
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
