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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier;

import com.sonymobile.tools.gerrit.gerritevents.GerritCmdRunner;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.Topic;

import java.util.Collections;
import java.util.Map;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.extensions.GerritTriggeredBuildListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Start position that notifies Gerrit of events.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritNotifier {

    private static final Logger logger = LoggerFactory.getLogger(GerritNotifier.class);
    private GerritCmdRunner cmdRunner;
    private ParameterExpander parameterExpander;

    /**
     * Constructor.
     * @param config the config.
     * @param cmdRunner the command-runner.
     */
    public GerritNotifier(IGerritHudsonTriggerConfig config, GerritCmdRunner cmdRunner) {
        this.cmdRunner = cmdRunner;
        this.parameterExpander = new ParameterExpander(config);
    }

    /**
     * Constructor for testing.
     * @param config the config.
     * @param cmdRunner the command-runner.
     * @param jenkins the Jenkins instance.
     */
    public GerritNotifier(IGerritHudsonTriggerConfig config, GerritCmdRunner cmdRunner, Jenkins jenkins) {
        this.cmdRunner = cmdRunner;
        this.parameterExpander = new ParameterExpander(config, jenkins);
    }

    /**
     * Wrapper for sending a command to Gerrit
     *
     * @param command The command send to Gerrit
     */
    private void sendCommandToGerrit(String command) {
        if (command != null) {
            if (!command.isEmpty()) {
                logger.debug("Notifying BuildStarted to gerrit: {}", command);
                cmdRunner.sendCommand(command);
            } else {
                logger.debug("BuildStarted command is empty. Gerrit will not be notified of BuildStarted");
            }
        } else {
            logger.error("Something wrong during parameter extraction. "
                    + "Gerrit will not be notified of BuildStarted");
        }
    }

    /**
     * Returns all assigned changes related to a Gerrit topic.
     *
     * @param event the event.
     * @return Returns map of changes, if no changes an empty map is returned.
     */
    private Map<Change, PatchSet> queryTopicChanges(ChangeBasedEvent event) {
        Topic topic = event.getChange().getTopicObject();
        if (topic != null) {
            GerritServer server = PluginImpl.getServer_(event);
            if (server != null) {
                return topic.getChanges(server.getQueryHandler());
            } else {
                logger.error("Could get gerrit server based on event!");
            }
        }
        return Collections.emptyMap();
    }

    /**
     * Send build started message Gerrit changes assigned to a topic.
     *
     * @param build the build.
     * @param taskListener the task listener
     * @param event the event.
     * @param stats the build stats.
     */
    private void notifySameTopicBuildStarted(Run build, TaskListener taskListener,
                                             GerritTriggeredEvent event, BuildsStartedStats stats) {
        ChangeBasedEvent changeBasedEvent = (ChangeBasedEvent)event;
        IGerritHudsonTriggerConfig config = PluginImpl.getServerConfig(event);
        if (config != null) {
            if (config.isVoteSameTopic()) {
                Map<Change, PatchSet> changes = queryTopicChanges(changeBasedEvent);
                for (Map.Entry<Change, PatchSet> entry : changes.entrySet()) {
                    Change change = entry.getKey();
                    if (change.equals(changeBasedEvent.getChange())) {
                        continue;
                    }
                    PatchSet patchSet = entry.getValue();
                    String command = parameterExpander.getBuildStartedCommand(
                            build, taskListener, changeBasedEvent, stats, change, patchSet);
                    sendCommandToGerrit(command);
                }
            }
        } else {
            logger.error("Could not get server config.");
        }
    }

    /**
     * Sends build completed notification to all topic assigned patches.
     *
     * @param memoryImprint build memory imprint.
     * @param listener the listener.
     */
    private void notifySameTopicBuildCompleted(MemoryImprint memoryImprint, TaskListener listener) {
        ChangeBasedEvent changeBasedEvent = (ChangeBasedEvent)memoryImprint.getEvent();
        IGerritHudsonTriggerConfig config = PluginImpl.getServerConfig(changeBasedEvent);
        if (config != null) {
            if (config.isVoteSameTopic()) {
                Map<Change, PatchSet> changes = queryTopicChanges(changeBasedEvent);
                for (Map.Entry<Change, PatchSet> entry : changes.entrySet()) {
                    Change change = entry.getKey();
                    if (change.equals(changeBasedEvent.getChange())) {
                        continue;
                    }
                    PatchSet patchSet = entry.getValue();
                    String command = parameterExpander.getBuildCompletedCommand(
                            memoryImprint, listener, change, patchSet);
                    sendCommandToGerrit(command);
                }
            }
        } else {
            logger.error("Could not get server config.");
        }
    }

    /**
     * Generates the build-started command based on configured templates and build-values and sends it to Gerrit.
     * @param build the build.
     * @param taskListener the taskListener.
     * @param event the event.
     * @param stats the stats.
     */
    public void buildStarted(Run build, TaskListener taskListener,
            GerritTriggeredEvent event, BuildsStartedStats stats) {
        try {
            /* Without a change, it doesn't make sense to notify gerrit */
            if (event instanceof ChangeBasedEvent) {
                ChangeBasedEvent changeBasedEvent = (ChangeBasedEvent)event;
                String command = parameterExpander.getBuildStartedCommand(
                        build, taskListener, changeBasedEvent, stats);
                sendCommandToGerrit(command);
                notifySameTopicBuildStarted(build, taskListener, changeBasedEvent, stats);
                GerritTriggeredBuildListener.fireOnStarted(event, command);
            }
        } catch (Exception ex) {
            logger.error("Could not complete BuildStarted notification!", ex);
        }
    }

    /**
     * Generates the build-completed command based on configured templates and build-values and sends it to Gerrit.
     * @param memoryImprint the memory of all the builds for an event.
     * @param listener the taskListener.
     */
    public void buildCompleted(MemoryImprint memoryImprint, TaskListener listener) {

        try {
            /* Without a change, it doesn't make sense to notify gerrit */
            if (memoryImprint.getEvent() instanceof ChangeBasedEvent) {
                String command = parameterExpander.getBuildCompletedCommand(memoryImprint, listener);
                sendCommandToGerrit(command);
                notifySameTopicBuildCompleted(memoryImprint, listener);
                GerritTriggeredBuildListener.fireOnCompleted(memoryImprint, command);
            }
        } catch (Exception ex) {
            logger.error("Could not complete BuildCompleted notification!", ex);
        }
    }
}
