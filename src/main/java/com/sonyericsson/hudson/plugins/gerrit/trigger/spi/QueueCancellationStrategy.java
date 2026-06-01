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

import hudson.model.Queue.LeftItem;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Abstract base class for queue cancellation strategies in different deployment modes.
 *
 * <p>Determines whether a cancelled Jenkins queue item should be ignored because it was
 * cancelled by the HA load balancer (i.e. migrated to another replica), not by a user
 * or by a new patchset event.</p>
 *
 * <ul>
 *   <li><b>Local mode:</b> Always returns false — no load balancer present in standalone mode.</li>
 *   <li><b>Hazelcast mode:</b> Inspects the item's actions and cause-of-blockage for
 *       load-balancer markers to avoid sending premature Gerrit feedback.</li>
 * </ul>
 *
 * @see CoordinationModeProvider
 */
public abstract class QueueCancellationStrategy {

    /**
     * Determines whether a cancelled queue item should be ignored because it was
     * cancelled by the HA load balancer moving it to another replica.
     *
     * @param item the queue item that left the queue as cancelled
     * @return true if the cancellation is an HA load-balancing operation and should be skipped
     */
    public abstract boolean isLoadBalancedCancellation(@NonNull LeftItem item);
}
