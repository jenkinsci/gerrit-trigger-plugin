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
import java.util.function.Consumer;

/**
 * Result of a claim attempt, allows chaining handlers for not-claimed and error cases.
 * <p>
 * This interface supports a fluent API pattern for handling different outcomes
 * of claim attempts in both event and notification claiming strategies.
 * <p>
 * <strong>Usage Example:</strong>
 * <pre>
 * strategy.withClaim(event, () -&gt; {
 *     processEvent(event);
 * })
 * .notClaimed(() -&gt; {
 *     logger.debug("Event already claimed, skipping");
 * })
 * .onError((ex) -&gt; {
 *     logger.error("Error processing event", ex);
 * });
 * </pre>
 *
 * @see EventClaimStrategy
 * @see NotificationClaimStrategy
 * @see ClaimResults
 */
public interface ClaimResult {
    /**
     * Handler called if the claim was not acquired (another instance already processing).
     * <p>
     * This is optional - if not specified, nothing happens when the claim fails.
     *
     * @param notClaimed action to execute if claim failed
     * @return this for chaining
     */
    @NonNull
    ClaimResult notClaimed(@NonNull Runnable notClaimed);

    /**
     * Handler called if an exception occurs during claim processing.
     * <p>
     * This is optional - if not specified, exceptions are silently ignored
     * (though they may be logged by the implementation).
     *
     * @param onError action to execute on error (receives the exception)
     * @return this for chaining
     */
    @NonNull
    ClaimResult onError(@NonNull Consumer<Exception> onError);
}
