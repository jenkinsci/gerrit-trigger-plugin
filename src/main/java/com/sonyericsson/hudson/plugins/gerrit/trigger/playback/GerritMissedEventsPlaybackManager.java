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
import com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.CoordinationModeFactory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.InstanceIdentity;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.MissedEventsCatchUpOutcome;
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
     */
    @Override
    public void connectionEstablished() {
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
                CoordinationModeFactory.get().getMissedEventsCoordinationStrategy();
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
                this::fetchAndTriggerMissedEvents,
                () -> instanceTimestampStore.pruneStaleInstanceFiles(staleAgeMillis));
        logger.info("Missed-events catch-up outcome for server {}: {}", serverName, outcome);

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
     * held, so this never runs concurrently with another JVM's catch-up for the same server.
     *
     * @param lowerBound the point to fetch missed events from.
     * @return the epoch-millis timestamp this attempt started at, recorded as the new shared
     *         watermark on success.
     * @throws IOException if fetching or building the playback query URL fails.
     */
    private long fetchAndTriggerMissedEvents(Date lowerBound) throws IOException {
        long fetchStartedAt = System.currentTimeMillis();
        List<GerritTriggeredEvent> events = getEventsFromDateRange(lowerBound);
        logger.info("({}) missed events to process for server: {} ...", events.size(), serverName);
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
                logger.info("({}) Triggering: {}", serverName, evt);
                GerritServer server = PluginImpl.getServer_(serverName);
                if (server == null) {
                    logger.error("Server for {} could not be found. Skipping this event", serverName);
                    continue;
                }
                server.triggerEvent(evt);
                receivedEventCache.add(evt);
                logger.debug("Added event {} to received cache for server: {}", evt, serverName);
            }
        }
        return fetchStartedAt;
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

        String url = EVENTS_LOG_PLUGIN_URL + "?t1=" + URLEncoder.encode(df.format(date1), StandardCharsets.UTF_8);

        String gerritFrontEndUrl = config.getGerritFrontEndUrl();
        String restUrl = gerritFrontEndUrl;
        if (gerritFrontEndUrl != null && !gerritFrontEndUrl.endsWith("/")) {
            restUrl = gerritFrontEndUrl + "/";
        }
        return restUrl + url;
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
            long ownTimeSlice = serverTimestamp != null ? serverTimestamp.getTimeSlice() : Long.MIN_VALUE;
            MissedEventsCoordinationStrategy coordinationStrategy =
                    CoordinationModeFactory.get().getMissedEventsCoordinationStrategy();

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
                persistTimeStamp(bestKnownTimeSlice, ownTimeSlice);
            }
        }

        /**
         * Saves {@code timeSliceToPersist} to this instance's own xml file. When it equals this
         * instance's own {@code ownTimeSlice}, the real {@link #serverTimestamp} (with its known
         * events) is persisted, same as before this method took parameters; otherwise it's a value
         * borrowed from a peer via the shared freshness signal, persisted as a bare timestamp with
         * no events - {@link InstanceTimestampStore#computeMaxTimestampAcrossInstances()} only
         * ever reads {@link EventTimeSlice#getTimeSlice()} back out, never the events list, so
         * this loses nothing that read path relies on.
         *
         * @param timeSliceToPersist the value to write - this instance's own, or a borrowed one.
         * @param ownTimeSlice this instance's own current time slice, for comparison.
         */
        private void persistTimeStamp(long timeSliceToPersist, long ownTimeSlice) {
            try {
                EventTimeSlice toPersist = timeSliceToPersist == ownTimeSlice
                        ? EventTimeSlice.shallowCopy(serverTimestamp)
                        : new EventTimeSlice(timeSliceToPersist);
                instanceTimestampStore.writeTimestamp(toPersist);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
