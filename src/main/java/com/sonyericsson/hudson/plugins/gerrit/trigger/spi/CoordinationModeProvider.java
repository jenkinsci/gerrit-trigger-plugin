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
 * @see EventClaimStrategy
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.CoordinationModeFactory
 */
public abstract class CoordinationModeProvider implements ExtensionPoint {

    /**
     * System property to specify coordination mode.
     */
    private static final String COORDINATION_MODE_PROPERTY = "gerrit.trigger.coordination.mode";

    /**
     * Gets the configured coordination mode.
     * <p>
     * <strong>Helper method for providers:</strong> Centralizes the logic for determining
     * which coordination mode is configured. This allows future evolution from system
     * properties to UI configuration without changing provider implementations.
     * <p>
     * <strong>Today:</strong> Reads from system property
     * {@code gerrit.trigger.coordination.mode} (default: "local")
     * <p>
     * <strong>Future:</strong> Can check UI configuration when providers add config pages
     * (e.g., Redis/JDBC providers with connection settings in Jenkins UI)
     *
     * @return the configured mode name (e.g., "local", "hazelcast", "redis", "jdbc")
     */
    public static String getConfiguredMode() {
        return System.getProperty(COORDINATION_MODE_PROPERTY, "local");
    }

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

    /**
     * Creates a new EventClaimStrategy instance for this mode.
     *
     * <p>Called once during factory initialization after this provider is selected
     * as the highest-priority available provider.</p>
     *
     * <p>The EventClaimStrategy prevents duplicate build processing when multiple Jenkins
     * instances receive the same Gerrit event in HA/HS deployments. In local mode, this
     * is a NO-OP (always claims). In distributed mode (e.g., Hazelcast), this uses
     * distributed coordination to ensure only one instance processes each event.</p>
     *
     * <p><b>Thread Safety:</b> This method may be called from multiple threads during
     * factory initialization (double-checked locking). Implementations should be stateless
     * or properly synchronized.</p>
     *
     * @return a new EventClaimStrategy instance (non-null)
     */
    public abstract EventClaimStrategy createEventClaimStrategy();

    /**
     * Creates a new QueueCancellationStrategy instance for this mode.
     *
     * <p>Called once during factory initialization after this provider is selected
     * as the highest-priority available provider.</p>
     *
     * <p>The QueueCancellationStrategy determines whether a cancelled Jenkins queue item
     * should be ignored because it was moved by the HA load balancer rather than being
     * cancelled by a user or a new patchset event. In local mode, this is a NO-OP (always
     * returns false). In distributed mode (e.g., Hazelcast), this inspects the item for
     * load-balancer markers.</p>
     *
     * <p><b>Thread Safety:</b> This method may be called from multiple threads during
     * factory initialization (double-checked locking). Implementations should be stateless
     * or properly synchronized.</p>
     *
     * @return a new QueueCancellationStrategy instance (non-null)
     */
    public abstract QueueCancellationStrategy createQueueCancellationStrategy();

    /**
     * Initializes this coordination mode provider.
     *
     * <p>Called during plugin startup (PluginImpl.start()) to initialize any resources
     * needed by this provider. For example:</p>
     * <ul>
     *   <li>Local mode: no-op (no initialization needed)</li>
     *   <li>Hazelcast mode: initializes Hazelcast instance and cluster membership</li>
     *   <li>Redis mode: establishes connection pool</li>
     *   <li>JDBC mode: initializes database connection</li>
     * </ul>
     *
     * <p><b>IMPORTANT:</b> This method is called BEFORE the provider is selected by
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.CoordinationModeFactory}.
     * The provider must be fully initialized when {@link #isAvailable()} is called during
     * provider discovery.</p>
     *
     * <p><b>Error Handling:</b> If initialization fails, implementations should throw an
     * exception. The plugin will log the error and continue, allowing {@link #isAvailable()}
     * to return false so a fallback provider can be selected.</p>
     *
     * @throws Exception if initialization fails
     */
    public abstract void initialize() throws Exception;

    /**
     * Shuts down this coordination mode provider.
     *
     * <p>Called during plugin shutdown (PluginImpl.stop()) to release any resources
     * held by this provider. For example:</p>
     * <ul>
     *   <li>Local mode: no-op (no resources to release)</li>
     *   <li>Hazelcast mode: shuts down Hazelcast instance gracefully</li>
     *   <li>Redis mode: closes connection pool</li>
     *   <li>JDBC mode: closes database connections</li>
     * </ul>
     *
     * <p><b>Error Handling:</b> Implementations should handle errors gracefully and
     * not throw exceptions, as this is called during shutdown and exceptions cannot
     * be meaningfully handled.</p>
     */
    public abstract void shutdown();
}
