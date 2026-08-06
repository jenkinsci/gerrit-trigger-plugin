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
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.MissedEventsCoordinationStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.NotificationClaimStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.QueueCancellationStrategy;
import hudson.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

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
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.CoordinationMode
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
     * System property: minimum number of Hazelcast cluster members expected before connecting to Gerrit.
     * Default 1 disables the wait (single-instance or local mode).
     * <p>
     * <strong>Only relevant for an externally-managed Hazelcast cluster</strong> whose member
     * discovery/formation is decoupled from this Jenkins replica's own startup - e.g. a shared
     * cluster whose membership can still be changing (scaling, rebalancing, network delays)
     * independently of when this replica boots. In that topology, formation time isn't bounded
     * by anything Jenkins controls, so it can plausibly exceed Jenkins' own (slow) startup time.
     * <p>
     * Does <strong>not</strong> apply to a per-replica Hazelcast sidecar (one server co-located
     * with each Jenkins pod, joining only its Jenkins-managed peers): there, sidecar formation
     * and Jenkins startup share the same pod lifecycle, and in practice Jenkins' own boot time
     * (JVM start, CasC, plugin/extension loading - tens of seconds) dwarfs sidecar discovery time
     * (single-digit seconds even from a cold multi-pod restart), so the client always observes
     * the fully-formed cluster on its first connection regardless of this setting.
     */
    public static final String HAZELCAST_EXPECTED_MEMBERS_PROPERTY =
            "gerrit.trigger.coordination.hazelcast.expected.members";

    /**
     * System property: maximum seconds to wait for Hazelcast cluster formation.
     * Default: 30 seconds.
     */
    public static final String HAZELCAST_CLUSTER_WAIT_TIMEOUT_PROPERTY =
            "gerrit.trigger.coordination.hazelcast.cluster.wait.timeout.seconds";

    private static final int DEFAULT_EXPECTED_CLUSTER_MEMBERS = 1;

    private static final int DEFAULT_CLUSTER_WAIT_TIMEOUT_SECONDS = 30;

    private static final long CLUSTER_WAIT_POLL_INTERVAL_MS = 500L;

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
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.CoordinationMode}
     * may call this method before
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl}
     * has initialized providers. Without this check, {@code CoordinationMode} would select the
     * Hazelcast provider before Hazelcast is actually running, causing builds to not trigger.
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
     * Creates Hazelcast missed-events coordination strategy.
     * <p>
     * Uses a distributed lock and shared watermark IMap to ensure only one replica performs
     * missed-events playback catch-up for a given Gerrit server at a time, and that a later
     * reconnect (on any replica) never re-requests an already-covered range.
     *
     * @return HazelcastMissedEventsCoordinationStrategy instance
     */
    @Override
    public MissedEventsCoordinationStrategy createMissedEventsCoordinationStrategy() {
        // Fetch instance from provider (multiple Extension instances may exist)
        HazelcastInstance instance = HazelcastInstanceProvider.getInstanceOrThrow();
        logger.info("Creating HazelcastMissedEventsCoordinationStrategy with instance: {}", instance.getName());
        return new HazelcastMissedEventsCoordinationStrategy(instance);
    }

    /**
     * Initializes Hazelcast coordination mode.
     * <p>
     * Only initializes if coordination mode is configured as 'hazelcast'.
     * This ensures Hazelcast is not started when using local mode.
     * <p>
     * Also waits for the Hazelcast cluster to reach the expected member count (see
     * {@link #HAZELCAST_EXPECTED_MEMBERS_PROPERTY}) before returning, so that
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl#start()} does not open
     * Gerrit server connections until the distributed claim map is shared across replicas.
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

        waitForClusterFormation(this.hazelcastInstance);
    }

    /**
     * Waits for the Hazelcast cluster to reach the expected number of members.
     * <p>
     * In distributed scenarios each replica has its own SSH connection to Gerrit and therefore
     * receives every event independently. Without this guard, a replica that starts while
     * the Hazelcast cluster is still forming will process events against its own single-member
     * IMap, making the distributed claim invisible to other replicas and causing duplicate builds.
     * See {@link #HAZELCAST_EXPECTED_MEMBERS_PROPERTY} for when this scenario actually applies -
     * in short, an externally-managed cluster, not a per-replica sidecar.
     * <p>
     * The wait is skipped when {@link #HAZELCAST_EXPECTED_MEMBERS_PROPERTY} is 1 (the default).
     *
     * @param hz the Hazelcast instance whose cluster membership should be observed
     */
    private void waitForClusterFormation(HazelcastInstance hz) {
        int expectedMembers = Integer.getInteger(HAZELCAST_EXPECTED_MEMBERS_PROPERTY,
                DEFAULT_EXPECTED_CLUSTER_MEMBERS);
        if (expectedMembers <= DEFAULT_EXPECTED_CLUSTER_MEMBERS) {
            return;
        }

        int timeoutSeconds = Integer.getInteger(HAZELCAST_CLUSTER_WAIT_TIMEOUT_PROPERTY,
                DEFAULT_CLUSTER_WAIT_TIMEOUT_SECONDS);
        logger.info("Waiting for Hazelcast cluster to form ({} expected members, timeout: {}s)...",
                expectedMembers, timeoutSeconds);

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        int currentSize = hz.getCluster().getMembers().size();
        while (currentSize < expectedMembers && System.currentTimeMillis() < deadline) {
            logger.debug("Hazelcast cluster has {} of {} expected members, waiting...",
                    currentSize, expectedMembers);
            try {
                Thread.sleep(CLUSTER_WAIT_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while waiting for Hazelcast cluster formation");
                return;
            }
            currentSize = hz.getCluster().getMembers().size();
        }

        if (currentSize >= expectedMembers) {
            logger.info("Hazelcast cluster ready: {} member(s)", currentSize);
        } else {
            logger.warn("Timed out waiting for Hazelcast cluster ({}/{} members). "
                    + "Proceeding anyway - duplicate builds may occur.", currentSize, expectedMembers);
        }
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
