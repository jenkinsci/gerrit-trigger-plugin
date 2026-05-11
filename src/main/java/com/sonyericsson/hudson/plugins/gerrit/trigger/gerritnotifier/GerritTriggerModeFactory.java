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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier;

import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.BuildMemoryStorage;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.GerritTriggerModeProvider;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.NotificationClaimStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.storage.LocalBuildMemoryStorage;
import hudson.ExtensionList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Factory for discovering and creating deployment mode implementations.
 *
 * <p>This factory discovers the appropriate deployment mode (local, cluster, etc.) and
 * provides all component implementations from that mode. This ensures consistency -
 * all components come from the same mode and share the same availability conditions.</p>
 *
 * <h2>Design Rationale:</h2>
 * <p>Instead of separate factories for each component, we use a single factory that
 * manages the entire deployment mode. This is because:</p>
 * <ul>
 *   <li>Components always deploy together in the same mode</li>
 *   <li>Mode detection happens once, not per component</li>
 *   <li>Guaranteed consistency (no mixed modes)</li>
 *   <li>Simpler API with fewer factories to manage</li>
 * </ul>
 *
 * <h2>Selection Logic:</h2>
 * <ol>
 *   <li>Discover all providers via {@link ExtensionList#lookup(Class)}</li>
 *   <li>Filter to only available providers (via {@link GerritTriggerModeProvider#isAvailable()})</li>
 *   <li>Select the provider with highest {@link GerritTriggerModeProvider#getPriority()}</li>
 *   <li>Create all component instances from selected provider</li>
 * </ol>
 *
 * <h2>Thread Safety:</h2>
 * <p>This factory is thread-safe using double-checked locking pattern. Multiple threads
 * calling {@link #getStorage()} or {@link #getClaimStrategy()} concurrently will result
 * in exactly one mode being selected and one set of instances being created.</p>
 *
 * <h2>Test Support:</h2>
 * <p>The {@link #reset()} method allows tests to clear the cached instances and force
 * re-discovery on next access. This is critical for test isolation when using JenkinsRule,
 * which creates fresh Jenkins instances per test.</p>
 *
 * @see GerritTriggerModeProvider
 * @see BuildMemoryStorage
 * @see NotificationClaimStrategy
 */
public final class GerritTriggerModeFactory {

    private static final Logger logger = LoggerFactory.getLogger(GerritTriggerModeFactory.class);

    /**
     * The selected mode provider (for diagnostics).
     */
    private static volatile GerritTriggerModeProvider selectedMode;

    /**
     * The storage instance, lazily initialized.
     */
    private static volatile BuildMemoryStorage storage;

    /**
     * The claim strategy instance, lazily initialized.
     */
    private static volatile NotificationClaimStrategy claimStrategy;

    /**
     * Private constructor to prevent instantiation.
     */
    private GerritTriggerModeFactory() {
        // Utility class, no instances
    }

    /**
     * Gets the BuildMemoryStorage instance for the current deployment mode.
     *
     * <p>Uses double-checked locking for thread-safe lazy initialization.
     * The mode is discovered and instances are created on first access.</p>
     *
     * @return the storage implementation
     * @throws IllegalStateException if no available mode provider is found
     */
    @NonNull
    public static BuildMemoryStorage getStorage() {
        ensureInitialized();
        return storage;
    }

    /**
     * Gets the NotificationClaimStrategy instance for the current deployment mode.
     *
     * <p>Uses double-checked locking for thread-safe lazy initialization.
     * The mode is discovered and instances are created on first access.</p>
     *
     * @return the claim strategy implementation
     * @throws IllegalStateException if no available mode provider is found
     */
    @NonNull
    public static NotificationClaimStrategy getClaimStrategy() {
        ensureInitialized();
        return claimStrategy;
    }

    /**
     * Ensures the factory is initialized by discovering the mode if needed.
     * Uses double-checked locking for thread safety.
     */
    private static void ensureInitialized() {
        if (selectedMode == null) {
            synchronized (GerritTriggerModeFactory.class) {
                if (selectedMode == null) {
                    discoverMode();
                }
            }
        }
    }

    /**
     * Discovers and selects the best available deployment mode.
     *
     * <p>ExtensionList returns providers ordered by {@code @Extension(ordinal)} (highest first).
     * Selects the first provider that is currently available.</p>
     *
     * <p>If ExtensionList is not available (e.g., in unit tests without Jenkins),
     * falls back to creating local mode implementations directly.</p>
     *
     * @throws IllegalStateException if no providers are available
     */
    private static void discoverMode() {
        logger.debug("Starting discovery of deployment mode");

        try {
            ExtensionList<GerritTriggerModeProvider> providers =
                ExtensionList.lookup(GerritTriggerModeProvider.class);

            if (providers == null || providers.isEmpty()) {
                logger.warn("No GerritTriggerModeProvider extensions found, using fallback");
                createFallbackMode();
                return;
            }

            logger.debug("Found {} provider(s)", providers.size());

            GerritTriggerModeProvider selectedProvider = null;

            // ExtensionList is already ordered by ordinal (highest first)
            // Pick the first available provider
            for (GerritTriggerModeProvider provider : providers) {
                logger.debug("Checking provider: {} (available={})",
                    provider.getModeName(),
                    provider.isAvailable());

                if (provider.isAvailable()) {
                    selectedProvider = provider;
                    logger.info("Selected deployment mode: {}", provider.getModeName());
                    break;
                }
            }

            if (selectedProvider == null) {
                throw new IllegalStateException(
                    "No available GerritTriggerModeProvider found. "
                    + "At least LocalModeProvider should be available.");
            }

            selectedMode = selectedProvider;

            // Create both implementations from the selected mode
            storage = selectedProvider.createStorage();
            claimStrategy = selectedProvider.createClaimStrategy();

            logger.info("Created BuildMemoryStorage: {}", storage.getClass().getSimpleName());
            logger.info("Created NotificationClaimStrategy: {}", claimStrategy.getClass().getSimpleName());

        } catch (Exception e) {
            logger.warn("Failed to discover mode via ExtensionList, using fallback", e);
            createFallbackMode();
        }
    }

    /**
     * Creates fallback mode implementations when ExtensionList is unavailable.
     * Used in unit test environments without Jenkins.
     */
    private static void createFallbackMode() {
        logger.info("Using fallback local mode (ExtensionList unavailable)");
        storage = new LocalBuildMemoryStorage();
        claimStrategy = new LocalNotificationClaimStrategy();
        selectedMode = null; // No provider in fallback mode
    }

    /**
     * Resets the factory, clearing the cached mode and all instances.
     *
     * <p><strong>Test Support:</strong> This method is intended for test isolation.
     * When using JenkinsRule, each test gets a fresh Jenkins instance, but this
     * factory's static fields persist. Tests must call reset() to force re-discovery.</p>
     *
     * <p><strong>Not for production use:</strong> Calling this in production code
     * could cause inconsistent behavior. The factory is designed to discover once
     * at startup.</p>
     *
     * <p><strong>Thread Safety:</strong> This method is synchronized to prevent
     * concurrent reset/get calls from causing race conditions.</p>
     */
    public static synchronized void reset() {
        logger.debug("Resetting GerritTriggerModeFactory");
        selectedMode = null;
        storage = null;
        claimStrategy = null;
    }

    /**
     * Returns the currently selected mode provider for diagnostics.
     *
     * @return the selected provider, or null if not yet initialized or using fallback
     */
    public static GerritTriggerModeProvider getSelectedMode() {
        return selectedMode;
    }
}
