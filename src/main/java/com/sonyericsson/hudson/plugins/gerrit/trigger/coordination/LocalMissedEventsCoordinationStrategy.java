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

import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.MissedEventsCatchUpAction;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.MissedEventsCatchUpOutcome;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.MissedEventsCoordinationStrategy;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Single-JVM implementation of {@link MissedEventsCoordinationStrategy}.
 *
 * <p>The per-server lock is still needed even though there is only one JVM: {@code
 * GerritMissedEventsPlaybackManager#connectionEstablished()} runs on {@code GerritConnection}'s
 * own thread on every reconnect and never checks whether a previous reconnect's catch-up retry
 * (scheduled separately on {@code GerritMissedEventsPlaybackManager#CATCH_UP_RETRY_SCHEDULER}) is
 * still pending. So a fresh reconnect and a stale retry for the very same server can call {@link
 * #coordinateCatchUp} concurrently from two different threads. Without the lock, both could read
 * the same stale watermark, run overlapping fetches, and race on the watermark write. No lease is
 * needed to guard against this, though (unlike the distributed strategy's cluster-wide map),
 * since {@code finally} always releases it on the same thread/JVM - there is no cross-JVM
 * crash-without-release scenario here.</p>
 */
public class LocalMissedEventsCoordinationStrategy extends MissedEventsCoordinationStrategy {

    private static final long LOCK_WAIT_TIMEOUT_SECONDS = 30;

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> watermarks = new ConcurrentHashMap<>();

    @NonNull
    @Override
    public MissedEventsCatchUpOutcome coordinateCatchUp(
            @NonNull String serverName,
            long candidateCatchUpFrom,
            @NonNull MissedEventsCatchUpAction catchUpAction,
            @NonNull Runnable maintenanceAction) {
        ReentrantLock lock = locks.computeIfAbsent(serverName, key -> new ReentrantLock());
        boolean locked;
        try {
            locked = lock.tryLock(LOCK_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return MissedEventsCatchUpOutcome.LOCK_TIMEOUT;
        }
        if (!locked) {
            return MissedEventsCatchUpOutcome.LOCK_TIMEOUT;
        }
        try {
            long watermark = watermarks.getOrDefault(serverName, 0L);
            return performCatchUp(serverName, watermark, candidateCatchUpFrom, catchUpAction,
                    w -> watermarks.put(serverName, w));
        } finally {
            // maintenanceAction must never prevent the unlock below - a RuntimeException out of
            // it would otherwise leave this lock held forever, since (unlike the distributed
            // strategy) there is no lease here to release it.
            try {
                maintenanceAction.run();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * @param serverName the Gerrit server to look up.
     * @return the current watermark for {@code serverName}, or empty if none has been recorded
     *         yet.
     */
    @NonNull
    @Override
    public OptionalLong getWatermark(@NonNull String serverName) {
        Long watermark = watermarks.get(serverName);
        return watermark != null ? OptionalLong.of(watermark) : OptionalLong.empty();
    }

    /**
     * Always returns {@code true}: a single JVM has no peer to race against, so there is nothing
     * to dedupe here - the per-JVM {@code receivedEventCache} in
     * {@code GerritMissedEventsPlaybackManager} already covers a second fetch by this same
     * instance.
     *
     * @param serverName unused.
     * @param eventKey unused.
     * @return always {@code true}.
     */
    @Override
    public boolean claimEvent(@NonNull String serverName, @NonNull String eventKey) {
        return true;
    }

    /**
     * No-op: a single JVM has no other instance to share freshness with, and its own per-instance
     * file already is its complete view.
     *
     * @param serverName unused.
     * @param timestampMillis this JVM's own last-known-alive timestamp; handed straight back since
     *         there is no peer value it could ever lose to.
     * @return {@code timestampMillis}, unchanged.
     */
    @Override
    public long publishInstanceFreshness(@NonNull String serverName, long timestampMillis) {
        // Nothing to share with in a single-JVM deployment.
        return timestampMillis;
    }

    /**
     * @param serverName unused.
     * @return always empty - see {@link #publishInstanceFreshness}.
     */
    @NonNull
    @Override
    public OptionalLong getSharedInstanceFreshness(@NonNull String serverName) {
        return OptionalLong.empty();
    }
}
