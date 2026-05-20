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

import com.hazelcast.core.HazelcastInstance;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton provider for the Hazelcast instance.
 * <p>
 * Provides thread-safe access to the Hazelcast embedded member instance.
 * The instance is set by {@link HazelcastManager} during initialization.
 *
 */
public final class HazelcastInstanceProvider {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastInstanceProvider.class);

    private static volatile HazelcastInstance instance;
    private static final Object LOCK = new Object();

    /**
     * Private constructor to prevent instantiation.
     */
    private HazelcastInstanceProvider() {
        // Singleton
    }

    /**
     * Sets the Hazelcast instance.
     * Should only be called by HazelcastManager during initialization.
     *
     * @param hazelcastInstance the Hazelcast instance to set
     * @throws IllegalStateException if instance is already set
     */
    static void setInstance(@NonNull HazelcastInstance hazelcastInstance) {
        synchronized (LOCK) {
            if (instance != null) {
                throw new IllegalStateException("Hazelcast instance is already set. "
                        + "Call clearInstance() before setting a new instance.");
            }
            instance = hazelcastInstance;
            logger.info("Hazelcast instance set: {}", hazelcastInstance.getName());
        }
    }

    /**
     * Clears the Hazelcast instance.
     * Should only be called by HazelcastManager during shutdown.
     */
    static void clearInstance() {
        synchronized (LOCK) {
            if (instance != null) {
                logger.info("Clearing Hazelcast instance: {}", instance.getName());
                instance = null;
            }
        }
    }

    /**
     * Gets the Hazelcast instance.
     *
     * @return the Hazelcast instance, or null if not initialized
     */
    @CheckForNull
    public static HazelcastInstance getInstance() {
        return instance;
    }

    /**
     * Gets the Hazelcast instance, throwing an exception if not initialized.
     *
     * @return the Hazelcast instance
     * @throws IllegalStateException if Hazelcast is not initialized
     */
    @NonNull
    public static HazelcastInstance getInstanceOrThrow() {
        HazelcastInstance hz = instance;
        if (hz == null) {
            throw new IllegalStateException("Hazelcast instance is not initialized. "
                    + "Ensure coordination mode is 'hazelcast' and Hazelcast has been started.");
        }
        return hz;
    }

    /**
     * Checks if the Hazelcast instance is initialized.
     *
     * @return true if instance is initialized and running
     */
    public static boolean isInitialized() {
        HazelcastInstance hz = instance;
        return hz != null && hz.getLifecycleService().isRunning();
    }

    /**
     * Gets the cluster name if Hazelcast is initialized.
     *
     * @return cluster name, or null if not initialized
     */
    @CheckForNull
    public static String getClusterName() {
        HazelcastInstance hz = instance;
        if (hz != null) {
            return hz.getConfig().getClusterName();
        }
        return null;
    }

    /**
     * Gets the instance name if Hazelcast is initialized.
     *
     * @return instance name, or null if not initialized
     */
    @CheckForNull
    public static String getInstanceName() {
        HazelcastInstance hz = instance;
        if (hz != null) {
            return hz.getName();
        }
        return null;
    }

    /**
     * Gets the number of members in the cluster.
     *
     * @return member count, or 0 if not initialized
     */
    public static int getClusterSize() {
        HazelcastInstance hz = instance;
        if (hz != null) {
            return hz.getCluster().getMembers().size();
        }
        return 0;
    }
}
