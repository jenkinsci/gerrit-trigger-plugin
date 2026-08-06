/*
 * The MIT License
 *
 * Copyright (c) 2014 Ericsson
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

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.NamedGerritEventListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.CoordinationMode;
import com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.InstanceIdentity;
import com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.LocalMissedEventsCoordinationStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.MissedEventsCatchUpOutcome;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.MissedEventsCatchUpResult;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.MissedEventsCoordinationStrategy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.GerritPluginChecker;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.HttpUtils;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil;
import com.sonymobile.tools.gerrit.gerritevents.ConnectionListener;
import com.sonymobile.tools.gerrit.gerritevents.GerritEventListener;
import com.sonymobile.tools.gerrit.gerritevents.GerritJsonEventFactory;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Provider;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;

import hudson.Util;
import hudson.XmlFile;
import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.umd.cs.findbugs.annotations.CheckForNull;


/**
 * The GerritMissedEventsPlaybackManager is responsible for recording a last-alive timestamp
 * for each server connection. The motivation is that we want to be able to know when we last
 * received an event. This will help us determine upon connection startup, if we have missed
 * some events while the connection was down.
 *
 * Once the server is re-connected, the missed event will be played back as if they had been
 * received originally.
 *
 * @author scott.hebert@ericsson.com
 */
public class GerritMissedEventsPlaybackManager implements ConnectionListener, NamedGerritEventListener {

    private static final Logger logger = LoggerFactory.getLogger(GerritMissedEventsPlaybackManager.class);
    static final String EVENTS_LOG_PLUGIN_NAME = "events-log";
    private static final String EVENTS_LOG_PLUGIN_URL = "a/plugins/" + EVENTS_LOG_PLUGIN_NAME + "/events/";
    /**
     * System property: maximum age in hours a per-instance timestamp file may go without being
     * rewritten before it is considered orphaned (from a permanently decommissioned JVM) and
     * pruned. Default is 7 days.
     */
    private static final String STALE_INSTANCE_FILE_AGE_PROPERTY =
            "gerrit.trigger.playback.instance.stale.age.hours";
    private static final Duration DEFAULT_STALE_INSTANCE_FILE_AGE = Duration.ofDays(7);
    /**
     * System property: maximum number of catch-up attempts made for a single reconnect - the
     * initial synchronous attempt plus however many {@link #scheduleCatchUpRetry} schedules after
     * it. A hard backstop, not the primary way this stops: {@link #hasWatermarkPassed} normally
     * ends the sequence earlier, once the events-log plugin's own indexing is confirmed caught up -
     * this bound only matters when that confirmation never arrives (e.g. a persistently quiet
     * events-log response), so it's kept small enough to be cheap even then: at the default delay
     * this adds at most a few seconds to such a reconnect, in exchange for meaningfully raising the
     * odds of closing a real gap within the same reconnect otherwise. See {@link
     * #CATCH_UP_RETRY_DELAY_MILLIS_PROPERTY}'s own comment for why this retry exists at all.
     */
    private static final String CATCH_UP_RETRY_MAX_ATTEMPTS_PROPERTY =
            "gerrit.trigger.playback.catchup.retry.max.attempts";
    private static final int DEFAULT_CATCH_UP_RETRY_MAX_ATTEMPTS = 4;
    /**
     * System property: milliseconds to wait before each scheduled catch-up retry attempt.
     *
     * <p>The events-log plugin has its own indexing latency between "Gerrit processed this event"
     * and "this event is queryable via its REST endpoint" - a fetch that races ahead of that can
     * get back a response that doesn't yet contain the very event it exists to catch up on. {@link
     * MissedEventsCatchUpResult}'s own watermark semantics keep that race from causing PERMANENT
     * loss (the floor never advances past ground it hasn't proven), but without this retry a
     * single unlucky fetch would still leave the gap open until some unrelated future reconnect
     * happens to occur - this retry instead gives the events-log plugin a brief window to catch up
     * within the same reconnect, since that indexing lag has been observed to resolve within about
     * a second in practice. Scheduled on {@link #CATCH_UP_RETRY_SCHEDULER} rather than a blocking
     * sleep - see that field's own javadoc.</p>
     */
    private static final String CATCH_UP_RETRY_DELAY_MILLIS_PROPERTY =
            "gerrit.trigger.playback.catchup.retry.delay.millis";
    private static final long DEFAULT_CATCH_UP_RETRY_DELAY_MILLIS = 2000L;
    /**
     * Dedicated, process-wide scheduler for {@link #scheduleCatchUpRetry} - deliberately NOT {@code
     * jenkins.util.Timer}'s own shared executor: that pool is small and fed by a great many
     * unrelated periodic tasks across all of Jenkins core, and a scheduled task can sit queued
     * behind other work for far longer than its own requested delay under load (observed directly:
     * a 2-second retry delay queued for over 90 seconds before running, on a controller doing
     * nothing more unusual than a handful of concurrent test jobs) - exactly defeating the point of
     * a short, bounded retry window. One JVM normally configures only a handful of Gerrit servers
     * at most, so a single dedicated daemon thread shared across every instance of this class is
     * cheap and keeps this retry's own timing independent of how busy the rest of Jenkins is.
     */
    private static final ScheduledExecutorService CATCH_UP_RETRY_SCHEDULER = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "GerritMissedEventsPlaybackManager-catchup-retry");
                thread.setDaemon(true);
                return thread;
            });

    private String serverName;
    /**
     * Server Timestamp.
     */
    protected EventTimeSlice serverTimestamp = null;
    /**
     * Time slice last persisted by this instance. Deliberately per-instance (not static): each
     * configured Gerrit server gets its own {@link GerritMissedEventsPlaybackManager}, and a
     * shared static field here would let one server's persistence cycle suppress another's.
     */
    private volatile long lastPersistedTimeSlice = 0;
    /**
     * Last own {@code serverTimestamp} value pushed to the shared cross-instance freshness signal
     * (see {@link MissedEventsCoordinationStrategy#publishInstanceFreshness}). Deliberately
     * separate from {@link #lastPersistedTimeSlice}, which tracks what was last written to this
     * instance's own local file - that value may be a borrowed, cluster-wide one rather than this
     * field's own push, so the two guards can legitimately diverge.
     */
    private volatile long lastPublishedTimeSlice = 0;
    /**
     * The most advanced point this instance has actually PROVEN is safe to advertise to peers (via
     * {@link MissedEventsCoordinationStrategy#publishInstanceFreshness}) or persist to its own
     * local file - as opposed to {@link #serverTimestamp}, which advances on every live event this
     * instance happens to receive, regardless of project, and so proves nothing about whether an
     * earlier, unrelated gap from this same connection session was ever actually closed.
     *
     * <p>Empty until this instance's own {@link #connectionEstablished()} has run at least once
     * and reached either the "nothing to catch up" shortcut or an actual catch-up attempt - before
     * that (a brand-new environment with no prior cross-instance signal at all), there is nothing
     * to prove yet, so the persistence thread falls back to {@link #serverTimestamp} as it always
     * has, to bootstrap the very first publish/persist cycle.</p>
     *
     * <p>This field itself is deliberately never advanced by {@link #gerritEvent}/{@link
     * #saveTimestamp} - only by {@link #connectionEstablished()} itself, once it has confirmed
     * (via the "already fresh" shortcut, or via {@link MissedEventsCoordinationStrategy#getWatermark}'s
     * own already-proven value after a catch-up attempt) that this point is genuinely safe to
     * advertise. Before that first confirmation, an ordinary live event proves only that this JVM
     * is currently receiving events, not that everything before it - including whatever this same
     * reconnect's own catch-up may have just missed - has been accounted for.</p>
     *
     * <p>Once a floor IS established, though, a live event timestamped AFTER it is equally
     * trustworthy: this JVM receiving it live is itself direct proof nothing at that point was
     * missed, for exactly the same reason {@link #hasWatermarkPassed} treats a fresh watermark
     * value as proof of indexing catch-up. See {@link GerritMissedEventsPlaybackPersistRunnable#run}
     * for where this is actually combined with {@link #serverTimestamp} - this field alone, read in
     * isolation, would otherwise silently freeze at whatever this instance's LAST reconnect
     * established, understating its true freshness for however long it stays connected without
     * reconnecting again (confirmed root cause of a spurious missed-events re-trigger on an
     * already-stable, long-connected peer - HZ-023/024, 2026-07-31).</p>
     */
    private volatile OptionalLong confirmedCatchUpFloor = OptionalLong.empty();
    /**
     * Identifier for this JVM process, used to namespace this instance's persisted timestamp
     * file from those of other JVMs sharing the same {@code JENKINS_HOME}.
     */
    private final String instanceId = InstanceIdentity.get();
    /**
     * Scoped to this manager's own server (and JVM instance) at construction time - one manager
     * already exists per configured Gerrit server, so the store never needs to be told which
     * server it's persisting for on every call.
     */
    private final InstanceTimestampStore instanceTimestampStore;
    /**
     * List that contains received Gerrit Events.
     */
    protected List<GerritTriggeredEvent> receivedEventCache
        = Collections.synchronizedList(new ArrayList<>());
    /**
     * True from the moment {@link #connectionEstablished()} first schedules a {@link
     * #scheduleCatchUpRetry} attempt until that retry sequence has genuinely finished (either
     * {@link #hasWatermarkPassed} confirms events-log's own indexing has caught up, {@code
     * maxAttempts} is spent, or an attempt returns {@code LOCK_TIMEOUT}/{@code FAILED}).
     *
     * <p>{@link #gerritEvent} - itself invoked for every event {@link #fetchAndTriggerMissedEvents}
     * just triggered, since {@code server.triggerEvent(evt)} re-enters the normal live-event
     * dispatch pipeline this same listener is also registered on - uses this to tell "genuinely
     * back to live operation" apart from "still mid-retry-sequence for this reconnect": without
     * it, that re-entrant delivery of the very event this reconnect just caught up on would arrive
     * after {@link #playBackComplete} has already flipped {@code true} (set once the first,
     * synchronous attempt returns, well before any 2-second-later scheduled retry runs) and wipe
     * {@link #receivedEventCache} clean via {@link #gerritEvent}'s own reset branch - discarding
     * the very record a still-pending retry needs to recognize that event as already delivered,
     * and defeating {@link #receivedEventCache}'s purpose for exactly the window it exists to
     * cover: without this guard, a retry attempt seconds later re-fetching the same
     * already-triggered event (a legitimate, expected overlap - see {@link
     * #CATCH_UP_RETRY_DELAY_MILLIS_PROPERTY}'s own javadoc on events-log's whole-second query
     * resolution) finds an empty cache and calls {@code server.triggerEvent(evt)} on it again.</p>
     */
    private volatile boolean catchUpRetryPending = false;

    private boolean isSupported = false;
    private boolean playBackComplete = false;
    private boolean previousIsSupported;
    private GerritMissedEventsPlaybackPersistRunnable persistenceCheck;

    /**
     * @param name Gerrit Server Name.
     */
    public GerritMissedEventsPlaybackManager(String name) {
        this.serverName = name;
        this.instanceTimestampStore = new InstanceTimestampStore(serverName);
        checkIfEventsLogPluginSupported();
        previousIsSupported = isSupported;
        persistenceCheck = new GerritMissedEventsPlaybackPersistRunnable();
    }

    /**
     * Start the persistenceCheck thread.
     */
    private void startPersistenceCheck() {
        lastPersistedTimeSlice = 0;
        lastPublishedTimeSlice = 0;
        persistenceCheck.start();
    }

    /**
     * Stop the persistenceCheck thread.
     */
    private void stopPersistenceCheck() {
        persistenceCheck.stop();
    }

    /**
     * Method to perform check to see if Events Plugin is enabled.
     * @throws IOException if occurs.
     */
    public void performCheck() throws IOException {
        if (playBackComplete) {
            checkIfEventsLogPluginSupported();
            if (previousIsSupported && !isSupported) {
                logger.warn("Missed Events Playback used to be supported. now it is not!");
                // we could be missing events here that we should be persisting...
                // so let's remove this instance's own data file so we are ready if it comes back
                try {
                    instanceTimestampStore.deleteTimestamp();
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
                stopPersistenceCheck();
            }
            if (!previousIsSupported && isSupported) {
                logger.warn("Missed Events Playback used to be NOT supported. now it IS!");
            }
            if (previousIsSupported != isSupported) {
                previousIsSupported = isSupported;
            }
        }
    }

    /**
     * The name of the {@link GerritServer} this is managing.
     *
     * @return the {@link GerritServer#getName()}.
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * method to verify if plugin is supported.
     */
    public void checkIfEventsLogPluginSupported() {
        PluginImpl instance = PluginImpl.getInstance();
        if (instance == null) {
            throw new IllegalStateException("Jenkins is not up");
        }
        GerritServer server = instance.getServer(serverName);
        if (server != null && server.getConfig() != null) {
            Boolean newValue = GerritPluginChecker.isPluginEnabled(server.getConfig(), EVENTS_LOG_PLUGIN_NAME, true);
            if (newValue == null) {
                logger.warn("Could not determine plugin support for " + EVENTS_LOG_PLUGIN_NAME
                    + "; leaving status as " + isSupported);
            } else {
                isSupported = newValue;
            }
        }
    }

    /**
     * Load in the last-alive Timestamp file.
     * @throws IOException is we cannot unmarshal.
     */
    protected void load() throws IOException {
        // Only read from disk on a true cold start (nothing accumulated in this JVM yet). A live
        // reconnect (this JVM never restarted, only its Gerrit connection dropped) already holds
        // a serverTimestamp at least as fresh as anything the file could offer - the file is only
        // ever a lagging, periodically-written snapshot of it (see the persistence thread below) -
        // so unconditionally re-reading here would regress this instance's own knowledge backward
        // on every single reconnect.
        if (serverTimestamp == null) {
            serverTimestamp = instanceTimestampStore.readTimestamp();
        }
    }

    /**
     * get DateRange from current and last known time.
     * @return last known timestamp or current date if not found.
     */
    protected synchronized Date getDateFromTimestamp() {
        //get timestamp for server
        if (serverTimestamp != null) {
            Date myDate = new Date(serverTimestamp.getTimeSlice());
            logger.debug("Previous alive timestamp was: {}", myDate);
            return myDate;
        }
        return new Date();
    }

    /**
     * When the connection is established, we determine the most advanced known timestamp across
     * every JVM instance persisting state for this server - combining the per-instance file scan
     * ({@link InstanceTimestampStore#computeMaxTimestampAcrossInstances()}) with whatever faster,
     * shared cross-instance signal this coordination mode offers ({@link
     * MissedEventsCoordinationStrategy#getSharedInstanceFreshness}) - and - coordinated via {@link
     * MissedEventsCoordinationStrategy#coordinateCatchUp} so that at most one JVM does this at a
     * time and no reconnect re-requests an already-covered range - request any missed events from
     * the Gerrit events-log plugin and pump them in to play them back.
     *
     * <p>This runs on every reconnect, not just process cold-start: each JVM keeps its own live
     * Gerrit connection and event processing is already deduplicated across JVMs via the event
     * claim strategy, so this cross-instance catch-up specifically covers the case where every
     * JVM was simultaneously unable to process events (e.g. a full outage).</p>
     *
     * <p>Waits on {@link JobsLoadedGate} first, before computing anything below - including the
     * candidate catch-up timestamp itself. This runs on {@code GerritConnection}'s own background
     * thread (see that class), not on Jenkins' own initializer thread, so blocking here doesn't
     * delay Jenkins' own boot - but on a cold start, this method can otherwise run (and replay a
     * missed event) before this server's own jobs have finished loading and registering their
     * {@code GerritTrigger} listeners, silently dropping that event with no retry. The wait has to
     * come before the timestamp computation, not just before the actual catch-up call below -
     * computing "now" early and then blocking would leave that value stale by the time the gate
     * opens.</p>
     */
    @Override
    public void connectionEstablished() {
        JobsLoadedGate.await();
        playBackComplete = false;
        checkIfEventsLogPluginSupported();
        if (!isSupported) {
            logger.warn("Playback of missed events not supported for server {}!", serverName);
            playBackComplete = true;
            return;
        }
        logger.debug("Connection Established!");

        try {
            load();
        } catch (IOException e) {
            logger.error("Failed to load in timestamps for server {}", serverName);
            logger.error("Exception: {}", e.getMessage(), e);
            playBackComplete = true;
            return;
        }

        MissedEventsCoordinationStrategy coordinationStrategy =
                CoordinationMode.get().getMissedEventsCoordinationStrategy();
        OptionalLong candidate = maxOptionalLong(
                instanceTimestampStore.computeMaxTimestampAcrossInstances(),
                coordinationStrategy.getSharedInstanceFreshness(serverName));
        if (candidate.isEmpty()) {
            logger.debug("No known last-alive timestamp for server {}", serverName);
            playBackComplete = true;
            return;
        }
        long candidateCatchUpFrom = candidate.getAsLong();
        long diff = System.currentTimeMillis() - candidateCatchUpFrom;
        if (diff <= 0) {
            logger.debug("Zero date range from last-alive timestamp for server {}", serverName);
            advanceConfirmedCatchUpFloor(candidateCatchUpFrom);
            ensureBaselineCaptured(candidateCatchUpFrom);
            playBackComplete = true;
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Non-zero date range from last-alive timestamp exists for server {} : {}"
                    , serverName, Util.getPastTimeString(diff));
        }

        long staleAgeMillis = Duration.ofHours(
                Integer.getInteger(STALE_INSTANCE_FILE_AGE_PROPERTY, (int)DEFAULT_STALE_INSTANCE_FILE_AGE.toHours()))
                .toMillis();
        long catchUpAttemptsStartedAt = System.currentTimeMillis();
        MissedEventsCatchUpOutcome outcome = performCatchUpAttempt(
                coordinationStrategy, candidateCatchUpFrom, staleAgeMillis);
        logger.info("Missed-events catch-up outcome for server {}: {}", serverName, outcome);

        // Whatever the coordination strategy's own watermark now holds is, by construction, only
        // ever advanced to a point it has actually observed events up to (see that watermark's own
        // "empty means nothing proven" contract) - safe to advertise regardless of whether this
        // specific call was the one that advanced it, or a peer's concurrent attempt did.
        OptionalLong watermarkAfterAttempt = coordinationStrategy.getWatermark(serverName);
        watermarkAfterAttempt.ifPresent(this::advanceConfirmedCatchUpFloor);

        // LOCK_TIMEOUT/FAILED mean this attempt confirmed NOTHING - the next reconnect must retry
        // (see each outcome's own javadoc) - so neither the retry schedule below nor
        // ensureBaselineCaptured's own baseline-seeding may treat candidateCatchUpFrom as proven.
        boolean catchUpAttemptTrustworthy = outcome != MissedEventsCatchUpOutcome.LOCK_TIMEOUT
                && outcome != MissedEventsCatchUpOutcome.FAILED;

        if (catchUpAttemptTrustworthy && !hasWatermarkPassed(coordinationStrategy, catchUpAttemptsStartedAt)) {
            catchUpRetryPending = true;
            scheduleCatchUpRetry(
                    coordinationStrategy, watermarkAfterAttempt.orElse(candidateCatchUpFrom), staleAgeMillis, 2,
                    catchUpAttemptsStartedAt);
        }

        if (catchUpAttemptTrustworthy) {
            ensureBaselineCaptured(candidateCatchUpFrom);
        } else {
            ensurePersistenceCheckStarted();
        }

        playBackComplete = true;
        logger.info("Processing completed for server: {}", serverName);
    }

    /**
     * Invokes {@link MissedEventsCoordinationStrategy#coordinateCatchUp} once.
     *
     * @param coordinationStrategy the coordination strategy to invoke.
     * @param candidateCatchUpFrom the candidate catch-up floor for this attempt.
     * @param staleAgeMillis forwarded unchanged to the maintenance action.
     * @return the outcome of this attempt.
     */
    private MissedEventsCatchUpOutcome performCatchUpAttempt(
            MissedEventsCoordinationStrategy coordinationStrategy, long candidateCatchUpFrom, long staleAgeMillis) {
        return coordinationStrategy.coordinateCatchUp(
                serverName,
                candidateCatchUpFrom,
                lowerBound -> fetchAndTriggerMissedEvents(lowerBound, coordinationStrategy),
                () -> instanceTimestampStore.pruneStaleInstanceFiles(staleAgeMillis));
    }

    /**
     * @param coordinationStrategy the coordination strategy whose watermark to check.
     * @param referenceMillis a fixed wall-clock instant to compare against - not "now" at check
     *         time, which drifts forward regardless of whether the underlying data actually
     *         became any fresher, but the moment this reconnect's own catch-up sequence began.
     * @return whether the watermark now holds a value strictly after {@code referenceMillis} - the
     *         events-log plugin's own response contained an event actually created after this
     *         reconnect's catch-up sequence started, which is direct, unambiguous proof its
     *         indexing has caught up to (at least) that instant. Anything genuinely missed by an
     *         earlier outage happened well before that instant, so this proves events-log's own
     *         indexing lag - the entire reason this retry exists - is no longer a factor for it:
     *         if it were still missing, this fetch's response could not legitimately contain
     *         anything created later without also containing it. Deliberately not "is the
     *         watermark close to now" (fooled by unrelated busy traffic making a stale response
     *         look fresh) or "did the watermark change since the last attempt" (fooled by a fixed
     *         set of already-known duplicate events masking a genuinely new one) - both were tried
     *         and both were observed to give a false positive in exactly the scenario retrying
     *         exists to handle. See {@link #scheduleCatchUpRetry}'s own javadoc.
     */
    private boolean hasWatermarkPassed(MissedEventsCoordinationStrategy coordinationStrategy, long referenceMillis) {
        OptionalLong watermark = coordinationStrategy.getWatermark(serverName);
        return watermark.isPresent() && watermark.getAsLong() > referenceMillis;
    }

    /**
     * Schedules attempt {@code attempt} of a catch-up retry on {@link #CATCH_UP_RETRY_SCHEDULER} -
     * deliberately not a blocking {@code Thread.sleep} loop on the calling ({@code
     * GerritConnection}) thread, which would hold that thread hostage for the entire retry window
     * instead of freeing it to keep servicing the live connection, and deliberately not {@code
     * jenkins.util.Timer}'s own shared executor either - see {@link #CATCH_UP_RETRY_SCHEDULER}'s
     * own javadoc for why. See {@link #CATCH_UP_RETRY_DELAY_MILLIS_PROPERTY}'s own javadoc for why
     * this retry exists at all.
     *
     * <p>Each retry re-queries from wherever the watermark already reached as of the previous
     * attempt (not the original candidate again) - {@link MissedEventsCatchUpResult}'s own
     * watermark semantics only ever advance it to a point actually observed, so a retry's own
     * {@code effectiveLowerBound} naturally narrows to just what is still outstanding, same as any
     * other peer serializing in behind an earlier attempt via the same lock. This narrowing is
     * cooperative with (not a substitute for) {@link LocalMissedEventsCoordinationStrategy}'s own
     * {@code Math.max(watermark, candidateCatchUpFrom)} floor - passing the advanced watermark
     * here just keeps this parameter honest with what the coordination strategy already does
     * internally, rather than silently relying on it.</p>
     *
     * <p>Keeps scheduling further retries until either {@code maxAttempts} is spent or {@link
     * #hasWatermarkPassed} confirms the events-log plugin's own indexing has caught up past {@code
     * catchUpAttemptsStartedAt} - see that method's own javadoc for why comparing against that
     * fixed reference, rather than the watermark's mere closeness to "now" or whether it changed
     * since the previous attempt, is what actually holds up under real, observed traffic on a busy,
     * shared Gerrit instance (two earlier versions of this method tried each of those instead, and
     * both were fooled in exactly the scenario retrying exists to handle).</p>
     *
     * <p>Stops, without scheduling a further retry, on {@link MissedEventsCatchUpOutcome#LOCK_TIMEOUT}
     * or {@link MissedEventsCatchUpOutcome#FAILED} - retrying either here would either fight the
     * same contention that just caused the timeout, or repeat an I/O failure likely to recur
     * immediately; the next reconnect (or this coordination mode's own lock semantics) already
     * covers those cases. Either way, clears {@link #catchUpRetryPending} - this was the last
     * attempt this reconnect will make, so {@link #gerritEvent} is safe to treat this server as
     * genuinely back to live operation again.</p>
     *
     * <p>Re-checks {@link MissedEventsCoordinationStrategy#getSharedInstanceFreshness} immediately
     * before each attempt's own {@link #performCatchUpAttempt} call - unlike {@link
     * #connectionEstablished()}'s own initial "diff &lt;= 0" shortcut, {@link #performCatchUpAttempt}
     * fetches from events-log by raw date range and re-triggers anything not already in this JVM's
     * own {@link #receivedEventCache}, with no awareness of what a peer has published since this
     * retry was scheduled - a live peer's shared freshness signal advancing past {@code catchUpFrom}
     * in the delay between attempts is direct proof that peer already processed everything up to
     * that point live, so skipping straight to a fresh events-log fetch here would re-surface and
     * re-trigger an event a live peer already handled (confirmed root cause of a spurious
     * missed-events re-trigger on an already-stable, long-connected peer - HZ-023/024, 2026-07-31 -
     * this same incident {@link #confirmedCatchUpFloor} was introduced for kept recurring afterward
     * because that fix only closed the gap in the *published* freshness value, not this retry loop's
     * own failure to ever re-consult it).</p>
     *
     * @param coordinationStrategy the coordination strategy to invoke.
     * @param catchUpFrom the catch-up floor to query from for this attempt - the original
     *         candidate for the first retry (attempt 2), or the watermark as of the previous
     *         attempt for every one after that.
     * @param staleAgeMillis forwarded unchanged to every attempt's own maintenance action.
     * @param attempt this retry's own attempt number (the first retry is attempt 2 - attempt 1 was
     *         the synchronous call already made before scheduling this).
     * @param catchUpAttemptsStartedAt the fixed wall-clock instant this reconnect's whole catch-up
     *         sequence began - see {@link #hasWatermarkPassed}'s own javadoc.
     */
    private void scheduleCatchUpRetry(
            MissedEventsCoordinationStrategy coordinationStrategy, long catchUpFrom,
            long staleAgeMillis, int attempt, long catchUpAttemptsStartedAt) {
        int maxAttempts = Integer.getInteger(
                CATCH_UP_RETRY_MAX_ATTEMPTS_PROPERTY, DEFAULT_CATCH_UP_RETRY_MAX_ATTEMPTS);
        long retryDelayMillis = Long.getLong(
                CATCH_UP_RETRY_DELAY_MILLIS_PROPERTY, DEFAULT_CATCH_UP_RETRY_DELAY_MILLIS);
        CATCH_UP_RETRY_SCHEDULER.schedule(() -> {
            OptionalLong freshnessBeforeAttempt = coordinationStrategy.getSharedInstanceFreshness(serverName);
            if (freshnessBeforeAttempt.isPresent() && freshnessBeforeAttempt.getAsLong() >= catchUpFrom) {
                logger.info("Missed-events catch-up retry {}/{} outcome for server {}: {} (shared freshness "
                        + "signal already covers this gap - skipping events-log re-fetch)",
                        attempt, maxAttempts, serverName, MissedEventsCatchUpOutcome.ALREADY_CAUGHT_UP);
                advanceConfirmedCatchUpFloor(freshnessBeforeAttempt.getAsLong());
                catchUpRetryPending = false;
                return;
            }
            MissedEventsCatchUpOutcome outcome = performCatchUpAttempt(
                    coordinationStrategy, catchUpFrom, staleAgeMillis);
            logger.info("Missed-events catch-up retry {}/{} outcome for server {}: {}",
                    attempt, maxAttempts, serverName, outcome);
            OptionalLong watermarkAfterAttempt = coordinationStrategy.getWatermark(serverName);
            watermarkAfterAttempt.ifPresent(this::advanceConfirmedCatchUpFloor);
            if (outcome != MissedEventsCatchUpOutcome.LOCK_TIMEOUT && outcome != MissedEventsCatchUpOutcome.FAILED
                    && attempt < maxAttempts && !hasWatermarkPassed(coordinationStrategy, catchUpAttemptsStartedAt)) {
                scheduleCatchUpRetry(
                        coordinationStrategy, watermarkAfterAttempt.orElse(catchUpFrom), staleAgeMillis, attempt + 1,
                        catchUpAttemptsStartedAt);
            } else {
                catchUpRetryPending = false;
            }
        }, retryDelayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Advances {@link #confirmedCatchUpFloor} to {@code candidate} if it is more advanced than
     * whatever this instance already holds - monotonic, same reasoning as {@link
     * #maxOptionalLong}, so a later call can never regress a floor an earlier call already
     * established as safe.
     *
     * @param candidate a point this call has just confirmed is safe to advertise.
     */
    private void advanceConfirmedCatchUpFloor(long candidate) {
        if (confirmedCatchUpFloor.isEmpty() || candidate > confirmedCatchUpFloor.getAsLong()) {
            confirmedCatchUpFloor = OptionalLong.of(candidate);
        }
    }

    /**
     * Ensures this instance's own known baseline reflects at least {@code candidateCatchUpFrom} -
     * the cross-instance floor just confirmed by this {@link #connectionEstablished()} call - and
     * that the persistence thread is running to push/pull it immediately.
     *
     * <p>Callers must only reach this when the catch-up attempt was genuinely trustworthy (not
     * {@code LOCK_TIMEOUT}/{@code FAILED} - see each outcome's own javadoc): those two outcomes
     * confirm nothing at all, so seeding {@link #serverTimestamp} from {@code candidateCatchUpFrom}
     * on either would fabricate a baseline as if the gap had been checked and found clear, when it
     * was never actually checked - silently and permanently losing anything genuinely missed in
     * that window, with no future retry, since a later {@link #load()} will no longer read past
     * this false value. Use {@link #ensurePersistenceCheckStarted()} alone for those two outcomes.</p>
     *
     * <p>Neither an {@code ALREADY_CAUGHT_UP} outcome (this instance never itself fetched) nor a
     * {@code PERFORMED} outcome whose fetched range happened to be empty (nor the "already
     * essentially caught up" {@code diff <= 0} short-circuit above) ever calls {@link
     * #saveTimestamp}, so none of them touch {@link #serverTimestamp} on their own. Without this,
     * a newly caught-up instance that just confirmed a real floor value would have nothing
     * capturing it - not {@code serverTimestamp}, not its own local file, not even a push to the
     * shared freshness signal - until its own live Gerrit stream happens to deliver an event,
     * which may be arbitrarily far in the future.</p>
     *
     * @param candidateCatchUpFrom the cross-instance floor just confirmed by this call.
     */
    private void ensureBaselineCaptured(long candidateCatchUpFrom) {
        if (serverTimestamp == null) {
            serverTimestamp = new EventTimeSlice(candidateCatchUpFrom);
        }
        ensurePersistenceCheckStarted();
    }

    /**
     * Starts the persistence thread if it isn't already running - unlike {@link
     * #ensureBaselineCaptured}, safe to call regardless of whether this reconnect's own catch-up
     * attempt actually confirmed anything: an instance with nothing yet proven simply publishes
     * and persists nothing until it has something real to report (see {@link
     * GerritMissedEventsPlaybackPersistRunnable#run}'s own {@code Long.MIN_VALUE} handling), so
     * starting it early is harmless. The persistence thread is likewise only ever started from
     * {@link #gerritEvent} otherwise, so an instance that has never yet processed an event of its
     * own (live or replayed) needs it started here too, rather than waiting on that same first
     * event.
     */
    private void ensurePersistenceCheckStarted() {
        if (!persistenceCheck.isRunning()) {
            startPersistenceCheck();
        }
    }

    /**
     * @param a one candidate, possibly empty.
     * @param b another candidate, possibly empty.
     * @return the larger of the two if both are present, whichever one is present if only one is,
     *         or empty if neither is.
     */
    private static OptionalLong maxOptionalLong(OptionalLong a, OptionalLong b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return OptionalLong.of(Math.max(a.getAsLong(), b.getAsLong()));
    }

    /**
     * Fetches missed events from the given lower bound and feeds them into normal processing.
     * Invoked by the {@link MissedEventsCoordinationStrategy} while its coordination lock is
     * held, so this never runs concurrently with another JVM's catch-up for the same server -
     * but a peer may have run its own catch-up moments earlier and released the lock already, so
     * this fetch can still legitimately overlap a peer's just-completed one (see
     * {@link MissedEventsCoordinationStrategy#coordinateCatchUp}'s own "no redundant
     * re-triggering" contract). {@link #receivedEventCache} alone cannot catch that: it is a
     * per-JVM field, so it only dedupes a second fetch by this same instance, never a peer's -
     * {@code coordinationStrategy.claimEvent} closes that gap.
     *
     * @param lowerBound the point to fetch missed events from.
     * @param coordinationStrategy used to claim each about-to-be-triggered event across every JVM
     *         sharing this coordination mode, so a peer's own overlapping fetch recognizes it was
     *         already handled.
     * @return the new watermark plus how many events this call actually triggered - see
     *         {@link MissedEventsCatchUpResult}. Returns a {@link MissedEventsCatchUpResult#fetchFailed()}
     *         result, rather than throwing, if {@link #getEventsFromDateRange} could not complete
     *         the fetch at all - see that method's own javadoc for why.
     */
    private MissedEventsCatchUpResult fetchAndTriggerMissedEvents(
            Date lowerBound, MissedEventsCoordinationStrategy coordinationStrategy) {
        Optional<List<GerritTriggeredEvent>> fetchedEvents = getEventsFromDateRange(lowerBound);
        if (fetchedEvents.isEmpty()) {
            logger.warn("({}) Could not fetch missed events - treating this catch-up attempt as failed, "
                    + "not as confirmed empty.", serverName);
            return new MissedEventsCatchUpResult(OptionalLong.empty(), 0, true);
        }
        List<GerritTriggeredEvent> events = fetchedEvents.get();
        logger.info("({}) missed events to process for server: {} ...", events.size(), serverName);
        int triggeredCount = 0;
        for (GerritTriggeredEvent evt: events) {
            logger.debug("({}) Processing missed event {}", serverName, evt);
            boolean receivedEvtFound = false;
            synchronized (receivedEventCache) {
                // Must be in synchronized block
                for (GerritTriggeredEvent rEvt : receivedEventCache) {
                    if (rEvt.equals(evt)) {
                        receivedEvtFound = true;
                        break;
                    }
                }
            }
            if (receivedEvtFound) {
                logger.debug("({}) Event already triggered...skipping trigger.", serverName);
            } else {

                //do we have this event in the time slice already known to this instance?
                long currentEventCreatedTime = evt.getEventCreatedOn().getTime();
                if (serverTimestamp != null && serverTimestamp.getTimeSlice() == currentEventCreatedTime) {
                    if (serverTimestamp.getEvents().contains(evt)) {
                        logger.debug("({}) Event already triggered from time slice...skipping trigger.", serverName);
                        continue;
                    }
                }
                if (!coordinationStrategy.claimEvent(serverName, evt.toString())) {
                    logger.debug("({}) Event already claimed by a peer's own catch-up...skipping trigger.",
                            serverName);
                    receivedEventCache.add(evt);
                    continue;
                }
                logger.info("({}) Triggering: {}", serverName, evt);
                GerritServer server = PluginImpl.getServer_(serverName);
                if (server == null) {
                    logger.error("Server for {} could not be found. Skipping this event", serverName);
                    continue;
                }
                server.triggerEvent(evt);
                receivedEventCache.add(evt);
                triggeredCount++;
                logger.debug("Added event {} to received cache for server: {}", evt, serverName);
            }
        }
        // The latest eventCreatedOn actually present in this fetch's own response - never the
        // wall-clock instant this call started - so an events-log response that raced ahead of
        // that plugin's own indexing (and so doesn't yet contain the very event this catch-up
        // exists to find) leaves the watermark exactly where it was, rather than advancing past a
        // gap it never actually proved was covered. See MissedEventsCatchUpResult's own javadoc.
        OptionalLong newWatermark = events.stream().mapToLong(evt -> evt.getEventCreatedOn().getTime()).max();
        return new MissedEventsCatchUpResult(newWatermark, triggeredCount);
    }

    /**
     * Log when the connection goes down.
     */
    @Override
    public void connectionDown() {
        logger.info("connectionDown for server: {}", serverName);
        stopPersistenceCheck();
    }

    /**
     * This allows us to persist a last known alive time
     * for the server.
     * @param event Gerrit Event
     */
    @Override
    public void gerritEvent(GerritEvent event) {
        if (!isSupported()) {
            return;
        }
        if (!persistenceCheck.isRunning()) {
            startPersistenceCheck();
        }

        if (event instanceof GerritTriggeredEvent triggeredEvent) {
            logger.debug("Recording timestamp due to an event {} for server: {}", event, serverName);
            Provider provider = triggeredEvent.getProvider();

            if (provider != null) {
              String eventServer = provider.getName();
              if (!eventServer.equals(serverName)) {
                logger.debug("{} Ignoring event since it came from different server {}", serverName, eventServer);
                return;
              }
            }

            saveTimestamp(triggeredEvent);
            //add to cache
            // Also true while catchUpRetryPending, not just !playBackComplete: fetchAndTriggerMissedEvents's
            // own server.triggerEvent(evt) call re-enters this same listener, so the event a retry attempt
            // still needs receivedEventCache to recognize as already-delivered can arrive here again well
            // before that retry runs - see catchUpRetryPending's own javadoc for why treating that as
            // "playback complete, safe to reset" would silently defeat this cache for exactly the window it
            // exists to cover.
            if (!playBackComplete || catchUpRetryPending) {
                boolean receivedEvtFound = false;
                synchronized (this) {
                    // Must be in synchronized block
                    for (GerritTriggeredEvent rEvt : receivedEventCache) {
                        if (rEvt.equals(triggeredEvent)) {
                            receivedEvtFound = true;
                            break;
                        }
                    }
                }
                if (!receivedEvtFound) {
                    receivedEventCache.add(triggeredEvent);
                    logger.debug("Added event {} to received cache for server: {}", event, serverName);
                } else {
                    logger.debug("Event {} ALREADY in received cache for server: {}", event, serverName);
                }
            } else {
                receivedEventCache = Collections.synchronizedList(new ArrayList<>());
                logger.debug("Playback complete...will NOT add event {} to received cache for server: {}"
                        , event, serverName);
            }
        }
    }

    /**
     * Get events for a given lower bound date.
     *
     * @param lowerDate lower bound for which to request missed events.
     * @return the collection of Gerrit events, or empty if the fetch could not be completed at
     *         all (server config missing, URL could not be built, or {@link
     *         #getEventsFromEventsLogPlugin} itself failed) - deliberately distinct from a present
     *         but empty list, which means the fetch completed and genuinely found nothing. See
     *         {@link #getEventsFromEventsLogPlugin}'s own javadoc for why this is a plain return
     *         value rather than a thrown exception.
     */
    protected Optional<List<GerritTriggeredEvent>> getEventsFromDateRange(Date lowerDate) {

        GerritServer server = PluginImpl.getServer_(serverName);
        if (server == null) {
            logger.warn("({}) Could not fetch missed events - server config not found.", serverName);
            return Optional.empty();
        }
        IGerritHudsonTriggerConfig config = server.getConfig();

        String url;
        try {
            url = buildEventsLogURL(config, lowerDate);
        } catch (UnsupportedEncodingException e) {
            logger.warn("({}) Could not build missed-events query URL: {}", serverName, e.getMessage());
            return Optional.empty();
        }

        return getEventsFromEventsLogPlugin(config, url).map(this::createEventsFromString);
    }

    /**
     * Takes a string of json events and creates a collection.
     * @param eventsString Events in json in a string.
     * @return collection of events.
     */
    private List<GerritTriggeredEvent> createEventsFromString(String eventsString) {
        List<GerritTriggeredEvent> events = Collections.synchronizedList(new ArrayList<>());
        Scanner scanner = new Scanner(eventsString);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            logger.debug("found line: {}", line);
            JSONObject jsonObject = null;
            try {
                jsonObject = GerritJsonEventFactory.getJsonObjectIfInterestingAndUsable(line);
                if (jsonObject == null) {
                    continue;
                }
            } catch (Exception ex) {
                logger.warn("Unanticipated error when creating DTO representation of JSON string.", ex);
                continue;
            }
            GerritEvent evt = GerritJsonEventFactory.getEvent(jsonObject);
            if (evt instanceof GerritTriggeredEvent) {
                Provider provider = new Provider();
                provider.setName(serverName);
                ((GerritTriggeredEvent)evt).setProvider(provider);
                events.add((GerritTriggeredEvent)evt);
            }
        }
        scanner.close();
        return events;
    }

    /**
     * Fetches the raw events-log response body for {@code url}.
     *
     * <p>Deliberately never throws - a connection failure, a non-success status, or a body-read
     * failure here is a routine, expected condition (a network blip, Gerrit mid-restart, or the
     * events-log plugin briefly erroring), not an exceptional one, and this runs on {@code
     * GerritConnection}'s own background thread on every reconnect (including the very first one
     * at boot, via {@link #connectionEstablished()}) - modeling it as a thrown exception would
     * mean either a full stack trace logged on every occurrence (this can recur every retry
     * attempt and every subsequent reconnect until the underlying problem clears) or discarding
     * that detail just to keep logs quiet. Each failure path instead logs one concise line with
     * only the underlying message, and returns {@link Optional#empty()} - which {@link
     * #getEventsFromDateRange} and {@link #fetchAndTriggerMissedEvents} propagate as a {@link
     * MissedEventsCatchUpResult#fetchFailed()} result, letting {@link
     * MissedEventsCoordinationStrategy#coordinateCatchUp} report {@link
     * MissedEventsCatchUpOutcome#FAILED} - never {@link MissedEventsCatchUpOutcome#ALREADY_CAUGHT_UP}
     * - without any exception ever crossing a thread boundary or disrupting the connection this
     * runs on.</p>
     *
     * @param config Gerrit config for server.
     * @param url URL to use.
     * @return the response body, or empty if it could not be fetched at all - distinct from a
     *         present but empty body, which means events-log responded successfully and simply
     *         had nothing to report.
     */
    protected Optional<String> getEventsFromEventsLogPlugin(IGerritHudsonTriggerConfig config, String url) {
        logger.debug("({}) Going to GET: {}", serverName, url);

        HttpResponse execute;
        try {
            execute = HttpUtils.performHTTPGet(config, url);
        } catch (IOException e) {
            logger.warn("({}) Could not reach {} plugin at {}: {}", serverName, EVENTS_LOG_PLUGIN_NAME, url,
                    e.getMessage());
            return Optional.empty();
        }

        int statusCode = execute.getStatusLine().getStatusCode();
        logger.debug("Received status code: {} for server: {}", statusCode, serverName);

        if (statusCode != HttpURLConnection.HTTP_OK) {
            logger.warn("({}) Unexpected HTTP {} requesting missed events from {} plugin at {}",
                    serverName, statusCode, EVENTS_LOG_PLUGIN_NAME, url);
            return Optional.empty();
        }

        HttpEntity entity = execute.getEntity();
        if (entity == null) {
            logger.warn("({}) Empty response entity requesting missed events from {} plugin at {}",
                    serverName, EVENTS_LOG_PLUGIN_NAME, url);
            return Optional.empty();
        }
        try {
            ContentType contentType = ContentType.get(entity);
            if (contentType == null) {
                contentType = ContentType.DEFAULT_TEXT;
            }
            Charset charset = contentType.getCharset();
            if (charset == null) {
                charset = Charset.defaultCharset();
            }
            InputStream bodyStream = entity.getContent();
            String body = IOUtils.toString(bodyStream, charset);
            logger.debug(body);
            return Optional.of(body);
        } catch (IOException ioe) {
            logger.warn("({}) Failed reading response body from {} plugin at {}: {}",
                    serverName, EVENTS_LOG_PLUGIN_NAME, url, ioe.getMessage());
            return Optional.empty();
        }
    }

    /**
     *
     * @param config Gerrit Config for server.
     * @param date1 lower bound for date range,
     * @return url to use to request missed events.
     * @throws UnsupportedEncodingException if URL encoding not supported.
     */
    protected String buildEventsLogURL(IGerritHudsonTriggerConfig config, Date date1)
            throws UnsupportedEncodingException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        String url = EVENTS_LOG_PLUGIN_URL + "?t1=" + URLEncoder.encode(df.format(roundUpToWholeSecond(date1)),
                StandardCharsets.UTF_8);

        String gerritFrontEndUrl = config.getGerritFrontEndUrl();
        String restUrl = gerritFrontEndUrl;
        if (gerritFrontEndUrl != null && !gerritFrontEndUrl.endsWith("/")) {
            restUrl = gerritFrontEndUrl + "/";
        }
        return restUrl + url;
    }

    /**
     * Rounds {@code date1} up to the next whole second if it carries a sub-second component,
     * otherwise returns it unchanged.
     *
     * <p>The events-log plugin's own {@code t1} query parameter only has whole-second resolution
     * ({@code df} above has no millisecond pattern) - formatting a sub-second lower bound directly
     * would silently FLOOR it to the start of that second (e.g. a lower bound of {@code 33.628s}
     * becomes the string {@code "...:33"}, which the events-log plugin reads back as {@code
     * 33.000s}), re-widening the query to include everything already processed earlier in that
     * same second. That is exactly what let two replicas or nodes of the same logical instance,
     * reconnecting within the same second, both independently re-fetch and re-trigger
     * the identical missed event even though {@link MissedEventsCoordinationStrategy}'s own
     * millisecond-precision watermark had already correctly ordered them - only the separate,
     * per-event {@code EventClaimStrategy} layer kept that from becoming a duplicate build.
     * {@link #receivedEventCache} alone cannot catch this: it is a per-JVM field, so it only
     * dedupes a second fetch by the SAME instance, never a peer's.</p>
     *
     * <p>Rounding up - never down - trades a bounded, worst-case sub-one-second window (an event
     * landing between the true lower bound and the next whole second could be first seen by a
     * later catch-up rather than this one) for eliminating a guaranteed same-second duplicate
     * re-fetch/re-trigger. That trade is consistent with the precision already inherent elsewhere
     * in this subsystem - the persistence thread that publishes/refreshes the shared freshness
     * signal only ticks once per second to begin with (see {@code
     * GerritMissedEventsPlaybackPersistRunnable#CHECK_INTERVAL}).</p>
     *
     * @param date1 the candidate lower bound, possibly sub-second.
     * @return {@code date1} unchanged if already second-aligned, otherwise the next whole second.
     */
    private static Date roundUpToWholeSecond(Date date1) {
        long millis = date1.getTime();
        long wholeSeconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        if (TimeUnit.SECONDS.toMillis(wholeSeconds) == millis) {
            return date1;
        }
        return new Date(TimeUnit.SECONDS.toMillis(wholeSeconds + 1));
    }

    /**
     * Takes an event timestamp and saves it.
     * The thread persistenceCheck stores this to xml.
     * @param evt Gerrit Event to save.
     * @return true if successfully saved.
     */
    synchronized boolean saveTimestamp(GerritTriggeredEvent evt) {
        // If there is not timestamp, then ignore this event.
        if (evt == null || evt.getEventCreatedOn() == null) {
            logger.debug("'eventCreatedOn' is null; skipping event.");
            return false;
        }

        long ts = evt.getEventCreatedOn().getTime();
        // If the timestamp is invalid, then ignore this event.
        if (ts == 0) {
            logger.debug("'eventCreatedOn' is 0; skipping event.");
            return false;
        }

        if (serverTimestamp != null && ts < serverTimestamp.getTimeSlice()) {
            logger.debug("Event has same time slice {} or is earlier...NOT Updating time slice.", ts);
            return false;
        } else {
            if (serverTimestamp == null) {
                serverTimestamp = new EventTimeSlice(ts);
                serverTimestamp.addEvent(evt);
            } else {
                if (ts > serverTimestamp.getTimeSlice()) {
                    logger.debug("Current timestamp {} is GREATER than slice time {}.",
                            ts, serverTimestamp.getTimeSlice());
                    serverTimestamp = new EventTimeSlice(ts);
                    serverTimestamp.addEvent(evt);
                } else {
                    if (ts == serverTimestamp.getTimeSlice()) {
                        logger.debug("Current timestamp {} is EQUAL to slice time {}.",
                                ts, serverTimestamp.getTimeSlice());
                        serverTimestamp.addEvent(evt);
                    }
                }
            }
        }
        return true;
    }

    /**
     * Shutdown the listener.
     */
    public void shutdown() {
        GerritServer server = PluginImpl.getServer_(serverName);
        if (server != null) {
            server.removeListener((GerritEventListener)this);
        } else {
            logger.error("Could not find server {}", serverName);
        }
        stopPersistenceCheck();
    }

    /**
     * @return whether playback is supported.
     */
    public boolean isSupported() {
        return isSupported;
    }

    /**
     * Return server timestamp.
     * @return timestamp.
     */
    public EventTimeSlice getServerTimestamp() {
        return serverTimestamp;
    }

    /**
     * @param serverName The Name of the Gerrit Server to load config for.
     * @return XmlFile corresponding to the legacy shared gerrit-trigger-server-timestamps.xml.
     * @throws IOException if it occurs.
     * @deprecated kept only as the upgrade-compatibility fallback read path used by {@link
     *         InstanceTimestampStore#computeMaxTimestampAcrossInstances()}; new writes go
     *         to a per-JVM-instance file instead. See {@link InstanceTimestampStore}.
     */
    @Deprecated
    @CheckForNull
    public static XmlFile getConfigXml(String serverName) throws IOException {
        return new InstanceTimestampStore(serverName).getLegacyConfigXml();
    }

    @Override
    public String getDisplayName() {
        return StringUtil.getDefaultDisplayNameForSpecificServer(this, getServerName());
    }

    /**
     * Responsible for persisting timestamps to xml.
     * Time slices jump by 1000ms so the thread only checks every 1 second.
     */
    class GerritMissedEventsPlaybackPersistRunnable implements Runnable {
        private final AtomicBoolean running = new AtomicBoolean(false);
        private static final long CHECK_INTERVAL = 1000;
        private ScheduledExecutorService scheduler = jenkins.util.Timer.get();
        private ScheduledFuture<?> task = null;

        /**
         * Constructor.
         */
        GerritMissedEventsPlaybackPersistRunnable() {
        }

        /**
         * Start the persistence thread loop.
         */
        public synchronized void start() {
            if (!isRunning()) {
                running.set(true);
                task = scheduler.scheduleAtFixedRate(this, 0, CHECK_INTERVAL, TimeUnit.MILLISECONDS);
            }
        }

        /**
         * Stop the persistence thread loop.
         */
        public void stop() {
            if (isRunning()) {
                task.cancel(true);
                running.set(false);
            }
        }

        /**
         * @return if thread is currently running or not
         */
        public boolean isRunning() {
            return running.get();
        }

        @Override
        public void run() {
            // The proven floor (confirmedCatchUpFloor) is the baseline - before it exists at all
            // (brand-new environment, first connectionEstablished() not yet run), this falls back
            // to the raw, live-event-driven serverTimestamp to bootstrap the first publish/persist
            // cycle. But once a floor DOES exist, still take the max with serverTimestamp rather
            // than pinning to the floor alone: serverTimestamp keeps advancing on every live event
            // this instance receives (see saveTimestamp), and any such event timestamped past the
            // floor is itself proof this instance has not missed that point - not advancing this
            // published value to match would freeze it at whatever the LAST reconnect established
            // for as long as this connection stays up without reconnecting again, silently
            // understating this instance's true freshness to every peer relying on it (see
            // confirmedCatchUpFloor's own javadoc for the confirmed incident this caused).
            long liveTimeSlice = serverTimestamp != null ? serverTimestamp.getTimeSlice() : Long.MIN_VALUE;
            long ownTimeSlice = confirmedCatchUpFloor.isPresent()
                    ? Math.max(confirmedCatchUpFloor.getAsLong(), liveTimeSlice)
                    : liveTimeSlice;
            MissedEventsCoordinationStrategy coordinationStrategy =
                    CoordinationMode.get().getMissedEventsCoordinationStrategy();

            // Push this instance's own freshness up first, so peers can see it - independently
            // throttled from the local persist below, since the two can legitimately diverge
            // (e.g. this instance's own value hasn't moved, but a peer's has). publishInstanceFreshness
            // already merges against the shared value to decide whether to advance it, so its return
            // value doubles as this cycle's shared read - only fall back to a separate
            // getSharedInstanceFreshness round trip when publish was skipped and that read never happened.
            long sharedTimeSlice;
            if (ownTimeSlice > lastPublishedTimeSlice) {
                lastPublishedTimeSlice = ownTimeSlice;
                sharedTimeSlice = coordinationStrategy.publishInstanceFreshness(serverName, ownTimeSlice);
            } else {
                sharedTimeSlice = coordinationStrategy.getSharedInstanceFreshness(serverName).orElse(Long.MIN_VALUE);
            }

            // Persist the best currently-known value - this instance's own, or a fresher one
            // borrowed from a peer via the shared signal - to this instance's own local file, so a
            // later cold-start file scan sees cluster-wide freshness, not just what this specific
            // instance itself ever directly observed.
            long bestKnownTimeSlice = Math.max(ownTimeSlice, sharedTimeSlice);
            if (bestKnownTimeSlice > Long.MIN_VALUE && lastPersistedTimeSlice < bestKnownTimeSlice) {
                lastPersistedTimeSlice = bestKnownTimeSlice;
                persistTimeStamp(bestKnownTimeSlice);
            }
        }

        /**
         * Saves {@code timeSliceToPersist} to this instance's own xml file. When it exactly
         * matches {@link #serverTimestamp}'s own time slice, the real {@code serverTimestamp}
         * (with its known events) is persisted; otherwise - a value borrowed from a peer via the
         * shared freshness signal, or {@code ownTimeSlice} itself coming from {@link
         * #confirmedCatchUpFloor} rather than {@code serverTimestamp} - it's persisted as a bare
         * timestamp with no events. {@link InstanceTimestampStore#computeMaxTimestampAcrossInstances()}
         * only ever reads {@link EventTimeSlice#getTimeSlice()} back out, never the events list, so
         * this loses nothing that read path relies on. Deliberately checked against {@code
         * serverTimestamp} directly, not against the {@code ownTimeSlice} parameter this was
         * originally compared to: the two can now legitimately diverge (see {@link
         * #confirmedCatchUpFloor}'s own javadoc), and shallow-copying {@code serverTimestamp} under
         * a timestamp it doesn't actually match would silently attach the wrong events list to it.
         *
         * @param timeSliceToPersist the value to write - this instance's own, or a borrowed one.
         */
        private void persistTimeStamp(long timeSliceToPersist) {
            try {
                boolean matchesOwnServerTimestamp =
                        serverTimestamp != null && serverTimestamp.getTimeSlice() == timeSliceToPersist;
                EventTimeSlice toPersist = matchesOwnServerTimestamp
                        ? EventTimeSlice.shallowCopy(serverTimestamp)
                        : new EventTimeSlice(timeSliceToPersist);
                instanceTimestampStore.writeTimestamp(toPersist);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
