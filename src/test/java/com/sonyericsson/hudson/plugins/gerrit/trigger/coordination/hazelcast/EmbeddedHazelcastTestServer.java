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

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Starts an embedded Hazelcast member (server) in the test JVM so that
 * {@link HazelcastManager#initialize()} (which creates a client) has a server to connect to.
 * <p>
 * The server binds to a free port on {@code localhost} chosen at {@link #start()} time, rather
 * than a fixed port. Maven Surefire runs multiple forked JVMs concurrently ({@code forkCount}
 * in the pom), and a fixed port would let two forks' embedded servers collide on the same
 * address, corrupting each other's cluster state mid-test. Callers must read {@link #getPort()}
 * after {@link #start()} and point the Hazelcast client at it (see
 * {@link HazelcastServerTestListener}).
 * <p>
 * This is required because since member mode was removed the plugin only creates a
 * Hazelcast <em>client</em>, so tests must supply the server themselves.
 */
public final class EmbeddedHazelcastTestServer {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedHazelcastTestServer.class);

    private static volatile HazelcastInstance serverInstance = null;
    private static volatile int port = -1;
    private static final Object LOCK = new Object();

    private EmbeddedHazelcastTestServer() {
        // utility class
    }

    /**
     * Starts the embedded Hazelcast server if not already running, binding it to a free
     * port on localhost.
     * Idempotent — safe to call multiple times.
     */
    public static void start() {
        synchronized (LOCK) {
            if (serverInstance != null && serverInstance.getLifecycleService().isRunning()) {
                logger.debug("Embedded Hazelcast test server already running on port {}", port);
                return;
            }

            int chosenPort = findFreePort();
            logger.info("Starting embedded Hazelcast test server on localhost:{}", chosenPort);
            try {
                Config config = buildServerConfig(chosenPort);
                serverInstance = Hazelcast.newHazelcastInstance(config);
                port = chosenPort;
                logger.info("Embedded Hazelcast test server started: {}", serverInstance.getName());
            } catch (Exception e) {
                logger.error("Failed to start embedded Hazelcast test server", e);
                throw new RuntimeException("Failed to start embedded Hazelcast test server", e);
            }
        }
    }

    /**
     * Returns the port the embedded server is bound to.
     *
     * @return the port, or -1 if the server has not been started yet
     */
    public static int getPort() {
        return port;
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to find a free port for the embedded Hazelcast test server", e);
        }
    }

    /**
     * Shuts down the embedded Hazelcast server if running.
     * Idempotent — safe to call multiple times.
     */
    public static void stop() {
        synchronized (LOCK) {
            if (serverInstance == null) {
                return;
            }
            try {
                logger.info("Stopping embedded Hazelcast test server");
                serverInstance.shutdown();
                logger.info("Embedded Hazelcast test server stopped");
            } catch (Exception e) {
                logger.warn("Error stopping embedded Hazelcast test server", e);
            } finally {
                serverInstance = null;
                port = -1;
            }
        }
    }

    /**
     * Returns true if the server is running.
     *
     * @return true if running
     */
    public static boolean isRunning() {
        HazelcastInstance current = serverInstance;
        return current != null && current.getLifecycleService().isRunning();
    }

    private static Config buildServerConfig(int testPort) {
        Config config = new Config();

        config.setClusterName(HazelcastConfig.DEFAULT_CLUSTER_NAME);
        config.setInstanceName("gerrit-trigger-test-server");
        config.setProperty("hazelcast.logging.type", "slf4j");
        // Suppress startup banner in test output
        config.setProperty("hazelcast.shutdownhook.enabled", "false");

        NetworkConfig network = config.getNetworkConfig();
        network.setPort(testPort);
        network.setPortAutoIncrement(false);
        network.getInterfaces().setEnabled(true).addInterface("127.0.0.1");

        // TCP-IP with only localhost — no multicast, no Kubernetes discovery
        JoinConfig join = network.getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(true).addMember("127.0.0.1:" + testPort);

        return config;
    }
}
