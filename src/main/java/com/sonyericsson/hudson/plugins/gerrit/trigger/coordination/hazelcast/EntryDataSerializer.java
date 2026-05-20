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

/**
 * Hazelcast Compact Serializer for {@link EntryData}.
 * <p>
 * Serializes individual BuildMemory entries with fixed schema.
 *
 * @author CloudBees, Inc.
 */
public class EntryDataSerializer implements CompactSerializer<EntryData> {

    /**
     * Type name for schema registration.
     * Must be unique across all compact serialized types.
     */
    private static final String TYPE_NAME = "EntryData";

    @Override
    @NonNull
    public EntryData read(@NonNull CompactReader reader) {
        EntryData entry = new EntryData();
        entry.setProjectFullName(reader.readString("projectFullName"));
        entry.setBuildId(reader.readString("buildId"));
        entry.setBuildCompleted(reader.readBoolean("buildCompleted"));
        entry.setCancelled(reader.readBoolean("cancelled"));
        entry.setCustomUrl(reader.readString("customUrl"));
        entry.setUnsuccessfulMessage(reader.readString("unsuccessfulMessage"));
        entry.setTriggeredTimestamp(reader.readInt64("triggeredTimestamp"));
        entry.setCompletedTimestamp(reader.readNullableInt64("completedTimestamp"));
        entry.setStartedTimestamp(reader.readNullableInt64("startedTimestamp"));
        return entry;
    }

    @Override
    public void write(@NonNull CompactWriter writer, @NonNull EntryData entry) {
        writer.writeString("projectFullName", entry.getProjectFullName());
        writer.writeString("buildId", entry.getBuildId());
        writer.writeBoolean("buildCompleted", entry.isBuildCompleted());
        writer.writeBoolean("cancelled", entry.isCancelled());
        writer.writeString("customUrl", entry.getCustomUrl());
        writer.writeString("unsuccessfulMessage", entry.getUnsuccessfulMessage());
        writer.writeInt64("triggeredTimestamp", entry.getTriggeredTimestamp());
        writer.writeNullableInt64("completedTimestamp", entry.getCompletedTimestamp());
        writer.writeNullableInt64("startedTimestamp", entry.getStartedTimestamp());
    }

    @Override
    @NonNull
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    @NonNull
    public Class<EntryData> getCompactClass() {
        return EntryData.class;
    }
}
