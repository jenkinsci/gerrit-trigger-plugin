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

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the lifecycle of Hazelcast embedded member.
 * <p>
 * This manager handles initialization and shutdown of the Hazelcast instance.
 * Whether to initialize is determined by {@link HazelcastCoordinationProvider#isAvailable()},
 * not by this class.
 *
 */
public final class HazelcastManager {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastManager.class);

    private static volatile boolean initialized = false;
    private static final Object INIT_LOCK = new Object();

    /**
     * Private constructor to prevent instantiation.
     */
    private HazelcastManager() {
        // Utility class
    }

    /**
     * Initializes Hazelcast embedded member.
     * <p>
     * Creates a Hazelcast member in the Jenkins JVM with configuration from
     * {@link HazelcastConfig#createConfig()}.
     * <p>
     * This method is idempotent - calling it multiple times returns the existing instance.
     *
     * @return the Hazelcast instance
     * @throws RuntimeException if initialization fails
     */
    public static HazelcastInstance initialize() {
        synchronized (INIT_LOCK) {
            if (initialized) {
                logger.debug("Hazelcast is already initialized");
                HazelcastInstance existing = HazelcastInstanceProvider.getInstance();
                if (existing != null) {
                    return existing;
                }
            }

            try {
                logger.info("Initializing Hazelcast embedded member...");

                // Create Hazelcast configuration
                com.hazelcast.config.Config config = HazelcastConfig.createConfig();

                // Create Hazelcast instance
                HazelcastInstance hazelcastInstance = Hazelcast.newHazelcastInstance(config);

                // Register with provider (for backward compatibility with helper methods)
                HazelcastInstanceProvider.setInstance(hazelcastInstance);

                initialized = true;

                // Log cluster information
                int clusterSize = hazelcastInstance.getCluster().getMembers().size();
                logger.info("Hazelcast embedded member initialized. Cluster: {}, Instance: {}, Members: {}",
                        config.getClusterName(),
                        hazelcastInstance.getName(),
                        clusterSize);

                return hazelcastInstance;

            } catch (Exception e) {
                logger.error("Failed to initialize Hazelcast", e);
                initialized = false;
                throw new RuntimeException("Failed to initialize Hazelcast", e);
            }
        }
    }

    /**
     * Shuts down Hazelcast gracefully.
     * <p>
     * Shuts down the Hazelcast embedded member and cleans up resources.
     * <p>
     * This method is idempotent - calling it multiple times has no effect if already shut down.
     */
    public static void shutdown() {
        synchronized (INIT_LOCK) {
            if (!initialized) {
                logger.debug("Hazelcast is not initialized, nothing to shut down");
                return;
            }

            try {
                logger.info("Shutting down Hazelcast embedded member...");

                HazelcastInstance instance = HazelcastInstanceProvider.getInstance();
                if (instance != null) {
                    String instanceName = instance.getName();

                    // Shutdown the instance
                    instance.shutdown();

                    logger.info("Hazelcast embedded member shut down: {}", instanceName);
                }

                // Clear the provider
                HazelcastInstanceProvider.clearInstance();

                initialized = false;

                logger.info("Hazelcast shutdown complete");

            } catch (Exception e) {
                logger.error("Error during Hazelcast shutdown", e);
                // Continue with cleanup even if error occurred
                HazelcastInstanceProvider.clearInstance();
                initialized = false;
            }
        }
    }

    /**
     * Checks if Hazelcast is currently initialized.
     *
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return initialized && HazelcastInstanceProvider.isInitialized();
    }

    /**
     * Reinitializes Hazelcast.
     * This will shutdown the existing instance and create a new one.
     * Used when configuration has changed.
     *
     * @return the new Hazelcast instance
     */
    public static HazelcastInstance reinitialize() {
        logger.info("Reinitializing Hazelcast...");

        synchronized (INIT_LOCK) {
            // Shutdown existing instance
            if (initialized) {
                shutdown();
            }

            // Initialize new instance
            return initialize();
        }
    }

    /**
     * Gets status information about Hazelcast cluster.
     *
     * @return status string with cluster information
     */
    public static String getStatus() {
        if (!initialized) {
            return "Hazelcast: Not initialized";
        }

        HazelcastInstance instance = HazelcastInstanceProvider.getInstance();
        if (instance == null) {
            return "Hazelcast: Error - initialized flag is true but instance is null";
        }

        if (!instance.getLifecycleService().isRunning()) {
            return "Hazelcast: Not running";
        }

        try {
            int clusterSize = instance.getCluster().getMembers().size();
            String clusterName = instance.getConfig().getClusterName();
            String instanceName = instance.getName();

            return String.format("Hazelcast: Running | Cluster: %s | Instance: %s | Members: %d",
                    clusterName, instanceName, clusterSize);
        } catch (Exception e) {
            return String.format("Hazelcast: Error getting status: %s", e.getMessage());
        }
    }
}
