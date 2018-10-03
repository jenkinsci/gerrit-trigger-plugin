/*
 *  The MIT License
 *
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import hudson.model.Job;
import java.util.TimerTask;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

/**
 * TimerTasks that are created from a GerritTrigger and periodically calls
 * GerritTrigger.updateTriggerConfigURL().
 *
 * @author Fredrik Abrahamson &lt;fredrik.abrahamson@sonymobile.com&gt;
 */
public class GerritTriggerTimerTask extends TimerTask {
    //TODO possible need to handle renames
    private String job;

    /**
     * Constructor
     *
     * @param gerritTrigger the GerritTrigger that created this timerTask
     */
    GerritTriggerTimerTask(@Nonnull GerritTrigger gerritTrigger) {
        job = gerritTrigger.getJob().getFullName();
        GerritTriggerTimer.getInstance().schedule(this, gerritTrigger);
    }

    /**
     * Called periodically by the GerritTriggerTimer according to its schedule.
     */
    @Override
    public void run() {
        GerritTrigger trigger = getGerritTrigger();
        if (trigger == null) {
            return;
        }
        if (StringUtils.isEmpty(trigger.getTriggerConfigURL())) {
            return;
        }
        if (trigger.getJob() != null && !trigger.getJob().isBuildable()) {
            return;
        }
        trigger.updateTriggerConfigURL();
    }

    @Override
    public String toString() {
        return "GerritTriggerTimerTask{job='" + job + '\'' + '}';
    }

    /**
     * The {@link GerritTrigger} that created this timerTask.
     *
     * @return the trigger.
     */
    @CheckForNull
    public GerritTrigger getGerritTrigger() {
        if (gerritTrigger != null) {
            //We are loading an older instance, check if start has been called yet so we can "convert"
            //So far it should be the correct instance.
            //This can't unfortunately be done in readResolve since the job name is discovered when start is called.
            if (gerritTrigger.getJob() != null) {
                //We are in luck!, lets forget our old ways.
                job = gerritTrigger.getJob().getFullName();
                gerritTrigger = null;
            } else {
                //Still needs to cling to our old ways I guess, for a few more ms.
                return gerritTrigger;
            }
        }
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return null;
        }
        Job p = jenkins.getItemByFullName(job, Job.class);
        if (p == null) {
            return null;
        }
        return GerritTrigger.getTrigger(p);
    }

    @Deprecated
    private transient GerritTrigger gerritTrigger;
}
