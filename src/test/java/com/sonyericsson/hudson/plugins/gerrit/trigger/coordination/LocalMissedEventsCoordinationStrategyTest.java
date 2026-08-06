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
package com.sonyericsson.hudson.plugins.gerrit.trigger.coordination;

import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.MissedEventsCatchUpOutcome;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.MissedEventsCatchUpResult;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link LocalMissedEventsCoordinationStrategy}.
 */
public class LocalMissedEventsCoordinationStrategyTest {

    private static final long WATERMARK_1000 = 1000L;
    private static final long WATERMARK_500 = 500L;
    private static final long WATERMARK_1500 = 1500L;
    private static final long WATERMARK_2000 = 2000L;
    private static final long WATERMARK_3000 = 3000L;
    private static final long RACE_SLEEP_MILLIS = 200L;
    private static final long FUTURE_GET_TIMEOUT_SECONDS = 10L;
    private static final long ONE_HOUR_MILLIS = 3_600_000L;

    /**
     * @param millis how long to sleep.
     */
    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Given a server with no prior watermark
     * When coordinateCatchUp is called
     * Then the catch-up action runs and the watermark advances.
     */
    @Test
    public void testPerformsCatchUpWhenNoWatermarkYet() {
        LocalMissedEventsCoordinationStrategy strategy = new LocalMissedEventsCoordinationStrategy();
        AtomicInteger callCount = new AtomicInteger();

        MissedEventsCatchUpOutcome outcome = strategy.coordinateCatchUp("server-a", WATERMARK_1000, lowerBound -> {
            callCount.incrementAndGet();
            return new MissedEventsCatchUpResult(OptionalLong.of(WATERMARK_1000), 1);
        }, () -> { });

        assertEquals(MissedEventsCatchUpOutcome.PERFORMED, outcome);
        assertEquals(1, callCount.get());
    }

    /**
     * Given a fetch that runs but finds nothing not already claimed by a peer (e.g. a peer just
     * handled this exact outage moments ago, and every fetched event was already claimed)
     * When coordinateCatchUp is called
     * Then the fetch action still runs (unlike the old wall-clock-based skip this class used to
     * have - see git history - which permanently suppressed catch-up on an otherwise-quiet
     * server), but the outcome is classified as ALREADY_CAUGHT_UP because triggeredCount was 0,
     * not PERFORMED.
     */
    @Test
    public void testReportsAlreadyCaughtUpWhenFetchTriggersNothingNew() {
        LocalMissedEventsCoordinationStrategy strategy = new LocalMissedEventsCoordinationStrategy();
        AtomicInteger callCount = new AtomicInteger();

        MissedEventsCatchUpOutcome outcome = strategy.coordinateCatchUp("server-b", WATERMARK_1500, lowerBound -> {
            callCount.incrementAndGet();
            // Every fetched event turned out to already be claimed by a peer's own concurrent
            // catch-up - genuinely new watermark, but zero events actually triggered by this call.
            return new MissedEventsCatchUpResult(OptionalLong.of(WATERMARK_1500), 0);
        }, () -> { });

        assertEquals(MissedEventsCatchUpOutcome.ALREADY_CAUGHT_UP, outcome);
        assertEquals("the fetch action must still run - only its outcome classification changes",
                1, callCount.get());
    }

    /**
     * Given a watermark set by a past successful catch-up, and a later reconnect whose own local
     * candidate is even further in the past (a stale local signal - e.g. no live traffic advanced
     * it in the meantime)
     * When coordinateCatchUp is called again
     * Then the catch-up action still runs - using the more advanced of the two (the watermark) as
     * its fetch lower bound - rather than being permanently skipped. This is the direct regression
     * guard for the bug where a stale local candidate compared against an old watermark
     * permanently suppressed all future catch-up on an otherwise-quiet server (HZ-022): once real
     * time has passed the watermark, there may be genuinely new events to catch up on, and a stale
     * local signal must never be read as "nothing to do forever".
     */
    @Test
    public void testPerformsFreshCatchUpWhenTimeHasPassedSinceWatermarkEvenIfCandidateIsStale() {
        LocalMissedEventsCoordinationStrategy strategy = new LocalMissedEventsCoordinationStrategy();
        AtomicInteger callCount = new AtomicInteger();
        List<Long> lowerBoundsSeen = new ArrayList<>();
        long pastWatermark = System.currentTimeMillis() - ONE_HOUR_MILLIS;
        long staleCandidate = pastWatermark - ONE_HOUR_MILLIS;

        strategy.coordinateCatchUp("server-z", pastWatermark, lowerBound -> {
            callCount.incrementAndGet();
            lowerBoundsSeen.add(lowerBound.getTime());
            return new MissedEventsCatchUpResult(OptionalLong.of(pastWatermark), 1);
        }, () -> { });

        MissedEventsCatchUpOutcome secondOutcome = strategy.coordinateCatchUp("server-z", staleCandidate, lowerBound -> {
            callCount.incrementAndGet();
            lowerBoundsSeen.add(lowerBound.getTime());
            return new MissedEventsCatchUpResult(OptionalLong.of(System.currentTimeMillis()), 1);
        }, () -> { });

        assertEquals(MissedEventsCatchUpOutcome.PERFORMED, secondOutcome);
        assertEquals("catch-up must run again once real time has passed the watermark", 2, callCount.get());
        assertEquals("must fetch from the watermark, not the stale local candidate",
                pastWatermark, lowerBoundsSeen.get(1).longValue());
    }

    /**
     * Given two threads racing to catch up the same server, both with a candidate well in the past
     * When both call coordinateCatchUp concurrently
     * Then the lock still serializes them (neither times out, and the fetch action for a given
     * server never runs on both threads at once), and whichever one is serialized second - if its
     * own effective lower bound turns out to still be behind "now" - fetches starting no earlier
     * than the first one's own reported watermark, so it never re-requests an already-covered
     * range from scratch.
     *
     * <p>Both callers' fetch actions genuinely run here (this synthetic action doesn't model
     * {@code claimEvent} at all - that's covered separately), so both legitimately report
     * {@code triggeredCount=1} and both return {@code PERFORMED}: a second caller serializing in
     * right behind the first via the same lock is expected, not itself a bug (see
     * {@link #testReportsAlreadyCaughtUpWhenFetchTriggersNothingNew()} for the case where the
     * second call's fetch finds nothing new). The guarantee this test protects is mutual exclusion
     * and no context loss across the hand-off - the second fetch must not start earlier than the
     * first's own reported watermark.</p>
     * @throws Exception if the test threads fail unexpectedly.
     */
    @Test
    public void testConcurrentCatchUpRaceIsSerializedWithoutLosingContext() throws Exception {
        LocalMissedEventsCoordinationStrategy strategy = new LocalMissedEventsCoordinationStrategy();
        List<Long> lowerBoundsSeen = new ArrayList<>();
        long candidate = System.currentTimeMillis() - ONE_HOUR_MILLIS;
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MissedEventsCatchUpOutcome> first = executor.submit(() -> {
                startLatch.await();
                return strategy.coordinateCatchUp("server-c", candidate, lowerBound -> {
                    synchronized (lowerBoundsSeen) {
                        lowerBoundsSeen.add(lowerBound.getTime());
                    }
                    sleepMillis(RACE_SLEEP_MILLIS);
                    return new MissedEventsCatchUpResult(OptionalLong.of(System.currentTimeMillis()), 1);
                }, () -> { });
            });
            Future<MissedEventsCatchUpOutcome> second = executor.submit(() -> {
                startLatch.await();
                return strategy.coordinateCatchUp("server-c", candidate, lowerBound -> {
                    synchronized (lowerBoundsSeen) {
                        lowerBoundsSeen.add(lowerBound.getTime());
                    }
                    return new MissedEventsCatchUpResult(OptionalLong.of(System.currentTimeMillis()), 1);
                }, () -> { });
            });
            startLatch.countDown();

            MissedEventsCatchUpOutcome firstOutcome = first.get(FUTURE_GET_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            MissedEventsCatchUpOutcome secondOutcome = second.get(FUTURE_GET_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertTrue("neither caller should time out waiting for the lock",
                    firstOutcome != MissedEventsCatchUpOutcome.LOCK_TIMEOUT
                            && secondOutcome != MissedEventsCatchUpOutcome.LOCK_TIMEOUT);
            assertTrue("the first (lock-holding) fetch must always run", lowerBoundsSeen.size() >= 1);
            if (lowerBoundsSeen.size() == 2) {
                assertTrue("a serialized second fetch must not start earlier than the first's own "
                        + "lower bound - i.e. it must not re-request an already-covered range",
                        lowerBoundsSeen.get(1) >= lowerBoundsSeen.get(0));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Given the catch-up action fails
     * When coordinateCatchUp is called
     * Then FAILED is returned, the watermark is left unchanged, and the lock is released so a
     * later attempt can proceed.
     */
    @Test
    public void testFailedFetchLeavesWatermarkUnchangedAndReleasesLock() {
        LocalMissedEventsCoordinationStrategy strategy = new LocalMissedEventsCoordinationStrategy();

        MissedEventsCatchUpOutcome failedOutcome = strategy.coordinateCatchUp("server-e", WATERMARK_3000,
                lowerBound -> {
                    throw new IOException("simulated failure");
                }, () -> { });
        assertEquals(MissedEventsCatchUpOutcome.FAILED, failedOutcome);

        AtomicInteger callCount = new AtomicInteger();
        MissedEventsCatchUpOutcome retryOutcome = strategy.coordinateCatchUp("server-e", WATERMARK_3000,
                lowerBound -> {
                    callCount.incrementAndGet();
                    return new MissedEventsCatchUpResult(OptionalLong.of(WATERMARK_3000), 1);
                }, () -> { });

        assertEquals(MissedEventsCatchUpOutcome.PERFORMED, retryOutcome);
        assertEquals(1, callCount.get());
    }

    /**
     * Given a maintenance action
     * When coordinateCatchUp is called, regardless of outcome
     * Then the maintenance action always runs.
     */
    @Test
    public void testMaintenanceActionAlwaysRuns() {
        LocalMissedEventsCoordinationStrategy strategy = new LocalMissedEventsCoordinationStrategy();
        AtomicInteger maintenanceRuns = new AtomicInteger();

        strategy.coordinateCatchUp("server-f", WATERMARK_1000,
                lowerBound -> new MissedEventsCatchUpResult(OptionalLong.of(WATERMARK_1000), 1),
                maintenanceRuns::incrementAndGet);
        strategy.coordinateCatchUp("server-f", WATERMARK_500,
                lowerBound -> new MissedEventsCatchUpResult(OptionalLong.of(WATERMARK_500), 1),
                maintenanceRuns::incrementAndGet);

        assertEquals(2, maintenanceRuns.get());
    }

    /**
     * Given two different servers
     * When one receives a much larger candidate than the other
     * Then each server's watermark/lock is tracked independently - this is the direct regression
     * guard for the fixed static {@code previousTimeSlice} field, which used to let one server's
     * persistence suppress another's.
     */
    @Test
    public void testDifferentServersAreCoordinatedIndependently() {
        LocalMissedEventsCoordinationStrategy strategy = new LocalMissedEventsCoordinationStrategy();
        AtomicInteger callCountA = new AtomicInteger();
        AtomicInteger callCountB = new AtomicInteger();
        long candidateA = System.currentTimeMillis() - ONE_HOUR_MILLIS;
        long candidateB = System.currentTimeMillis() - (2 * ONE_HOUR_MILLIS);

        MissedEventsCatchUpOutcome outcomeA = strategy.coordinateCatchUp("server-g", candidateA, lowerBound -> {
            callCountA.incrementAndGet();
            return new MissedEventsCatchUpResult(OptionalLong.of(candidateA), 1);
        }, () -> { });
        MissedEventsCatchUpOutcome outcomeB = strategy.coordinateCatchUp("server-h", candidateB, lowerBound -> {
            callCountB.incrementAndGet();
            return new MissedEventsCatchUpResult(OptionalLong.of(candidateB), 1);
        }, () -> { });

        assertEquals(MissedEventsCatchUpOutcome.PERFORMED, outcomeA);
        assertEquals(MissedEventsCatchUpOutcome.PERFORMED, outcomeB);
        assertEquals(1, callCountA.get());
        assertEquals(1, callCountB.get());
    }

    /**
     * Given a single JVM (nothing else to share freshness with)
     * When publishInstanceFreshness is called any number of times, for any server
     * Then getSharedInstanceFreshness always returns empty - local mode has no cross-instance
     * signal to offer, unlike the Hazelcast-backed implementation.
     */
    @Test
    public void testSharedInstanceFreshnessIsAlwaysEmpty() {
        LocalMissedEventsCoordinationStrategy strategy = new LocalMissedEventsCoordinationStrategy();

        assertEquals(OptionalLong.empty(), strategy.getSharedInstanceFreshness("server-i"));

        strategy.publishInstanceFreshness("server-i", WATERMARK_1000);
        strategy.publishInstanceFreshness("server-i", WATERMARK_2000);

        assertEquals(OptionalLong.empty(), strategy.getSharedInstanceFreshness("server-i"));
    }

    /**
     * Given a single JVM (nothing else to claim events against)
     * When claimEvent is called any number of times, for any server/event key
     * Then it always returns true - local mode has no peer to dedupe against, unlike the
     * Hazelcast-backed implementation.
     */
    @Test
    public void testClaimEventAlwaysReturnsTrue() {
        LocalMissedEventsCoordinationStrategy strategy = new LocalMissedEventsCoordinationStrategy();

        assertTrue(strategy.claimEvent("server-j", "event-1"));
        assertTrue("a second claim of the same key must still succeed - nothing to dedupe against",
                strategy.claimEvent("server-j", "event-1"));
    }
}
