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

/**
 * The result of a {@link MissedEventsCoordinationStrategy#coordinateCatchUp} attempt.
 */
public enum MissedEventsCatchUpOutcome {

    /**
     * The catch-up fetch was performed and the shared watermark was advanced.
     */
    PERFORMED,

    /**
     * The shared watermark was already at or ahead of the candidate timestamp, so no fetch was
     * needed - either another JVM already performed the catch-up, or this reconnect has nothing
     * new to cover.
     */
    ALREADY_CAUGHT_UP,

    /**
     * The coordination lock could not be acquired within the configured wait time. The caller
     * must NOT independently perform the catch-up in this case - that would defeat the mutual
     * exclusion the lock exists to provide. The next reconnect will retry.
     */
    LOCK_TIMEOUT,

    /**
     * The lock was acquired but the catch-up action itself failed - either it threw (an
     * unanticipated error) or it returned a result with {@link
     * MissedEventsCatchUpResult#fetchFailed()} set (the expected, routine case: the fetch itself
     * could not be completed, e.g. events-log was unreachable or returned a non-success response -
     * see that field's own javadoc for why this is a plain result rather than a thrown exception).
     * Either way, the shared watermark is left unchanged so the next reconnect retries.
     */
    FAILED
}
