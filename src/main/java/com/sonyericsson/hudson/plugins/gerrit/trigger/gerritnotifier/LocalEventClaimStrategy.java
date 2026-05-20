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

import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.EventClaimStrategy;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Local (standalone) implementation of EventClaimStrategy.
 * Always succeeds since there is no coordination needed in single-instance mode.
 * Executes the claimed action immediately.
 *
 * <p>This is the fallback implementation used when no higher-priority coordination
 * mode (like Hazelcast) is available. In standalone Jenkins deployments, there's
 * only one instance, so it always processes all events without coordination.</p>
 *
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.LocalCoordinationProvider
 * @see EventClaimStrategy
 */
public class LocalEventClaimStrategy extends EventClaimStrategy {

    private static final Logger logger = LoggerFactory.getLogger(LocalEventClaimStrategy.class);

    /**
     * Claims the event and executes the action immediately.
     * Always succeeds in local mode since there's no contention.
     *
     * @param event the Gerrit event to claim
     * @param claimed action to execute (always runs in local mode)
     * @return ClaimResult indicating success or error
     */
    @Override
    @NonNull
    public ClaimResult withClaim(@NonNull GerritTriggeredEvent event, @NonNull Runnable claimed) {
        // Local mode: always claim and execute immediately
        try {
            claimed.run();
            return new SuccessfulClaim();
        } catch (Exception e) {
            logger.error("Error processing event in local mode", e);
            return new FailedClaim(e);
        }
    }

    /**
     * Claim result for successful claim (local mode always succeeds).
     */
    private static class SuccessfulClaim implements ClaimResult {
        @Override
        @NonNull
        public ClaimResult notClaimed(@NonNull Runnable notClaimed) {
            // Never called - local mode always claims
            return this;
        }

        @Override
        @NonNull
        public ClaimResult onError(@NonNull Consumer<Exception> onError) {
            // Never called - no error occurred
            return this;
        }
    }

    /**
     * Claim result for failed claim (exception during processing).
     */
    private static class FailedClaim implements ClaimResult {
        private final Exception exception;

        /**
         * Constructor.
         * @param exception the exception that occurred
         */
        FailedClaim(Exception exception) {
            this.exception = exception;
        }

        @Override
        @NonNull
        public ClaimResult notClaimed(@NonNull Runnable notClaimed) {
            // Never called - local mode always claims (even if it fails during execution)
            return this;
        }

        @Override
        @NonNull
        public ClaimResult onError(@NonNull Consumer<Exception> onError) {
            // Execute error handler
            onError.accept(exception);
            return this;
        }
    }
}
