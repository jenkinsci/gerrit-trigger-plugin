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
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.MissedEventsCatchUpResult;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
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
    private static final long RACE_SLEEP_MILLIS = 200L;
    private static final long FUTURE_GET_TIMEOUT_SECONDS = 10L;
    private static final String SHORT_LOCK_WAIT_TIMEOUT_SECONDS = "2";
    private static final long ONE_HOUR_MILLIS = 3_600_000L;

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
        clearEventClaimMap();
    }

    @After
    public void tearDown() {
        restoreProperty(HazelcastMissedEventsCoordinationStrategy.LOCK_WAIT_TIMEOUT_PROPERTY, originalWaitTimeout);
        restoreProperty(HazelcastMissedEventsCoordinationStrategy.LOCK_LEASE_PROPERTY, originalLease);
        clearWatermarkMap();
        clearInstanceFreshnessMap();
        clearEventClaimMap();
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

    private void clearEventClaimMap() {
        if (HazelcastInstanceProvider.isInitialized()) {
            HazelcastInstanceProvider.getInstance()
                    .getMap(HazelcastMissedEventsCoordinationStrategy.EVENT_CLAIM_MAP_NAME)
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
                    return new MissedEventsCatchUpResult(OptionalLong.of(WATERMARK_1000), 1);
                },
                () -> { });

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
        HazelcastMissedEventsCoordinationStrategy strategy = newStrategy();
        AtomicInteger callCount = new AtomicInteger();

        MissedEventsCatchUpOutcome outcome = strategy.coordinateCatchUp(
                "server-b", WATERMARK_1500,
                lowerBound -> {
                    callCount.incrementAndGet();
                    // Every fetched event turned out to already be claimed by a peer's own
                    // concurrent catch-up - genuinely new watermark, but zero events actually
                    // triggered by this call.
                    return new MissedEventsCatchUpResult(OptionalLong.of(WATERMARK_1500), 0);
                },
                () -> { });

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
     * its fetch lower bound - rather than being permanently skipped. Direct regression guard for
     * the bug where a stale local candidate compared against an old watermark permanently
     * suppressed all future catch-up on an otherwise-quiet server (HZ-022).
     */
    @Test
    public void testPerformsFreshCatchUpWhenTimeHasPassedSinceWatermarkEvenIfCandidateIsStale() {
        HazelcastMissedEventsCoordinationStrategy strategy = newStrategy();
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
     * Then the lock still serializes them (neither times out), and whichever one is serialized
     * second - if its own effective lower bound turns out to still be behind "now" - fetches
     * starting no earlier than the first one's own reported watermark, so it never re-requests an
     * already-covered range from scratch.
     *
     * <p>Both callers' fetch actions genuinely run here (this synthetic action doesn't model
     * {@code claimEvent} at all - that's covered separately by
     * {@link #testClaimEventOnlyLetsOneConcurrentCallerThrough()}), so both legitimately report
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
        HazelcastMissedEventsCoordinationStrategy strategy = newStrategy();
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
                return new MissedEventsCatchUpResult(OptionalLong.of(WATERMARK_1000), 1);
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
        HazelcastMissedEventsCoordinationStrategy strategy = newStrategy();
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

    /**
     * Given two peers (two separate strategy objects backed by the same cluster) racing to claim
     * the identical event key at essentially the same time
     * When both call claimEvent concurrently
     * Then exactly one of them gets {@code true} - this is the direct regression test for the
     * HZ-026 defect: two replicas whose own concurrent catch-up fetches legitimately overlapped
     * both independently re-triggered the same {@code PatchsetCreated} event, because nothing
     * cross-instance recognized it had already been delivered.
     * @throws Exception if the test threads fail unexpectedly.
     */
    @Test
    public void testClaimEventOnlyLetsOneConcurrentCallerThrough() throws Exception {
        HazelcastMissedEventsCoordinationStrategy first = newStrategy();
        HazelcastMissedEventsCoordinationStrategy second = newStrategy();
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstClaim = executor.submit(() -> {
                startLatch.await();
                return first.claimEvent("server-l", "PatchsetCreated: Change-Id for #2809 PatchSet: 1");
            });
            Future<Boolean> secondClaim = executor.submit(() -> {
                startLatch.await();
                return second.claimEvent("server-l", "PatchsetCreated: Change-Id for #2809 PatchSet: 1");
            });
            startLatch.countDown();

            boolean firstResult = firstClaim.get(FUTURE_GET_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            boolean secondResult = secondClaim.get(FUTURE_GET_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertTrue("exactly one caller must win the claim", firstResult ^ secondResult);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Given an event key already claimed for a server
     * When claimEvent is called again with the same server/key
     * Then it returns false - a peer's own concurrent fetch must recognize this event was already
     * delivered, not re-trigger it.
     */
    @Test
    public void testClaimEventReturnsFalseForAlreadyClaimedKey() {
        HazelcastMissedEventsCoordinationStrategy strategy = newStrategy();

        assertTrue("first claim of a fresh key must succeed", strategy.claimEvent("server-m", "event-1"));
        assertTrue("the same key must never collide with a different server",
                strategy.claimEvent("server-n", "event-1"));
        assertEquals("a second claim of the same server+key must be rejected",
                false, strategy.claimEvent("server-m", "event-1"));
    }
}
