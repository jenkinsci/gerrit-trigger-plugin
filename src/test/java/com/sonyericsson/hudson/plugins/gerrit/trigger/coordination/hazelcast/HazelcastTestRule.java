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

import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit rule for initializing and cleaning up Hazelcast for tests.
 * <p>
 * This rule handles the complete lifecycle of Hazelcast for integration tests:
 * <ul>
 *   <li>Sets coordination mode system property</li>
 *   <li>Initializes Hazelcast embedded instance</li>
 *   <li>Verifies Hazelcast is ready</li>
 *   <li>Cleans up after tests complete</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * public class MyHazelcastTest {
 *     @Rule
 *     public HazelcastTestRule hazelcast = new HazelcastTestRule();
 *
 *     @Rule
 *     public JenkinsRule jenkins = new JenkinsRule();
 *
 *     @Test
 *     public void testWithHazelcast() {
 *         // Test will run with Hazelcast coordination mode active
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Important:</strong> This rule must be declared BEFORE JenkinsRule
 * in the test class to ensure Hazelcast is initialized before Jenkins starts.</p>
 *
 */
public class HazelcastTestRule extends ExternalResource {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastTestRule.class);

    private static final String COORDINATION_MODE_PROPERTY = "gerrit.trigger.coordination.mode";
    private static final String HAZELCAST_MODE = "hazelcast";

    private String originalModeValue;
    private boolean initializedByThisRule = false;

    /**
     * Sets up Hazelcast before the test runs.
     * <p>
     * This method:
     * <ol>
     *   <li>Saves the original coordination mode property</li>
     *   <li>Sets coordination mode to "hazelcast"</li>
     *   <li>Initializes Hazelcast embedded instance</li>
     *   <li>Verifies initialization succeeded</li>
     * </ol>
     *
     * @throws Exception if Hazelcast initialization fails
     */
    @Override
    protected void before() throws Exception {
        logger.info("=== Hazelcast Test Setup START ===");

        // Save original property value
        originalModeValue = System.getProperty(COORDINATION_MODE_PROPERTY);
        logger.info("Original coordination mode: {}", originalModeValue);

        // Set coordination mode to hazelcast
        System.setProperty(COORDINATION_MODE_PROPERTY, HAZELCAST_MODE);
        logger.info("Set coordination mode to: {}", HAZELCAST_MODE);

        // Initialize Hazelcast if not already initialized
        if (!HazelcastManager.isInitialized()) {
            logger.info("Initializing Hazelcast for test...");
            com.hazelcast.core.HazelcastInstance instance = HazelcastManager.initialize();

            if (instance == null) {
                throw new IllegalStateException("Failed to initialize Hazelcast for test");
            }

            initializedByThisRule = true;
            logger.info("Hazelcast initialized successfully");
        } else {
            logger.info("Hazelcast already initialized, reusing existing instance");
            initializedByThisRule = false;
        }

        // Verify Hazelcast is available
        if (!HazelcastInstanceProvider.isInitialized()) {
            throw new IllegalStateException("Hazelcast instance not available after initialization");
        }

        logger.info("Hazelcast instance: {}", HazelcastInstanceProvider.getInstance().getName());
        logger.info("Cluster size: {}",
                HazelcastInstanceProvider.getInstance().getCluster().getMembers().size());
        logger.info("=== Hazelcast Test Setup COMPLETE ===");
    }

    /**
     * Cleans up Hazelcast after the test completes.
     * <p>
     * This method:
     * <ol>
     *   <li>Shuts down Hazelcast (if initialized by this rule)</li>
     *   <li>Restores original coordination mode property</li>
     * </ol>
     * <p>
     * Cleanup is best-effort - errors are logged but don't fail the test.
     */
    @Override
    protected void after() {
        logger.info("=== Hazelcast Test Cleanup START ===");

        try {
            // Only shutdown if we initialized it
            if (initializedByThisRule && HazelcastManager.isInitialized()) {
                logger.info("Shutting down Hazelcast...");
                HazelcastManager.shutdown();
                logger.info("Hazelcast shutdown complete");
            } else if (!initializedByThisRule) {
                logger.info("Hazelcast was not initialized by this rule, leaving it running");
            } else {
                logger.info("Hazelcast already shut down");
            }
        } catch (Exception e) {
            logger.error("Error shutting down Hazelcast (test cleanup)", e);
            // Don't fail the test on cleanup errors
        }

        // Restore original property
        if (originalModeValue == null) {
            System.clearProperty(COORDINATION_MODE_PROPERTY);
            logger.info("Cleared coordination mode property");
        } else {
            System.setProperty(COORDINATION_MODE_PROPERTY, originalModeValue);
            logger.info("Restored coordination mode to: {}", originalModeValue);
        }

        logger.info("=== Hazelcast Test Cleanup COMPLETE ===");
    }

    /**
     * Checks if Hazelcast was initialized by this rule.
     *
     * @return true if this rule initialized Hazelcast
     */
    public boolean wasInitializedByThisRule() {
        return initializedByThisRule;
    }
}
