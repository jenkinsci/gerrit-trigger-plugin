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

import java.util.OptionalLong;

/**
 * The result of one {@link MissedEventsCatchUpAction#fetchAndTrigger} invocation.
 *
 * @param newWatermark the epoch-millis timestamp of the latest {@code eventCreatedOn} actually
 *         seen in this fetch's own event list, or empty if the fetch found nothing - never the
 *         wall-clock instant the fetch started. The events-log plugin has its own indexing
 *         latency: a fetch that races ahead of it can get back a response that doesn't yet
 *         contain the very event it exists to catch up on. Advancing the watermark to "now"
 *         regardless would treat that incomplete response as proof of full coverage and
 *         permanently skip the gap - if the missed event was never delivered live either (e.g. it
 *         landed during a total outage), no later reconnect would ever look there again.
 *         Advancing only to what was actually observed means an empty/incomplete response leaves
 *         the watermark exactly where it was, so the next catch-up attempt queries the same
 *         still-uncovered range again instead of silently trusting it away.
 * @param triggeredCount how many of the fetched events were genuinely new - not already known to
 *         have been triggered, by this JVM or a peer's own concurrent catch-up - and were
 *         actually handed to {@code server.triggerEvent(evt)} by this call.
 *         {@link MissedEventsCoordinationStrategy#coordinateCatchUp} uses this, not fetch-time
 *         alone, to decide between {@link MissedEventsCatchUpOutcome#PERFORMED} and
 *         {@link MissedEventsCatchUpOutcome#ALREADY_CAUGHT_UP}: two JVMs can legitimately query an
 *         overlapping window when one serializes in via the coordination lock right behind the
 *         other - that alone is not a bug - but only whichever JVM(s) actually deliver at least
 *         one not-already-claimed event should report {@code PERFORMED}.
 * @param fetchFailed true if the underlying fetch could not be completed at all (e.g. the
 *         events-log plugin was unreachable, or returned a non-success response) - as opposed to
 *         completing and genuinely finding nothing. {@link MissedEventsCoordinationStrategy#coordinateCatchUp}
 *         must report {@link MissedEventsCatchUpOutcome#FAILED} for this, not {@link
 *         MissedEventsCatchUpOutcome#ALREADY_CAUGHT_UP} - the two are indistinguishable from {@code
 *         triggeredCount} alone (both are 0), but mean opposite things: one is proof the gap is
 *         covered, the other is proof it was never actually checked. Deliberately a plain field
 *         rather than a thrown exception - the underlying fetch failure is a routine, expected
 *         condition (a network blip, Gerrit mid-restart), not an exceptional one, and modeling it
 *         as an exception would mean either a stack trace on every occurrence or throwing away
 *         that detail to keep logs quiet; see {@code
 *         GerritMissedEventsPlaybackManager#getEventsFromEventsLogPlugin}'s own reasoning. Defaults
 *         to {@code false} via the two-argument constructor for every ordinary (non-failed) result.
 */
public record MissedEventsCatchUpResult(OptionalLong newWatermark, int triggeredCount, boolean fetchFailed) {

    /**
     * Convenience constructor for an ordinary, non-failed result - {@code fetchFailed} defaults to
     * {@code false}. Use the three-argument canonical constructor directly, with {@code
     * fetchFailed=true}, when the fetch itself could not be completed.
     *
     * @param newWatermark see the canonical constructor.
     * @param triggeredCount see the canonical constructor.
     */
    public MissedEventsCatchUpResult(OptionalLong newWatermark, int triggeredCount) {
        this(newWatermark, triggeredCount, false);
    }
}
