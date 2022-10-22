/*
 *  The MIT License
 *
 *  Copyright 2022 Christoph Kreisl. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.notification;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ParameterExpander;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

/**
 * Abstract class for Notification send to Gerrit.
 */
public abstract class Notification implements INotification {

    private static final Logger logger = LoggerFactory.getLogger(Notification.class);

    /**
     * The Gerrit event.
     */
    protected GerritTriggeredEvent gerritEvent;

    /**
     * The parameter expander.
     */
    protected ParameterExpander parameterExpander;

    /**
     * Abstract constructor.
     *
     * @param parameterExpander the parameter expander.
     * @param gerritEvent the gerrit event.
     */
    public Notification(ParameterExpander parameterExpander, GerritTriggeredEvent gerritEvent) {
        this.parameterExpander = parameterExpander;
        this.gerritEvent = gerritEvent;
    }

    /**
     * Returns true if voteSameTopic is enabled, otherwise false.
     *
     * @return true of false based on Gerrit config.
     */
    @Override
    public boolean isVoteSameTopic() {
        IGerritHudsonTriggerConfig config = PluginImpl.getServerConfig(gerritEvent);
        if (config == null) {
            logger.error("Could not get server config!");
            return false;
        }
        return config.isVoteSameTopic();
    }

    /**
     * Returns a map of changes and patchsets which are assigned to a topic.
     *
     * @return Map of changes and patchsets assigned to a topic
     */
    public Map<Change, PatchSet> queryTopicChanges() {

        ChangeBasedEvent event = (ChangeBasedEvent)gerritEvent;
        Topic topic = event.getChange().getTopicObject();

        if (topic == null) {
            logger.error("Topic is null not querying changes!");
            return Collections.emptyMap();
        }

        GerritServer server = PluginImpl.getServer_(event);
        if (server == null) {
            logger.error("Could not get gerrit server based on event!");
            return Collections.emptyMap();
        }

        return topic.getChanges(server.getQueryHandler());
    }

    /**
     * Returns a GerritTrigger event with event information based on change and patchset.
     *
     * @param event the event.
     * @param change the change.
     * @param patchSet the patchset.
     * @return Returns GerritTrigger event
     */
    protected GerritTriggeredEvent createEventTopicChange(GerritTriggeredEvent event,
                                                          Change change, PatchSet patchSet) {
        ChangeBasedEvent eventTopicChange = new PatchsetCreated();
        eventTopicChange.setAccount(event.getAccount());
        eventTopicChange.setProvider(event.getProvider());
        eventTopicChange.setReceivedOn(event.getReceivedOn());
        eventTopicChange.setChange(change);
        eventTopicChange.setPatchset(patchSet);
        return eventTopicChange;
    }
}
