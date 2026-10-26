/*
 *  The MIT License
 *
 *  Copyright 2026 versus. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.job.rest;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritChangeStatus;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Resolves the other changes in the topic of an event, so that REST notifications
 * can be sent to all of them when "Vote patch sets with same topic" is enabled.
 * Mirrors what the SSH notifications do in
 * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.notification.NotificationBuildCompleted}.
 */
final class TopicChangeEvents {

    private static final Logger logger = LoggerFactory.getLogger(TopicChangeEvents.class);

    /**
     * Utility class.
     */
    private TopicChangeEvents() {
    }

    /**
     * Returns events for the current patch sets of the other open changes in the same topic.
     *
     * @param config the server config.
     * @param event  the event that triggered the build.
     * @return the events, or an empty list if same topic voting is disabled or the change has no topic.
     */
    static List<ChangeBasedEvent> of(IGerritHudsonTriggerConfig config, ChangeBasedEvent event) {
        if (!config.isVoteSameTopic()) {
            return Collections.emptyList();
        }
        Topic topic = event.getChange().getTopicObject();
        if (topic == null) {
            return Collections.emptyList();
        }
        GerritServer server = PluginImpl.getServer_(event);
        if (server == null) {
            logger.error("Could not get gerrit server based on event!");
            return Collections.emptyList();
        }

        List<ChangeBasedEvent> events = new ArrayList<>();
        for (Map.Entry<Change, PatchSet> entry : topic.getChanges(server.getQueryHandler()).entrySet()) {
            Change change = entry.getKey();
            if (change.equals(event.getChange()) || change.getStatus() == GerritChangeStatus.ABANDONED) {
                continue;
            }
            ChangeBasedEvent topicEvent = new PatchsetCreated();
            topicEvent.setAccount(event.getAccount());
            topicEvent.setProvider(event.getProvider());
            topicEvent.setReceivedOn(event.getReceivedOn());
            topicEvent.setChange(change);
            topicEvent.setPatchset(entry.getValue());
            events.add(topicEvent);
        }
        return events;
    }
}
