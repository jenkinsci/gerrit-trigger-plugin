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
package com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.MissedEventsCatchUpOutcome;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
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
 * Tests for {@link HazelcastMissedEventsCoordinationStrategy}.
 *
 * <p>Runs against an embedded Hazelcast instance via {@link HazelcastTestRule}. Like other
 * Hazelcast integration tests in this project, it is skipped unless run with
 * {@code -Ptest-hazelcast} (see {@link HazelcastTestRule}).</p>
 */
public class HazelcastMissedEventsCoordinationStrategyTest {

    /**
     * Hazelcast test lifecycle rule; skips this test unless run with {@code -Ptest-hazelcast}.
     * A single class-level instance shares one embedded Hazelcast instance across all test
     * methods in this class - reinitializing per test method was observed to leave {@link
     * HazelcastInstanceProvider} without a registered instance on the second and later methods.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 1 LINES. REASON: HazelcastTestRule.
    @ClassRule
    public static final HazelcastTestRule HAZELCAST_TEST_RULE = new HazelcastTestRule();

    private static final long WATERMARK_1000 = 1000L;
    private static final long WATERMARK_500 = 500L;
    private static final long WATERMARK_1500 = 1500L;
    private static final long WATERMARK_2000 = 2000L;
    private static final long WATERMARK_3000 = 3000L;
    private static final long WATERMARK_5000 = 5000L;
    private static final long RACE_SLEEP_MILLIS = 200L;
    private static final long FUTURE_GET_TIMEOUT_SECONDS = 10L;
    private static final String SHORT_LOCK_WAIT_TIMEOUT_SECONDS = "2";

    private String originalWaitTimeout;
    private String originalLease;

    /**
     * Shortens the lock wait timeout so timeout-path tests don't take 30 real seconds, and
     * clears the watermark map so tests don't see state from a previous run.
     */
    @Before
    public void setUp() {
        originalWaitTimeout = System.getProperty(HazelcastMissedEventsCoordinationStrategy.LOCK_WAIT_TIMEOUT_PROPERTY);
        originalLease = System.getProperty(HazelcastMissedEventsCoordinationStrategy.LOCK_LEASE_PROPERTY);
        System.setProperty(HazelcastMissedEventsCoordinationStrategy.LOCK_WAIT_TIMEOUT_PROPERTY,
                SHORT_LOCK_WAIT_TIMEOUT_SECONDS);
        clearWatermarkMap();
        clearInstanceFreshnessMap();
    }

    @After
    public void tearDown() {
        restoreProperty(HazelcastMissedEventsCoordinationStrategy.LOCK_WAIT_TIMEOUT_PROPERTY, originalWaitTimeout);
        restoreProperty(HazelcastMissedEventsCoordinationStrategy.LOCK_LEASE_PROPERTY, originalLease);
        clearWatermarkMap();
        clearInstanceFreshnessMap();
    }

    private void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

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

    private void clearWatermarkMap() {
        if (HazelcastInstanceProvider.isInitialized()) {
            HazelcastInstanceProvider.getInstance()
                    .getMap(HazelcastMissedEventsCoordinationStrategy.WATERMARK_MAP_NAME)
                    .clear();
        }
    }

    private void clearInstanceFreshnessMap() {
        if (HazelcastInstanceProvider.isInitialized()) {
            HazelcastInstanceProvider.getInstance()
                    .getMap(HazelcastMissedEventsCoordinationStrategy.INSTANCE_FRESHNESS_MAP_NAME)
                    .clear();
        }
    }

    private HazelcastMissedEventsCoordinationStrategy newStrategy() {
        HazelcastInstance instance = HazelcastInstanceProvider.getInstance();
        return new HazelcastMissedEventsCoordinationStrategy(instance);
    }

    /**
     * Given a server with no prior watermark
     * When coordinateCatchUp is called
     * Then the catch-up action runs and the watermark advances.
     */
    @Test
    public void testPerformsCatchUpWhenNoWatermarkYet() {
        HazelcastMissedEventsCoordinationStrategy strategy = newStrategy();
        AtomicInteger callCount = new AtomicInteger();

        MissedEventsCatchUpOutcome outcome = strategy.coordinateCatchUp(
                "server-a", WATERMARK_1000,
                lowerBound -> {
                    callCount.incrementAndGet();
                    return WATERMARK_1000;
                },
                () -> { });

        assertEquals(MissedEventsCatchUpOutcome.PERFORMED, outcome);
        assertEquals(1, callCount.get());
    }

    /**
     * Given a watermark already at or ahead of the candidate
     * When coordinateCatchUp is called
     * Then the catch-up action is not invoked.
     */
    @Test
    public void testSkipsCatchUpWhenAlreadyCoveredByWatermark() {
        HazelcastMissedEventsCoordinationStrategy strategy = newStrategy();
        AtomicInteger callCount = new AtomicInteger();

        strategy.coordinateCatchUp("server-b", WATERMARK_2000, lowerBound -> {
            callCount.incrementAndGet();
            return WATERMARK_2000;
        }, () -> { });

        MissedEventsCatchUpOutcome secondOutcome = strategy.coordinateCatchUp(
                "server-b", WATERMARK_1500,
                lowerBound -> {
                    callCount.incrementAndGet();
                    return WATERMARK_1500;
                },
                () -> { });

        assertEquals(MissedEventsCatchUpOutcome.ALREADY_CAUGHT_UP, secondOutcome);
        assertEquals("catch-up action must only have run once", 1, callCount.get());
    }

    /**
     * Given two threads racing to catch up the same server
     * When both call coordinateCatchUp concurrently
     * Then exactly one of them actually performs the fetch.
     * @throws Exception if the test threads fail unexpectedly.
     */
    @Test
    public void testConcurrentCatchUpRaceIsResolvedToExactlyOnePerformer() throws Exception {
        HazelcastMissedEventsCoordinationStrategy strategy = newStrategy();
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
     * Given another party is already holding the lock for this server
     * When coordinateCatchUp is called and the wait timeout elapses
     * Then LOCK_TIMEOUT is returned and the catch-up action is never invoked.
     *
     * <p>The lock must be acquired on a separate thread: Hazelcast's key-based {@code IMap} lock
     * is reentrant per (instance, thread), so acquiring it on the same thread that later calls
     * {@code coordinateCatchUp} would let that call re-enter its own lock instead of timing out.
     * @throws Exception if the test threads fail unexpectedly.
     */
    @Test
    public void testReturnsLockTimeoutWithoutFetchingWhenLockHeldElsewhere() throws Exception {
        HazelcastInstance instance = HazelcastInstanceProvider.getInstance();
        IMap<String, Long> map = instance.getMap(HazelcastMissedEventsCoordinationStrategy.WATERMARK_MAP_NAME);
        ExecutorService lockHolderExecutor = Executors.newSingleThreadExecutor();
        try {
            lockHolderExecutor.submit(() -> map.lock("server-d")).get(FUTURE_GET_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            HazelcastMissedEventsCoordinationStrategy strategy = newStrategy();
            AtomicInteger callCount = new AtomicInteger();

            MissedEventsCatchUpOutcome outcome = strategy.coordinateCatchUp("server-d", WATERMARK_1000, lowerBound -> {
                callCount.incrementAndGet();
                return WATERMARK_1000;
            }, () -> { });

            assertEquals(MissedEventsCatchUpOutcome.LOCK_TIMEOUT, outcome);
            assertEquals("must not fetch when the lock could not be acquired", 0, callCount.get());
        } finally {
            map.forceUnlock("server-d");
            lockHolderExecutor.shutdownNow();
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
        HazelcastMissedEventsCoordinationStrategy strategy = newStrategy();

        MissedEventsCatchUpOutcome failedOutcome = strategy.coordinateCatchUp("server-e", WATERMARK_3000,
                lowerBound -> {
                    throw new IOException("simulated failure");
                }, () -> { });
        assertEquals(MissedEventsCatchUpOutcome.FAILED, failedOutcome);

        AtomicInteger callCount = new AtomicInteger();
        MissedEventsCatchUpOutcome retryOutcome = strategy.coordinateCatchUp("server-e", WATERMARK_3000, lowerBound -> {
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
        HazelcastMissedEventsCoordinationStrategy strategy = newStrategy();
        AtomicInteger maintenanceRuns = new AtomicInteger();

        strategy.coordinateCatchUp("server-f", WATERMARK_1000, lowerBound -> WATERMARK_1000,
                maintenanceRuns::incrementAndGet);
        strategy.coordinateCatchUp("server-f", WATERMARK_500, lowerBound -> WATERMARK_500,
                maintenanceRuns::incrementAndGet);

        assertEquals(2, maintenanceRuns.get());
    }

    /**
     * Given no instance has published a freshness value for a server
     * When getSharedInstanceFreshness is called
     * Then it returns empty, not a default of 0 or an exception.
     */
    @Test
    public void testSharedInstanceFreshnessIsEmptyWhenNothingPublished() {
        HazelcastMissedEventsCoordinationStrategy strategy = newStrategy();

        assertEquals(OptionalLong.empty(), strategy.getSharedInstanceFreshness("server-g"));
    }

    /**
     * Given one instance publishes a freshness value
     * When another instance (a separate strategy object backed by the same cluster) reads it
     * Then it sees the published value - this is the cross-instance visibility the whole signal
     * exists to provide.
     */
    @Test
    public void testPublishedFreshnessIsVisibleAcrossInstances() {
        HazelcastMissedEventsCoordinationStrategy publisher = newStrategy();
        HazelcastMissedEventsCoordinationStrategy reader = newStrategy();

        publisher.publishInstanceFreshness("server-h", WATERMARK_1000);

        assertEquals(OptionalLong.of(WATERMARK_1000), reader.getSharedInstanceFreshness("server-h"));
    }

    /**
     * Given a freshness value already published for a server
     * When a smaller value is published for the same server
     * Then the shared value stays at the larger one - this is a max-merge, not an overwrite, so a
     * momentarily-behind instance can never regress what peers already know.
     */
    @Test
    public void testPublishInstanceFreshnessNeverRegresses() {
        HazelcastMissedEventsCoordinationStrategy strategy = newStrategy();

        strategy.publishInstanceFreshness("server-i", WATERMARK_2000);
        strategy.publishInstanceFreshness("server-i", WATERMARK_500);

        assertEquals(OptionalLong.of(WATERMARK_2000), strategy.getSharedInstanceFreshness("server-i"));
    }

    /**
     * Given two different servers
     * When each publishes its own freshness value
     * Then each server's shared value is tracked independently, same guarantee as {@link
     * #testDifferentServersAreCoordinatedIndependently()} but for the freshness signal rather
     * than the watermark/lock.
     */
    @Test
    public void testInstanceFreshnessIsTrackedIndependentlyPerServer() {
        HazelcastMissedEventsCoordinationStrategy strategy = newStrategy();

        strategy.publishInstanceFreshness("server-j", WATERMARK_2000);
        strategy.publishInstanceFreshness("server-k", WATERMARK_500);

        assertEquals(OptionalLong.of(WATERMARK_2000), strategy.getSharedInstanceFreshness("server-j"));
        assertEquals(OptionalLong.of(WATERMARK_500), strategy.getSharedInstanceFreshness("server-k"));
    }
}
