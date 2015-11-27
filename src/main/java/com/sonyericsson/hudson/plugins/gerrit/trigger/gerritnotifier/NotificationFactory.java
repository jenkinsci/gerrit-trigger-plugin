/*
 * The MIT License
 *
 * Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 * Copyright 2012 Sony Mobile Communications AB. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier;

import com.sonymobile.tools.gerrit.gerritevents.GerritCmdRunner;
import com.sonymobile.tools.gerrit.gerritevents.GerritSendCommandQueue;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Provider;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.job.ssh.BuildCompletedCommandJob;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.job.rest.BuildCompletedRestCommandJob;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.job.ssh.BuildStartedCommandJob;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.job.rest.BuildStartedRestCommandJob;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factory for creating notification entities.
 * This factory is mainly created and used to ease unit testing.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class NotificationFactory {
    private static final Logger logger = LoggerFactory.getLogger(NotificationFactory.class);
    private static NotificationFactory instance;

    /**
     * Gets the singleton instance of the NotificationFactory.
     *
     * @return the NotificationFactory.
     */
    public static NotificationFactory getInstance() {
        if (instance == null) {
            instance = new NotificationFactory();
        }
        return instance;
    }

    /**
     * Shortcut method to get the config from {@link com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer}.
     *
     * @param serverName the name of the server.
     * @return the server-config.
     */
    public IGerritHudsonTriggerConfig getConfig(String serverName) {
        GerritServer server = PluginImpl.getServer_(serverName);
        if (server != null) {
            return server.getConfig();
        } else {
            logger.error("Could not find the Gerrit Server: {}", serverName);
        }
        return null;
    }

    /**
     * Factory method for creating a GerritNotifier.
     *
     * @param cmdRunner - something capable of sending commands to Gerrit.
     * @param serverName  the name of the server.
     * @return a GerritNotifier
     */
    public GerritNotifier createGerritNotifier(GerritCmdRunner cmdRunner, String serverName) {
        IGerritHudsonTriggerConfig config = getConfig(serverName);
        return createGerritNotifier(config, cmdRunner);
    }

    /**
     * Factory method for creating a GerritNotifier.
     *
     * @param config    - a configuration to use for parameter expansion.
     * @param cmdRunner - something capable of sending commands to Gerrit.
     * @return a GerritNotifier
     */
    public GerritNotifier createGerritNotifier(IGerritHudsonTriggerConfig config, GerritCmdRunner cmdRunner) {
        return new GerritNotifier(config, cmdRunner);
    }

    //CS IGNORE LineLength FOR NEXT 8 LINES. REASON: Javadoc

    /**
     * Queues a build completed command on the send-command queue.
     *
     * @param memoryImprint the memory of the builds.
     * @param listener      a listener.
     * @see GerritSendCommandQueue#queue(com.sonymobile.tools.gerrit.gerritevents.workers.cmd.AbstractSendCommandJob)
     * @see BuildCompletedCommandJob
     */
    public void queueBuildCompleted(BuildMemory.MemoryImprint memoryImprint, TaskListener listener) {
        String serverName = getServerName(memoryImprint);
        if (serverName != null) {
            IGerritHudsonTriggerConfig config = getConfig(serverName);
            if (config != null) {
                if (config.isUseRestApi()
                        && memoryImprint.getEvent() instanceof ChangeBasedEvent) {
                    GerritSendCommandQueue.queue(new BuildCompletedRestCommandJob(config, memoryImprint, listener));
                } else {
                    GerritSendCommandQueue.queue(new BuildCompletedCommandJob(config, memoryImprint, listener));
                }
            } else {
                logger.warn("Nothing queued since there is no configuration for serverName: {}", serverName);
            }
        } else {
            logger.warn("Nothing queued since the event in memory contained no serverName: {}", memoryImprint);
        }
    }

    /**
     * Get the server name from the event provider.
     *
     * @param memoryImprint the memory of the builds.
     * @return serverName the server name.
     */
    private String getServerName(BuildMemory.MemoryImprint memoryImprint) {
        if (memoryImprint != null) {
            GerritTriggeredEvent event = memoryImprint.getEvent();
            if (event != null) {
                return getServerName(event);
            } else {
                logger.error("Could not get the GerritTriggeredEvent from memoryImprint");
            }
        } else {
            logger.error("The memory imprint is null");
        }
        return null;
    }

    /**
     * Get the server name from the event provider.
     *
     * @param event the event
     * @return the server name from the provider or null if none is found.
     */
    private String getServerName(GerritTriggeredEvent event) {
        if (event != null) {
            Provider prov = event.getProvider();
            if (prov != null) {
                String serverName = prov.getName();
                if (serverName != null) {
                    return serverName;
                } else {
                    logger.warn("Could not find the Gerrit Server name from the provider {}", prov);
                }
            } else {
                logger.warn("Could not get the Provider from event {}", event);
            }
        }
        return null;
    }


    //CS IGNORE LineLength FOR NEXT 10 LINES. REASON: Javadoc

    /**
     * Queues a build started command on the send-command queue.
     *
     * @param build    the build.
     * @param listener a listener.
     * @param event    the event.
     * @param stats    the started stats.
     * @see GerritSendCommandQueue#queue(com.sonymobile.tools.gerrit.gerritevents.workers.cmd.AbstractSendCommandJob)
     * @see BuildStartedCommandJob
     */
    public void queueBuildStarted(Run build, TaskListener listener,
                                  GerritTriggeredEvent event, BuildsStartedStats stats) {
        String serverName = getServerName(event);
        if (serverName != null) {
            IGerritHudsonTriggerConfig config = getConfig(serverName);
            if (config != null) {
                if (config.isUseRestApi() && event instanceof ChangeBasedEvent) {
                    GerritSendCommandQueue.queue(new BuildStartedRestCommandJob(config, build, listener,
                            (ChangeBasedEvent)event, stats));
                } else {
                    GerritSendCommandQueue.queue(new BuildStartedCommandJob(config, build, listener, event, stats));
                }
            } else {
                logger.warn("Nothing queued since there is no configuration for serverName: {}", serverName);
            }
        } else {
            logger.warn("Nothing queued since the event contained no serverName: {}", event);
        }
    }
}
