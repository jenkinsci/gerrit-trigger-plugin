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
import hudson.Extension;

/**
 * Provider for local (standalone) deployment mode.
 *
 * <p>This mode provides TreeMap-based storage and always-true notification claiming,
 * suitable for single-instance Jenkins deployments where no coordination is needed.</p>
 *
 * <p>This provider has the lowest priority (0) and is always available, serving as
 * the fallback when no higher-priority modes (like cluster) are available.</p>
 *
 * <p>The {@code @Extension(ordinal = 0)} annotation registers this provider with Jenkins.
 * The ordinal value determines the order in which providers are considered - lower values
 * are fallbacks, higher values take precedence.</p>
 *
 * @see LocalBuildMemoryStorage
 * @see LocalNotificationClaimStrategy
 * @see GerritTriggerModeFactory
 */
@Extension(ordinal = 0)
public class LocalModeProvider extends GerritTriggerModeProvider {

    /**
     * Returns the priority of this provider.
     * Lowest priority (0) since this is the fallback mode.
     *
     * @return 0 (lowest priority)
     */
    @Override
    public int getPriority() {
        return 0;
    }

    /**
     * Checks if local mode is available.
     * Always returns true since local mode has no dependencies.
     *
     * @return true (always available)
     */
    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * Returns the name of this deployment mode.
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
}
