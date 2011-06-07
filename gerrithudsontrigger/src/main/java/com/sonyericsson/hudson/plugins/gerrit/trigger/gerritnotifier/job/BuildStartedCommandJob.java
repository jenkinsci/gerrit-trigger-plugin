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

package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.job;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.cmd.AbstractSendCommandJob;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.GerritNotifier;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.NotificationFactory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;

/**
 * A send-command-job that calculates and sends the build started command.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class BuildStartedCommandJob extends AbstractSendCommandJob {

    private AbstractBuild build;
    private TaskListener taskListener;
    private GerritTriggeredEvent event;
    private BuildsStartedStats stats;

    /**
     * Standard constructor with all the required data for the job.
     *
     * @param config       the config.
     * @param build        a build.
     * @param taskListener a listener.
     * @param event        the event.
     * @param stats        the stats.
     * @see GerritNotifier#buildStarted(AbstractBuild, TaskListener, PatchsetCreated, BuildsStartedStats)
     */
    public BuildStartedCommandJob(IGerritHudsonTriggerConfig config, AbstractBuild build,
                                  TaskListener taskListener, GerritTriggeredEvent event,
                                  BuildsStartedStats stats) {
        super(config);
        this.build = build;
        this.taskListener = taskListener;
        this.event = event;
        this.stats = stats;
    }

    @Override
    public void run() {
        GerritNotifier notifier = NotificationFactory.getInstance()
                .createGerritNotifier((IGerritHudsonTriggerConfig)getConfig(), this);
        notifier.buildStarted(build, taskListener, event, stats);
    }
}
