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

import java.util.logging.Logger;

/**
 * Hazelcast (distributed) implementation of QueueCancellationStrategy.
 *
 * <p>Detects queue item cancellations triggered by the CloudBees HA load balancer
 * (QueueLoadBalancer), which moves queue items between replicas by cancelling the
 * original and re-queuing it on the target replica. These cancellations must be
 * ignored to avoid sending premature "build cancelled" feedback to Gerrit.</p>
 *
 * <p>Class names are checked via string matching to avoid a mandatory compile-time
 * dependency on the CloudBees replication plugin.</p>
 *
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.hazelcast.HazelcastCoordinationProvider
 * @see QueueCancellationStrategy
 */
public class HazelcastQueueCancellationStrategy extends QueueCancellationStrategy {

    private static final Logger logger = Logger.getLogger(HazelcastQueueCancellationStrategy.class.getName());

    /**
     * Returns true if the cancelled item was moved by the HA load balancer.
     *
     * <p>Two markers are checked (either is sufficient):</p>
     * <ul>
     *   <li>{@code QueueLoadBalancerAction} in the item's actions — present on the
     *       new queue item created on the target replica.</li>
     *   <li>{@code LoadBalancedCauseOfBlockage} as cause-of-blockage — present on
     *       the original item cancelled by {@code CancelQueueItem}.</li>
     * </ul>
     *
     * @param item the queue item that left the queue as cancelled
     * @return true if cancelled by the HA load balancer
     */
    @Override
    public boolean isLoadBalancedCancellation(@NonNull LeftItem item) {
        boolean result = item.getActions().stream()
                .anyMatch(a -> a.getClass().getName().contains("QueueLoadBalancerAction"))
                || (item.getCauseOfBlockage() != null
                && item.getCauseOfBlockage().getClass().getName()
                .contains("LoadBalancedCauseOfBlockage"));
        if (result) {
            logger.fine("Queue item cancelled due to HA load balancing, skipping Gerrit cancellation: " + item);
        }
        return result;
    }
}
