/*
 * The MIT License
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
 * Hazelcast Compact Serializer for {@link EventClaim}.
 * <p>
 * Compact Serialization is schema-based and doesn't require class definitions
 * on the Hazelcast server (sidecar container). This enables cross-JVM serialization
 * without classloading issues.
 * <p>
 * The serializer writes a schema with field names and types, which the sidecar
 * Hazelcast can process without needing the EventClaim class.
 *
 * @author CloudBees, Inc.
 */
public class EventClaimSerializer implements CompactSerializer<EventClaim> {

    /**
     * Type name for schema registration.
     * Must be unique across all compact serialized types.
     */
    private static final String TYPE_NAME = "EventClaim";

    @Override
    @NonNull
    public EventClaim read(@NonNull CompactReader reader) {
        String eventId = reader.readString("eventId");
        String claimedBy = reader.readString("claimedBy");
        long claimedAt = reader.readInt64("claimedAt");
        String eventType = reader.readString("eventType");

        return new EventClaim(eventId, claimedBy, claimedAt, eventType);
    }

    @Override
    public void write(@NonNull CompactWriter writer, @NonNull EventClaim claim) {
        writer.writeString("eventId", claim.getEventId());
        writer.writeString("claimedBy", claim.getClaimedBy());
        writer.writeInt64("claimedAt", claim.getClaimedAt());
        writer.writeString("eventType", claim.getEventType());
    }

    @Override
    @NonNull
    public String getTypeName() {
        return TYPE_NAME;
    }

    @Override
    @NonNull
    public Class<EventClaim> getCompactClass() {
        return EventClaim.class;
    }
}
