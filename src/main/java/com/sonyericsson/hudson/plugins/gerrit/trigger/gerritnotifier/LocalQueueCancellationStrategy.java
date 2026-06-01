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

import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.QueueCancellationStrategy;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Queue.LeftItem;

/**
 * Local (standalone) implementation of QueueCancellationStrategy.
 * Always returns false since there is no HA load balancer in single-instance mode.
 *
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.LocalCoordinationProvider
 * @see QueueCancellationStrategy
 */
public class LocalQueueCancellationStrategy extends QueueCancellationStrategy {

    /**
     * Always returns false in standalone mode — no load balancer is present.
     *
     * @param item the queue item that was cancelled
     * @return false
     */
    @Override
    public boolean isLoadBalancedCancellation(@NonNull LeftItem item) {
        return false;
    }
}
