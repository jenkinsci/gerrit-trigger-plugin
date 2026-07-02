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
package com.sonyericsson.hudson.plugins.gerrit.trigger.coordination;

import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.BuildMemoryStorage;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.CoordinationModeProvider;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.EventClaimStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.NotificationClaimStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.QueueCancellationStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.storage.LocalBuildMemoryStorage;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.LocalEventClaimStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.LocalNotificationClaimStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.LocalQueueCancellationStrategy;
import hudson.Extension;
import hudson.ExtensionList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Factory for discovering and creating coordination mode implementations.
 *
 * <p>This factory discovers the appropriate coordination mode (local, cluster, etc.) and
 * provides all component implementations from that mode. This ensures consistency -
 * all components come from the same mode and share the same availability conditions.</p>
 *
 * <h2>Coordination vs Triggering:</h2>
 * <p>"Coordination mode" refers to how Jenkins instances coordinate with each other when
 * processing Gerrit events, NOT how triggering behaves. The mode determines:</p>
 * <ul>
 *   <li>Where build memory is stored (local TreeMap vs distributed cache)</li>
 *   <li>How notification claiming works (always-claim vs distributed claiming)</li>
 *   <li>Whether instances need to coordinate at all (standalone vs cluster)</li>
 * </ul>
 *
 * <h2>Design Rationale:</h2>
 * <p>Instead of separate factories for each component, we use a single factory that
 * manages the entire coordination mode. This is because:</p>
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
 *   <li>Filter to only available providers (via {@link CoordinationModeProvider#isAvailable()})</li>
 *   <li>Select the first available provider from the ordered list (ordered by ordinal)</li>
 *   <li>Create all component instances from selected provider</li>
 * </ol>
 *
 * <h2>Thread Safety:</h2>
 * <p>This factory is thread-safe using double-checked locking pattern. Multiple threads
 * calling {@link #getStorage()} or {@link #getClaimStrategy()} concurrently will result
 * in exactly one mode being selected and one set of instances being created.</p>
 *
 * <h2>Lifecycle Management:</h2>
 * <p>This class is a Jenkins {@code @Extension} singleton, managed by Jenkins lifecycle.
 * Each Jenkins instance gets exactly one factory instance, which is automatically created
 * and discarded when Jenkins starts/stops. This design eliminates the need for manual
 * {@code reset()} methods in tests - JenkinsRule creates fresh instances automatically.</p>
 *
 * @see CoordinationModeProvider
 * @see BuildMemoryStorage
 * @see NotificationClaimStrategy
 * @see EventClaimStrategy
 */
@Extension
public class CoordinationModeFactory {

    private static final Logger logger = LoggerFactory.getLogger(CoordinationModeFactory.class);

    /**
     * The selected mode provider (for diagnostics).
     * Instance field - managed by Jenkins lifecycle, not static.
     */
    private volatile CoordinationModeProvider selectedMode;

    /**
     * The storage instance, lazily initialized.
     * Instance field - managed by Jenkins lifecycle, not static.
     */
    private volatile BuildMemoryStorage storage;

    /**
     * The claim strategy instance, lazily initialized.
     * Instance field - managed by Jenkins lifecycle, not static.
     */
    private volatile NotificationClaimStrategy claimStrategy;

    /**
     * The event claim strategy instance, lazily initialized.
     * Instance field - managed by Jenkins lifecycle, not static.
     */
    private volatile EventClaimStrategy eventClaimStrategy;

    /**
     * The queue cancellation strategy instance, lazily initialized.
     * Instance field - managed by Jenkins lifecycle, not static.
     */
    private volatile QueueCancellationStrategy queueCancellationStrategy;

    /**
     * Constructor - called by Jenkins once per Jenkins instance.
     * Public constructor allows Jenkins to instantiate via @Extension mechanism.
     */
    public CoordinationModeFactory() {
        logger.debug("CoordinationModeFactory instance created by Jenkins");
    }

    /**
     * Fallback instance used when Jenkins is not available (unit tests without JenkinsRule).
     * Lazily initialized on first access when ExtensionList is unavailable.
     */
    private static volatile CoordinationModeFactory fallbackInstance;

    /**
     * Gets the singleton factory instance managed by Jenkins.
     *
     * <p>This method uses {@link ExtensionList#lookupSingleton(Class)} to retrieve
     * the factory instance from Jenkins' extension registry. Jenkins guarantees
     * exactly one instance per Jenkins instance.</p>
     *
     * <p>If Jenkins is not available (e.g., unit tests without JenkinsRule), this method
     * creates and returns a fallback instance that uses local mode implementations.</p>
     *
     * @return the factory instance
     */
    @NonNull
    public static CoordinationModeFactory get() {
        try {
            return ExtensionList.lookupSingleton(CoordinationModeFactory.class);
        } catch (IllegalStateException | NullPointerException e) {
            // Jenkins not available (unit tests without JenkinsRule)
            // Use fallback instance with local mode
            if (fallbackInstance == null) {
                synchronized (CoordinationModeFactory.class) {
                    if (fallbackInstance == null) {
                        logger.debug("Jenkins not available, creating fallback CoordinationModeFactory");
                        fallbackInstance = new CoordinationModeFactory();
                    }
                }
            }
            return fallbackInstance;
        }
    }

    /**
     * Gets the BuildMemoryStorage instance for the current coordination mode.
     *
     * <p>Uses double-checked locking for thread-safe lazy initialization.
     * The mode is discovered and instances are created on first access.</p>
     *
     * @return the storage implementation
     * @throws IllegalStateException if no available mode provider is found
     */
    @NonNull
    public BuildMemoryStorage getStorage() {
        ensureInitialized();
        return storage;
    }

    /**
     * Gets the NotificationClaimStrategy instance for the current coordination mode.
     *
     * <p>Uses double-checked locking for thread-safe lazy initialization.
     * The mode is discovered and instances are created on first access.</p>
     *
     * @return the claim strategy implementation
     * @throws IllegalStateException if no available mode provider is found
     */
    @NonNull
    public NotificationClaimStrategy getClaimStrategy() {
        ensureInitialized();
        return claimStrategy;
    }

    /**
     * Gets the EventClaimStrategy instance for the current coordination mode.
     *
     * <p>Uses double-checked locking for thread-safe lazy initialization.
     * The mode is discovered and instances are created on first access.</p>
     *
     * <p>The EventClaimStrategy prevents duplicate build processing when multiple Jenkins
     * instances receive the same Gerrit event in distributed scenarios.</p>
     *
     * @return the event claim strategy implementation
     * @throws IllegalStateException if no available mode provider is found
     */
    @NonNull
    public EventClaimStrategy getEventClaimStrategy() {
        ensureInitialized();
        return eventClaimStrategy;
    }

    /**
     * Gets the QueueCancellationStrategy instance for the current coordination mode.
     *
     * <p>Uses double-checked locking for thread-safe lazy initialization.
     * The mode is discovered and instances are created on first access.</p>
     *
     * @return the queue cancellation strategy implementation
     * @throws IllegalStateException if no available mode provider is found
     */
    @NonNull
    public QueueCancellationStrategy getQueueCancellationStrategy() {
        ensureInitialized();
        return queueCancellationStrategy;
    }

    /**
     * Ensures the factory is initialized by discovering the mode if needed.
     * Uses double-checked locking for thread safety.
     */
    private void ensureInitialized() {
        if (selectedMode == null) {
            synchronized (this) {
                if (selectedMode == null) {
                    discoverMode();
                }
            }
        }
    }

    /**
     * Discovers and selects the best available coordination mode.
     *
     * <p>ExtensionList returns providers ordered by {@code @Extension(ordinal)} (highest first).
     * Selects the first provider that is currently available.</p>
     *
     * <p>If ExtensionList is not available (e.g., in unit tests without Jenkins),
     * falls back to creating local mode implementations directly.</p>
     *
     * @throws IllegalStateException if no providers are available
     */
    private void discoverMode() {
        logger.debug("Starting discovery of coordination mode");

        try {
            ExtensionList<CoordinationModeProvider> providers =
                ExtensionList.lookup(CoordinationModeProvider.class);

            if (providers == null || providers.isEmpty()) {
                logger.warn("No CoordinationModeProvider extensions found, using fallback");
                createFallbackMode();
                return;
            }

            logger.debug("Found {} provider(s)", providers.size());

            CoordinationModeProvider selectedProvider = null;

            // ExtensionList is already ordered by ordinal (highest first)
            // Pick the first available provider
            for (CoordinationModeProvider provider : providers) {
                logger.debug("Checking provider: {} (available={})",
                    provider.getModeName(),
                    provider.isAvailable());

                if (provider.isAvailable()) {
                    selectedProvider = provider;
                    logger.info("Selected coordination mode: {}", provider.getModeName());
                    break;
                }
            }

            if (selectedProvider == null) {
                throw new IllegalStateException(
                    "No available CoordinationModeProvider found. "
                    + "At least LocalCoordinationProvider should be available.");
            }

            selectedMode = selectedProvider;

            // Create all three implementations from the selected mode
            storage = selectedProvider.createStorage();
            claimStrategy = selectedProvider.createClaimStrategy();
            eventClaimStrategy = selectedProvider.createEventClaimStrategy();
            queueCancellationStrategy = selectedProvider.createQueueCancellationStrategy();

            logger.info("Created BuildMemoryStorage: {}", storage.getClass().getSimpleName());
            logger.info("Created NotificationClaimStrategy: {}", claimStrategy.getClass().getSimpleName());
            logger.info("Created EventClaimStrategy: {}", eventClaimStrategy.getClass().getSimpleName());
            logger.info("Created QueueCancellationStrategy: {}", queueCancellationStrategy.getClass().getSimpleName());

        } catch (Exception e) {
            logger.warn("Failed to discover mode via ExtensionList, using fallback", e);
            createFallbackMode();
        }
    }

    /**
     * Creates fallback mode implementations when ExtensionList is unavailable.
     * Used in unit test environments without Jenkins.
     */
    private void createFallbackMode() {
        logger.info("Using fallback local mode (ExtensionList unavailable)");
        storage = new LocalBuildMemoryStorage();
        claimStrategy = new LocalNotificationClaimStrategy();
        eventClaimStrategy = new LocalEventClaimStrategy();
        queueCancellationStrategy = new LocalQueueCancellationStrategy();
        selectedMode = null; // No provider in fallback mode
    }

    /**
     * Returns the currently selected mode provider for diagnostics.
     *
     * @return the selected provider, or null if not yet initialized or using fallback
     */
    public CoordinationModeProvider getSelectedMode() {
        return selectedMode;
    }
}
