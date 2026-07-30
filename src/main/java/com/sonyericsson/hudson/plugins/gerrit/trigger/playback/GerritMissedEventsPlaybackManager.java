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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.OptionalLong;
import java.util.Scanner;
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
    private static final int DEFAULT_STALE_INSTANCE_FILE_AGE_HOURS = 168;
    /**
     * Used by {@link #roundUpToWholeSecond} to round a sub-second catch-up lower bound up to the
     * whole-second resolution the events-log plugin's own {@code t1} query parameter is limited to.
     */
    private static final long MILLIS_PER_SECOND = 1000L;

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
     * <p>Deliberately never advanced by {@link #gerritEvent}/{@link #saveTimestamp} - only by
     * {@link #connectionEstablished()} itself, once it has confirmed (via the "already fresh"
     * shortcut, or via {@link MissedEventsCoordinationStrategy#getWatermark}'s own already-proven
     * value after a catch-up attempt) that this point is genuinely safe to advertise. An ordinary
     * live event proves only that this JVM is currently receiving events, not that everything
     * before it - including whatever this same reconnect's own catch-up may have just missed -
     * has been accounted for.</p>
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

        long staleAgeMillis = TimeUnit.HOURS.toMillis(
                Integer.getInteger(STALE_INSTANCE_FILE_AGE_PROPERTY, DEFAULT_STALE_INSTANCE_FILE_AGE_HOURS));
        MissedEventsCatchUpOutcome outcome = coordinationStrategy.coordinateCatchUp(
                serverName,
                candidateCatchUpFrom,
                lowerBound -> fetchAndTriggerMissedEvents(lowerBound, coordinationStrategy),
                () -> instanceTimestampStore.pruneStaleInstanceFiles(staleAgeMillis));
        logger.info("Missed-events catch-up outcome for server {}: {}", serverName, outcome);

        // Whatever the coordination strategy's own watermark now holds is, by construction, only
        // ever advanced to a point it has actually observed events up to (see that watermark's own
        // "empty means nothing proven" contract) - safe to advertise regardless of whether this
        // specific call was the one that advanced it, or a peer's concurrent attempt did.
        coordinationStrategy.getWatermark(serverName).ifPresent(this::advanceConfirmedCatchUpFloor);

        ensureBaselineCaptured(candidateCatchUpFrom);

        playBackComplete = true;
        logger.info("Processing completed for server: {}", serverName);
    }

    /**
     * Ensures this instance's own known baseline reflects at least {@code candidateCatchUpFrom} -
     * the cross-instance floor just confirmed by this {@link #connectionEstablished()} call - and
     * that the persistence thread is running to push/pull it immediately.
     *
     * <p>Neither an {@code ALREADY_CAUGHT_UP} outcome (this instance never itself fetched) nor a
     * {@code PERFORMED} outcome whose fetched range happened to be empty (nor the "already
     * essentially caught up" {@code diff <= 0} short-circuit above) ever calls {@link
     * #saveTimestamp}, so none of them touch {@link #serverTimestamp} on their own. Without this,
     * a newly caught-up instance that just confirmed a real floor value would have nothing
     * capturing it - not {@code serverTimestamp}, not its own local file, not even a push to the
     * shared freshness signal - until its own live Gerrit stream happens to deliver an event,
     * which may be arbitrarily far in the future. The persistence thread is likewise only ever
     * started from {@link #gerritEvent}, so an instance that has never yet processed an event of
     * its own (live or replayed) needs it started here too, rather than waiting on that same
     * first event.</p>
     *
     * @param candidateCatchUpFrom the cross-instance floor computed for this call.
     */
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

    private void ensureBaselineCaptured(long candidateCatchUpFrom) {
        if (serverTimestamp == null) {
            serverTimestamp = new EventTimeSlice(candidateCatchUpFrom);
        }
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
     *         {@link MissedEventsCatchUpResult}.
     * @throws IOException if fetching or building the playback query URL fails.
     */
    private MissedEventsCatchUpResult fetchAndTriggerMissedEvents(
            Date lowerBound, MissedEventsCoordinationStrategy coordinationStrategy) throws IOException {
        List<GerritTriggeredEvent> events = getEventsFromDateRange(lowerBound);
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
            if (!playBackComplete) {
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
     * @param lowerDate lower bound for which to request missed events.
     * @return collection of gerrit events.
     * @throws IOException if HTTP errors occur
     */
    protected List<GerritTriggeredEvent> getEventsFromDateRange(Date lowerDate) throws IOException {

        GerritServer server = PluginImpl.getServer_(serverName);
        if (server == null) {
            logger.error("Server for {} could not be found.", serverName);
            return Collections.synchronizedList(new ArrayList<>());
        }
        IGerritHudsonTriggerConfig config = server.getConfig();

        String events = getEventsFromEventsLogPlugin(config, buildEventsLogURL(config, lowerDate));

        return createEventsFromString(events);
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
     *
     * @param config Gerrit config for server.
     * @param url URL to use.
     * @return String of gerrit events.
     */
    protected String getEventsFromEventsLogPlugin(IGerritHudsonTriggerConfig config, String url) {
        logger.debug("({}) Going to GET: {}", serverName, url);

        HttpResponse execute = null;
        try {
            execute = HttpUtils.performHTTPGet(config, url);
        } catch (IOException e) {
            logger.warn(e.getMessage(), e);
            return "";
        }

        int statusCode = execute.getStatusLine().getStatusCode();
        logger.debug("Received status code: {} for server: {}", statusCode, serverName);

        if (statusCode == HttpURLConnection.HTTP_OK) {
            try {
                HttpEntity entity = execute.getEntity();
                if (entity != null) {
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
                    return body;
                }
            } catch (IOException ioe) {
                logger.warn(ioe.getMessage(), ioe);
            }
        }
        logger.warn("Not successful at requesting missed events from {} plugin. (errorcode: {})",
                EVENTS_LOG_PLUGIN_NAME, statusCode);
        return "";
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
        long remainder = millis % MILLIS_PER_SECOND;
        if (remainder == 0) {
            return date1;
        }
        return new Date(millis - remainder + MILLIS_PER_SECOND);
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
            // Prefer the proven floor once one exists (see its own javadoc for why) - only before
            // this instance's first connectionEstablished() has run at all (a brand-new
            // environment with no prior cross-instance signal) does this fall back to the raw,
            // live-event-driven serverTimestamp, purely to bootstrap the very first publish/persist
            // cycle.
            long ownTimeSlice = confirmedCatchUpFloor.isPresent()
                    ? confirmedCatchUpFloor.getAsLong()
                    : (serverTimestamp != null ? serverTimestamp.getTimeSlice() : Long.MIN_VALUE);
            MissedEventsCoordinationStrategy coordinationStrategy =
                    CoordinationMode.get().getMissedEventsCoordinationStrategy();

            // Push this instance's own freshness up first, so peers can see it - independently
            // throttled from the local persist below, since the two can legitimately diverge
            // (e.g. this instance's own value hasn't moved, but a peer's has).
            if (ownTimeSlice > lastPublishedTimeSlice) {
                lastPublishedTimeSlice = ownTimeSlice;
                coordinationStrategy.publishInstanceFreshness(serverName, ownTimeSlice);
            }

            // Persist the best currently-known value - this instance's own, or a fresher one
            // borrowed from a peer via the shared signal - to this instance's own local file, so a
            // later cold-start file scan sees cluster-wide freshness, not just what this specific
            // instance itself ever directly observed.
            long sharedTimeSlice = coordinationStrategy.getSharedInstanceFreshness(serverName).orElse(Long.MIN_VALUE);
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
