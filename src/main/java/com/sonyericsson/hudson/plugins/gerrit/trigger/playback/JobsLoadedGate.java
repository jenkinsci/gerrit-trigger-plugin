/*
 * The MIT License
 *
 * Copyright (c) 2026 CloudBees, Inc.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.playback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A one-shot, process-lifetime latch that opens once Jenkins reaches {@link
 * hudson.init.InitMilestone#JOB_CONFIG_ADAPTED} - i.e. once every job's
 * configuration/trigger registration has been adapted/updated.
 *
 * <p>{@link com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer#startConnection()}
 * fires as early as {@link hudson.init.InitMilestone#PLUGINS_STARTED} (needed
 * so {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger#start}
 * can register a listener as each job loads), and the resulting Gerrit
 * connection runs on its own background thread - so {@link
 * GerritMissedEventsPlaybackManager#connectionEstablished()} can genuinely
 * run, and replay missed events, before any job's {@code GerritTrigger} has
 * registered as a listener. A replayed event delivered at that point is
 * silently dropped (no listener to receive it) and never retried - this
 * gate exists specifically to close that window, without delaying when the
 * connection itself opens (see {@code connectionEstablished()}'s own
 * comment for why that distinction matters).
 *
 * <p>Deliberately a single process-lifetime latch, not reset per
 * connect/disconnect cycle: only the very first catch-up of a JVM's
 * lifetime can race job loading. Every later reconnect (e.g. a clean
 * disconnect/reconnect, or a replica coming back after a network blip)
 * happens long after {@code JOB_CONFIG_ADAPTED}, so {@link #await()} returns
 * immediately for those - matching {@link
 * com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer#isNoConnectionOnStartup()}'s
 * existing semantics unchanged for servers that connect on-demand rather
 * than at boot.
 */
public final class JobsLoadedGate {

    private static final Logger logger = LoggerFactory.getLogger(JobsLoadedGate.class);

    /**
     * System property: upper bound in minutes on how long {@link #await()} will block before
     * giving up and letting the caller proceed anyway. Default is 30 minutes. Large instances
     * with many jobs can take far longer than a few minutes to reach {@link
     * hudson.init.InitMilestone#JOB_CONFIG_ADAPTED} - this timeout exists only so a stalled/broken
     * initializer elsewhere can't wedge missed-events catch-up for the lifetime of the JVM, so it
     * should be set well above any expected startup time rather than tuned tight.
     */
    private static final String AWAIT_TIMEOUT_MINUTES_PROPERTY =
            "gerrit.trigger.playback.jobsLoadedGate.timeout.minutes";
    private static final Duration DEFAULT_AWAIT_TIMEOUT = Duration.ofMinutes(30);
    private static final long AWAIT_TIMEOUT_MINUTES =
            Long.getLong(AWAIT_TIMEOUT_MINUTES_PROPERTY, DEFAULT_AWAIT_TIMEOUT.toMinutes());

    /**
     * Deliberately a plain static field, not an {@code @Extension} singleton: there is no Jenkins
     * event that would ever need to reopen this gate. {@code Jenkins#reload()} (the "Reload
     * Configuration from Disk" action) explicitly documents that it "calls neither {@code
     * ItemListener#onLoaded} nor Initializers", and its own {@code loadTasks()} task graph never
     * re-requires {@link hudson.init.InitMilestone#JOB_CONFIG_ADAPTED} - so our {@code
     * @Initializer(after = JOB_CONFIG_ADAPTED)} hook cannot fire again on reload, and there is
     * nothing for an {@code @Extension} to listen for that would re-arm this latch. (Verified
     * against jenkins-core 2.479.3's {@code jenkins/model/Jenkins.java}.)
     */
    private static final CountDownLatch LATCH = new CountDownLatch(1);

    private JobsLoadedGate() {
    }

    /**
     * Opens the gate - called once, from the {@code @Initializer(after =
     * InitMilestone.JOB_CONFIG_ADAPTED)} hook in {@link
     * com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl}. Safe to call more than once
     * (e.g. under test); {@link CountDownLatch#countDown()} is a no-op once the count has already
     * reached zero.
     */
    public static void open() {
        LATCH.countDown();
    }

    /**
     * Blocks the calling thread until the gate opens, or until {@link #AWAIT_TIMEOUT_MINUTES}
     * elapses - whichever comes first. Intended to be called from {@code GerritConnection}'s own
     * background thread, never from an {@code @Initializer} method itself, so blocking here
     * never delays Jenkins' own boot sequence.
     */
    static void await() {
        try {
            if (!LATCH.await(AWAIT_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                logger.warn("Gave up waiting {} minute(s) for Jenkins to finish loading jobs - "
                        + "proceeding with missed-events catch-up anyway. If this happens "
                        + "routinely, something else is stalling Jenkins' own startup.",
                        AWAIT_TIMEOUT_MINUTES);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for Jenkins to finish loading jobs - "
                    + "proceeding with missed-events catch-up anyway.");
        }
    }
}
