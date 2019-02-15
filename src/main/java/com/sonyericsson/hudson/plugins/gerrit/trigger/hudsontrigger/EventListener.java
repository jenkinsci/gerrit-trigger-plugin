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
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
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
        // Wait for the project list to be ready before we try to process the event.
        try {
            t.waitForProjectListToBeReady();
        } catch (InterruptedException e) {
            // This thread has been interrupted.
            //
            // This is only possible if the configuration had not been loaded yet
            // and we were waiting for it.
            //
            // We are going to assume we've been asked to cancel, so we're going
            // to just return now without processing the event.
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
        logger.trace("job: {}; event: {}", job, event);
        GerritTrigger t = getTrigger();
        if (t == null) {
            logger.warn("Couldn't find a configured trigger for {}", job);
            return;
        }
        // Wait for the project list to be ready before we try to process the event.
        try {
            t.waitForProjectListToBeReady();
        } catch (InterruptedException e) {
            // This thread has been interrupted.
            //
            // This is only possible if the configuration had not been loaded yet
            // and we were waiting for it.
            //
            // We are going to assume we've been asked to cancel, so we're going
            // to just return now without processing the event.
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
        logger.trace("job: {}; event: {}", job, event);
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
        // Wait for the project list to be ready before we try to process the event.
        try {
            t.waitForProjectListToBeReady();
        } catch (InterruptedException e) {
            // This thread has been interrupted.
            //
            // This is only possible if the configuration had not been loaded yet
            // and we were waiting for it.
            //
            // We are going to assume we've been asked to cancel, so we're going
            // to just return now without processing the event.
            return;
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
    protected void schedule(GerritTrigger t, GerritCause cause, GerritTriggeredEvent event, final Job project) {
        BadgeAction badgeAction = new BadgeAction(event);
        //during low traffic we still don't want to spam Gerrit, 3 is a nice number, isn't it?
        int projectbuildDelay = t.getBuildScheduleDelay();
        if (cause instanceof GerritUserCause) {
            // it's a manual trigger, no need for a quiet period
            projectbuildDelay = 0;
        } else if (project instanceof ParameterizedJobMixIn.ParameterizedJob) {
            ParameterizedJobMixIn.ParameterizedJob abstractProject = (ParameterizedJobMixIn.ParameterizedJob)project;
            if (abstractProject.getQuietPeriod() > projectbuildDelay) {
                projectbuildDelay = abstractProject.getQuietPeriod();
            }
        }
        ParametersAction parameters = createParameters(event, project);

        Future futureBuild;
        if (project instanceof ParameterizedJobMixIn.ParameterizedJob) {
            futureBuild = schedule(project, projectbuildDelay, cause, badgeAction, parameters);
        } else {
            throw new IllegalStateException("Unexpected error. Unsupported Job type for Gerrit Trigger: "
                    + project.getClass().getName());
        }

        IGerritHudsonTriggerConfig serverConfig = getServerConfig(event);

        if (event instanceof ChangeBasedEvent) {
            ChangeBasedEvent changeBasedEvent = (ChangeBasedEvent)event;
            if (serverConfig != null && serverConfig.isGerritBuildCurrentPatchesOnly()) {
                t.getRunningJobs().scheduled(changeBasedEvent);
            }
            if (null != changeBasedEvent.getPatchSet()) {
                logger.info("Project {} Build Scheduled: {} By event: {}",
                        new Object[]{project.getName(), (futureBuild != null),
                                changeBasedEvent.getChange().getNumber() + "/"
                                        + changeBasedEvent.getPatchSet().getNumber(), });
            } else {
                logger.info("Project {} Build Scheduled: {} By event: {}",
                        new Object[]{project.getName(), (futureBuild != null),
                                changeBasedEvent.getChange().getNumber(), });
            }
        } else if (event instanceof RefUpdated) {
            RefUpdated refUpdated = (RefUpdated)event;
            logger.info("Project {} Build Scheduled: {} By event: {}",
                    new Object[]{project.getName(), (futureBuild != null),
                            refUpdated.getRefUpdate().getRefName() + " " + refUpdated.getRefUpdate().getNewRev(), });
        }
    }

    /**
     * Schedules a build of a job.
     * <p>
     * Added here to facilitate unit testing.
     *
     * @param theJob The job.
     * @param quitePeriod Quite period.
     * @param cause Build cause.
     * @param badgeAction build badge action.
     * @param parameters Build parameters.
     * @return Scheduled build future.
     */
    protected Future schedule(final Job theJob, int quitePeriod, GerritCause cause, BadgeAction badgeAction,
                              ParametersAction parameters) {
        ParameterizedJobMixIn jobMixIn = new ParameterizedJobMixIn() {
            @Override
            protected Job asJob() {
                return theJob;
            }
        };
        return jobMixIn.scheduleBuild2(quitePeriod, new CauseAction(cause),
                badgeAction,
                new RetriggerAction(cause.getContext()),
                new RetriggerAllAction(cause.getContext()),
                parameters);
    }

    /**
     * Creates a ParameterAction and fills it with the project's default parameters + the Standard Gerrit parameters.
     * If running on a core version that let's us specify safeParameters for the ParameterAction
     * the Gerrit specific parameters will be specified in the safeParameters list in addition to anything the admin
     * might have set.
     * A warning will be printed to the log if that is not possible but SECURITY-170 appears to be in effect.
     *
     * @param event   the event.
     * @param project the project.
     * @return the ParameterAction.
     */
    protected ParametersAction createParameters(GerritTriggeredEvent event, Job project) {
        List<ParameterValue> parameters = getDefaultParametersValues(project);
        setOrCreateParameters(event, project, parameters);
        try {
            Constructor<ParametersAction> constructor = ParametersAction.class.getConstructor(List.class,
                                                                                              Collection.class);
            return constructor.newInstance(parameters, GerritTriggerParameters.getNamesSet());
        } catch (NoSuchMethodException e) {
            ParametersActionInspection inspection = getParametersInspection();
            if (inspection.isInspectionFailure()) {
                logger.warn("Failed to inspect ParametersAction to determine "
                                    + "if we can behave normally around SECURITY-170.\nSee "
                                    + "https://wiki.jenkins-ci.org/display/SECURITY/Jenkins+Security+Advisory+2016-05-11"
                                    + " for information.");
            } else if (inspection.isHasSafeParameterConfig()) {
                StringBuilder txt = new StringBuilder(
                        "Running on a core with SECURITY-170 fixed but no direct way for Gerrit Trigger"
                                + " to self-specify safe parameters.");
                txt.append(" You should consider upgrading to a new Jenkins core version.\n");
                if (inspection.isKeepUndefinedParameters()) {
                    txt.append(".keepUndefinedParameters is set so the trigger should behave normally.");
                } else if (inspection.isSafeParametersSet()) {
                    txt.append("All Gerrit related parameters are set in .safeParameters");
                    txt.append(" so the trigger should behave normally.");
                } else {
                    txt.append("No overriding system properties appears to be set,");
                    txt.append(" your builds might not work as expected.\n");
                    txt.append("See https://wiki.jenkins-ci.org/display/SECURITY/Jenkins+Security+Advisory+2016-05-11");
                    txt.append(" for information.");
                }
                logger.warn(txt.toString());
            } else {
                logger.debug("Running on an old core before safe parameters, we should be safe.", e);
            }
        } catch (IllegalAccessException e) {
            logger.warn("Running on a core with safe parameters fix available, but not allowed to specify them", e);
        } catch (Exception e) {
            logger.warn("Running on a core with safe parameters fix available, but failed to provide them", e);
        }
        return new ParametersAction(parameters);
    }

    /**
     * Retrieves all default parameter values for a project.
     * Copied from {@link hudson.model.AbstractProject#getDefaultParametersValues()}
     * version 1.362. TODO: This is not a good way to solve the problem.
     *
     * @param project the project.
     * @return the default parameter values.
     */
    private List<ParameterValue> getDefaultParametersValues(Job project) {
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
        // With security handler, this method return null, if current user could not read this project.
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

    /**
     * Inspects {@link ParametersAction} to see what kind of capabilities it has in regards to SECURITY-170.
     * Assuming the safeParameters constructor could not be found.
     *
     * @return the inspection result
     * @see #createParameters(GerritTriggeredEvent, Job)
     */
    private static synchronized ParametersActionInspection getParametersInspection() {
        if (parametersInspectionCache == null) {
            parametersInspectionCache = new ParametersActionInspection();
        }
        return parametersInspectionCache;
    }

    /**
     * Stored cache of the inspection.
     * @see #getParametersInspection()
     */
    private static volatile ParametersActionInspection parametersInspectionCache = null;

    /**
     * Data structure with information regarding what kind of capabilities {@link ParametersAction} has.
     * @see #getParametersInspection()
     * @see #createParameters(GerritTriggeredEvent, Job)
     */
    private static class ParametersActionInspection {
        private static final Class<ParametersAction> KLASS = ParametersAction.class;
        private boolean inspectionFailure;
        private boolean safeParametersSet = false;
        private boolean keepUndefinedParameters = false;
        private boolean hasSafeParameterConfig = false;

        /**
         * Constructor that performs the inspection.
         */
        ParametersActionInspection() {
            try {
                for (Field field : KLASS.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())
                            &&  (
                                 field.getName().equals("KEEP_UNDEFINED_PARAMETERS_SYSTEM_PROPERTY_NAME")
                                 || field.getName().equals("SAFE_PARAMETERS_SYSTEM_PROPERTY_NAME")
                                )
                       ) {
                        this.hasSafeParameterConfig = true;
                        break;
                    }
                }
                if (hasSafeParameterConfig) {
                    if (Boolean.getBoolean(KLASS.getName() + ".keepUndefinedParameters")) {
                        this.keepUndefinedParameters = true;
                    }
                    String safeParameters = System.getProperty(KLASS.getName() + ".safeParameters");
                    if (!StringUtils.isBlank(safeParameters)) {
                        safeParameters = safeParameters.toUpperCase(Locale.ENGLISH);
                        boolean declared = true;
                        for (GerritTriggerParameters parameter : GerritTriggerParameters.values()) {
                            if (!safeParameters.contains(parameter.name())) {
                                declared = false;
                                break;
                            }
                        }
                        this.safeParametersSet = declared;
                    } else {
                        this.safeParametersSet = false;
                    }
                }
                this.inspectionFailure = false;
            } catch (Exception e) {
                this.inspectionFailure = true;
            }
        }

        /**
         * If the system property .safeParameters is set and contains all Gerrit related parameters.
         * @return true if so.
         */
        boolean isSafeParametersSet() {
            return safeParametersSet;
        }

        /**
         * If the system property .keepUndefinedParameters is set and set to true.
         *
         * @return true if so.
         */
        boolean isKeepUndefinedParameters() {
            return keepUndefinedParameters;
        }

        /**
         * If any of the constant fields regarding safeParameters are declared in {@link ParametersAction}.
         *
         * @return true if so.
         */
        boolean isHasSafeParameterConfig() {
            return hasSafeParameterConfig;
        }

        /**
         * If there was an exception when inspecting the class.
         *
         * @return true if so.
         */
        public boolean isInspectionFailure() {
            return inspectionFailure;
        }
    }
}
