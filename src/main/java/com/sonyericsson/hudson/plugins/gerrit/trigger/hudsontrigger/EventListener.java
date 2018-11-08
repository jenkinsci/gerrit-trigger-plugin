/*
 *  The MIT License
 *
 *  Copyright (c) 2014, CloudBees, Inc. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import com.sonymobile.tools.gerrit.gerritevents.GerritEventListener;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import hudson.model.Job;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Event listener for {@link GerritTrigger}.  When this class receives a Gerrit
 * event, it immediately tells its GerritTrigger about the event.
 *
 * The intent of this class is to be used to build up the event queue in the
 * GerritTrigger.  The GerritTrigger instance will process those events when it
 * is ready to do so.
 *
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 */
public final class EventListener implements GerritEventListener {

    private static final Logger logger = LoggerFactory.getLogger(EventListener.class);

    private final String job;

    /**
     * Standard constructor.
     *
     * @param job the job to handle.
     */
    EventListener(@Nonnull Job job) {
        this(job.getFullName());
    }

    /**
     * Standard constructor.
     *
     * @param fullName the job to handle full name.
     */
    EventListener(@Nonnull String fullName) {
        this.job = fullName;
    }

    /**
     * The {@link Job#getFullName()} this listener is for.
     *
     * @return the fullName of the Job
     */
    public String getJob() {
        return job;
    }

    @Override
    public void gerritEvent(GerritEvent event) {
        logger.trace("job: {}; event: {}", job, event);

        GerritTrigger t = getTrigger();
        if (t == null) {
            logger.warn("Couldn't find a configured trigger for {}", job);
            return;
        }

        if (event instanceof GerritTriggeredEvent) {
            GerritTriggeredEvent triggeredEvent = (GerritTriggeredEvent)event;
            t.addEvent(triggeredEvent);
        }
    }

    /**
     * Called when a ManualPatchsetCreated event arrives.
     *
     * @param event the event
     */
    public void gerritEvent(ManualPatchsetCreated event) {
        logger.trace("job: {}; event: {}", job, event);

        GerritTrigger t = getTrigger();
        if (t == null) {
            logger.warn("Couldn't find a configured trigger for {}", job);
            return;
        }

        t.addEvent(event);
    }

    /**
     * Called when a CommentAdded event arrives.
     *
     * @param event the event.
     */
    public void gerritEvent(CommentAdded event) {
        logger.trace("job: {}; event: {}", job, event);

        GerritTrigger t = getTrigger();
        if (t == null) {
            logger.warn("Couldn't find a configured trigger for {}", job);
            return;
        }

        t.addEvent(event);
    }

    /**
     * Utility method for finding the {@link GerritTrigger} instance in {@link #job}.
     *
     * @return the trigger or null if job is gone or doesn't have a trigger.
     */
    @CheckForNull
    @Restricted(NoExternalUse.class)
    public GerritTrigger getTrigger() {
        Job p = findJob();
        if (p == null) {
            return null;
        }
        return GerritTrigger.getTrigger(p);
    }

    /**
     * Utility method for finding the Job instance referred to by {@link #job}.
     *
     * @return the job unless environment doesn't allow it.
     */
    @CheckForNull
    @Restricted(NoExternalUse.class)
    public Job findJob() {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return null;
        }
        return jenkins.getItemByFullName(job, Job.class);
    }

    @Override
    public int hashCode() {
        return job.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof EventListener && ((EventListener)obj).job.equals(job);
    }
}
