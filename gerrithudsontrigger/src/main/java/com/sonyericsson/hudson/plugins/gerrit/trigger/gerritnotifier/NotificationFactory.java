/*
 * The MIT License
 *
 * Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritCmdRunner;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritSendCommandQueue;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.job.BuildCompletedCommandJob;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.job.BuildStartedCommandJob;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;

/**
 * A factory for creating notification entities.
 * This factory is mainly created and used to ease unit testing.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class NotificationFactory {
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
     * Shortcut method to get the config from {@link com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl}.
     * Throws an IllegalStateException if PluginImpl hasn't been started yet.
     *
     * @return the plugin-config.
     */
    public IGerritHudsonTriggerConfig getConfig() {
        if (PluginImpl.getInstance() == null) {
            //If this happens we are sincerely screwed anyways.
            throw new IllegalStateException("PluginImpl has not been loaded yet!");
        }
        return PluginImpl.getInstance().getConfig();
    }

    /**
     * Factory method for creating a GerritNotifier.
     *
     * @param cmdRunner - something capable of sending commands to Gerrit.
     * @return a GerritNotifier
     */
    public GerritNotifier createGerritNotifier(GerritCmdRunner cmdRunner) {
        IGerritHudsonTriggerConfig config = getConfig();
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
     * @see GerritSendCommandQueue#queue(com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.cmd.AbstractSendCommandJob)
     * @see BuildCompletedCommandJob
     */
    public void queueBuildCompleted(BuildMemory.MemoryImprint memoryImprint, TaskListener listener) {
        BuildCompletedCommandJob job = new BuildCompletedCommandJob(getConfig(),
                memoryImprint, listener);
        GerritSendCommandQueue.queue(job);
    }

    //CS IGNORE LineLength FOR NEXT 10 LINES. REASON: Javadoc

    /**
     * Queues a build started command on the send-command queue.
     *
     * @param build    the build.
     * @param listener a listener.
     * @param event    the event.
     * @param stats    the started stats.
     * @see GerritSendCommandQueue#queue(com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.cmd.AbstractSendCommandJob)
     * @see BuildStartedCommandJob
     */
    public void queueBuildStarted(AbstractBuild build, TaskListener listener,
                                  GerritTriggeredEvent event, BuildsStartedStats stats) {
        BuildStartedCommandJob job = new BuildStartedCommandJob(getConfig(),
                build, listener, event, stats);
        GerritSendCommandQueue.queue(job);
    }
}
