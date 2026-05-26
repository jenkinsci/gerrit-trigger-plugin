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
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.NotificationClaimStrategy;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Hazelcast-backed implementation of NotificationClaimStrategy for HA/HS deployments.
 * <p>
 * In HA/HS (High Availability/High Scalability) environments with multiple replicas, each replica tracks build
 * completions independently. To prevent duplicate notifications to Gerrit,
 * replicas use distributed notification claiming:
 * <ul>
 *   <li>First replica to claim notification right sends feedback to Gerrit</li>
 *   <li>Other replicas skip notification (already sent)</li>
 * </ul>
 * <p>
 * Claims are stored in a Hazelcast IMap with atomic {@code putIfAbsent} operations
 * to prevent race conditions. Claims automatically expire via TTL to prevent memory leaks.
 * <p>
 * <strong>Fail-open behavior:</strong> If Hazelcast is unavailable, this strategy
 * allows notification sending to continue (better to risk duplicate notifications than
 * lose feedback entirely).
 *
 */
public class HazelcastNotificationClaimStrategy extends NotificationClaimStrategy {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastNotificationClaimStrategy.class);

    /**
     * The Hazelcast instance to use for notification claiming.
     */
    private final HazelcastInstance hazelcastInstance;

    /**
     * Hazelcast map name for notification claim flags.
     */
    private static final String NOTIFICATION_FLAGS_MAP = "gerrit-trigger-notification-flags";

    /**
     * Constructor.
     *
     * @param hazelcastInstance the Hazelcast instance to use
     */
    public HazelcastNotificationClaimStrategy(@NonNull HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * Default notification claim TTL in minutes (10 minutes).
     * Claims expire automatically to prevent memory leaks.
     */
    private static final int DEFAULT_NOTIFICATION_CLAIM_TTL_MINUTES = 10;

    /**
     * System property to override notification claim TTL.
     * Example: -Dgerrit.trigger.coordination.hazelcast.notification.ttl.minutes=20
     */
    private static final String NOTIFICATION_TTL_PROPERTY =
            "gerrit.trigger.coordination.hazelcast.notification.ttl.minutes";

    /**
     * Cached claim TTL in seconds.
     * Parsed once at class initialization from system property or default.
     */
    private static final int NOTIFICATION_TTL_SECONDS = parseNotificationTtlMinutes();


    @Override
    @NonNull
    public ClaimResult withClaim(@NonNull GerritTriggeredEvent event, @NonNull Runnable claimed) {
        // Check Hazelcast instance availability
        if (hazelcastInstance == null) {
            logger.warn("Hazelcast not available for notification claim, proceeding with local mode (fail-open)");
            // Fail-open: execute the notification action even without claiming
            try {
                claimed.run();
                return new SuccessfulClaim();
            } catch (Exception e) {
                return new FailedClaim(e);
            }
        }

        try {
            IMap<String, Boolean> notificationFlags = hazelcastInstance.getMap(NOTIFICATION_FLAGS_MAP);
            String eventId = EventIdentifier.generateEventId(event);
            String flagKey = "notified-" + eventId;

            // Atomic operation: set flag if not already set
            Boolean previousValue = notificationFlags.putIfAbsent(
                    flagKey,
                    Boolean.TRUE,
                    NOTIFICATION_TTL_SECONDS,
                    TimeUnit.MINUTES
            );

            if (previousValue == null) {
                // Successfully claimed notification right
                logger.debug("Successfully claimed notification right for event: {}", eventId);
                try {
                    claimed.run();
                    return new SuccessfulClaim();
                } catch (Exception actionException) {
                    logger.error("Error executing notification action after successful claim: {}", eventId,
                            actionException);
                    return new FailedClaim(actionException);
                }
            } else {
                // Another replica already claimed notification
                logger.debug("Another replica already claimed notification for event: {}", eventId);
                return new NotClaimedResult();
            }
        } catch (Exception e) {
            // Hazelcast operation failed
            logger.error("Error claiming notification right, proceeding with send to avoid notification loss", e);
            // Fail-open: execute the notification action even on error
            try {
                claimed.run();
            } catch (Exception innerException) {
                logger.error("Error executing notification action after claim failure", innerException);
                return new FailedClaim(innerException);
            }
            return new SuccessfulClaim();
        }
    }

    /**
     * Parse the configured notification claim TTL in minutes.
     * <p>
     * Called once at class initialization to parse and cache the TTL value.
     * Can be overridden via system property {@link #NOTIFICATION_TTL_PROPERTY}.
     * Default is {@link #DEFAULT_NOTIFICATION_CLAIM_TTL_MINUTES} (10 minutes).
     *
     * @return TTL in minutes
     */
    private static int parseNotificationTtlMinutes() {
        String ttlProperty = System.getProperty(NOTIFICATION_TTL_PROPERTY);
        if (ttlProperty != null) {
            try {
                int ttl = Integer.parseInt(ttlProperty);
                if (ttl > 0) {
                    logger.info("Using custom notification TTL: {} seconds (from system property)", ttl);
                    return ttl;
                } else {
                    logger.warn("Invalid notification TTL property (must be > 0): {}, using default", ttlProperty);
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid notification TTL property (not a number): {}, using default", ttlProperty);
            }
        }
        return DEFAULT_NOTIFICATION_CLAIM_TTL_MINUTES;
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
     * Not claimed result - another instance already claimed the notification.
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
