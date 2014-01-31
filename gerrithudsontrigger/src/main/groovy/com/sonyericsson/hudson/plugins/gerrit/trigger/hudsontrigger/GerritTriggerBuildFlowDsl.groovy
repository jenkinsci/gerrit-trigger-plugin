/*
 *  The MIT License
 *
 *  Copyright 2014 Sony Mobile Communications AB. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger

import com.cloudbees.plugins.flow.FlowDelegate
import com.cloudbees.plugins.flow.JobInvocation
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent
import hudson.model.AbstractProject

/**
 * The actual extension object that is given to the build flow.
 *
 * @see BuildFlowDslGerritExtension
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
class GerritTriggerBuildFlowDsl {
    private FlowDelegate dsl;

    /**
     * Standard constructor.
     * @param dsl the delegate.
     */
    public GerritTriggerBuildFlowDsl(FlowDelegate dsl) {
        this.dsl = dsl;
    }

    /**
     * World.
     */
    public void hello() {
        dsl.out.println("Hello Gerrit");
    }

    /**
     * The Gerrit event that triggered the build flow.
     * Will fail the flow if it wasn't triggered by a Gerrit event.
     * @return the event.
     */
    public GerritTriggeredEvent getEvent() {
        GerritCause cause = dsl.flowRun.getCause(GerritCause.class);
        if (cause != null) {
            return cause.event;
        } else {
            dsl.out.println("This build flow was not triggered by Gerrit!");
            dsl.fail();
        }
    }

    /**
     * Trigger a build as if it was triggered by a Gerrit Event.
     * Does not wait for the build.
     */
    public void trigger(Map args, GerritTriggeredEvent event, String projectName) {
        JobInvocation job = new JobInvocation(dsl.flowRun, projectName);
        trigger(args, event, job.getProject());
    }

    /**
     * Trigger a build as if it was triggered by a Gerrit Event.
     * Does not wait for the build.
     * Gets the event from the build flow
     */
    public void trigger(Map args, String projectName) {
        trigger(args, getEvent(), projectName);
    }

    /**
     * Trigger a build as if it was triggered by a Gerrit Event.
     * Does not wait for the build.
     * Gets the event from the build flow
     */
    public void trigger(Map args, AbstractProject project) {
        trigger(args, getEvent(), project);
    }

    /**
     * Trigger a build as if it was triggered by a Gerrit Event.
     * Does not wait for the build.
     */
    public void trigger(Map args, GerritTriggeredEvent event, AbstractProject project) {
        GerritTrigger trigger = GerritTrigger.getTrigger(project);
        if (trigger == null) {
            trigger = new GerritTrigger([], null, null, null, null, null, null, null, null, null, null, null,
                    args.silentMode as boolean, args.escapeQuotes as boolean, args.noNameAndEmailParameters as boolean,
                    null, null, null, null, null, null, null, null, [], false, false, null);
        }
        BuildFlowDslCause cause = new BuildFlowDslCause(event, args.silentMode as boolean, dsl.build);
        trigger.schedule(cause, event, project);

    }
}
