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

import org.junit.Test;

import java.io.IOException;
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
    private static final long WATERMARK_5000 = 5000L;
    private static final long WATERMARK_LARGE = 2000000000000L;
    private static final long WATERMARK_SMALL = 1000000000000L;
    private static final long RACE_SLEEP_MILLIS = 200L;
    private static final long FUTURE_GET_TIMEOUT_SECONDS = 10L;

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
            return WATERMARK_1000;
        }, () -> { });

        assertEquals(MissedEventsCatchUpOutcome.PERFORMED, outcome);
        assertEquals(1, callCount.get());
    }

    /**
     * Given a watermark already at or ahead of the candidate
     * When coordinateCatchUp is called
     * Then the catch-up action is not invoked, and this holds for two different manager
     * instances sharing this one strategy - reproducing how every {@code
     * GerritMissedEventsPlaybackManager} for the same server would share the JVM-wide strategy.
     */
    @Test
    public void testSkipsCatchUpWhenAlreadyCoveredByWatermark() {
        LocalMissedEventsCoordinationStrategy strategy = new LocalMissedEventsCoordinationStrategy();
        AtomicInteger callCount = new AtomicInteger();

        strategy.coordinateCatchUp("server-b", WATERMARK_2000, lowerBound -> {
            callCount.incrementAndGet();
            return WATERMARK_2000;
        }, () -> { });

        MissedEventsCatchUpOutcome secondOutcome = strategy.coordinateCatchUp("server-b", WATERMARK_1500,
                lowerBound -> {
                    callCount.incrementAndGet();
                    return WATERMARK_1500;
                }, () -> { });

        assertEquals(MissedEventsCatchUpOutcome.ALREADY_CAUGHT_UP, secondOutcome);
        assertEquals("catch-up action must only have run once", 1, callCount.get());
    }

    /**
     * Given two threads racing to catch up the same server
     * When both call coordinateCatchUp concurrently
     * Then exactly one of them actually performs the fetch - this is the core guarantee that
     * replaces the old static {@code previousTimeSlice} field.
     * @throws Exception if the test threads fail unexpectedly.
     */
    @Test
    public void testConcurrentCatchUpRaceIsResolvedToExactlyOnePerformer() throws Exception {
        LocalMissedEventsCoordinationStrategy strategy = new LocalMissedEventsCoordinationStrategy();
        AtomicInteger callCount = new AtomicInteger();
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MissedEventsCatchUpOutcome> first = executor.submit(() -> {
                startLatch.await();
                return strategy.coordinateCatchUp("server-c", WATERMARK_5000, lowerBound -> {
                    callCount.incrementAndGet();
                    sleepMillis(RACE_SLEEP_MILLIS);
                    return WATERMARK_5000;
                }, () -> { });
            });
            Future<MissedEventsCatchUpOutcome> second = executor.submit(() -> {
                startLatch.await();
                return strategy.coordinateCatchUp("server-c", WATERMARK_5000, lowerBound -> {
                    callCount.incrementAndGet();
                    return WATERMARK_5000;
                }, () -> { });
            });
            startLatch.countDown();

            MissedEventsCatchUpOutcome firstOutcome = first.get(FUTURE_GET_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            MissedEventsCatchUpOutcome secondOutcome = second.get(FUTURE_GET_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertEquals(1, callCount.get());
            assertTrue("exactly one thread should have performed the catch-up",
                    (firstOutcome == MissedEventsCatchUpOutcome.PERFORMED)
                            != (secondOutcome == MissedEventsCatchUpOutcome.PERFORMED));
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
                    return WATERMARK_3000;
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

        strategy.coordinateCatchUp("server-f", WATERMARK_1000, lowerBound -> WATERMARK_1000,
                maintenanceRuns::incrementAndGet);
        strategy.coordinateCatchUp("server-f", WATERMARK_500, lowerBound -> WATERMARK_500,
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

        MissedEventsCatchUpOutcome outcomeA = strategy.coordinateCatchUp("server-g", WATERMARK_LARGE, lowerBound -> {
            callCountA.incrementAndGet();
            return WATERMARK_LARGE;
        }, () -> { });
        MissedEventsCatchUpOutcome outcomeB = strategy.coordinateCatchUp("server-h", WATERMARK_SMALL, lowerBound -> {
            callCountB.incrementAndGet();
            return WATERMARK_SMALL;
        }, () -> { });

        assertEquals(MissedEventsCatchUpOutcome.PERFORMED, outcomeA);
        assertEquals(MissedEventsCatchUpOutcome.PERFORMED, outcomeB);
        assertEquals(1, callCountA.get());
        assertEquals(1, callCountB.get());
    }
}
