/*
 * The MIT License
 *
 * Copyright 2013 Sony Mobile Communications AB. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.Coordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Monitors the last time something came in on the stream-events connection (via signal from the controller/handler).
 * And restarts the connection if the timeout has passed.
 *
 * @author Robert Sandell &lt;robert.sandell@sonymobile.com&gt;
 */
public class StreamWatchdog extends TimerTask {

    /**
     * Default millis until first timeout check.
     *
     * @see Timer#schedule(java.util.TimerTask, long, long)
     */
    public static final long DEFAULT_CHECK_START_DELAY = TimeUnit.MINUTES.toMillis(1);
    /**
     * Default millis between timeout checks.
     *
     * @see Timer#schedule(java.util.TimerTask, long, long)
     */
    public static final long DEFAULT_CHECK_PERIOD = TimeUnit.SECONDS.toMillis(20);

    private static final Logger logger = LoggerFactory.getLogger(StreamWatchdog.class);

    private long lastSignal;
    private Timer timer;
    private Coordinator coordinator;
    private int timeoutSeconds;
    private WatchTimeExceptionData exceptionData;

    /**
     * Standard Constructor. Same as calling <code> StreamWatchdog(coordinator, timeoutSeconds, exceptionData,
     * DEFAULT_CHECK_START_DELAY, DEFAULT_CHECK_PERIOD) </code>
     *
     * @param coordinator    the coordinator who can do the actual restart of the connection.
     * @param timeoutSeconds number of seconds before a timeout should occur.
     * @param exceptionData  time spans and days when the timeout trigger should not be in effect.
     * @see #StreamWatchdog(Coordinator, int, WatchTimeExceptionData, long, long)
     */
    public StreamWatchdog(Coordinator coordinator, int timeoutSeconds, WatchTimeExceptionData exceptionData) {
        this(coordinator, timeoutSeconds, exceptionData, DEFAULT_CHECK_START_DELAY, DEFAULT_CHECK_PERIOD);
    }

    /**
     * Standard Constructor.
     *
     * @param coordinator     the coordinator who can do the actual restart of the connection.
     * @param timeoutSeconds  number of seconds before a timeout should occur.
     * @param exceptionData   time spans and days when the timeout trigger should not be in effect.
     * @param checkStartDelay millis until the first timeout check should be performed
     * @param checkPeriod     millis between timeout checks
     */
    public StreamWatchdog(Coordinator coordinator, int timeoutSeconds, WatchTimeExceptionData exceptionData,
                          long checkStartDelay, long checkPeriod) {
        this.coordinator = coordinator;
        this.timeoutSeconds = timeoutSeconds;
        this.exceptionData = exceptionData;
        lastSignal = System.currentTimeMillis();
        timer = new Timer(StreamWatchdog.class.getName());
        timer.schedule(this, checkStartDelay, checkPeriod);
    }

    @Override
    public void run() {
        if (!exceptionData.isExceptionNow()) {
            long quietTime = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - getLastSignal());
            logger.debug("Quiettime: {}", quietTime);
            if (quietTime > timeoutSeconds) {
                logger.info("Last data from Gerrit was {} seconds ago; reconnecting.", quietTime);
                coordinator.reconnect();
            }
        }
    }

    /**
     * The current time millis since last we got a signal.
     *
     * @return ms since the last epoch.
     *
     * @see System#currentTimeMillis()
     * @see #signal()
     */
    public synchronized long getLastSignal() {
        return lastSignal;
    }

    /**
     * Signals that something has come through the wire. Resetting the countdown to an eventual connection restart.
     */
    public synchronized void signal() {
        lastSignal = System.currentTimeMillis();
    }

    /**
     * Shuts down the watchdog timer. A new StreamWatchdog will need to be created to continue watching it.
     */
    public void shutdown() {
        timer.cancel();
        timer = null;
    }
}
