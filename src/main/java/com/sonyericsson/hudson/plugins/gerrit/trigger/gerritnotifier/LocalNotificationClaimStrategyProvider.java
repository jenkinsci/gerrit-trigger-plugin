/*
 *  The MIT License
 *
 *  Copyright (c) 2010, 2014 Sony Mobile Communications Inc. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.NotificationClaimStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.NotificationClaimStrategyProvider;
import hudson.Extension;

/**
 * Provider for local (standalone) notification claim strategy.
 *
 * <p>This provider has the lowest priority (0) and is always available, serving as
 * the fallback when no higher-priority providers (like cluster mode) are available.</p>
 *
 * <p>The {@code @Extension(ordinal = 0)} annotation registers this provider with Jenkins.
 * The ordinal value determines the order in which providers are considered - lower values
 * are fallbacks, higher values take precedence.</p>
 *
 * @see LocalNotificationClaimStrategy
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.NotificationClaimStrategyFactory
 */
@Extension(ordinal = 0)
public class LocalNotificationClaimStrategyProvider extends NotificationClaimStrategyProvider {

    /**
     * Returns the priority of this provider.
     * Lowest priority (0) since this is the fallback implementation.
     *
     * @return 0 (lowest priority)
     */
    @Override
    public int getPriority() {
        return 0;
    }

    /**
     * Checks if local notification claiming is available.
     * Always returns true since local mode has no dependencies.
     *
     * @return true (always available)
     */
    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * Creates a new local notification claim strategy instance.
     *
     * @return a new LocalNotificationClaimStrategy
     */
    @Override
    public NotificationClaimStrategy createStrategy() {
        return new LocalNotificationClaimStrategy();
    }
}
