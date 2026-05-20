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

import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;

/**
 * Abstract base class for event claiming strategies in different deployment modes.
 * Prevents duplicate build processing when multiple Jenkins instances receive
 * the same Gerrit event in HA/HS deployments.
 *
 * <p>This abstract class enables switching between local (standalone) and coordination modes:</p>
 * <ul>
 *   <li><b>Local mode:</b> Always claims events - no coordination needed</li>
 *   <li><b>Hazelcast mode:</b> Uses distributed coordination to ensure only one replica processes each event</li>
 *   <li><b>Future modes:</b> Redis, JDBC, etc.</li>
 * </ul>
 *
 * <h2>Fluent API Pattern:</h2>
 * <p>Uses fluent API pattern similar to Jenkins Queue and ACL for automatic resource management:</p>
 * <pre>
 * claimStrategy.withClaim(event, () -&gt; {
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
 * <h2>Benefits:</h2>
 * <ul>
 *   <li>Automatic claim lifecycle management (no manual release needed)</li>
 *   <li>No risk of forgetting to release claim in finally blocks</li>
 *   <li>Cleaner integration code</li>
 *   <li>Built-in error handling</li>
 *   <li>Follows Jenkins patterns (Queue, ACL)</li>
 * </ul>
 *
 * <p>Implementations are discovered via the Extension Points pattern using
 * {@link CoordinationModeProvider}.</p>
 *
 * <p><strong>Design Note:</strong> This is an abstract class (not an interface) to allow
 * adding concrete helper methods in the future without breaking existing implementations.</p>
 *
 * @see CoordinationModeProvider
 */
public abstract class EventClaimStrategy {

    /**
     * Attempts to claim an event and execute the given action if successful.
     * Automatically handles claim lifecycle (acquire + release).
     *
     * <p>The claim is automatically released after the action executes (success or failure),
     * so implementations do not need manual cleanup code.</p>
     *
     * <p><b>Usage Example:</b></p>
     * <pre>
     * claimStrategy.withClaim(event, () -&gt; {
     *     // This code runs only if claim was acquired
     *     // Claim is automatically released after this block
     *     processEvent(event);
     * })
     * .notClaimed(() -&gt; {
     *     // Optional: runs if claim was not acquired
     *     logger.debug("Another instance is processing this event");
     * })
     * .onError((ex) -&gt; {
     *     // Optional: runs if an exception occurs during processing
     *     logger.error("Failed to process event", ex);
     * });
     * </pre>
     *
     * @param event the Gerrit event to claim
     * @param claimed action to execute if claim succeeds (runs with claim held, auto-released)
     * @return ClaimResult for chaining notClaimed/onError handlers
     */
    @NonNull
    public abstract ClaimResult withClaim(@NonNull GerritTriggeredEvent event, @NonNull Runnable claimed);

    /**
     * Result of a claim attempt, allows chaining handlers for not-claimed and error cases.
     *
     * <p>This interface supports a fluent API pattern for handling different outcomes
     * of the claim attempt.</p>
     */
    public interface ClaimResult {
        /**
         * Handler called if the claim was not acquired (another instance already processing).
         *
         * <p>This is optional - if not specified, nothing happens when the claim fails.</p>
         *
         * @param notClaimed action to execute if claim failed
         * @return this for chaining
         */
        @NonNull
        ClaimResult notClaimed(@NonNull Runnable notClaimed);

        /**
         * Handler called if an exception occurs during claim processing.
         *
         * <p>This is optional - if not specified, exceptions are silently ignored
         * (though they may be logged by the implementation).</p>
         *
         * @param onError action to execute on error (receives the exception)
         * @return this for chaining
         */
        @NonNull
        ClaimResult onError(@NonNull Consumer<Exception> onError);
    }
}
