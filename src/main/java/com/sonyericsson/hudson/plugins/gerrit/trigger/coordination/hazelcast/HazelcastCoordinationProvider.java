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
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.BuildMemoryStorage;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.CoordinationModeProvider;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.EventClaimStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.NotificationClaimStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.QueueCancellationStrategy;
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
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.CoordinationModeFactory
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.LocalCoordinationProvider (fallback)
 */
@Extension(ordinal = HazelcastCoordinationProvider.HAZELCAST_PRIORITY)
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
     * The Hazelcast instance for this provider.
     * Set during initialization, used to create strategies.
     */
    private HazelcastInstance hazelcastInstance;

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
     * <p>
     * The initialization check is necessary because
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.CoordinationModeFactory}
     * may call this method before
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl}
     * has initialized providers. Without this check, the factory would select Hazelcast provider
     * before Hazelcast is actually running, causing builds to not trigger.
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
            logger.debug("Coordination mode is '{}' but Hazelcast not initialized yet. "
                    + "Provider will become available after initialization.", HAZELCAST_MODE);
            return false;
        }

        logger.debug("Hazelcast coordination mode active");
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
        // Fetch instance from provider (multiple Extension instances may exist)
        HazelcastInstance instance = HazelcastInstanceProvider.getInstanceOrThrow();
        logger.info("Creating HazelcastBuildMemoryStorage with instance: {}", instance.getName());
        return new HazelcastBuildMemoryStorage(instance);
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
        // Fetch instance from provider (multiple Extension instances may exist)
        HazelcastInstance instance = HazelcastInstanceProvider.getInstanceOrThrow();
        logger.info("Creating HazelcastNotificationClaimStrategy with instance: {}", instance.getName());
        return new HazelcastNotificationClaimStrategy(instance);
    }

    /**
     * Creates Hazelcast event claim strategy.
     * <p>
     * Uses distributed IMap with atomic {@code putIfAbsent} to ensure only one replica
     * processes each Gerrit event. The first replica to claim an event processes it,
     * while other replicas skip it. This prevents duplicate builds in distributed scenarios.
     * <p>
     * <strong>Replica-level claiming:</strong> Once a replica claims an event, ALL jobs
     * on that replica can process it. This allows multiple jobs on the same replica to be
     * triggered by the same event while preventing duplicate processing across replicas.
     *
     * @return HazelcastEventClaimStrategy instance
     */
    @Override
    public EventClaimStrategy createEventClaimStrategy() {
        // Fetch instance from provider (multiple Extension instances may exist)
        HazelcastInstance instance = HazelcastInstanceProvider.getInstanceOrThrow();
        logger.info("Creating HazelcastEventClaimStrategy with instance: {}", instance.getName());
        return new HazelcastEventClaimStrategy(instance);
    }

    /**
     * Creates Hazelcast queue cancellation strategy.
     * <p>
     * Detects cancellations triggered by the potential distributed load balancer so that
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritQueueListener}
     * can skip them and avoid sending premature Gerrit feedback.
     *
     * @return HazelcastQueueCancellationStrategy instance
     */
    @Override
    public QueueCancellationStrategy createQueueCancellationStrategy() {
        return new HazelcastQueueCancellationStrategy();
    }

    /**
     * Initializes Hazelcast coordination mode.
     * <p>
     * Only initializes if coordination mode is configured as 'hazelcast'.
     * This ensures Hazelcast is not started when using local mode.
     * <p>
     * If initialization fails, an exception is thrown and the provider will
     * not be available (isAvailable() will return false).
     *
     * @throws Exception if Hazelcast initialization fails
     */
    @Override
    public void initialize() throws Exception {
        // Check coordination mode - only initialize if this provider should be used
        String configuredMode = getConfiguredMode();
        if (!HAZELCAST_MODE.equalsIgnoreCase(configuredMode)) {
            logger.trace("Coordination mode is '{}', skipping Hazelcast initialization", configuredMode);
            return;
        }

        logger.info("Initializing Hazelcast coordination mode...");
        this.hazelcastInstance = HazelcastManager.initialize();
        logger.info("Hazelcast initialized successfully");
    }

    /**
     * Shuts down Hazelcast coordination mode.
     * <p>
     * This gracefully shuts down the Hazelcast instance, leaving the cluster
     * and releasing all resources.
     */
    @Override
    public void shutdown() {
        logger.info("Shutting down Hazelcast coordination mode...");
        HazelcastManager.shutdown();
        logger.info("Hazelcast shut down complete");
    }
}
