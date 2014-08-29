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

import hudson.model.AbstractProject;
import java.util.TimerTask;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;

/**
 * TimerTasks that are created from a GerritTrigger and periodically calls
 * GerritTrigger.updateTriggerConfigURL().
 *
 * @author Fredrik Abrahamson &lt;fredrik.abrahamson@sonymobile.com&gt;
 */
public class GerritTriggerTimerTask extends TimerTask {
    private final String job;

    /**
     * Constructor
     *
     * @param gerritTrigger the GerritTrigger that created this timerTask
     */
    GerritTriggerTimerTask(GerritTrigger gerritTrigger) {
        job = gerritTrigger.getJob().getFullName();
        GerritTriggerTimer.getInstance().schedule(this);
    }

    /**
     * Called periodically by the GerritTriggerTimer according to its schedule.
     */
    @Override
    public void run() {
        GerritTrigger gerritTrigger = getGerritTrigger();
        if (gerritTrigger == null) {
            return;
        }
        gerritTrigger.updateTriggerConfigURL();
    }

    /**
     * The {@link GerritTrigger} that created this timerTask.
     *
     * @return the trigger.
     */
    public @CheckForNull GerritTrigger getGerritTrigger() {
        AbstractProject p = Jenkins.getInstance().getItemByFullName(job, AbstractProject.class);
        if (p == null) {
            return null;
        }
        return (GerritTrigger) p.getTrigger(GerritTrigger.class);
    }
}
