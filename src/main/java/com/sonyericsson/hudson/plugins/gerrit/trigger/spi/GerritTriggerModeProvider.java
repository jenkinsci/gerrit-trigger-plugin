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
package com.sonyericsson.hudson.plugins.gerrit.trigger.spi;

import hudson.ExtensionPoint;

/**
 * Extension point for providing deployment mode implementations.
 *
 * <p>Each deployment mode (local, cluster, etc.) provides all necessary implementations
 * together as a cohesive unit. This ensures that related components always use
 * implementations from the same mode, avoiding inconsistent configurations.</p>
 *
 * <h2>Design Rationale:</h2>
 * <p>Rather than having separate extension points for each component (storage, notification
 * claiming, etc.), we group them by deployment mode. This is because:</p>
 * <ul>
 *   <li>Components are always deployed together in the same mode</li>
 *   <li>Mode detection logic is shared across components</li>
 *   <li>Prevents mismatched configurations (e.g., local storage + cluster claiming)</li>
 *   <li>Simpler to add new modes (implement one provider, not N providers)</li>
 * </ul>
 *
 * <h2>Usage Pattern:</h2>
 * <pre>{@code
 * @Extension(ordinal = 0)  // Lower ordinal = lower priority (fallback)
 * public class LocalModeProvider extends GerritTriggerModeProvider {
 *
 *     @Override
 *     public int getPriority() {
 *         return 0;  // Fallback priority
 *     }
 *
 *     @Override
 *     public boolean isAvailable() {
 *         return true;  // Always available
 *     }
 *
 *     @Override
 *     public String getModeName() {
 *         return "Local";
 *     }
 *
 *     @Override
 *     public BuildMemoryStorage createStorage() {
 *         return new LocalBuildMemoryStorage();
 *     }
 *
 *     @Override
 *     public NotificationClaimStrategy createClaimStrategy() {
 *         return new LocalNotificationClaimStrategy();
 *     }
 * }
 *
 * @Extension(ordinal = 100)  // Higher ordinal = higher priority
 * public class ClusterModeProvider extends GerritTriggerModeProvider {
 *
 *     @Override
 *     public int getPriority() {
 *         return 100;  // Wins over local when available
 *     }
 *
 *     @Override
 *     public boolean isAvailable() {
 *         return ClusterModeProvider.isClusterModeEnabled()
 *             && HazelcastInstanceProvider.getInstance() != null;
 *     }
 *
 *     @Override
 *     public String getModeName() {
 *         return "Cluster";
 *     }
 *
 *     @Override
 *     public BuildMemoryStorage createStorage() {
 *         return new HazelcastBuildMemoryStorage();
 *     }
 *
 *     @Override
 *     public NotificationClaimStrategy createClaimStrategy() {
 *         return new ClusterNotificationClaimStrategy();
 *     }
 * }
 * }</pre>
 *
 * <h2>Selection Algorithm:</h2>
 * <ol>
 *   <li>Jenkins discovers all providers via ExtensionList.lookup()</li>
 *   <li>Filters to only available providers (isAvailable() == true)</li>
 *   <li>Selects the provider with highest priority</li>
 *   <li>Creates all component instances from selected provider</li>
 * </ol>
 *
 * @see BuildMemoryStorage
 * @see NotificationClaimStrategy
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.GerritTriggerModeFactory
 */
public abstract class GerritTriggerModeProvider implements ExtensionPoint {

    /**
     * Returns the priority of this provider.
     * Higher values take precedence over lower values when multiple providers are available.
     *
     * <p><b>Recommended values:</b></p>
     * <ul>
     *   <li>0 = Local/fallback mode (always available)</li>
     *   <li>100 = Cluster mode (conditionally available)</li>
     *   <li>Custom implementations can use any value to order themselves</li>
     * </ul>
     *
     * @return the priority value (higher = higher priority)
     */
    public abstract int getPriority();

    /**
     * Checks if this mode provider can create implementations in the current environment.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Local mode: always returns true (fallback)</li>
     *   <li>Cluster mode: checks if cluster mode enabled AND Hazelcast available</li>
     * </ul>
     *
     * <p>Called during provider discovery to filter out unavailable modes.</p>
     *
     * @return true if this provider can create working implementations, false otherwise
     */
    public abstract boolean isAvailable();

    /**
     * Returns the name of this deployment mode for logging and debugging.
     *
     * <p>Examples: "Local", "Cluster", "Redis", etc.</p>
     *
     * @return the mode name (non-null)
     */
    public abstract String getModeName();

    /**
     * Creates a new BuildMemoryStorage instance for this mode.
     *
     * <p>Called once during factory initialization after this provider is selected
     * as the highest-priority available provider.</p>
     *
     * <p><b>Thread Safety:</b> This method may be called from multiple threads during
     * factory initialization (double-checked locking). Implementations should be stateless
     * or properly synchronized.</p>
     *
     * @return a new BuildMemoryStorage instance (non-null)
     */
    public abstract BuildMemoryStorage createStorage();

    /**
     * Creates a new NotificationClaimStrategy instance for this mode.
     *
     * <p>Called once during factory initialization after this provider is selected
     * as the highest-priority available provider.</p>
     *
     * <p><b>Thread Safety:</b> This method may be called from multiple threads during
     * factory initialization (double-checked locking). Implementations should be stateless
     * or properly synchronized.</p>
     *
     * @return a new NotificationClaimStrategy instance (non-null)
     */
    public abstract NotificationClaimStrategy createClaimStrategy();
}
