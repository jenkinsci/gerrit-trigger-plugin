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
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.GerritPluginChecker;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.HttpUtils;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

import javax.annotation.CheckForNull;

import jenkins.model.Jenkins;


/**
 * The GerritMissedEventsPlaybackManager is responsible for recording a last-alive timestamp
 * for each server connection. The motivation is that we want to be able to know when we last
 * received an event. This will help us determine upon connection startup, if we have missed
 * some events while the connection was down.
 *
 * Once the server is re-connected, the missed event will be played back as if they had been
 * received orginally.
 *
 * @author scott.hebert@ericsson.com
 */
public class GerritMissedEventsPlaybackManager implements ConnectionListener, GerritEventListener {

    private static final String GERRIT_SERVER_EVENT_DATA_FOLDER = "/gerrit-server-event-data/";
    private static final Logger logger = LoggerFactory.getLogger(GerritMissedEventsPlaybackManager.class);
    static final String EVENTS_LOG_PLUGIN_NAME = "events-log";
    private static final String EVENTS_LOG_PLUGIN_URL = "a/plugins/" + EVENTS_LOG_PLUGIN_NAME + "/events/";
    private static final String GERRIT_TRIGGER_SERVER_TIMESTAMPS_XML = "gerrit-trigger-server-timestamps.xml";

    private String serverName;
    /**
     * Server Timestamp.
     */
    protected EventTimeSlice serverTimestamp = null;
    /**
     * List that contains received Gerrit Events.
     */
    protected List<GerritTriggeredEvent> receivedEventCache
        = Collections.synchronizedList(new ArrayList<GerritTriggeredEvent>());

    private boolean isSupported = false;
    private boolean playBackComplete = false;

    /**
     * @param name Gerrit Server Name.
     */
    public GerritMissedEventsPlaybackManager(String name) {
        this.serverName = name;
        checkIfEventsLogPluginSupported();
    }

    /**
     * method to verify if plugin is supported.
     */
    public void checkIfEventsLogPluginSupported() {
        GerritServer server = PluginImpl.getServer_(serverName);
        if (server != null && server.getConfig() != null) {
            isSupported = GerritPluginChecker.isPluginEnabled(
                    server.getConfig(), EVENTS_LOG_PLUGIN_NAME);
        }
    }

    /**
     * Load in the last-alive Timestamp file.
     * @throws IOException is we cannot unmarshal.
     */
    protected void load() throws IOException {
        XmlFile xml = getConfigXml(serverName);
        if (xml.exists()) {
            serverTimestamp  = (EventTimeSlice)xml.unmarshal(serverTimestamp);
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
     * When the connection is established, we load in the last-alive
     * timestamp for this server and try to determine if a time range
     * exist whereby we missed some events. If so, request the events
     * from the Gerrit events-log plugin and pump them in to play them back.
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
        Date timeStampDate = getDateFromTimestamp();
        long diff = System.currentTimeMillis() - timeStampDate.getTime();
        if (diff > 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("Non-zero date range from last-alive timestamp exists for server {} : {}"
                        , serverName, Util.getPastTimeString(diff));
            }
        } else {
            logger.debug("Zero date range from last-alive timestamp for server {}", serverName);
            playBackComplete = true;
            return;
        }
        try {
            List<GerritTriggeredEvent> events = getEventsFromDateRange(timeStampDate);
            logger.info("({}) missed events to process for server: {} ...", events.size(), serverName);
            for (GerritTriggeredEvent evt: events) {
                logger.debug("({}) Processing missed event {}", serverName, evt);
                boolean receivedEvtFound = false;
                synchronized (receivedEventCache) {
                  Iterator<GerritTriggeredEvent> i = receivedEventCache.iterator(); // Must be in synchronized block
                  while (i.hasNext()) {
                      GerritTriggeredEvent rEvt = i.next();
                      if (rEvt.equals(evt)) {
                        receivedEvtFound = true;
                        break;
                      }
                  }
                }
                if (receivedEvtFound) {
                    logger.debug("({}) Event already triggered...skipping trigger.", serverName);
                } else {

                    //do we have this event in the time slice?
                    long currentEventCreatedTime = evt.getEventCreatedOn().getTime();
                    if (serverTimestamp.getTimeSlice() == currentEventCreatedTime) {
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
        } catch (UnsupportedEncodingException e) {
            logger.error("Error building URL for playback query: " + e.getMessage(), e);
        } catch (IOException e) {
            logger.error("Error accessing URL for playback query: " + e.getMessage(), e);
        }
        playBackComplete = true;
        logger.info("Processing completed for server: {}", serverName);
    }

    /**
     * Log when the connection goes down.
     */
    @Override
    public void connectionDown() {
        logger.info("connectionDown for server: {}", serverName);
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

        if (event instanceof GerritTriggeredEvent) {
            logger.debug("Recording timestamp due to an event {} for server: {}", event, serverName);
            GerritTriggeredEvent triggeredEvent = (GerritTriggeredEvent)event;
            persist(triggeredEvent);
            //add to cache
            if (!playBackComplete) {
                boolean receivedEvtFound = false;
                synchronized (receivedEventCache) {
                  Iterator<GerritTriggeredEvent> i = receivedEventCache.iterator(); // Must be in synchronized block
                  while (i.hasNext()) {
                      GerritTriggeredEvent rEvt = i.next();
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
                receivedEventCache = Collections.synchronizedList(new ArrayList<GerritTriggeredEvent>());
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
            return Collections.synchronizedList(new ArrayList<GerritTriggeredEvent>());
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
        List<GerritTriggeredEvent> events = Collections.synchronizedList(new ArrayList<GerritTriggeredEvent>());
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
                    String body = IOUtils.toString(bodyStream, charset.name());
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

        String url = EVENTS_LOG_PLUGIN_URL + "?t1=" + URLEncoder.encode(df.format(date1), "UTF-8");

        String gerritFrontEndUrl = config.getGerritFrontEndUrl();
        String restUrl = gerritFrontEndUrl;
        if (gerritFrontEndUrl != null && !gerritFrontEndUrl.endsWith("/")) {
            restUrl = gerritFrontEndUrl + "/";
        }
        return restUrl + url;
    }

    /**
     * Takes a timestamp and persists to xml file.
     * @param evt Gerrit Event to persist.
     * @return true if was able to persist event.
     */
    synchronized boolean persist(GerritTriggeredEvent evt) {

        if (evt == null || evt.getEventCreatedOn() == null) {
            logger.warn("Event CreatedOn is null...Gerrit Server might not support attribute eventCreatedOn. "
                    + "Will NOT persist this event and Missed Events will be disabled!");
            isSupported = false;
            return false;
        }

        long ts = evt.getEventCreatedOn().getTime();
        if (ts == 0) {
            logger.warn("Event CreatedOn is 0...Gerrit Server does not support attribute eventCreatedOn. "
                    + "Will NOT persist this event and Missed Events will be disabled!");
            isSupported = false;
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

        try {
            XmlFile config = getConfigXml(serverName);
            config.write(serverTimestamp);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return false;
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
     * @return XmlFile corresponding to gerrit-trigger-server-timestamps.xml.
     * @throws IOException if it occurs.
     */
    @CheckForNull
    public static XmlFile getConfigXml(String serverName) throws IOException {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return null;
        }

        File dataDir = new File(jenkins.getRootDir(), GERRIT_SERVER_EVENT_DATA_FOLDER);
        File serverDataDir = new File(dataDir, serverName);
        serverDataDir.mkdirs();
        File xmlFile = new File(serverDataDir, GERRIT_TRIGGER_SERVER_TIMESTAMPS_XML);

        return new XmlFile(Jenkins.XSTREAM, xmlFile);
    }

}
