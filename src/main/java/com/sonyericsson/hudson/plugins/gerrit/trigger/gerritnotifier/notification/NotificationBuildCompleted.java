/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ParameterExpander;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritChangeStatus;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.Topic;
import hudson.model.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Wrapper for the Commands and Notifications send to gerrit if build is completed.
 */
public class NotificationBuildCompleted extends Notification {

    private static final Logger logger = LoggerFactory.getLogger(NotificationBuildCompleted.class);
    private final BuildMemory.MemoryImprint memoryImprint;
    private final TaskListener listener;
    private NotificationCommands commands;

    /**
     * Constructor.
     *
     * @param memoryImprint the memoryImprint.
     * @param listener the task listener.
     * @param parameterExpander the parameter expander.
     */
    public NotificationBuildCompleted(BuildMemory.MemoryImprint memoryImprint, TaskListener listener,
                                      ParameterExpander parameterExpander) {
        super(parameterExpander, memoryImprint.getEvent());
        this.memoryImprint = memoryImprint;
        this.listener = listener;
        initCommands();
    }

    /**
     * Init NotificationCommands object.
     */
    private void initCommands() {
        ChangeBasedEvent event = (ChangeBasedEvent)gerritEvent;
        String command = parameterExpander.getBuildCompletedCommand(
                memoryImprint, listener, null);

        this.commands = new NotificationCommands(command);
        Topic topic = event.getChange().getTopicObject();

        if (topic == null) {
            return;
        }

        if (!isVoteSameTopic()) {
            return;
        }

        Map<Change, PatchSet> changes = queryTopicChanges();
        for (Map.Entry<Change, PatchSet> entry : changes.entrySet()) {
            Change change = entry.getKey();
            if (change.equals(event.getChange())) {
                continue;
            }
            if (change.getStatus() == GerritChangeStatus.ABANDONED) {
                continue;
            }
            PatchSet patchSet = entry.getValue();

            // Create dummy PatchsetCreated event and fill with original event content
            // Change and Patchset will be overwritten with information from change assigned in topic
            // So that ParameterExpander takes this event into account.
            GerritTriggeredEvent eventTopicChange = createEventTopicChange(event, change, patchSet);
            String topicChangeCommand = parameterExpander.getBuildCompletedCommand(
                    memoryImprint, listener, eventTopicChange);

            this.commands.addTopicChangeCommand(topicChangeCommand);
        }
    }

    @Override
    public NotificationCommands getCommands() {
        return commands;
    }

    @Override
    public boolean isValid() {
        return commands.isValid();
    }
}
