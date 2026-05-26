/*
 * The MIT License
 *
 *  Copyright 2026 CloudBees, Inc.
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

import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.RefUpdated;

/**
 * Utility class for generating unique, consistent event identifiers.
 * <p>
 * Event IDs are used for distributed event claiming in HA/HS (High Availability/High Scalability) environments.
 * The same Gerrit event arriving at different replicas must produce the same event ID
 * to enable proper claim coordination.
 * <p>
 * Event IDs are deterministic and based on immutable event properties such as:
 * <ul>
 *   <li>Change number and patchset number (for change-based events)</li>
 *   <li>Project and ref name (for ref-updated events)</li>
 *   <li>Event type</li>
 *   <li>Server-side timestamp (eventCreatedOn from Gerrit server)</li>
 * </ul>
 * <p>
 * <b>Important:</b> Uses {@code eventCreatedOn} (server timestamp) rather than {@code receivedOn}
 * (replica timestamp) to ensure identical event IDs across all replicas receiving the same event.
 *
 */
public final class EventIdentifier {

    /**
     * Length of short Git revision hash (first 8 characters).
     */
    private static final int SHORT_REVISION_LENGTH = 8;

    /**
     * Initial prime number for hash computation (standard Java hashCode practice).
     */
    private static final int HASH_INITIAL_PRIME = 17;

    /**
     * Multiplier prime number for hash computation (standard Java hashCode practice).
     */
    private static final int HASH_MULTIPLIER_PRIME = 31;

    /**
     * Number of bits to shift for long-to-int hash conversion.
     */
    private static final int HASH_LONG_SHIFT_BITS = 32;

    /**
     * Private constructor to prevent instantiation.
     */
    private EventIdentifier() {
        // Utility class
    }

    /**
     * Generates a unique identifier for a Gerrit event.
     * <p>
     * The ID is deterministic - the same event on different replicas produces the same ID.
     * This is critical for distributed event claiming to work correctly.
     *
     * @param event the Gerrit event
     * @return unique event identifier
     */
    public static String generateEventId(GerritTriggeredEvent event) {
        if (event instanceof ChangeBasedEvent) {
            return generateChangeBasedEventId((ChangeBasedEvent)event);
        }

        if (event instanceof RefUpdated) {
            return generateRefUpdatedEventId((RefUpdated)event);
        }

        // Fallback for other event types
        return generateFallbackEventId(event);
    }

    /**
     * Generates ID for change-based events (patchset-created, comment-added, etc.).
     * <p>
     * Prefers {@code changeId} over deprecated {@code change.getNumber()}.
     * Includes branch since changeId is not unique across branches.
     *
     * @param event the change-based event
     * @return event ID in format: change-{project}-{changeId}-{branch}-{patchset}-{type}-{timestamp}
     */
    private static String generateChangeBasedEventId(ChangeBasedEvent event) {
        Change change = event.getChange();
        PatchSet patchSet = event.getPatchSet();

        if (change == null || patchSet == null) {
            return generateFallbackEventId(event);
        }

        // Use server-side timestamp (eventCreatedOn) for consistency across replicas
        // Fall back to receivedOn if eventCreatedOn is not available
        long timestamp = getEventTimestamp(event);

        // Prefer changeId (I...) over deprecated change number
        // Note: changeId is not unique across branches, so branch must be included
        String changeIdentifier;
        if (change.getId() != null && !change.getId().isEmpty()) {
            // Use Change-Id (format: I1234567890abcdef...)
            changeIdentifier = sanitize(change.getId());
        } else {
            // Fallback to change number if changeId not available (old Gerrit versions)
            changeIdentifier = "num-" + change.getNumber();
        }

        // Include branch for uniqueness (changeId can be reused across branches)
        String branch = "unknown";
        if (change.getBranch() != null && !change.getBranch().isEmpty()) {
            branch = sanitize(change.getBranch());
        }

        // Format: change-{project}-{changeId}-{branch}-{patchset}-{type}-{timestamp}
        return String.format("change-%s-%s-%s-%s-%s-%d",
                sanitize(change.getProject()),
                changeIdentifier,
                branch,
                patchSet.getNumber(),
                sanitizeEventType(event.getEventType().getTypeValue()),
                timestamp);
    }

    /**
     * Generates ID for ref-updated events.
     *
     * @param event the ref-updated event
     * @return event ID in format: ref-{project}-{refName}-{shortRev}-{timestamp}
     */
    private static String generateRefUpdatedEventId(RefUpdated event) {
        if (event.getRefUpdate() == null) {
            return generateFallbackEventId(event);
        }

        String project = sanitize(event.getRefUpdate().getProject());
        String refName = sanitize(event.getRefUpdate().getRefName());
        String newRev = event.getRefUpdate().getNewRev();

        // Use first 8 chars of revision (short hash)
        String shortRev;
        if (newRev != null && newRev.length() >= SHORT_REVISION_LENGTH) {
            shortRev = newRev.substring(0, SHORT_REVISION_LENGTH);
        } else {
            shortRev = "unknown";
        }

        // Use server-side timestamp (eventCreatedOn) for consistency across replicas
        // Fall back to receivedOn if eventCreatedOn is not available
        long timestamp = getEventTimestamp(event);

        // Format: ref-<project>-<refName>-<shortRev>-<timestamp>
        return String.format("ref-%s-%s-%s-%d",
                project,
                refName,
                shortRev,
                timestamp);
    }

    /**
     * Generates fallback ID for events that don't match known patterns.
     * <p>
     * Uses only deterministic fields to ensure the same event produces the same ID
     * across all replicas. Specifically avoids {@code hashCode()} which is not stable
     * across JVMs.
     *
     * @param event the event
     * @return event ID in format: event-{type}-{server}-{timestamp}-{deterministicHash}
     */
    private static String generateFallbackEventId(GerritTriggeredEvent event) {
        // Use server-side timestamp (eventCreatedOn) for consistency across replicas
        // Fall back to receivedOn if eventCreatedOn is not available
        long timestamp = getEventTimestamp(event);

        // Get server name for additional uniqueness
        String serverName = "unknown";
        if (event.getProvider() != null && event.getProvider().getName() != null) {
            serverName = sanitize(event.getProvider().getName());
        }

        // Create deterministic hash from event fields (not object hashCode!)
        int deterministicHash = computeDeterministicHash(event);

        // Format: event-<type>-<server>-<timestamp>-<deterministicHash>
        // All components are deterministic across replicas
        return String.format("event-%s-%s-%d-%08x",
                sanitizeEventType(event.getEventType().getTypeValue()),
                serverName,
                timestamp,
                deterministicHash);
    }

    /**
     * Computes a deterministic hash from event fields.
     * <p>
     * This hash is stable across JVMs because it's computed from the event's actual
     * field values, not from the object's identity or {@code hashCode()}.
     * <p>
     * Uses the same fields that would typically be in a well-implemented {@code hashCode()}:
     * event type and timestamp. The provider name is included in the event ID directly,
     * so doesn't need to be part of the hash.
     *
     * @param event the event
     * @return deterministic hash value
     */
    private static int computeDeterministicHash(GerritTriggeredEvent event) {
        int result = HASH_INITIAL_PRIME; // Start with prime number

        // Use event type (always available)
        if (event.getEventType() != null && event.getEventType().getTypeValue() != null) {
            result = HASH_MULTIPLIER_PRIME * result + event.getEventType().getTypeValue().hashCode();
        }

        // Use timestamp (already deterministic across replicas)
        long timestamp = getEventTimestamp(event);
        result = HASH_MULTIPLIER_PRIME * result + (int)(timestamp ^ (timestamp >>> HASH_LONG_SHIFT_BITS));

        // Use server name if available
        if (event.getProvider() != null && event.getProvider().getName() != null) {
            result = HASH_MULTIPLIER_PRIME * result + event.getProvider().getName().hashCode();
        }

        return result;
    }

    /**
     * Gets the event timestamp, preferring server-side eventCreatedOn over replica-local receivedOn.
     * <p>
     * This ensures that the same Gerrit event produces the same event ID across all Jenkins replicas,
     * which is critical for distributed event claiming to work correctly.
     * <p>
     * Falls back to receivedOn if eventCreatedOn is null (defensive programming for older Gerrit
     * versions or events that don't populate this field).
     *
     * @param event the Gerrit event
     * @return timestamp in milliseconds since epoch
     */
    private static long getEventTimestamp(GerritTriggeredEvent event) {
        if (event.getEventCreatedOn() != null) {
            // Prefer server-side timestamp (same across all replicas)
            return event.getEventCreatedOn().getTime();
        }
        // Fallback to replica-local timestamp (may differ between replicas)
        return event.getReceivedOn();
    }

    /**
     * Sanitizes a string to be safe for use in event ID.
     * Replaces special characters with underscores.
     *
     * @param input the input string
     * @return sanitized string safe for use in identifiers
     */
    private static String sanitize(String input) {
        if (input == null) {
            return "null";
        }
        // Replace non-alphanumeric characters (except dash and underscore) with underscore
        return input.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * Sanitizes event type string.
     * Converts to lowercase and replaces spaces/special chars with dashes.
     *
     * @param eventType the event type
     * @return sanitized event type
     */
    private static String sanitizeEventType(String eventType) {
        if (eventType == null) {
            return "unknown";
        }
        return eventType.toLowerCase().replaceAll("[^a-z0-9-]", "-");
    }
}
