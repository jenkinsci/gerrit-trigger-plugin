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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.EventClaimStrategy;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Hazelcast-backed implementation of EventClaimStrategy for HA/HS deployments.
 * <p>
 * In HA/HS (High Availability/High Scalability) environments with multiple replicas, each Gerrit event
 * arrives at all replicas via SSH event stream. To prevent duplicate builds,
 * replicas use distributed event claiming:
 * <ul>
 *   <li>First replica to claim an event processes it</li>
 *   <li>Other replicas skip the event (already claimed)</li>
 * </ul>
 * <p>
 * Claims are stored in a Hazelcast IMap with atomic {@code putIfAbsent} operations
 * to prevent race conditions. Claims automatically expire via TTL to prevent memory leaks.
 * <p>
 * <strong>Replica-level claiming:</strong> Once a replica claims an event, ALL jobs
 * on that replica can process it. This allows multiple jobs on the same replica to be
 * triggered by the same event while preventing duplicate processing across replicas.
 * <p>
 * <strong>Fail-open behavior:</strong> If Hazelcast is unavailable, this strategy
 * allows event processing to continue (better to risk duplicate builds than drop events).
 *
 */
public class HazelcastEventClaimStrategy extends EventClaimStrategy {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastEventClaimStrategy.class);

    /**
     * Hazelcast map name for event claims.
     */
    private static final String CLAIMS_MAP_NAME = "gerrit-trigger-event-claims";

    /**
     * Default TTL for event claims in seconds (5 minutes).
     * Claims expire automatically to prevent memory leaks.
     */
    private static final long DEFAULT_CLAIM_TTL_SECONDS = 300;

    /**
     * System property to override claim TTL.
     * Example: -Dgerrit.trigger.coordination.hazelcast.claim.ttl.seconds=600
     */
    private static final String CLAIM_TTL_PROPERTY = "gerrit.trigger.coordination.hazelcast.claim.ttl.seconds";

    /**
     * Cached claim TTL in seconds.
     * Parsed once at class initialization from system property or default.
     */
    private static final long CLAIM_TTL_SECONDS = parseClaimTtlSeconds();

    /**
     * Cached instance identifier (hostname or pod name).
     */
    private static volatile String instanceId = null;

    @Override
    @NonNull
    public ClaimResult withClaim(@NonNull GerritTriggeredEvent event, @NonNull Runnable claimed) {
        // Get Hazelcast instance
        HazelcastInstance hazelcast = HazelcastInstanceProvider.getInstance();
        if (hazelcast == null) {
            logger.error("Hazelcast not available, cannot claim event. Allowing event processing (fail-open).");
            // Fail-open: execute the action even without claiming
            try {
                claimed.run();
                return new SuccessfulClaim();
            } catch (Exception e) {
                return new FailedClaim(e);
            }
        }

        // Generate event ID
        String eventId = EventIdentifier.generateEventId(event);
        String thisInstanceId = getInstanceId();

        try {
            // Get claims map
            IMap<String, EventClaim> claimsMap = hazelcast.getMap(CLAIMS_MAP_NAME);

            // Check if event is already claimed
            EventClaim existingClaim = claimsMap.get(eventId);

            if (existingClaim != null) {
                // Event already claimed - check who claimed it
                if (existingClaim.getClaimedBy().equals(thisInstanceId)) {
                    // Claimed by THIS replica (another job already processed it)
                    // Allow this job to also process the event
                    logger.trace("Event already claimed by this replica, allowing: {} (job processing)",
                            eventId);
                    try {
                        claimed.run();
                        return new SuccessfulClaim();
                    } catch (Exception actionException) {
                        logger.error("Error executing action after claim: {}", eventId, actionException);
                        return new FailedClaim(actionException);
                    }
                } else {
                    // Claimed by ANOTHER replica - skip processing
                    logger.debug("Event already claimed by {}: {} (type: {})",
                            existingClaim.getClaimedBy(), eventId, event.getEventType().getTypeValue());
                    return new NotClaimedResult();
                }
            }

            // Event not yet claimed - attempt to claim it
            EventClaim claim = new EventClaim(
                    eventId,
                    thisInstanceId,
                    System.currentTimeMillis(),
                    event.getEventType().getTypeValue()
            );

            // Attempt atomic claim with TTL
            EventClaim previousClaim = claimsMap.putIfAbsent(
                    eventId,
                    claim,
                    CLAIM_TTL_SECONDS,
                    TimeUnit.SECONDS
            );

            if (previousClaim == null) {
                // Successfully claimed by this replica
                logger.debug("Successfully claimed event: {} (type: {})",
                        eventId, event.getEventType().getTypeValue());
                try {
                    claimed.run();
                    return new SuccessfulClaim();
                } catch (Exception actionException) {
                    logger.error("Error executing action after successful claim: {}", eventId, actionException);
                    return new FailedClaim(actionException);
                }
            } else {
                // Race condition: another replica claimed it between our get() and putIfAbsent()
                // Check if it was claimed by this replica or another
                if (previousClaim.getClaimedBy().equals(thisInstanceId)) {
                    // Claimed by THIS replica (race between jobs on same replica)
                    logger.trace("Event claimed by this replica during race condition: {}", eventId);
                    try {
                        claimed.run();
                        return new SuccessfulClaim();
                    } catch (Exception actionException) {
                        logger.error("Error executing action in race condition: {}", eventId, actionException);
                        return new FailedClaim(actionException);
                    }
                } else {
                    // Claimed by ANOTHER replica
                    logger.debug("Event claimed by {} during race condition: {} (type: {})",
                            previousClaim.getClaimedBy(), eventId, event.getEventType().getTypeValue());
                    return new NotClaimedResult();
                }
            }
        } catch (Exception e) {
            // Hazelcast operation failed
            logger.error("Failed to claim event, allowing processing to continue (fail-open): " + eventId, e);
            // Fail-open: execute the action even on error
            try {
                claimed.run();
            } catch (Exception innerException) {
                logger.error("Error executing claimed action after claim failure", innerException);
                return new FailedClaim(innerException);
            }
            return new SuccessfulClaim();
        }
    }

    /**
     * Gets the current instance identifier.
     * <p>
     * Uses hostname or pod name to identify this Jenkins instance.
     * Cached after first retrieval for performance.
     *
     * @return instance ID (hostname or pod name)
     */
    private static String getInstanceId() {
        if (instanceId == null) {
            synchronized (HazelcastEventClaimStrategy.class) {
                if (instanceId == null) {
                    try {
                        instanceId = System.getenv("HOSTNAME");
                        if (instanceId == null || "".equals(instanceId.trim())) {
                            instanceId = InetAddress.getLocalHost().getHostName();
                        }
                    } catch (Exception e) {
                        logger.warn("Could not determine hostname, using fallback", e);
                        instanceId = "unknown-" + System.currentTimeMillis();
                    }
                }
            }
        }
        return instanceId;
    }

    /**
     * Parses the configured claim TTL in seconds from system property.
     * <p>
     * Called once at class initialization to parse and cache the TTL value.
     * Can be overridden via system property {@link #CLAIM_TTL_PROPERTY}.
     * Default is {@link #DEFAULT_CLAIM_TTL_SECONDS} (5 minutes).
     *
     * @return TTL in seconds
     */
    private static long parseClaimTtlSeconds() {
        String ttlProperty = System.getProperty(CLAIM_TTL_PROPERTY);
        if (ttlProperty != null) {
            try {
                long ttl = Long.parseLong(ttlProperty);
                if (ttl > 0) {
                    logger.info("Using custom claim TTL: {} seconds (from system property)", ttl);
                    return ttl;
                } else {
                    logger.warn("Invalid claim TTL property (must be > 0): {}, using default: {}",
                            ttlProperty, DEFAULT_CLAIM_TTL_SECONDS);
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid claim TTL property (not a number): {}, using default: {}",
                        ttlProperty, DEFAULT_CLAIM_TTL_SECONDS);
            }
        }
        return DEFAULT_CLAIM_TTL_SECONDS;
    }

    /**
     * Successful claim result - the action was executed.
     */
    private static class SuccessfulClaim implements ClaimResult {
        @Override
        @NonNull
        public ClaimResult notClaimed(@NonNull Runnable notClaimed) {
            // Claim was successful, don't run notClaimed handler
            return this;
        }

        @Override
        @NonNull
        public ClaimResult onError(@NonNull Consumer<Exception> onError) {
            // No error occurred
            return this;
        }
    }

    /**
     * Not claimed result - another instance already claimed the event.
     */
    private static class NotClaimedResult implements ClaimResult {
        @Override
        @NonNull
        public ClaimResult notClaimed(@NonNull Runnable notClaimed) {
            // Run the notClaimed handler
            try {
                notClaimed.run();
            } catch (Exception e) {
                logger.error("Error in notClaimed handler", e);
            }
            return this;
        }

        @Override
        @NonNull
        public ClaimResult onError(@NonNull Consumer<Exception> onError) {
            // No error occurred (just not claimed)
            return this;
        }
    }

    /**
     * Failed claim result - an error occurred during processing.
     */
    private static class FailedClaim implements ClaimResult {
        private final Exception exception;

        /**
         * Constructor.
         *
         * @param exception the exception that occurred
         */
        FailedClaim(Exception exception) {
            this.exception = exception;
        }

        @Override
        @NonNull
        public ClaimResult notClaimed(@NonNull Runnable notClaimed) {
            // Don't run notClaimed - this was an error, not a "not claimed" situation
            return this;
        }

        @Override
        @NonNull
        public ClaimResult onError(@NonNull Consumer<Exception> onError) {
            // Run the error handler
            try {
                onError.accept(exception);
            } catch (Exception e) {
                logger.error("Error in error handler", e);
            }
            return this;
        }
    }
}
