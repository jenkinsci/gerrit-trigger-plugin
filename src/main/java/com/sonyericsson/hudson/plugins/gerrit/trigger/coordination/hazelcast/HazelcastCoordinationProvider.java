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

import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.BuildMemoryStorage;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.CoordinationModeProvider;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.EventClaimStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.NotificationClaimStrategy;
import hudson.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordination provider for Hazelcast distributed mode.
 * <p>
 * This provider is automatically discovered via Jenkins Extension Points mechanism.
 * It has higher priority than LocalCoordinationProvider (100 vs -1000), so it will be
 * selected when available.
 * <p>
 * <strong>Availability Criteria:</strong>
 * <ul>
 *   <li>Coordination mode set to 'hazelcast' via system property:
 *       {@code -Dgerrit.trigger.coordination.mode=hazelcast}</li>
 *   <li>Hazelcast instance is initialized and running</li>
 * </ul>
 * <p>
 * When selected, provides:
 * <ul>
 *   <li>{@link HazelcastBuildMemoryStorage} - Distributed build tracking across replicas</li>
 *   <li>{@link HazelcastNotificationClaimStrategy} - Notification coordination to prevent duplicates</li>
 *   <li>{@link HazelcastEventClaimStrategy} - Event processing coordination to prevent duplicates</li>
 * </ul>
 * <p>
 * <strong>Architecture Note:</strong> This single class replaces ALL the
 * {@code if (ClusterModeProvider.isClusterModeEnabled())} checks throughout the codebase!
 * All three coordination concerns (build state storage, notification rights, event processing rights)
 * now use the same Extension Points pattern consistently.
 *
 * @see CoordinationModeFactory
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.LocalCoordinationProvider (fallback)
 * @author CloudBees, Inc.
 */
// CHECKSTYLE:OFF MagicNumber - Ordinal must be literal in annotation, 100 ensures higher priority than fallback
@Extension(ordinal = HazelcastCoordinationProvider.HAZELCAST_PRIORITY)
// CHECKSTYLE:ON MagicNumber
public class HazelcastCoordinationProvider extends CoordinationModeProvider {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastCoordinationProvider.class);

    /**
     * Extension ordinal priority for Hazelcast coordination provider.
     * Higher value than LocalCoordinationProvider (-1000) ensures this is selected first when available.
     */
    static final int HAZELCAST_PRIORITY = 100;

    /**
     * The mode name that enables this provider.
     */
    private static final String HAZELCAST_MODE = "hazelcast";

    /**
     * Checks if this provider is available.
     * <p>
     * Returns true only if:
     * <ul>
     *   <li>Coordination mode is configured as 'hazelcast' (via system property)</li>
     *   <li>Hazelcast instance is initialized and running</li>
     * </ul>
     * <p>
     * Uses the {@link CoordinationModeProvider#getConfiguredMode()} helper method to check
     * the coordination mode. This is future-proof - when we add UI configuration for coordination
     * modes, only that one helper method needs to be updated.
     *
     * @return true if Hazelcast coordination mode is available, false otherwise
     */
    @Override
    public boolean isAvailable() {
        // Check coordination mode using helper method (future-proof for UI config)
        String configuredMode = getConfiguredMode();
        if (!HAZELCAST_MODE.equalsIgnoreCase(configuredMode)) {
            logger.trace("Coordination mode is '{}', not '{}'", configuredMode, HAZELCAST_MODE);
            return false;
        }

        // Check Hazelcast availability
        if (!HazelcastInstanceProvider.isInitialized()) {
            logger.warn("Coordination mode is '{}' but Hazelcast not initialized. "
                    + "Hazelcast must be initialized before coordination provider discovery. "
                    + "Falling back to local mode.", HAZELCAST_MODE);
            return false;
        }

        logger.info("Hazelcast coordination mode active");
        return true;
    }

    /**
     * Returns the human-readable name of this coordination mode.
     *
     * @return "Hazelcast (Distributed)"
     */
    @Override
    public String getModeName() {
        return "Hazelcast (Distributed)";
    }

    /**
     * Creates Hazelcast-backed build memory storage.
     * <p>
     * Uses distributed IMap to share build tracking state across all Jenkins replicas.
     * All build lifecycle events (triggered, started, completed) are stored in Hazelcast,
     * allowing any replica to see what builds other replicas are processing.
     *
     * @return HazelcastBuildMemoryStorage instance
     */
    @Override
    public BuildMemoryStorage createStorage() {
        logger.info("Creating HazelcastBuildMemoryStorage");
        return new HazelcastBuildMemoryStorage();
    }

    /**
     * Creates Hazelcast notification claim strategy.
     * <p>
     * Uses distributed atomic operations to ensure only one replica sends feedback
     * to Gerrit for each event. Prevents duplicate comments/votes on Gerrit reviews.
     *
     * @return HazelcastNotificationClaimStrategy instance
     */
    @Override
    public NotificationClaimStrategy createClaimStrategy() {
        logger.info("Creating HazelcastNotificationClaimStrategy");
        return new HazelcastNotificationClaimStrategy();
    }

    /**
     * Creates Hazelcast event claim strategy.
     * <p>
     * Uses distributed IMap with atomic {@code putIfAbsent} to ensure only one replica
     * processes each Gerrit event. The first replica to claim an event processes it,
     * while other replicas skip it. This prevents duplicate builds in HA/HS deployments.
     * <p>
     * <strong>Replica-level claiming:</strong> Once a replica claims an event, ALL jobs
     * on that replica can process it. This allows multiple jobs on the same replica to be
     * triggered by the same event while preventing duplicate processing across replicas.
     *
     * @return HazelcastEventClaimStrategy instance
     */
    @Override
    public EventClaimStrategy createEventClaimStrategy() {
        logger.info("Creating HazelcastEventClaimStrategy");
        return new HazelcastEventClaimStrategy();
    }
}
