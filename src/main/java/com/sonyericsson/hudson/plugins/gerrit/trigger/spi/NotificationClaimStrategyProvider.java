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
package com.sonyericsson.hudson.plugins.gerrit.trigger.spi;

import hudson.ExtensionPoint;

/**
 * Extension point for providing NotificationClaimStrategy implementations.
 *
 * <p>This follows the Jenkins Extension Points pattern to enable pluggable notification
 * claiming strategies without if/else conditionals in the code.</p>
 *
 * <h2>Usage Pattern:</h2>
 * <pre>{@code
 * @Extension(ordinal = 0)  // Lower ordinal = lower priority (fallback)
 * public class LocalNotificationClaimStrategyProvider extends NotificationClaimStrategyProvider {
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
 *     public NotificationClaimStrategy createStrategy() {
 *         return new LocalNotificationClaimStrategy();
 *     }
 * }
 *
 * @Extension(ordinal = 100)  // Higher ordinal = higher priority
 * public class ClusterNotificationClaimStrategyProvider extends NotificationClaimStrategyProvider {
 *
 *     @Override
 *     public int getPriority() {
 *         return 100;  // Wins over local when available
 *     }
 *
 *     @Override
 *     public boolean isAvailable() {
 *         return ClusterModeProvider.isClusterModeEnabled();
 *     }
 *
 *     @Override
 *     public NotificationClaimStrategy createStrategy() {
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
 *   <li>Creates strategy instance via createStrategy()</li>
 * </ol>
 *
 * @see NotificationClaimStrategy
 * @see NotificationClaimStrategyFactory
 */
public abstract class NotificationClaimStrategyProvider implements ExtensionPoint {

    /**
     * Returns the priority of this provider.
     * Higher values take precedence over lower values when multiple providers are available.
     *
     * <p><b>Recommended values:</b></p>
     * <ul>
     *   <li>0 = Local/fallback implementation (always available)</li>
     *   <li>100 = Cluster mode implementation (conditionally available)</li>
     *   <li>Custom implementations can use any value to order themselves</li>
     * </ul>
     *
     * @return the priority value (higher = higher priority)
     */
    public abstract int getPriority();

    /**
     * Checks if this provider can create a strategy in the current environment.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>Local provider: always returns true (fallback)</li>
     *   <li>Cluster provider: checks if cluster mode enabled AND Hazelcast available</li>
     * </ul>
     *
     * <p>Called during provider discovery to filter out unavailable implementations.</p>
     *
     * @return true if this provider can create a working strategy, false otherwise
     */
    public abstract boolean isAvailable();

    /**
     * Creates a new NotificationClaimStrategy instance.
     *
     * <p>Called once during factory initialization after this provider is selected
     * as the highest-priority available provider.</p>
     *
     * <p><b>Thread Safety:</b> This method may be called from multiple threads during
     * factory initialization (double-checked locking). Implementations should be stateless
     * or properly synchronized.</p>
     *
     * @return a new NotificationClaimStrategy instance
     */
    public abstract NotificationClaimStrategy createStrategy();

    /**
     * Returns the name of this provider for logging and debugging.
     * Default implementation returns the simple class name.
     *
     * @return the provider name (non-null)
     */
    public String getName() {
        return this.getClass().getSimpleName();
    }
}
