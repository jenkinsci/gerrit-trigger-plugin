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

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.lifecycle.GerritEventLifecycle;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.RetriggerAction;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.RetriggerAllAction;
import com.sonymobile.tools.gerrit.gerritevents.GerritEventListener;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.RefUpdated;
import hudson.model.AbstractProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl.getServerConfig;
import static com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerParameters.setOrCreateParameters;

/**
 * Event listener and scheduling for {@link GerritTrigger}.
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
    EventListener(@Nonnull AbstractProject job) {
        this.job = job.getFullName();
    }

    @Override
    public void gerritEvent(GerritEvent event) {
        logger.trace("event: {}", event);
        GerritTrigger t = getTrigger();
        if (t == null) {
            logger.warn("Couldn't find a configured trigger for {}", job);
            return;
        }
        if (event instanceof GerritTriggeredEvent) {
            GerritTriggeredEvent triggeredEvent = (GerritTriggeredEvent)event;
            if (t.isInteresting(triggeredEvent)) {
                logger.trace("The event is interesting.");
                notifyOnTriggered(t, triggeredEvent);
                schedule(t, new GerritCause(triggeredEvent, t.isSilentMode()), triggeredEvent);
            }
        }
    }

    /**
     * Called when a ManualPatchsetCreated event arrives.
     *
     * @param event the event
     */
    public void gerritEvent(ManualPatchsetCreated event) {
        logger.trace("event: {}", event);
        GerritTrigger t = getTrigger();
        if (t == null) {
            logger.warn("Couldn't find a configured trigger for {}", job);
            return;
        }
        if (t.isInteresting(event)) {
            logger.trace("The event is interesting.");
            notifyOnTriggered(t, event);
            schedule(t, new GerritManualCause(event, t.isSilentMode()), event);
        }
    }

    /**
     * Called when a CommentAdded event arrives.
     *
     * @param event the event.
     */
    public void gerritEvent(CommentAdded event) {
        logger.trace("event: {}", event);
        GerritTrigger t = getTrigger();
        if (t == null) {
            logger.warn("Couldn't find a configured trigger for {}", job);
            return;
        }
        ToGerritRunListener listener = ToGerritRunListener.getInstance();
        if (listener != null) {
            if (listener.isBuilding(t.getJob(), event)) {
                logger.trace("Already building.");
                return;
            }
        }
        if (t.isInteresting(event) && t.commentAddedMatch(event)) {
            logger.trace("The event is interesting.");
            notifyOnTriggered(t, event);
            schedule(t, new GerritCause(event, t.isSilentMode()), event);
        }
    }

    /**
     * Schedules a build with parameters from the event. With {@link #job} as the project to build.
     *
     * @param t the trigger config
     * @param cause the cause of the build.
     * @param event the event.
     */
    protected void schedule(GerritTrigger t, GerritCause cause, GerritTriggeredEvent event) {
        schedule(t, cause, event, t.getJob());
    }

    /**
     * Schedules a build with parameters from the event.
     *
     * @param t       the trigger config
     * @param cause   the cause of the build.
     * @param event   the event.
     * @param project the project to build.
     */
    protected void schedule(GerritTrigger t, GerritCause cause, GerritTriggeredEvent event, AbstractProject project) {
        BadgeAction badgeAction = new BadgeAction(event);
        //during low traffic we still don't want to spam Gerrit, 3 is a nice number, isn't it?
        int projectbuildDelay = t.getBuildScheduleDelay();
        if (cause instanceof GerritUserCause) {
            // it's a manual trigger, no need for a quiet period
            projectbuildDelay = 0;
        } else if (project.getHasCustomQuietPeriod()
                && project.getQuietPeriod() > projectbuildDelay) {
            projectbuildDelay = project.getQuietPeriod();
        }
        ParametersAction parameters = createParameters(event, project);
        Future build = project.scheduleBuild2(
                projectbuildDelay,
                cause,
                badgeAction,
                new RetriggerAction(cause.getContext()),
                new RetriggerAllAction(cause.getContext()),
                parameters);

        IGerritHudsonTriggerConfig serverConfig = getServerConfig(event);

        if (event instanceof ChangeBasedEvent) {
            ChangeBasedEvent changeBasedEvent = (ChangeBasedEvent)event;
            if (serverConfig != null && serverConfig.isGerritBuildCurrentPatchesOnly()) {
                t.getRunningJobs().scheduled(changeBasedEvent, parameters, project.getName());
            }
            if (null != changeBasedEvent.getPatchSet()) {
                logger.info("Project {} Build Scheduled: {} By event: {}",
                        new Object[]{project.getName(), (build != null),
                                changeBasedEvent.getChange().getNumber() + "/"
                                        + changeBasedEvent.getPatchSet().getNumber(), });
            } else {
                logger.info("Project {} Build Scheduled: {} By event: {}",
                        new Object[]{project.getName(), (build != null),
                                changeBasedEvent.getChange().getNumber(), });
            }
        } else if (event instanceof RefUpdated) {
            RefUpdated refUpdated = (RefUpdated)event;
            logger.info("Project {} Build Scheduled: {} By event: {}",
                    new Object[]{project.getName(), (build != null),
                            refUpdated.getRefUpdate().getRefName() + " " + refUpdated.getRefUpdate().getNewRev(), });
        }
    }

    /**
     * Creates a ParameterAction and fills it with the project's default parameters + the Standard Gerrit parameters.
     *
     * @param event   the event.
     * @param project the project.
     * @return the ParameterAction.
     */
    protected ParametersAction createParameters(GerritTriggeredEvent event, AbstractProject project) {
        List<ParameterValue> parameters = getDefaultParametersValues(project);
        setOrCreateParameters(event, project, parameters);
        return new ParametersAction(parameters);
    }

    /**
     * Retrieves all default parameter values for a project.
     * Copied from {@link AbstractProject#getDefaultParametersValues()}
     * version 1.362. TODO: This is not a good way to solve the problem.
     *
     * @param project the project.
     * @return the default parameter values.
     */
    private List<ParameterValue> getDefaultParametersValues(AbstractProject project) {
        ParametersDefinitionProperty paramDefProp =
                (ParametersDefinitionProperty)project.getProperty(ParametersDefinitionProperty.class);
        List<ParameterValue> defValues = new ArrayList<ParameterValue>();

        /*
         * This check is made ONLY if someone calls this method even if isParametrized() is false.
         */
        if (paramDefProp == null) {
            return defValues;
        }

        /* Scan for all parameters with an associated default value */
        for (ParameterDefinition paramDefinition : paramDefProp.getParameterDefinitions()) {
            ParameterValue defaultValue = paramDefinition.getDefaultParameterValue();

            if (defaultValue != null) {
                defValues.add(defaultValue);
            }
        }

        return defValues;
    }

    /**
     * Notify that that build will be triggered for the event.
     *
     * @param t the trigger config
     * @param event The event
     */
    private void notifyOnTriggered(GerritTrigger t, GerritTriggeredEvent event) {
        if (!t.isSilentMode()) {
            ToGerritRunListener listener = ToGerritRunListener.getInstance();
            if (listener != null) {
                listener.onTriggered(t.getJob(), event);
            }
        } else {
            if (event instanceof GerritEventLifecycle) {
                ((GerritEventLifecycle)event).fireProjectTriggered(t.getJob());
            }
        }
    }

    /**
     * Utility method for finding the {@link GerritTrigger} instance in {@link #job}.
     *
     * @return the trigger or null if job is gone or doesn't have a trigger.
     */
    @CheckForNull
    private GerritTrigger getTrigger() {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return null;
        }
        AbstractProject p = jenkins.getItemByFullName(job, AbstractProject.class);
        if (p == null) {
            return null;
        }
        return (GerritTrigger)p.getTrigger(GerritTrigger.class);
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
