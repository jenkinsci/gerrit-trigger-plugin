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

/**
 * Abstract base class for notification claiming strategies in different deployment modes.
 * Prevents duplicate notification sending when multiple Jenkins instances need to send
 * feedback to Gerrit in HA/HS deployments.
 *
 * <p>This abstract class enables switching between local (standalone) and coordination modes:</p>
 * <ul>
 *   <li><b>Local mode:</b> Always claims notification rights - no coordination needed</li>
 *   <li><b>Hazelcast mode:</b> Uses distributed coordination to ensure only one replica sends feedback</li>
 *   <li><b>Future modes:</b> Redis, JDBC, etc.</li>
 * </ul>
 *
 * <h2>Fluent API Pattern:</h2>
 * <p>Uses fluent API pattern similar to Jenkins Queue and ACL for automatic resource management:</p>
 * <pre>
 * claimStrategy.withClaim(event, () -&gt; {
 *     sendNotificationToGerrit(event, buildResult);
 * })
 * .notClaimed(() -&gt; {
 *     logger.debug("Another replica sent notification, skipping");
 * })
 * .onError((ex) -&gt; {
 *     logger.error("Failed to send notification", ex);
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
 * @see CoordinationModeProvider
 */
public abstract class NotificationClaimStrategy {

    /**
     * Attempts to claim the right to send notification and execute the given action if successful.
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
     *     sendNotificationToGerrit(event, buildResult);
     * })
     * .notClaimed(() -&gt; {
     *     // Optional: runs if claim was not acquired
     *     logger.debug("Another instance is sending notification");
     * })
     * .onError((ex) -&gt; {
     *     // Optional: runs if an exception occurs during processing
     *     logger.error("Failed to send notification", ex);
     * });
     * </pre>
     *
     * @param event the Gerrit event to claim notification rights for
     * @param claimed action to execute if claim succeeds (runs with claim held, auto-released)
     * @return ClaimResult for chaining notClaimed/onError handlers
     * @see ClaimResults for shared result implementations
     */
    @NonNull
    public abstract ClaimResult withClaim(@NonNull GerritTriggeredEvent event, @NonNull Runnable claimed);
}
