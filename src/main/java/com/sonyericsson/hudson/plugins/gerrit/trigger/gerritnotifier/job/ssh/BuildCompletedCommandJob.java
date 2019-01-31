/*
 * The MIT License
 *
 * Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 * Copyright 2013 Sony Mobile Communications AB. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.job.ssh;

import com.sonymobile.tools.gerrit.gerritevents.workers.cmd.AbstractSendCommandJob;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.GerritNotifier;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.NotificationFactory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;

import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.security.ACLContext;

/**
 * A send-command-job that calculates and sends the builds completed command.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class BuildCompletedCommandJob extends AbstractSendCommandJob {

    private BuildMemory.MemoryImprint memoryImprint;
    private TaskListener listener;

    /**
     * Standard constructor with all the needed data for the job to perform.
     * @param config the config.
     * @param memoryImprint the memory of the builds.
     * @param listener a listener.
     * @see GerritNotifier#buildCompleted(BuildMemory.MemoryImprint, TaskListener)
     */
    public BuildCompletedCommandJob(IGerritHudsonTriggerConfig config,
                                    BuildMemory.MemoryImprint memoryImprint,
                                    TaskListener listener) {
        super(config);
        this.memoryImprint = memoryImprint;
        this.listener = listener;
    }

    @Override
    public void run() {
        try (ACLContext ctx = ACL.as(ACL.SYSTEM)) {
            GerritNotifier notifier = NotificationFactory.getInstance()
                .createGerritNotifier((IGerritHudsonTriggerConfig)getConfig(), this);
            notifier.buildCompleted(memoryImprint, listener);
        }
    }
}
