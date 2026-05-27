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

import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.ClaimResult;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.ClaimResults;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.NotificationClaimStrategy;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local (non-cluster) implementation of notification claiming.
 * Always executes the notification action since there's no need for coordination in standalone mode.
 *
 * <p>This is the default/fallback implementation used when cluster mode is not enabled.
 * In standalone Jenkins deployments, there's only one instance, so it always has the
 * right to send notifications.</p>
 *
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.LocalCoordinationProvider
 */
public class LocalNotificationClaimStrategy extends NotificationClaimStrategy {

    private static final Logger logger = LoggerFactory.getLogger(LocalNotificationClaimStrategy.class);

    @Override
    @NonNull
    public ClaimResult withClaim(@NonNull GerritTriggeredEvent event,
                                  @NonNull String notificationType,
                                  String jobIdentifier,
                                  @NonNull Runnable claimed) {
        // In local mode, always allow notification - no coordination needed
        // jobIdentifier is ignored since there's only one instance
        try {
            claimed.run();
            return ClaimResults.success();
        } catch (Exception e) {
            logger.error("Error executing notification action", e);
            return ClaimResults.failed(e);
        }
    }
}
