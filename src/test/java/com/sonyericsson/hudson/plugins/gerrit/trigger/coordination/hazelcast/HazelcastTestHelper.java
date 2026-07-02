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

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test helper utilities for Hazelcast tests.
 * <p>
 * Provides methods to clear Hazelcast state between tests, preventing
 * state pollution when tests run in sequence with a shared Hazelcast instance.
 *
 */
public final class HazelcastTestHelper {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastTestHelper.class);

    /**
     * The name of the BuildMemory distributed map in Hazelcast.
     */
    private static final String BUILD_MEMORY_MAP_NAME = "gerrit-trigger-build-memory";

    /**
     * Private constructor to prevent instantiation.
     */
    private HazelcastTestHelper() {
        // Utility class
    }

    /**
     * Clears all Hazelcast distributed maps used by Gerrit Trigger.
     * <p>
     * This should be called in @After methods to ensure clean state between tests.
     * Safe to call even if Hazelcast is not initialized.
     */
    public static void clearAllMaps() {
        if (!HazelcastInstanceProvider.isInitialized()) {
            logger.debug("Hazelcast not initialized, nothing to clear");
            return;
        }

        try {
            HazelcastInstance instance = HazelcastInstanceProvider.getInstance();

            // Clear BuildMemory map
            IMap<?, ?> buildMemoryMap = instance.getMap(BUILD_MEMORY_MAP_NAME);
            int entries = buildMemoryMap.size();
            buildMemoryMap.clear();
            logger.debug("Cleared {} entries from BuildMemory map", entries);

        } catch (Exception e) {
            logger.warn("Error clearing Hazelcast maps (test cleanup)", e);
            // Don't fail tests on cleanup errors
        }
    }

    /**
     * Clears only the BuildMemory distributed map.
     * <p>
     * Useful when you want to clear build state but keep other state intact.
     * Safe to call even if Hazelcast is not initialized.
     */
    public static void clearBuildMemory() {
        if (!HazelcastInstanceProvider.isInitialized()) {
            logger.debug("Hazelcast not initialized, nothing to clear");
            return;
        }

        try {
            HazelcastInstance instance = HazelcastInstanceProvider.getInstance();
            IMap<?, ?> buildMemoryMap = instance.getMap(BUILD_MEMORY_MAP_NAME);
            int entries = buildMemoryMap.size();
            buildMemoryMap.clear();
            logger.debug("Cleared {} entries from BuildMemory map", entries);
        } catch (Exception e) {
            logger.warn("Error clearing BuildMemory map (test cleanup)", e);
            // Don't fail tests on cleanup errors
        }
    }

    /**
     * Gets the number of entries in the BuildMemory map.
     * Useful for debugging test failures.
     *
     * @return number of entries, or 0 if Hazelcast not initialized
     */
    public static int getBuildMemorySize() {
        if (!HazelcastInstanceProvider.isInitialized()) {
            return 0;
        }

        try {
            HazelcastInstance instance = HazelcastInstanceProvider.getInstance();
            IMap<?, ?> buildMemoryMap = instance.getMap(BUILD_MEMORY_MAP_NAME);
            return buildMemoryMap.size();
        } catch (Exception e) {
            logger.warn("Error getting BuildMemory size", e);
            return 0;
        }
    }
}
