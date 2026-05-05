/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.storage;

import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.BuildMemoryStorage;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.BuildMemoryStorageProvider;
import hudson.ExtensionList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Factory for discovering and creating BuildMemoryStorage implementations.
 * <p>
 * Uses Jenkins Extension Points to discover all implementations of
 * {@link BuildMemoryStorageProvider} and selects the one with the highest
 * priority that is currently available.
 * <p>
 * The factory uses lazy initialization with double-checked locking for
 * thread safety and performance.
 * <p>
 * <strong>Selection Logic:</strong>
 * <ol>
 *   <li>Discover all providers via {@link ExtensionList#lookup(Class)}</li>
 *   <li>Filter to only available providers (via {@link BuildMemoryStorageProvider#isAvailable()})</li>
 *   <li>Select the provider with the highest {@link BuildMemoryStorageProvider#getPriority()}</li>
 *   <li>Create the storage instance via {@link BuildMemoryStorageProvider#createStorage()}</li>
 * </ol>
 */
public final class BuildMemoryStorageFactory {

    private static final Logger logger = LoggerFactory.getLogger(BuildMemoryStorageFactory.class);

    /**
     * The singleton storage instance, lazily initialized.
     */
    private static volatile BuildMemoryStorage instance;

    /**
     * The selected provider (for diagnostics).
     */
    private static volatile BuildMemoryStorageProvider selectedProvider;

    /**
     * Private constructor to prevent instantiation.
     */
    private BuildMemoryStorageFactory() {
        // Utility class, no instances
    }

    /**
     * Gets the BuildMemoryStorage instance.
     * <p>
     * Uses double-checked locking for thread-safe lazy initialization.
     * The instance is discovered and created on first access.
     *
     * @return the storage implementation
     * @throws IllegalStateException if no available provider is found
     */
    @NonNull
    public static BuildMemoryStorage getInstance() {
        if (instance == null) {
            synchronized (BuildMemoryStorageFactory.class) {
                if (instance == null) {
                    instance = discoverImplementation();
                }
            }
        }
        return instance;
    }

    /**
     * Discovers and selects the best available storage implementation.
     * <p>
     * Iterates through all registered providers and selects the one with
     * the highest priority that is currently available.
     * <p>
     * If Jenkins ExtensionList is not available (e.g., in unit tests),
     * falls back to creating a LocalBuildMemoryStorage directly.
     *
     * @return the created storage instance
     * @throws IllegalStateException if no available provider is found
     */
    @NonNull
    private static BuildMemoryStorage discoverImplementation() {
        logger.info("Discovering BuildMemoryStorage implementations...");

        // Jenkins discovers all @Extension implementations automatically
        ExtensionList<BuildMemoryStorageProvider> providers =
                ExtensionList.lookup(BuildMemoryStorageProvider.class);

        // Fallback for unit tests where Jenkins is not initialized
        if (providers == null) {
            logger.info("Jenkins ExtensionList not available (unit test environment), using LocalBuildMemoryStorage");
            selectedProvider = null;
            return new LocalBuildMemoryStorage();
        }

        BuildMemoryStorageProvider selected = null;
        int highestPriority = Integer.MIN_VALUE;

        // Select highest priority available implementation
        for (BuildMemoryStorageProvider provider : providers) {
            boolean available = provider.isAvailable();
            int priority = provider.getPriority();
            String name = provider.getName();

            logger.info("  {} - Priority: {} - Available: {}", name, priority, available);

            if (available && priority > highestPriority) {
                selected = provider;
                highestPriority = priority;
            }
        }

        if (selected == null) {
            // No provider available, fallback to local storage
            logger.warn("No available BuildMemoryStorageProvider found, using LocalBuildMemoryStorage as fallback");
            selectedProvider = null;
            return new LocalBuildMemoryStorage();
        }

        selectedProvider = selected;
        logger.info("Selected: {} (priority: {})", selected.getName(), highestPriority);

        return selected.createStorage();
    }

    /**
     * Gets the currently selected provider.
     * <p>
     * This method is primarily for diagnostics and testing.
     * Returns null if getInstance() has not been called yet.
     *
     * @return the selected provider, or null if not initialized
     */
    public static BuildMemoryStorageProvider getSelectedProvider() {
        return selectedProvider;
    }

    /**
     * Resets the factory state.
     * <p>
     * <strong>FOR TESTING ONLY.</strong> This method clears the singleton
     * instance, forcing re-discovery on the next getInstance() call.
     * <p>
     * Not safe to call during normal operation.
     */
    public static synchronized void reset() {
        logger.debug("Resetting BuildMemoryStorageFactory");
        instance = null;
        selectedProvider = null;
    }
}
