/*
 *  The MIT License
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

/**
 * Coordinates missed-events playback catch-up across however many JVMs are sharing one
 * {@code JENKINS_HOME} for a given Gerrit server, so that at most one JVM performs the catch-up
 * fetch at a time and a later reconnect never re-requests an already-covered range.
 *
 * <p>Each configured Gerrit server already gets its own per-instance timestamp file (see
 * {@code InstanceTimestampStore} in the {@code playback} package); this strategy coordinates
 * what happens with the maximum timestamp computed across those files on reconnect:</p>
 * <ul>
 *   <li><b>Local mode:</b> a single JVM has nothing to coordinate with, so this reduces to a
 *       trivial in-process lock plus watermark.</li>
 *   <li><b>Hazelcast mode:</b> a distributed lock and shared watermark ensure only one replica
 *       fetches at a time, and every replica observes the same "already covered up to" point.</li>
 * </ul>
 *
 * <p>Implementations MUST guarantee both of the following, not just approximate them via lock
 * timing:</p>
 * <ol>
 *   <li><b>Mutual exclusion:</b> if the lock cannot be acquired, return {@link
 *       MissedEventsCatchUpOutcome#LOCK_TIMEOUT} and do not fetch - never fall back to an
 *       independent, uncoordinated fetch.</li>
 *   <li><b>No redundant re-fetching:</b> after acquiring the lock, compare against the shared
 *       watermark before fetching; if the watermark is already at or ahead of {@code
 *       candidateCatchUpFrom}, return {@link MissedEventsCatchUpOutcome#ALREADY_CAUGHT_UP}
 *       without fetching.</li>
 * </ol>
 *
 * <p>Implementations are discovered via the Extension Points pattern using {@link
 * CoordinationModeProvider}.</p>
 *
 * @see CoordinationModeProvider
 */
public abstract class MissedEventsCoordinationStrategy {

    /**
     * Coordinates a missed-events catch-up attempt for one Gerrit server.
     *
     * @param serverName the Gerrit server this catch-up is for; also the lock/watermark key
     *         namespace, since different servers have unrelated event timelines and must never
     *         share a watermark.
     * @param candidateCatchUpFrom the timestamp (epoch millis) this JVM computed as the point to
     *         catch up from, based on its own view of the per-instance timestamp files.
     * @param catchUpAction invoked, while the lock is held, only if the shared watermark is
     *         still behind {@code candidateCatchUpFrom}.
     * @param maintenanceAction invoked while still holding the same lock, after the catch-up
     *         decision either way, so maintenance work (e.g. pruning stale instance files) never
     *         races a concurrent catch-up attempt.
     * @return the outcome of this attempt.
     */
    @NonNull
    public abstract MissedEventsCatchUpOutcome coordinateCatchUp(
            @NonNull String serverName,
            long candidateCatchUpFrom,
            @NonNull MissedEventsCatchUpAction catchUpAction,
            @NonNull Runnable maintenanceAction);
}
