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
package com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.hazelcast;

import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.QueueCancellationStrategy;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Queue.LeftItem;

/**
 * Hazelcast (distributed) implementation of QueueCancellationStrategy.
 *
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.hazelcast.HazelcastCoordinationProvider
 * @see QueueCancellationStrategy
 */
public class HazelcastQueueCancellationStrategy extends QueueCancellationStrategy {

    /**
     * Returns false unconditionally.
     *
     * <p>Potential {@code CancelQueueItem} calls {@code Queue.cancel(item)} with no markers
     * attached to the resulting {@code LeftItem}:</p>
     * <ul>
     *   <li>{@code QueueLoadBalancerAction} is attached to the NEW item on the target instance
     *       (inside {@code QueueRequest} executed remotely), never to the item being cancelled.</li>
     *   <li>{@code LoadBalancedCauseOfBlockage} is a {@code BlockedItem.causeOfBlockage} that
     *       Jenkins does not copy into {@code LeftItem} — {@code LeftItem.getCauseOfBlockage()}
     *       returns null for load-balanced cancellations.</li>
     * </ul>
     *
     * @param item the queue item that left the queue as cancelled
     * @return always false
     */
    @Override
    public boolean isLoadBalancedCancellation(@NonNull LeftItem item) {
        return false;
    }
}
