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
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.MissedEventsCatchUpAction;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.MissedEventsCatchUpOutcome;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.MissedEventsCoordinationStrategy;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

/**
 * Hazelcast-backed implementation of {@link MissedEventsCoordinationStrategy}.
 *
 * <p>Uses a single distributed {@code IMap<String, Long>} (keyed by Gerrit server name) for
 * both the coordination lock and the shared watermark value - the same pattern {@code
 * HazelcastBuildMemoryStorage} uses (locking on the map that stores the data), rather than a
 * separate lock map.</p>
 *
 * <p>The lock is acquired with a bounded wait (so a Gerrit reconnect callback thread never blocks
 * indefinitely) and a lease (a safety net auto-release if a replica crashes mid-fetch; the lock is
 * always explicitly released in the normal path via {@code finally}, so the lease only matters on
 * crash). Mutual exclusion across replicas comes from the lock itself; avoiding a redundant
 * re-fetch after a lock hand-off comes from comparing against the shared watermark immediately
 * after acquiring the lock - the lease/timeout values alone do not guarantee either property.</p>
 */
public class HazelcastMissedEventsCoordinationStrategy extends MissedEventsCoordinationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastMissedEventsCoordinationStrategy.class);

    static final String WATERMARK_MAP_NAME = "gerrit-trigger-missed-events-watermark";

    /**
     * Cross-instance freshness map, keyed by server name, holding the most advanced last-known-
     * alive timestamp published by any JVM for that server - see {@link #publishInstanceFreshness}
     * and {@link #getSharedInstanceFreshness}. Deliberately a separate map from {@link
     * #WATERMARK_MAP_NAME}: this one is an unlocked, max-only signal with no mutual-exclusion
     * semantics, updated far more often (on every persistence tick, by every live instance) than
     * the watermark, which only changes on an actual catch-up fetch.
     */
    static final String INSTANCE_FRESHNESS_MAP_NAME = "gerrit-trigger-missed-events-instance-freshness";

    /**
     * System property: maximum seconds to wait to acquire the missed-events coordination lock.
     */
    public static final String LOCK_WAIT_TIMEOUT_PROPERTY =
            "gerrit.trigger.coordination.hazelcast.missedevents.lock.wait.timeout.seconds";

    /**
     * System property: lease duration in seconds for the missed-events coordination lock - a
     * crash safety net only, since the lock is always explicitly released in the normal path.
     */
    public static final String LOCK_LEASE_PROPERTY =
            "gerrit.trigger.coordination.hazelcast.missedevents.lock.lease.seconds";

    private static final int DEFAULT_LOCK_WAIT_TIMEOUT_SECONDS = 30;

    private static final int DEFAULT_LOCK_LEASE_SECONDS = 300;

    private final HazelcastInstance hazelcastInstance;

    /**
     * @param hazelcastInstance the Hazelcast instance to use for coordination.
     */
    public HazelcastMissedEventsCoordinationStrategy(@NonNull HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @NonNull
    @Override
    public MissedEventsCatchUpOutcome coordinateCatchUp(
            @NonNull String serverName,
            long candidateCatchUpFrom,
            @NonNull MissedEventsCatchUpAction catchUpAction,
            @NonNull Runnable maintenanceAction) {
        IMap<String, Long> map = hazelcastInstance.getMap(WATERMARK_MAP_NAME);
        int waitTimeoutSeconds = Integer.getInteger(LOCK_WAIT_TIMEOUT_PROPERTY, DEFAULT_LOCK_WAIT_TIMEOUT_SECONDS);
        int leaseSeconds = Integer.getInteger(LOCK_LEASE_PROPERTY, DEFAULT_LOCK_LEASE_SECONDS);

        boolean locked;
        try {
            locked = map.tryLock(serverName, waitTimeoutSeconds, TimeUnit.SECONDS, leaseSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for missed-events coordination lock for server {}", serverName);
            return MissedEventsCatchUpOutcome.LOCK_TIMEOUT;
        }
        if (!locked) {
            logger.warn("Timed out waiting for missed-events coordination lock for server {}; "
                    + "not performing an uncoordinated catch-up.", serverName);
            return MissedEventsCatchUpOutcome.LOCK_TIMEOUT;
        }
        try {
            Long watermark = map.get(serverName);
            long currentWatermark = watermark != null ? watermark : 0L;
            // Use whichever of the two is more advanced as the actual fetch lower bound, rather
            // than treating "a peer's watermark is ahead of my own candidate" as a reason to skip
            // fetching entirely. The watermark only reflects how far a PAST catch-up reached - it
            // says nothing about whether a NEW gap has opened up since then, so gating on it here
            // would (and did) permanently suppress catch-up after the first successful fetch, on
            // an otherwise-quiet Gerrit server. Only a truly empty window (nothing possibly missed
            // since the more advanced of the two) is skipped.
            long effectiveLowerBound = Math.max(currentWatermark, candidateCatchUpFrom);
            if (System.currentTimeMillis() <= effectiveLowerBound) {
                logger.debug("Missed-events effective lower bound for server {} is already current "
                        + "({}); nothing to catch up on.", serverName, effectiveLowerBound);
                return MissedEventsCatchUpOutcome.ALREADY_CAUGHT_UP;
            }
            try {
                long newWatermark = catchUpAction.fetchAndTrigger(new Date(effectiveLowerBound));
                map.put(serverName, newWatermark);
                return MissedEventsCatchUpOutcome.PERFORMED;
            } catch (IOException e) {
                logger.error("Missed-events catch-up failed for server {}", serverName, e);
                return MissedEventsCatchUpOutcome.FAILED;
            }
        } finally {
            maintenanceAction.run();
            map.unlock(serverName);
        }
    }

    /**
     * Atomically merges {@code timestampMillis} into the shared per-server freshness value via
     * {@link IMap#merge}, which Hazelcast executes as a single partition-confined operation - safe
     * for concurrent callers across every live instance without needing a lock, unlike {@link
     * #coordinateCatchUp}'s watermark.
     *
     * @param serverName the Gerrit server this timestamp is for.
     * @param timestampMillis this JVM's own last-known-alive timestamp (epoch millis).
     */
    @Override
    public void publishInstanceFreshness(@NonNull String serverName, long timestampMillis) {
        IMap<String, Long> map = hazelcastInstance.getMap(INSTANCE_FRESHNESS_MAP_NAME);
        map.merge(serverName, timestampMillis, Math::max);
    }

    /**
     * @param serverName the Gerrit server to look up.
     * @return the shared freshness timestamp, or empty if no instance has published one yet.
     */
    @NonNull
    @Override
    public OptionalLong getSharedInstanceFreshness(@NonNull String serverName) {
        IMap<String, Long> map = hazelcastInstance.getMap(INSTANCE_FRESHNESS_MAP_NAME);
        Long value = map.get(serverName);
        return value != null ? OptionalLong.of(value) : OptionalLong.empty();
    }
}
