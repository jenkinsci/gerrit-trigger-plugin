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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Single-JVM implementation of {@link MissedEventsCoordinationStrategy}.
 *
 * <p>One instance of this class is shared (via {@link CoordinationModeFactory}) by every {@code
 * GerritMissedEventsPlaybackManager} in the JVM, so the per-server lock and watermark maps here
 * are genuinely shared state - mirroring how a distributed implementation shares state via a
 * cluster-wide map. In a single JVM there is no other process to coordinate with, so this reduces
 * to a plain in-process lock: no lease is needed, since {@code finally} always releases it on the
 * same thread/JVM (no cross-JVM crash-without-release scenario exists here).</p>
 */
public class LocalMissedEventsCoordinationStrategy extends MissedEventsCoordinationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(LocalMissedEventsCoordinationStrategy.class);

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
            if (watermark >= candidateCatchUpFrom) {
                return MissedEventsCatchUpOutcome.ALREADY_CAUGHT_UP;
            }
            try {
                long newWatermark = catchUpAction.fetchAndTrigger(new Date(candidateCatchUpFrom));
                watermarks.put(serverName, newWatermark);
                return MissedEventsCatchUpOutcome.PERFORMED;
            } catch (IOException e) {
                logger.error("Missed-events catch-up failed for server {}", serverName, e);
                return MissedEventsCatchUpOutcome.FAILED;
            }
        } finally {
            maintenanceAction.run();
            lock.unlock();
        }
    }
}
