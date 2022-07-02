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
     * Notify changes with the same topic.
     *
     * @param event the change based event.
     * @param command the gerrit command.
     */
    private void notifySameTopic(ChangeBasedEvent event, String command) {
        Topic topic = event.getChange().getTopicObject();
        if (topic != null) {
            String pattern = new StringBuilder(event.getChange().getNumber()).append(',')
                    .append(event.getPatchSet().getNumber()).toString();

            if (!command.contains(pattern)) {
                logger.error("command {} has no pattern {}", command, pattern);
                return;
            }
            for (GerritServer server : PluginImpl.getServers_()) {
                if (!server.getConfig().isVoteSameTopic()) {
                    continue;
                }
                Map<Change, PatchSet> changes = topic.getChanges(server.getQueryHandler());
                for (Map.Entry<Change, PatchSet> entry : changes.entrySet()) {
                    Change change = entry.getKey();
                    if (change.equals(event.getChange())) {
                        continue;
                    }
                    PatchSet patchSet = entry.getValue();
                    String substitution = new StringBuilder(change.getNumber()).append(',').append(patchSet.getNumber())
                            .toString();
                    String command2 = command.replace(pattern, substitution);
                    logger.trace("notifySameTopic: {} {} {}", topic, change, command2);
                    server.getCmdRunner().sendCommand(command2);
                }
            }
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
                String command =
                        parameterExpander.getBuildStartedCommand(build, taskListener, changeBasedEvent, stats);
                if (command != null) {
                    if (!command.isEmpty()) {
                        logger.debug("Notifying BuildStarted to gerrit: {}", command);
                        cmdRunner.sendCommand(command);
                        notifySameTopic(changeBasedEvent, command);
                        GerritTriggeredBuildListener.fireOnStarted(event, command);
                    } else {
                        logger.debug("BuildStarted command is empty. Gerrit will not be notified of BuildStarted");
                    }
                } else {
                    logger.error("Something wrong during parameter extraction. "
                            + "Gerrit will not be notified of BuildStarted");
                }
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

                if (command != null) {
                    if (!command.isEmpty()) {
                        logger.debug("Notifying BuildCompleted to gerrit: {}", command);
                        cmdRunner.sendCommand(command);
                        notifySameTopic((ChangeBasedEvent)memoryImprint.getEvent(), command);
                        GerritTriggeredBuildListener.fireOnCompleted(memoryImprint, command);
                    } else {
                        logger.debug("BuildCompleted command is empty. Gerrit will not be notified of BuildCompleted");
                    }
                } else {
                    logger.error("Something wrong during parameter extraction. "
                            + "Gerrit will not be notified of BuildCompleted");
                }
            }
        } catch (Exception ex) {
            logger.error("Could not complete BuildCompleted notification!", ex);
        }
    }
}
