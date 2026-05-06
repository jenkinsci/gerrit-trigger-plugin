/*
 *  The MIT License
 *
 *  Copyright (c) 2010, 2014 Sony Mobile Communications Inc. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.NotificationClaimStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.NotificationClaimStrategyProvider;
import hudson.ExtensionList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Factory for discovering and creating NotificationClaimStrategy implementations.
 *
 * <p>Uses Jenkins Extension Points to discover all implementations of
 * {@link NotificationClaimStrategyProvider} and selects the one with the highest
 * priority that is currently available.</p>
 *
 * <p>The factory uses lazy initialization with double-checked locking for
 * thread safety and performance.</p>
 *
 * <h2>Selection Logic:</h2>
 * <ol>
 *   <li>Discover all providers via {@link ExtensionList#lookup(Class)}</li>
 *   <li>Filter to only available providers (via {@link NotificationClaimStrategyProvider#isAvailable()})</li>
 *   <li>Select the provider with the highest {@link NotificationClaimStrategyProvider#getPriority()}</li>
 *   <li>Create the strategy instance via {@link NotificationClaimStrategyProvider#createStrategy()}</li>
 * </ol>
 *
 * <h2>Thread Safety:</h2>
 * <p>This factory is thread-safe using double-checked locking pattern. Multiple threads
 * calling {@link #getInstance()} concurrently will result in exactly one instance being
 * created.</p>
 *
 * <h2>Test Support:</h2>
 * <p>The {@link #reset()} method allows tests to clear the cached instance and force
 * re-discovery on next access. This is critical for test isolation when using JenkinsRule,
 * which creates fresh Jenkins instances per test.</p>
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 * @see NotificationClaimStrategy
 * @see NotificationClaimStrategyProvider
 */
public final class NotificationClaimStrategyFactory {

    private static final Logger logger = LoggerFactory.getLogger(NotificationClaimStrategyFactory.class);

    /**
     * The singleton strategy instance, lazily initialized.
     */
    private static volatile NotificationClaimStrategy instance;

    /**
     * The selected provider (for diagnostics).
     */
    private static volatile NotificationClaimStrategyProvider selectedProvider;

    /**
     * Private constructor to prevent instantiation.
     */
    private NotificationClaimStrategyFactory() {
        // Utility class, no instances
    }

    /**
     * Gets the NotificationClaimStrategy instance.
     *
     * <p>Uses double-checked locking for thread-safe lazy initialization.
     * The instance is discovered and created on first access.</p>
     *
     * @return the strategy implementation
     * @throws IllegalStateException if no available provider is found
     */
    @NonNull
    public static NotificationClaimStrategy getInstance() {
        if (instance == null) {
            synchronized (NotificationClaimStrategyFactory.class) {
                if (instance == null) {
                    instance = discoverImplementation();
                }
            }
        }
        return instance;
    }

    /**
     * Discovers and selects the best available strategy implementation.
     *
     * <p>Iterates through all registered providers and selects the one with
     * the highest priority that is currently available.</p>
     *
     * <p>If ExtensionList is not available (e.g., in unit tests without Jenkins),
     * falls back to creating a LocalNotificationClaimStrategy directly.</p>
     *
     * @return the selected strategy implementation
     * @throws IllegalStateException if no providers are available
     */
    @NonNull
    private static NotificationClaimStrategy discoverImplementation() {
        logger.debug("Starting discovery of NotificationClaimStrategy implementation");

        try {
            ExtensionList<NotificationClaimStrategyProvider> providers =
                ExtensionList.lookup(NotificationClaimStrategyProvider.class);

            if (providers == null || providers.isEmpty()) {
                logger.warn("No NotificationClaimStrategyProvider extensions found, using fallback");
                return createFallbackStrategy();
            }

            logger.debug("Found {} provider(s)", providers.size());

            NotificationClaimStrategyProvider bestProvider = null;
            int bestPriority = Integer.MIN_VALUE;

            for (NotificationClaimStrategyProvider provider : providers) {
                logger.debug("Checking provider: {} (priority={}, available={})",
                    provider.getName(),
                    provider.getPriority(),
                    provider.isAvailable());

                if (provider.isAvailable()) {
                    if (provider.getPriority() > bestPriority) {
                        bestProvider = provider;
                        bestPriority = provider.getPriority();
                        logger.debug("New best provider: {} with priority {}",
                            provider.getName(), bestPriority);
                    }
                }
            }

            if (bestProvider == null) {
                throw new IllegalStateException(
                    "No available NotificationClaimStrategyProvider found. "
                    + "At least LocalNotificationClaimStrategyProvider should be available.");
            }

            selectedProvider = bestProvider;
            logger.info("Selected NotificationClaimStrategy provider: {} (priority={})",
                bestProvider.getName(), bestProvider.getPriority());

            NotificationClaimStrategy strategy = bestProvider.createStrategy();
            logger.info("Created NotificationClaimStrategy: {}", strategy.getClass().getSimpleName());

            return strategy;

        } catch (Exception e) {
            logger.warn("Failed to discover NotificationClaimStrategy via ExtensionList, using fallback", e);
            return createFallbackStrategy();
        }
    }

    /**
     * Creates a fallback strategy when ExtensionList is unavailable.
     * Used in unit test environments without Jenkins.
     *
     * @return a LocalNotificationClaimStrategy instance
     */
    @NonNull
    private static NotificationClaimStrategy createFallbackStrategy() {
        logger.info("Using fallback LocalNotificationClaimStrategy (ExtensionList unavailable)");
        return new LocalNotificationClaimStrategy();
    }

    /**
     * Resets the factory, clearing the cached instance and selected provider.
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
     * concurrent reset/getInstance calls from causing race conditions.</p>
     */
    public static synchronized void reset() {
        logger.debug("Resetting NotificationClaimStrategyFactory");
        instance = null;
        selectedProvider = null;
    }

    /**
     * Returns the currently selected provider for diagnostics.
     *
     * @return the selected provider, or null if not yet initialized
     */
    public static NotificationClaimStrategyProvider getSelectedProvider() {
        return selectedProvider;
    }
}
