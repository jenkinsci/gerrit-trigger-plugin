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

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit Platform {@link TestExecutionListener} that starts an embedded Hazelcast server
 * before any test in the forked JVM runs.
 * <p>
 * Activated by Maven Surefire when the {@code test-hazelcast} profile is active. This listener
 * is discovered via the {@code META-INF/services/org.junit.platform.launcher.TestExecutionListener}
 * service file, which is the correct discovery mechanism for the JUnit Platform provider (surefire
 * 3.x) — unlike the JUnit 4 {@code <listener>} property, which is not invoked for JUnit 5 tests.
 * <p>
 * Starting the server here ensures that when Jenkins initialises and
 * {@code PluginImpl.gerritStart()} calls {@link HazelcastManager#initialize()} (which creates a
 * Hazelcast client connecting to {@code localhost:5702}), the server is already listening.
 * Without this, the client hangs for several minutes trying to reach a non-existent server.
 *
 * @see EmbeddedHazelcastTestServer
 */
public class HazelcastServerTestListener implements TestExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastServerTestListener.class);

    private static final String COORDINATION_MODE_PROPERTY = "gerrit.trigger.coordination.mode";
    private static final String HAZELCAST_MODE = "hazelcast";

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        String mode = System.getProperty(COORDINATION_MODE_PROPERTY);
        if (!HAZELCAST_MODE.equalsIgnoreCase(mode)) {
            logger.debug("Coordination mode is '{}', skipping embedded Hazelcast server start", mode);
            return;
        }
        logger.info("=== Starting embedded Hazelcast test server (coordination mode: {}) ===", mode);
        EmbeddedHazelcastTestServer.start();
        logger.info("=== Embedded Hazelcast test server ready ===");
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        if (EmbeddedHazelcastTestServer.isRunning()) {
            logger.info("=== Stopping embedded Hazelcast test server ===");
            EmbeddedHazelcastTestServer.stop();
            logger.info("=== Embedded Hazelcast test server stopped ===");
        }
    }
}
