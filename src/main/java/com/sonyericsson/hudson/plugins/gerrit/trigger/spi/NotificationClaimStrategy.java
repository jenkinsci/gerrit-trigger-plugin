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
package com.sonyericsson.hudson.plugins.gerrit.trigger.spi;

import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Strategy interface for notification claiming in different deployment modes.
 * Implementations handle the coordination of which Jenkins instance should send
 * notifications to Gerrit in HA/HS deployments.
 *
 * <p>This interface enables switching between local (standalone) and cluster modes:</p>
 * <ul>
 *   <li><b>Local mode:</b> Always claims notification rights - no coordination needed</li>
 *   <li><b>Cluster mode:</b> Uses distributed coordination (e.g., Hazelcast) to ensure
 *       only one replica sends feedback to Gerrit per event</li>
 * </ul>
 *
 * <p>Implementations are discovered via the Extension Points pattern using
 * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.spi.GerritTriggerModeProvider}.</p>
 *
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.spi.GerritTriggerModeProvider
 */
public interface NotificationClaimStrategy {

    /**
     * Attempts to claim the right to send notification for an event.
     * In local mode, always returns true. In cluster mode, uses distributed
     * coordination to ensure only one replica sends the notification.
     *
     * @param event the Gerrit event
     * @return true if this instance should send the notification, false otherwise
     */
    boolean tryClaimNotificationRight(@NonNull GerritTriggeredEvent event);

    /**
     * Releases the notification claim for an event.
     * Called after notification is sent or on error to clean up resources.
     *
     * @param event the Gerrit event
     */
    void releaseNotificationRight(@NonNull GerritTriggeredEvent event);
}
