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

import edu.umd.cs.findbugs.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Shared implementations of ClaimResult for use by all claim strategy implementations.
 * <p>
 * This utility class eliminates code duplication across local and distributed claim strategies
 * by providing reusable result implementations. All strategies (NotificationClaimStrategy and
 * EventClaimStrategy, both local and Hazelcast modes) can use these shared implementations.
 * <p>
 * <strong>Usage:</strong>
 * <pre>
 * // In any claim strategy implementation:
 * return ClaimResults.success();
 * return ClaimResults.notClaimed();
 * return ClaimResults.failed(exception);
 * </pre>
 *
 * @see ClaimResult
 */
public final class ClaimResults {

    private static final Logger logger = LoggerFactory.getLogger(ClaimResults.class);

    /**
     * Private constructor - utility class with only static methods.
     */
    private ClaimResults() {
        // Prevent instantiation
    }

    /**
     * Creates a successful claim result.
     * Use when the claim was acquired and the action executed successfully.
     *
     * @return a ClaimResult indicating success
     */
    @NonNull
    public static ClaimResult success() {
        return SuccessfulClaim.INSTANCE;
    }

    /**
     * Creates a not-claimed result.
     * Use when the claim was not acquired (another instance already claimed it).
     *
     * @return a ClaimResult indicating not claimed
     */
    @NonNull
    public static ClaimResult notClaimed() {
        return new NotClaimedResult();
    }

    /**
     * Creates a failed claim result.
     * Use when an exception occurred during claim processing.
     *
     * @param exception the exception that occurred
     * @return a ClaimResult indicating failure
     */
    @NonNull
    public static ClaimResult failed(@NonNull Exception exception) {
        return new FailedClaim(exception);
    }

    /**
     * Successful claim result - the claim was acquired and action executed.
     * Singleton pattern for memory efficiency.
     */
    private static class SuccessfulClaim implements ClaimResult {

        static final SuccessfulClaim INSTANCE = new SuccessfulClaim();

        @Override
        @NonNull
        public ClaimResult notClaimed(@NonNull Runnable notClaimed) {
            // Claim was successful, don't run notClaimed handler
            return this;
        }

        @Override
        @NonNull
        public ClaimResult onError(@NonNull Consumer<Exception> onError) {
            // No error occurred
            return this;
        }
    }

    /**
     * Not claimed result - another instance already claimed it.
     */
    private static class NotClaimedResult implements ClaimResult {

        @Override
        @NonNull
        public ClaimResult notClaimed(@NonNull Runnable notClaimed) {
            // Run the notClaimed handler
            try {
                notClaimed.run();
            } catch (Exception e) {
                logger.error("Error in notClaimed handler", e);
            }
            return this;
        }

        @Override
        @NonNull
        public ClaimResult onError(@NonNull Consumer<Exception> onError) {
            // No error occurred (just not claimed)
            return this;
        }
    }

    /**
     * Failed claim result - an exception occurred during processing.
     */
    private static class FailedClaim implements ClaimResult {

        private final Exception exception;

        /**
         * Constructor.
         *
         * @param exception the exception that occurred
         */
        FailedClaim(@NonNull Exception exception) {
            this.exception = exception;
        }

        @Override
        @NonNull
        public ClaimResult notClaimed(@NonNull Runnable notClaimed) {
            // Don't run notClaimed - this was an error, not a "not claimed" situation
            return this;
        }

        @Override
        @NonNull
        public ClaimResult onError(@NonNull Consumer<Exception> onError) {
            // Run the error handler
            try {
                onError.accept(exception);
            } catch (Exception e) {
                logger.error("Error in error handler", e);
            }
            return this;
        }
    }
}
