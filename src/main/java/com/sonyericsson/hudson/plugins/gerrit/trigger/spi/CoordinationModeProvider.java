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
 * Extension point for providing coordination mode implementations.
 *
 * <p>Each coordination mode (local, cluster, etc.) provides all necessary implementations
 * together as a cohesive unit. This ensures that related components always use
 * implementations from the same mode, avoiding inconsistent configurations.</p>
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
 * <p>Rather than having separate extension points for each component (storage, notification
 * claiming, etc.), we group them by coordination mode. This is because:</p>
 * <ul>
 *   <li>Components are always deployed together in the same mode</li>
 *   <li>Mode detection logic is shared across components</li>
 *   <li>Prevents mismatched configurations (e.g., local storage + cluster claiming)</li>
 *   <li>Simpler to add new modes (implement one provider, not N providers)</li>
 * </ul>
 *
 * <h2>Provider Ordering:</h2>
 * <p>Providers are automatically ordered by Jenkins based on {@code @Extension(ordinal)} value.
 * The factory selects the first available provider from this ordered list.</p>
 * <ul>
 *   <li>Higher ordinal = Higher priority (checked first)</li>
 *   <li>{@code @Extension(ordinal = -1000)} - Local/fallback mode</li>
 *   <li>{@code @Extension(ordinal = 100)} - Cluster mode (future)</li>
 * </ul>
 *
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.LocalCoordinationProvider
 * @see BuildMemoryStorage
 * @see NotificationClaimStrategy
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.CoordinationModeFactory
 */
public abstract class CoordinationModeProvider implements ExtensionPoint {

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
     * Returns the name of this coordination mode for logging and debugging.
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
