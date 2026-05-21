/*
 *  The MIT License
 *
 *  Copyright 2026 CloudBees, Inc.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.hazelcast;

import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.RunListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit test listener that initializes Hazelcast once for the entire test suite.
 * <p>
 * This listener is automatically invoked by Maven Surefire when the test-hazelcast
 * profile is active. It ensures Hazelcast is initialized before any tests run,
 * allowing all tests to run in distributed coordination mode.
 * <p>
 * <strong>Configuration:</strong> Activated via Maven profile with test-hazelcast profile.
 *
 */
public class HazelcastTestListener extends RunListener {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastTestListener.class);

    private static final String COORDINATION_MODE_PROPERTY = "gerrit.trigger.coordination.mode";
    private static final String HAZELCAST_MODE = "hazelcast";

    private static boolean initialized = false;
    private static boolean shouldInitialize = false;

    /**
     * Called once before any tests run.
     * Initializes Hazelcast if coordination mode is set to hazelcast.
     *
     * @param description test run description
     */
    @Override
    public void testRunStarted(Description description) {
        String mode = System.getProperty(COORDINATION_MODE_PROPERTY);

        if (HAZELCAST_MODE.equalsIgnoreCase(mode)) {
            logger.info("=== Hazelcast Test Suite Initialization ===");
            logger.info("Coordination mode property: {}", mode);

            if (!HazelcastManager.isInitialized()) {
                try {
                    logger.info("Initializing Hazelcast for test suite...");
                    boolean success = HazelcastManager.initialize();

                    if (success) {
                        initialized = true;
                        shouldInitialize = true;
                        logger.info("Hazelcast initialized successfully for test suite");
                        logger.info("Cluster: {}",
                            HazelcastInstanceProvider.getInstance().getConfig().getClusterName());
                        logger.info("Instance: {}",
                            HazelcastInstanceProvider.getInstance().getName());
                        logger.info("Members: {}",
                            HazelcastInstanceProvider.getInstance().getCluster().getMembers().size());
                    } else {
                        logger.error("Failed to initialize Hazelcast for test suite");
                    }
                } catch (Exception e) {
                    logger.error("Exception initializing Hazelcast for test suite", e);
                }
            } else {
                logger.info("Hazelcast already initialized");
                initialized = true;
            }

            logger.info("=== Hazelcast Test Suite Initialization Complete ===");
        } else {
            logger.debug("Coordination mode is '{}', Hazelcast listener will not initialize", mode);
        }
    }

    /**
     * Called once after all tests complete.
     * Shuts down Hazelcast if it was initialized by this listener.
     *
     * @param result test run result
     */
    @Override
    public void testRunFinished(Result result) {
        if (shouldInitialize && initialized) {
            logger.info("=== Hazelcast Test Suite Cleanup ===");
            try {
                logger.info("Shutting down Hazelcast...");
                HazelcastManager.shutdown();
                initialized = false;
                shouldInitialize = false;
                logger.info("Hazelcast shutdown complete");
            } catch (Exception e) {
                logger.error("Error shutting down Hazelcast", e);
            }
            logger.info("=== Hazelcast Test Suite Cleanup Complete ===");
        }
    }

    /**
     * Checks if Hazelcast was initialized by this listener.
     *
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
