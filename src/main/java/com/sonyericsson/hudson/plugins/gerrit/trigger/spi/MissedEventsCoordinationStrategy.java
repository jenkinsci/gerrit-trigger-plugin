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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.OptionalLong;
import java.util.function.LongConsumer;

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
 *   <li><b>Distributed mode:</b> a distributed lock and shared watermark ensure only one
 *       instance fetches at a time, and every instance observes the same "already covered up
 *       to" point.</li>
 * </ul>
 *
 * <p>Implementations MUST guarantee both of the following, not just approximate them via lock
 * timing:</p>
 * <ol>
 *   <li><b>Mutual exclusion:</b> if the lock cannot be acquired, return {@link
 *       MissedEventsCatchUpOutcome#LOCK_TIMEOUT} and do not fetch - never fall back to an
 *       independent, uncoordinated fetch.</li>
 *   <li><b>No redundant re-triggering:</b> a peer serializing in right behind this JVM via the
 *       same lock may legitimately query an overlapping window - the shared watermark only
 *       records how far a past fetch reached, not whether a new gap has opened since, so skipping
 *       the fetch outright whenever the watermark is at or ahead of {@code candidateCatchUpFrom}
 *       would (and once did, in an earlier distributed-mode implementation - see this project's
 *       own git history) permanently suppress catch-up after the first successful fetch on an
 *       otherwise-quiet server. Instead, always fetch from the more-advanced of the watermark and {@code
 *       candidateCatchUpFrom}, and classify the outcome from {@link
 *       MissedEventsCatchUpResult#triggeredCount()}: {@link MissedEventsCatchUpOutcome#PERFORMED}
 *       only if this call actually delivered at least one event not already claimed via {@link
 *       #claimEvent}, otherwise {@link MissedEventsCatchUpOutcome#ALREADY_CAUGHT_UP}.</li>
 * </ol>
 *
 * <p>Implementations are discovered via the Extension Points pattern using {@link
 * CoordinationModeProvider}.</p>
 *
 * @see CoordinationModeProvider
 */
public abstract class MissedEventsCoordinationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(MissedEventsCoordinationStrategy.class);

    /**
     * Shared "fetch, classify, advance watermark" implementation of {@link #coordinateCatchUp},
     * common to every implementation: only lock acquisition/release and the watermark's backing
     * storage are mode-specific, so subclasses read their own current watermark, call this while
     * still holding their lock, and store whatever watermark it hands back via {@code
     * watermarkWriter} - rather than re-implementing this logic themselves (see this class's own
     * "no redundant re-triggering" contract above, which this method is what actually enforces).
     *
     * @param serverName the Gerrit server this catch-up is for; used only for logging here.
     * @param currentWatermark the caller's own already-read current watermark for {@code
     *         serverName} (0 if none recorded yet).
     * @param candidateCatchUpFrom see {@link #coordinateCatchUp}.
     * @param catchUpAction see {@link #coordinateCatchUp}.
     * @param watermarkWriter invoked with the fetch's new watermark, if any, so the caller can
     *         store it in whatever map or field this coordination mode backs its watermark with.
     * @return the outcome of this attempt.
     */
    @NonNull
    protected final MissedEventsCatchUpOutcome performCatchUp(
            @NonNull String serverName,
            long currentWatermark,
            long candidateCatchUpFrom,
            @NonNull MissedEventsCatchUpAction catchUpAction,
            @NonNull LongConsumer watermarkWriter) {
        // The watermark only reflects how far a PAST catch-up reached, not whether a NEW gap has
        // opened since - gating on "watermark >= candidateCatchUpFrom" alone would permanently
        // suppress catch-up after the first successful fetch on an otherwise-quiet server (see
        // this class's own "no redundant re-triggering" contract above). Always fetch from the
        // more advanced of the two instead, and classify the outcome from the fetch's own
        // triggeredCount rather than pre-fetch timing.
        long effectiveLowerBound = Math.max(currentWatermark, candidateCatchUpFrom);
        try {
            MissedEventsCatchUpResult result = catchUpAction.fetchAndTrigger(new Date(effectiveLowerBound));
            // fetchFailed means the fetch itself could not be completed - never treat that the
            // same as a confirmed-empty result, or an outage during a reconnect's catch-up window
            // would look identical to "nothing was missed" and never be retried.
            if (result.fetchFailed()) {
                logger.warn("Missed-events catch-up fetch failed for server {}; leaving watermark unchanged.",
                        serverName);
                return MissedEventsCatchUpOutcome.FAILED;
            }
            // Empty means nothing to derive a watermark from, so leave it untouched rather than
            // advancing on an unproven basis.
            result.newWatermark().ifPresent(watermarkWriter);
            return result.triggeredCount() > 0
                    ? MissedEventsCatchUpOutcome.PERFORMED
                    : MissedEventsCatchUpOutcome.ALREADY_CAUGHT_UP;
        } catch (IOException e) {
            logger.error("Missed-events catch-up failed for server {}", serverName, e);
            return MissedEventsCatchUpOutcome.FAILED;
        }
    }

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

    /**
     * Returns this coordination mode's own current watermark for {@code serverName} - the same
     * value {@link #coordinateCatchUp} itself only ever advances to a point some fetch actually
     * observed events up to (see {@link MissedEventsCatchUpResult#newWatermark()}'s own contract),
     * never to a wall-clock instant or any other unproven stand-in.
     *
     * <p>Callers use this to learn what is genuinely safe to advertise as this instance's own
     * cross-instance freshness (see {@link #publishInstanceFreshness}) - an ordinary live event
     * proves only that this JVM is currently receiving events, not that an earlier, unrelated gap
     * from the same connection session was ever actually closed, so freshness meant for peers must
     * be driven by this proven watermark instead.</p>
     *
     * @param serverName the Gerrit server to look up.
     * @return the current watermark, or empty if {@link #coordinateCatchUp} has never advanced one
     *         for this server (nothing proven yet).
     */
    @NonNull
    public abstract OptionalLong getWatermark(@NonNull String serverName);

    /**
     * Publishes this JVM's own last-known-alive timestamp for {@code serverName} to whatever
     * shared, cross-instance freshness signal this coordination mode offers, so that other JVMs
     * reconnecting can see it without waiting on the slower, eventually-consistent per-instance
     * file scan ({@code InstanceTimestampStore#computeMaxTimestampAcrossInstances()}).
     *
     * <p>This is deliberately a separate, best-effort signal from {@link #coordinateCatchUp}'s
     * watermark: it only ever moves forward (a max-merge, not an overwrite) and carries no mutual
     * exclusion or lock semantics of its own - multiple JVMs publish to it concurrently and
     * freely. A coordination mode with only one JVM to coordinate with may implement this as a
     * no-op, since that JVM's own per-instance file already is its complete view.</p>
     *
     * <p>Returns the resulting shared value (this JVM's own {@code timestampMillis}, or a peer's
     * already-more-advanced one) rather than {@code void}, so a caller that just published can read
     * back the current cross-instance freshness without a second round trip via {@link
     * #getSharedInstanceFreshness} - the merge already had to look at the shared value to decide
     * whether to advance it, so handing that back here is free.</p>
     *
     * @param serverName the Gerrit server this timestamp is for.
     * @param timestampMillis this JVM's own last-known-alive timestamp (epoch millis) for {@code
     *         serverName}.
     * @return the shared freshness value for {@code serverName} after merging in {@code
     *         timestampMillis} - the more advanced of this call's own value and whatever was
     *         already shared.
     */
    public abstract long publishInstanceFreshness(@NonNull String serverName, long timestampMillis);

    /**
     * Returns the most advanced last-known-alive timestamp published by any JVM (including this
     * one) for {@code serverName} via {@link #publishInstanceFreshness}, or empty if this
     * coordination mode has nothing to share (no JVM has published yet, or this mode doesn't
     * support cross-instance sharing at all - e.g. local mode, or a freshly-reformed cluster with
     * no prior state).
     *
     * <p>Callers should treat an empty result as "no additional information available", falling
     * back to the per-instance file scan rather than treating it as "definitely nothing missed" -
     * this signal is a faster-path supplement to that scan, not a replacement for it, since - in
     * a topology where every JVM's coordination-mode member can be lost simultaneously (e.g. a
     * distributed-mode sidecar co-located with its own Jenkins replica) - this shared value can be
     * wiped clean by the very same outage the file-based fallback exists to survive.</p>
     *
     * @param serverName the Gerrit server to look up.
     * @return the shared freshness timestamp, or empty if none is known.
     */
    @NonNull
    public abstract OptionalLong getSharedInstanceFreshness(@NonNull String serverName);

    /**
     * Atomically claims {@code eventKey} for {@code serverName}, across every JVM sharing this
     * coordination mode, for a short dedup window - letting a catch-up fetch recognize an event a
     * peer's own concurrent catch-up already triggered, even though both fetches legitimately
     * queried an overlapping range from the same watermark (see {@link #coordinateCatchUp}'s
     * "no redundant re-triggering" contract). A per-JVM cache alone cannot do this: it only
     * dedupes a second fetch by the same instance, never a peer's.
     *
     * <p>Local mode, with only one JVM to coordinate with, may implement this as always returning
     * {@code true}: nothing to dedupe against, and the per-JVM {@code receivedEventCache} in
     * {@code GerritMissedEventsPlaybackManager} already handles same-JVM re-fetches.</p>
     *
     * @param serverName the Gerrit server this event belongs to; scopes the claim so unrelated
     *         servers can never collide on the same key.
     * @param eventKey a stable identifier for the specific event (e.g. its own {@code toString()}
     *         - already used as this event's display identity in this subsystem's own logging).
     * @return {@code true} if this call is the first to claim {@code eventKey} within the dedup
     *         window (the caller should trigger it), {@code false} if some other call - this JVM
     *         or a peer's - already claimed it first (the caller must not trigger it again).
     */
    public abstract boolean claimEvent(@NonNull String serverName, @NonNull String eventKey);
}
