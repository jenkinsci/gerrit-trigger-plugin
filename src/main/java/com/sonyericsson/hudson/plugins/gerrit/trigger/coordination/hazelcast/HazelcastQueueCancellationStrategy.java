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
 * <p>Protection against external queue cancellations (e.g. CloudBees QueueLoadBalancer
 * moving items between replicas) is handled by the {@code isCancelling()} guard in
 * {@link HazelcastBuildMemoryStorage#cancelled} — only entries explicitly flagged by
 * {@code cancelOutdatedEvents()} are marked as completed.</p>
 *
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.hazelcast.HazelcastCoordinationProvider
 * @see QueueCancellationStrategy
 */
public class HazelcastQueueCancellationStrategy extends QueueCancellationStrategy {

    /**
     * Returns false unconditionally.
     *
     * <p>CloudBees {@code CancelQueueItem} calls {@code Queue.cancel(item)} with no markers
     * attached to the resulting {@code LeftItem}:</p>
     * <ul>
     *   <li>{@code QueueLoadBalancerAction} is attached to the NEW item on the target replica
     *       (inside {@code QueueRequest} executed remotely), never to the item being cancelled.</li>
     *   <li>{@code LoadBalancedCauseOfBlockage} is a {@code BlockedItem.causeOfBlockage} that
     *       Jenkins does not copy into {@code LeftItem} — {@code LeftItem.getCauseOfBlockage()}
     *       returns null for load-balanced cancellations.</li>
     * </ul>
     *
     * <p>Confirmed via bytecode analysis of {@code cloudbees-replication.jar} v2656.
     * The real protection is the {@code isCancelling()} guard in
     * {@link HazelcastBuildMemoryStorage#cancelled}.</p>
     *
     * @param item the queue item that left the queue as cancelled
     * @return always false
     */
    @Override
    public boolean isLoadBalancedCancellation(@NonNull LeftItem item) {
        return false;
    }
}
