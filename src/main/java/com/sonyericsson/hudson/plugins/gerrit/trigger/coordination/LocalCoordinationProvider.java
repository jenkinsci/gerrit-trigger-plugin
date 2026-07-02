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

import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.LocalEventClaimStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.LocalNotificationClaimStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.LocalQueueCancellationStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.BuildMemoryStorage;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.CoordinationModeProvider;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.EventClaimStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.NotificationClaimStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.QueueCancellationStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.storage.LocalBuildMemoryStorage;
import hudson.Extension;

/**
 * Provider for local (standalone) coordination mode.
 *
 * <p>This mode provides TreeMap-based storage and always-true notification claiming,
 * suitable for single-instance Jenkins deployments where no coordination is needed.</p>
 *
 * <p>This provider has the lowest priority and is always available, serving as
 * the fallback when no higher-priority modes (like cluster) are available.</p>
 *
 * <p>The {@code @Extension(ordinal = FALLBACK_PRIORITY)} annotation registers this provider
 * with Jenkins with a very low ordinal, ensuring it is only selected when no other modes
 * are available. Higher ordinal values take precedence.</p>
 *
 * @see LocalBuildMemoryStorage
 * @see LocalNotificationClaimStrategy
 * @see LocalEventClaimStrategy
 * @see CoordinationModeFactory
 */
@Extension(ordinal = LocalCoordinationProvider.FALLBACK_PRIORITY)
public class LocalCoordinationProvider extends CoordinationModeProvider {

    /**
     * Extension ordinal priority for local coordination provider.
     * Very low value ensures this is only selected when no other (higher priority) modes are available.
     */
    static final int FALLBACK_PRIORITY = -1000;

    /**
     * Checks if local coordination mode is available.
     * Always returns true since local mode has no dependencies.
     *
     * @return true (always available)
     */
    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * Returns the name of this coordination mode.
     *
     * @return "Local"
     */
    @Override
    public String getModeName() {
        return "Local";
    }

    /**
     * Creates a new local build memory storage instance.
     * Uses TreeMap-based in-memory storage.
     *
     * @return a new LocalBuildMemoryStorage
     */
    @Override
    public BuildMemoryStorage createStorage() {
        return new LocalBuildMemoryStorage();
    }

    /**
     * Creates a new local notification claim strategy instance.
     * Always returns true - no coordination needed in standalone mode.
     *
     * @return a new LocalNotificationClaimStrategy
     */
    @Override
    public NotificationClaimStrategy createClaimStrategy() {
        return new LocalNotificationClaimStrategy();
    }

    /**
     * Creates a new local event claim strategy instance.
     * Always succeeds and executes the action immediately - no coordination needed in standalone mode.
     *
     * @return a new LocalEventClaimStrategy
     */
    @Override
    public EventClaimStrategy createEventClaimStrategy() {
        return new LocalEventClaimStrategy();
    }

    /**
     * Creates a new local queue cancellation strategy instance.
     * Always returns false - no distributed load balancer present in standalone mode.
     *
     * @return a new LocalQueueCancellationStrategy
     */
    @Override
    public QueueCancellationStrategy createQueueCancellationStrategy() {
        return new LocalQueueCancellationStrategy();
    }

    /**
     * Initializes local coordination mode.
     * <p>
     * Local mode requires no initialization - no external resources to set up.
     */
    @Override
    public void initialize() {
        // Local mode needs no initialization
    }

    /**
     * Shuts down local coordination mode.
     * <p>
     * Local mode requires no shutdown - no external resources to release.
     */
    @Override
    public void shutdown() {
        // Local mode needs no shutdown
    }
}
