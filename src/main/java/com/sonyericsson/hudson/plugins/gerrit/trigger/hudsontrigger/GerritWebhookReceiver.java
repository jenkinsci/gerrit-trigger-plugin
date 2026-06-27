/*
 *  The MIT License
 *
 *  Copyright 2024 Sony Mobile Communications Inc. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonymobile.tools.gerrit.gerritevents.GerritJsonEventFactory;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Provider;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Receives Gerrit events via webhook (HTTP POST) and injects them
 * into the same event processing pipeline used by SSH stream-events
 * and HTTPS polling.
 *
 * <p>Gerrit's webhook plugin or events-log plugin sends events in
 * the standard stream-events JSON format (same format the
 * {@code gerrit stream-events} SSH command emits). This receiver
 * parses the payload, attaches the correct {@link Provider},
 * and posts the event to the server's event handler.</p>
 *
 * <p>The webhook endpoint is exposed via {@code GerritManagement}
 * at <code>POST /gerrit-trigger/webhook?server={name}</code>.</p>
 *
 * @author Michael Trimarchi
 */
public final class GerritWebhookReceiver {

    /**
     * The protocol scheme name for webhook transport.
     */
    public static final String GERRIT_PROTOCOL_SCHEME_NAME = "webhook";

    private static final Logger LOGGER = Logger.getLogger(GerritWebhookReceiver.class.getName());

    /**
     * Private constructor to prevent instantiation.
     */
    private GerritWebhookReceiver() {
    }

    /**
     * Processes a webhook payload (from Gerrit webhook or events-log plugin)
     * and posts events to the given server's handler.
     *
     * <p>The payload may be a single stream-events JSON object or a
     * JSON array of such objects (batched by the events-log plugin).</p>
     *
     * @param payload the raw JSON payload.
     * @param server the Gerrit server to route events to.
     * @return the number of events processed.
     */
    @Restricted(NoExternalUse.class)
    public static int processPayload(String payload, GerritServer server) {
        Provider provider = createProvider(server);
        String trimmed = payload.trim();

        if (trimmed.startsWith("[")) {
            return processArray(trimmed, server, provider);
        } else if (trimmed.startsWith("{")) {
            return processSingleEvent(trimmed, server, provider) ? 1 : 0;
        } else {
            LOGGER.warning("Webhook payload is not valid JSON: " + trimmed);
            return 0;
        }
    }

    /**
     * Processes a JSON array of events (batched webhook payload).
     */
    private static int processArray(String jsonArray, GerritServer server, Provider provider) {
        int count = 0;
        try {
            JSONArray array = (JSONArray)JSONSerializer.toJSON(jsonArray);
            for (int i = 0; i < array.size(); i++) {
                JSONObject eventJson = array.getJSONObject(i);
                if (processSingleJsonEvent(eventJson, server, provider)) {
                    count++;
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to parse webhook array payload", ex);
        }
        return count;
    }

    /**
     * Processes a single event JSON string.
     */
    private static boolean processSingleEvent(String jsonString, GerritServer server,
            Provider provider) {
        try {
            if (!GerritJsonEventFactory.isInterestingAndUsable(jsonString)) {
                LOGGER.fine("Webhook event is not interesting: " + jsonString);
                return false;
            }
            JSONObject jsonObject = (JSONObject)JSONSerializer.toJSON(jsonString);
            return processSingleJsonEvent(jsonObject, server, provider);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to process webhook event", ex);
            return false;
        }
    }

    /**
     * Processes a single parsed JSON event object, attaches the provider,
     * and posts it to the server's event handler.
     */
    private static boolean processSingleJsonEvent(JSONObject jsonEvent,
            GerritServer server, Provider provider) {
        try {
            com.sonymobile.tools.gerrit.gerritevents.dto.GerritJsonEvent event =
                    GerritJsonEventFactory.getEvent(jsonEvent);
            if (event instanceof GerritTriggeredEvent) {
                GerritTriggeredEvent gev = (GerritTriggeredEvent)event;
                if (gev.getProvider() == null) {
                    gev.setProvider(provider);
                }
                gev.setReceivedOn(System.currentTimeMillis());
            }
            server.triggerEvent(
                    (com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)event);
            LOGGER.fine("Webhook event posted: "
                    + ((com.sonymobile.tools.gerrit.gerritevents.dto.GerritJsonEvent)event)
                            .getEventType().getTypeValue());
            return true;
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to create event from webhook JSON", ex);
            return false;
        }
    }

    /**
     * Creates a Provider for the given server reflecting webhook transport.
     *
     * @param server the Gerrit server.
     * @return a Provider.
     */
    private static Provider createProvider(GerritServer server) {
        return new Provider(
                server.getName(),
                server.getConfig().getGerritHostName(),
                String.valueOf(server.getConfig().getGerritSshPort()),
                GERRIT_PROTOCOL_SCHEME_NAME,
                server.getConfig().getGerritFrontEndUrl(),
                server.getGerritVersion() != null ? server.getGerritVersion() : ""
        );
    }
}
