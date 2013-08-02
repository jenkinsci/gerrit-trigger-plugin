/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_BUILD_SCHEDULE_DELAY;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritQueryHandler;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Approval;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeMerged;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeRestored;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.DraftPublished;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.RefUpdated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.VerdictCategory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import static com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerParameters.setOrCreateParameters;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.GerritTriggerInformationAction;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.RetriggerAction;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.RetriggerAllAction;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.SkipVote;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginCommentAddedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginDraftPublishedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginGerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginPatchsetCreatedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.version.GerritVersionChecker;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Future;
import java.util.regex.PatternSyntaxException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Triggers a build based on Gerrit events.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritTrigger extends Trigger<AbstractProject> implements GerritEventListener {

    private static final Logger logger = LoggerFactory.getLogger(GerritTrigger.class);
    //! Association between patches and the jobs that we're running for them
    private transient RunningJobs runningJobs = new RunningJobs();
    private transient AbstractProject myProject;
    private List<GerritProject> gerritProjects;
    private List<GerritProject> dynamicGerritProjects;
    private SkipVote skipVote;
    private Integer gerritBuildStartedVerifiedValue;
    private Integer gerritBuildStartedCodeReviewValue;
    private Integer gerritBuildSuccessfulVerifiedValue;
    private Integer gerritBuildSuccessfulCodeReviewValue;
    private Integer gerritBuildFailedVerifiedValue;
    private Integer gerritBuildFailedCodeReviewValue;
    private Integer gerritBuildUnstableVerifiedValue;
    private Integer gerritBuildUnstableCodeReviewValue;
    private Integer gerritBuildNotBuiltVerifiedValue;
    private Integer gerritBuildNotBuiltCodeReviewValue;
    private boolean silentMode;
    private boolean escapeQuotes;
    private boolean noNameAndEmailParameters;
    private String buildStartMessage;
    private String buildFailureMessage;
    private String buildSuccessfulMessage;
    private String buildUnstableMessage;
    private String buildNotBuiltMessage;
    private String buildUnsuccessfulFilepath;
    private String customUrl;
    private List<PluginGerritEvent> triggerOnEvents;
    private boolean allowTriggeringUnreviewedPatches;
    private boolean dynamicTriggerConfiguration;
    private String triggerConfigURL;

    private GerritTriggerTimerTask gerritTriggerTimerTask;

    private GerritTriggerInformationAction triggerInformationAction;

    /**
     * Default DataBound Constructor.
     *
     * @param gerritProjects                 the set of triggering rules.
     * @param skipVote                       what votes if any should be skipped in the final
     *                                       verified/code review calculation.
     * @param gerritBuildStartedVerifiedValue
     *                                       Job specific Gerrit verified vote when a build is started, null means that
     *                                       the global value should be used.
     * @param gerritBuildStartedCodeReviewValue
     *                                       Job specific Gerrit code review vote when a build is started, null means
     *                                       that the global value should be used.
     * @param gerritBuildSuccessfulVerifiedValue
     *                                       Job specific Gerrit verified vote when a build is successful, null means
     *                                       that the global value should be used.
     * @param gerritBuildSuccessfulCodeReviewValue
     *                                       Job specific Gerrit code review vote when a build is successful, null means
     *                                       that the global value should be used.
     * @param gerritBuildFailedVerifiedValue Job specific Gerrit verified vote when a build is failed, null means that
     *                                       the global value should be used.
     * @param gerritBuildFailedCodeReviewValue
     *                                       Job specific Gerrit code review vote when a build is failed, null means
     *                                       that the global value should be used.
     * @param gerritBuildUnstableVerifiedValue
     *                                       Job specific Gerrit verified vote when a build is unstable, null means that
     *                                       the global value should be used.
     * @param gerritBuildUnstableCodeReviewValue
     *                                       Job specific Gerrit code review vote when a build is unstable, null means
     *                                       that the global value should be used.
     * @param gerritBuildNotBuiltVerifiedValue
     *                                       Job specific Gerrit verified vote when a build is not built, null means that
     *                                       the global value should be used.
     * @param gerritBuildNotBuiltCodeReviewValue
     *                                       Job specific Gerrit code review vote when a build is not built, null means
     *                                       that the global value should be used.
     * @param silentMode                     Silent Mode on or off.
     * @param escapeQuotes                   EscapeQuotes on or off.
     * @param noNameAndEmailParameters       Whether to create parameters containing name and email
     * @param buildStartMessage              Message to write to Gerrit when a build begins
     * @param buildSuccessfulMessage         Message to write to Gerrit when a build succeeds
     * @param buildUnstableMessage           Message to write to Gerrit when a build is unstable
     * @param buildFailureMessage            Message to write to Gerrit when a build fails
     * @param buildNotBuiltMessage           Message to write to Gerrit when all builds are not built
     * @param buildUnsuccessfulFilepath      Filename to retrieve Gerrit comment message from, in the case of an
     *                                       unsuccessful build.
     * @param customUrl                      Custom URL to sen to gerrit instead of build URL
     * @param triggerOnEvents                The list of event types to trigger on.
     * @param dynamicTriggerConfiguration    Dynamic trigger configuration on or off
     * @param allowTriggeringUnreviewedPatches
     *                                       Is automatic patch checking allowed when connection is established
     * @param triggerConfigURL               Where to fetch the configuration file from
     */
    @DataBoundConstructor
    public GerritTrigger(
            List<GerritProject> gerritProjects,
            SkipVote skipVote,
            Integer gerritBuildStartedVerifiedValue,
            Integer gerritBuildStartedCodeReviewValue,
            Integer gerritBuildSuccessfulVerifiedValue,
            Integer gerritBuildSuccessfulCodeReviewValue,
            Integer gerritBuildFailedVerifiedValue,
            Integer gerritBuildFailedCodeReviewValue,
            Integer gerritBuildUnstableVerifiedValue,
            Integer gerritBuildUnstableCodeReviewValue,
            Integer gerritBuildNotBuiltVerifiedValue,
            Integer gerritBuildNotBuiltCodeReviewValue,
            boolean silentMode,
            boolean escapeQuotes,
            boolean noNameAndEmailParameters,
            String buildStartMessage,
            String buildSuccessfulMessage,
            String buildUnstableMessage,
            String buildFailureMessage,
            String buildNotBuiltMessage,
            String buildUnsuccessfulFilepath,
            String customUrl,
            List<PluginGerritEvent> triggerOnEvents,
            boolean dynamicTriggerConfiguration,
            boolean allowTriggeringUnreviewedPatches,
            String triggerConfigURL) {
        this.gerritProjects = gerritProjects;
        this.skipVote = skipVote;
        this.gerritBuildStartedVerifiedValue = gerritBuildStartedVerifiedValue;
        this.gerritBuildStartedCodeReviewValue = gerritBuildStartedCodeReviewValue;
        this.gerritBuildSuccessfulVerifiedValue = gerritBuildSuccessfulVerifiedValue;
        this.gerritBuildSuccessfulCodeReviewValue = gerritBuildSuccessfulCodeReviewValue;
        this.gerritBuildFailedVerifiedValue = gerritBuildFailedVerifiedValue;
        this.gerritBuildFailedCodeReviewValue = gerritBuildFailedCodeReviewValue;
        this.gerritBuildUnstableVerifiedValue = gerritBuildUnstableVerifiedValue;
        this.gerritBuildUnstableCodeReviewValue = gerritBuildUnstableCodeReviewValue;
        this.gerritBuildNotBuiltVerifiedValue = gerritBuildNotBuiltVerifiedValue;
        this.gerritBuildNotBuiltCodeReviewValue = gerritBuildNotBuiltCodeReviewValue;
        this.silentMode = silentMode;
        this.escapeQuotes = escapeQuotes;
        this.noNameAndEmailParameters = noNameAndEmailParameters;
        this.buildStartMessage = buildStartMessage;
        this.buildSuccessfulMessage = buildSuccessfulMessage;
        this.buildUnstableMessage = buildUnstableMessage;
        this.buildFailureMessage = buildFailureMessage;
        this.buildNotBuiltMessage = buildNotBuiltMessage;
        this.buildUnsuccessfulFilepath = buildUnsuccessfulFilepath;
        this.customUrl = customUrl;
        this.triggerOnEvents = triggerOnEvents;
        this.dynamicTriggerConfiguration = dynamicTriggerConfiguration;
        this.triggerConfigURL = triggerConfigURL;
        this.gerritTriggerTimerTask = null;
        triggerInformationAction = new GerritTriggerInformationAction();
        this.allowTriggeringUnreviewedPatches = allowTriggeringUnreviewedPatches;
    }

    /**
     * Converts old trigger configs when only patchset created was available as event.
     * If no event selection is set to true, triggering on patchset created will be.
     *
     * @return the resolved instance.
     * @throws ObjectStreamException if something beneath goes wrong.
     */
    public Object readResolve() throws ObjectStreamException {
        initializeTriggerOnEvents();
        return super.readResolve();
    }

    /**
     * Finds the GerritTrigger in a project.
     *
     * @param project the project.
     * @return the trigger if there is one, null otherwise.
     */
    public static GerritTrigger getTrigger(AbstractProject project) {
        return (GerritTrigger)project.getTrigger(GerritTrigger.class);
    }

    /**
     * Cancels the timerTask, if it exists.
     */
    public void cancelTimer() {
        if (gerritTriggerTimerTask != null) {
            logger.trace("GerritTrigger.cancelTimer(): {0}", myProject.getName());
            gerritTriggerTimerTask.cancel();
            gerritTriggerTimerTask = null;
        }
    }

    @Override
    public void start(AbstractProject project, boolean newInstance) {
        logger.debug("Start project: {}", project);
        super.start(project, newInstance);
        initializeTriggerOnEvents();
        this.myProject = project;
        try {
            if (PluginImpl.getInstance() != null) {
                PluginImpl.getInstance().addListener(this);
            } else {
                logger.warn("The plugin instance could not be found! Project {} will not be triggered!",
                        project.getFullDisplayName());
            }
        } catch (IllegalStateException e) {
            logger.error("I am too early!", e);
        }

        // Create a new timer task if there is a URL
        if (dynamicTriggerConfiguration) {
            gerritTriggerTimerTask = new GerritTriggerTimerTask(this);
        }

        GerritProjectList.removeTriggerFromProjectList(this);
        if (allowTriggeringUnreviewedPatches) {
            for (GerritProject p : gerritProjects) {
                GerritProjectList.addProject(p, this);
            }
        }
    }

    @Override
    public void stop() {
        logger.debug("Stop");
        super.stop();
        try {
            if (PluginImpl.getInstance() != null) {
                PluginImpl.getInstance().removeListener(this);
            }
        } catch (IllegalStateException e) {
            logger.error("I am too late!", e);
        }

        cancelTimer();
    }

    @Override
    public void gerritEvent(GerritEvent event) {
        //Default should do nothing
    }

    /**
     * Called when a PatchSetCreated event arrives.
     *
     * @param event the event
     */
    @Override
    public void gerritEvent(PatchsetCreated event) {
        logger.trace("event: {}", event);
        if (!myProject.isBuildable()) {
            logger.trace("Disabled.");
            return;
        }

        if (isInteresting(event)) {
            logger.trace("The event is interesting.");
            if (!silentMode) {
                ToGerritRunListener.getInstance().onTriggered(myProject, event);
            } else {
                event.fireProjectTriggered(myProject);
            }
            GerritCause cause;
            if (event instanceof ManualPatchsetCreated) {
                cause = new GerritManualCause((ManualPatchsetCreated)event, silentMode);
            } else {
                cause = new GerritCause(event, silentMode);
            }

            schedule(cause, event);
        }
    }

    /**
     * Called when a DraftPublished event arrives.
     *
     * @param event the event
     */
    @Override
    public void gerritEvent(DraftPublished event) {
        logger.trace("event: {}", event);
        if (!myProject.isBuildable()) {
            logger.trace("Disabled.");
            return;
        }

        if (isInteresting(event)) {
            logger.trace("The event is interesting.");
            if (!silentMode) {
                ToGerritRunListener.getInstance().onTriggered(myProject, event);
            } else {
                event.fireProjectTriggered(myProject);
            }

            schedule(new GerritCause(event, silentMode), event);
        }
    }

    /**
     * Checks if we should trigger for the given event.
     * @param event the event to check for.
     * @return true if we should trigger, false if not.
     */
    private boolean shouldTriggerOnEventType(GerritTriggeredEvent event) {
        if (triggerOnEvents == null || triggerOnEvents.isEmpty()) {
            return false;
        }
        for (PluginGerritEvent e : triggerOnEvents) {
            if (e.getCorrespondingEventClass().isInstance(event)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Schedules a build with parameters from the event. With {@link #myProject} as the project to build.
     *
     * @param cause the cause of the build.
     * @param event the event.
     */
    protected void schedule(GerritCause cause, GerritTriggeredEvent event) {
        schedule(cause, event, myProject);
    }

    /**
     * Schedules a build with parameters from the event.
     *
     * @param cause   the cause of the build.
     * @param event   the event.
     * @param project the project to build.
     */
    protected void schedule(GerritCause cause, GerritTriggeredEvent event, AbstractProject project) {
        BadgeAction badgeAction = new BadgeAction(event);
            //during low traffic we still don't want to spam Gerrit, 3 is a nice number, isn't it?
        int projectbuildDelay = getBuildScheduleDelay();
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
        //Experimental feature!
        if (event instanceof ChangeBasedEvent) {
            ChangeBasedEvent changeBasedEvent = (ChangeBasedEvent)event;
            if (PluginImpl.getInstance().getConfig().isGerritBuildCurrentPatchesOnly()) {
                getRunningJobs().scheduled(changeBasedEvent, parameters, project.getName());
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
     * Gives you {@link #runningJobs}. It makes sure that the reference is not null.
     *
     * @return the store of running jobs.
     */
    private synchronized RunningJobs getRunningJobs() {
        if (runningJobs == null) {
            runningJobs = new RunningJobs();
        }
        return runningJobs;
    }

    /**
     * Used to inform the plugin that the builds for a job have ended. This allows us to clean up our list of what jobs
     * we're running.
     *
     * @param event the event.
     */
    public void notifyBuildEnded(GerritTriggeredEvent event) {
        //Experimental feature!
        if (event instanceof ChangeBasedEvent
                && PluginImpl.getInstance().getConfig().isGerritBuildCurrentPatchesOnly()) {
            getRunningJobs().remove((ChangeBasedEvent)event);
        }
    }

    /**
     * getBuildScheduleDelay method will return configured buildScheduledelay value. If the value is missing or invalid
     * it the method will return default schedule delay or
     * {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues#DEFAULT_BUILD_SCHEDULE_DELAY}.
     *
     * @return buildScheduleDelay.
     */
    public int getBuildScheduleDelay() {
        if (PluginImpl.getInstance() == null || PluginImpl.getInstance().getConfig() == null) {
            return DEFAULT_BUILD_SCHEDULE_DELAY;
        } else {
            int buildScheduleDelay = PluginImpl.getInstance().getConfig().getBuildScheduleDelay();
            if (buildScheduleDelay < DEFAULT_BUILD_SCHEDULE_DELAY) {
                return DEFAULT_BUILD_SCHEDULE_DELAY;
            } else {
                return buildScheduleDelay;
            }
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
     * Re-triggers the build in {@link TriggerContext#getThisBuild()} for the context's event.
     * Will not do any {@link #isInteresting(GerritTriggeredEvent)} checks. If more
     * than one build was triggered by the event the results from those builds will be counted again, but they won't be
     * re-triggered. If any builds for the event are still running, this new scheduled build will replace its
     * predesessor. If the project is currently building the event, no scheduling will be done.
     *
     * @param context the previous context.
     */
    public void retriggerThisBuild(TriggerContext context) {
        if (context.getThisBuild().getProject().isBuildable()
                && !ToGerritRunListener.getInstance().isBuilding(context.getThisBuild().getProject(),
                        context.getEvent())) {

            if (!silentMode) {
                ToGerritRunListener.getInstance().onRetriggered(
                        context.getThisBuild().getProject(),
                        context.getEvent(),
                        context.getOtherBuilds());
            }
            final GerritUserCause cause = new GerritUserCause(context.getEvent(), silentMode);
            schedule(cause, context.getEvent(), context.getThisBuild().getProject());
        }
    }

    //CS IGNORE LineLength FOR NEXT 9 LINES. REASON: Javadoc see syntax.

    /**
     * Retriggers all builds in the given context. The builds will only be triggered if no builds for the event are
     * building.
     *
     * @param context the context to rebuild.
     * @see ToGerritRunListener#isBuilding(GerritTriggeredEvent)
     */
    public void retriggerAllBuilds(TriggerContext context) {
        if (!ToGerritRunListener.getInstance().isBuilding(context.getEvent())) {
            retrigger(context.getThisBuild().getProject(), context.getEvent());
            for (AbstractBuild build : context.getOtherBuilds()) {
                GerritTrigger trigger = (GerritTrigger)build.getProject().getTrigger(GerritTrigger.class);
                if (trigger != null) {
                    trigger.retrigger(build.getProject(), context.getEvent());
                }
            }
        }
    }

    /**
     * Retriggers one build in a set of many.
     *
     * @param project the project to retrigger.
     * @param event   the event.
     * @see #retriggerAllBuilds(com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext)
     */
    private void retrigger(AbstractProject project, GerritTriggeredEvent event) {
        if (project.isBuildable()) {
            if (!silentMode) {
                ToGerritRunListener.getInstance().onRetriggered(project, event, null);
            }
            GerritUserCause cause = new GerritUserCause(event, silentMode);
            schedule(cause, event, project);
        }
    }

    @Override
    public int hashCode() {
        if (myProject == null) {
            return super.hashCode();
        } else {
            return myProject.hashCode();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GerritTrigger) {
            GerritTrigger that = (GerritTrigger)obj;
            return this.myProject == that.myProject;
        }
        return false;
    }

    /**
     * Should we trigger on this event?
     *
     * @param event the event
     * @return true if we should.
     */
    private boolean isInteresting(GerritTriggeredEvent event) {
        if (!shouldTriggerOnEventType(event)) {
            return false;
        }
        List<GerritProject> allGerritProjects = new LinkedList<GerritProject>();
        if (gerritProjects != null) {
            allGerritProjects.addAll(gerritProjects);
        }
        if (dynamicGerritProjects != null) {
            allGerritProjects.addAll(dynamicGerritProjects);
        }
        logger.trace("entering isInteresting projects configured: {} the event: {}", allGerritProjects.size(), event);
        for (GerritProject p : allGerritProjects) {
            try {
                if (event instanceof ChangeBasedEvent) {
                    ChangeBasedEvent changeBasedEvent = (ChangeBasedEvent)event;
                    if (p.isInteresting(changeBasedEvent.getChange().getProject(),
                            changeBasedEvent.getChange().getBranch())) {
                        if (isFileTriggerEnabled() && p.getFilePaths() != null
                                && p.getFilePaths().size() > 0) {
                            if (p.isInteresting(changeBasedEvent.getChange().getProject(),
                                    changeBasedEvent.getChange().getBranch(),
                                    changeBasedEvent.getFiles(
                                            new GerritQueryHandler(PluginImpl.getInstance().getConfig())))) {
                                logger.trace("According to {} the event is interesting.", p);
                                return true;
                            }
                        } else {
                            logger.trace("According to {} the event is interesting.", p);
                            return true;
                        }
                    }
                } else if (event instanceof RefUpdated) {
                    RefUpdated refUpdated = (RefUpdated)event;
                    if (p.isInteresting(refUpdated.getRefUpdate().getProject(),
                            refUpdated.getRefUpdate().getRefName())) {
                        logger.trace("According to {} the event is interesting.", p);
                        return true;
                    }
                }
            } catch (PatternSyntaxException pse) {
                logger.error(MessageFormat.format("Exception caught for project {0} and pattern {1}, message: {2}",
                       new Object[]{myProject.getName(), p.getPattern(), pse.getMessage()}));
            }
        }
        logger.trace("Nothing interesting here, move along folks!");
        return false;
    }

    /**
     * Called when a ChangeAbandoned event arrives. Should probably not be listening on this here.
     *
     * @param event the event.
     */
    @Override
    public void gerritEvent(ChangeAbandoned event) {
        logger.trace("event: {}", event);
        if (!myProject.isBuildable()) {
            logger.trace("Disabled.");
            return;
        }
        if (isInteresting(event)) {
            logger.trace("The event is interesting.");
            if (!silentMode) {
                ToGerritRunListener.getInstance().onTriggered(myProject, event);
            }
            GerritCause cause = new GerritCause(event, silentMode);
            schedule(cause, event);
        }
    }

    /**
     * Called when a ChangeMerged event arrives.
     *
     * @param event the event.
     */
    @Override
    public void gerritEvent(ChangeMerged event) {
        logger.trace("event: {}", event);
        if (!myProject.isBuildable()) {
            logger.trace("Disabled.");
            return;
        }
        if (isInteresting(event)) {
            logger.trace("The event is interesting.");
            if (!silentMode) {
                ToGerritRunListener.getInstance().onTriggered(myProject, event);
            }
            GerritCause cause = new GerritCause(event, silentMode);
            schedule(cause, event);
        }
    }

    /**
     * Called when a ChangeRestored event arrives.
     *
     * @param event the event.
     */
    @Override
    public void gerritEvent(ChangeRestored event) {
        logger.trace("event: {}", event);
        if (!myProject.isBuildable()) {
            logger.trace("Disabled.");
            return;
        }
        if (isInteresting(event)) {
            logger.trace("The event is interesting.");
            if (!silentMode) {
                ToGerritRunListener.getInstance().onTriggered(myProject, event);
            }
            GerritCause cause = new GerritCause(event, silentMode);
            schedule(cause, event);
        }
    }

    /**
     * Checks if the approvals associated with this comment-added event match what
     * this trigger is configured to look for.
     *
     * @param event the event.
     * @return true if the event matches the approval category and value configured.
     */
    private boolean matchesApproval(CommentAdded event) {
        PluginCommentAddedEvent commentAdded = null;
        for (PluginGerritEvent e : triggerOnEvents) {
            if (e instanceof PluginCommentAddedEvent) {
                commentAdded = (PluginCommentAddedEvent)e;
                for (Approval approval : event.getApprovals()) {
                    if (approval.getType().equals(commentAdded.getVerdictCategory())
                        && (approval.getValue().equals(commentAdded.getCommentAddedTriggerApprovalValue())
                        || ("+" + approval.getValue()).equals(commentAdded.getCommentAddedTriggerApprovalValue()))) {
                    return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Called when a CommentAdded event arrives.
     *
     * @param event the event.
     */
    @Override
    public void gerritEvent(CommentAdded event) {
        logger.trace("event: {}", event);
        if (!myProject.isBuildable()) {
            logger.trace("Disabled.");
            return;
        }
        if (ToGerritRunListener.getInstance().isBuilding(myProject, event)) {
            logger.trace("Already building.");
            return;
        }
        if (isInteresting(event) && matchesApproval(event)) {
            logger.trace("The event is interesting.");
            if (!silentMode) {
                ToGerritRunListener.getInstance().onTriggered(myProject, event);
            }
            GerritCause cause = new GerritCause(event, silentMode);
            schedule(cause, event);
        }
    }

    /**
     * Called when a RefUpdated event arrives.
     *
     * @param event the event.
     */
    @Override
    public void gerritEvent(RefUpdated event) {
        logger.trace("event: {}", event);
        if (!myProject.isBuildable()) {
            logger.trace("Disabled.");
            return;
        }
        if (isInteresting(event)) {
            logger.trace("The event is interesting.");
            if (!silentMode) {
                ToGerritRunListener.getInstance().onTriggered(myProject, event);
            }
            GerritCause cause = new GerritCause(event, silentMode);
            schedule(cause, event);
        }
    }

    /**
     * The list of GerritProject triggering rules.
     *
     * @return the rule-set.
     */
    public List<GerritProject> getGerritProjects() {
        return gerritProjects;
    }

    /**
     * The list of dynamically configured triggering rules.
     *
     *  @return the rule-set.
     */
    public List<GerritProject> getDynamicGerritProjects() {
        return dynamicGerritProjects;
    }

    /**
     * The list of GerritProject triggering rules.
     *
     * @param gerritProjects the rule-set
     */
    public void setGerritProjects(List<GerritProject> gerritProjects) {
        this.gerritProjects = gerritProjects;
    }

    /**
     * Job specific Gerrit code review vote when a build is failed, null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildFailedCodeReviewValue() {
        return gerritBuildFailedCodeReviewValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is failed, providing null means that the global value should be
     * used.
     *
     * @param gerritBuildFailedCodeReviewValue
     *         the vote value.
     */
    public void setGerritBuildFailedCodeReviewValue(Integer gerritBuildFailedCodeReviewValue) {
        this.gerritBuildFailedCodeReviewValue = gerritBuildFailedCodeReviewValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is failed, null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildFailedVerifiedValue() {
        return gerritBuildFailedVerifiedValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is failed, providing null means that the global value should be
     * used.
     *
     * @param gerritBuildFailedVerifiedValue the vote value.
     */
    public void setGerritBuildFailedVerifiedValue(Integer gerritBuildFailedVerifiedValue) {
        this.gerritBuildFailedVerifiedValue = gerritBuildFailedVerifiedValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is started, null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildStartedCodeReviewValue() {
        return gerritBuildStartedCodeReviewValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is started, providing null means that the global value should
     * be used.
     *
     * @param gerritBuildStartedCodeReviewValue
     *         the vote value.
     */
    public void setGerritBuildStartedCodeReviewValue(Integer gerritBuildStartedCodeReviewValue) {
        this.gerritBuildStartedCodeReviewValue = gerritBuildStartedCodeReviewValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is started, null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildStartedVerifiedValue() {
        return gerritBuildStartedVerifiedValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is started, providing null means that the global value should be
     * used.
     *
     * @param gerritBuildStartedVerifiedValue
     *         the vote value.
     */
    public void setGerritBuildStartedVerifiedValue(Integer gerritBuildStartedVerifiedValue) {
        this.gerritBuildStartedVerifiedValue = gerritBuildStartedVerifiedValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is successful, null means that the global value should be
     * used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildSuccessfulCodeReviewValue() {
        return gerritBuildSuccessfulCodeReviewValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is successful, providing null means that the global value
     * should be used.
     *
     * @param gerritBuildSuccessfulCodeReviewValue
     *         the vote value.
     */
    public void setGerritBuildSuccessfulCodeReviewValue(Integer gerritBuildSuccessfulCodeReviewValue) {
        this.gerritBuildSuccessfulCodeReviewValue = gerritBuildSuccessfulCodeReviewValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is successful, null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildSuccessfulVerifiedValue() {
        return gerritBuildSuccessfulVerifiedValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is successful, providing null means that the global value should
     * be used.
     *
     * @param gerritBuildSuccessfulVerifiedValue
     *         the vote value.
     */
    public void setGerritBuildSuccessfulVerifiedValue(Integer gerritBuildSuccessfulVerifiedValue) {
        this.gerritBuildSuccessfulVerifiedValue = gerritBuildSuccessfulVerifiedValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is unstable, null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildUnstableCodeReviewValue() {
        return gerritBuildUnstableCodeReviewValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is unstable, providing null means that the global value should
     * be used.
     *
     * @param gerritBuildUnstableCodeReviewValue
     *         the vote value.
     */
    public void setGerritBuildUnstableCodeReviewValue(Integer gerritBuildUnstableCodeReviewValue) {
        this.gerritBuildUnstableCodeReviewValue = gerritBuildUnstableCodeReviewValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is unstable, null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildUnstableVerifiedValue() {
        return gerritBuildUnstableVerifiedValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is unstable, providing null means that the global value should be
     * used.
     *
     * @param gerritBuildUnstableVerifiedValue
     *         the vote value.
     */
    public void setGerritBuildUnstableVerifiedValue(Integer gerritBuildUnstableVerifiedValue) {
        this.gerritBuildUnstableVerifiedValue = gerritBuildUnstableVerifiedValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is not built, null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildNotBuiltCodeReviewValue() {
        return gerritBuildNotBuiltCodeReviewValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is not built, providing null means that the global value should
     * be used.
     *
     * @param gerritBuildNotBuiltCodeReviewValue
     *         the vote value.
     */
    public void setGerritBuildNotBuiltCodeReviewValue(Integer gerritBuildNotBuiltCodeReviewValue) {
        this.gerritBuildNotBuiltCodeReviewValue = gerritBuildNotBuiltCodeReviewValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is not built, null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildNotBuiltVerifiedValue() {
        return gerritBuildNotBuiltVerifiedValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is not built, providing null means that the global value should be
     * used.
     *
     * @param gerritBuildNotBuiltVerifiedValue
     *         the vote value.
     */
    public void setGerritBuildNotBuiltVerifiedValue(Integer gerritBuildNotBuiltVerifiedValue) {
        this.gerritBuildNotBuiltVerifiedValue = gerritBuildNotBuiltVerifiedValue;
    }

    /**
     * Sets the path to a file that contains the unsuccessful Gerrit comment message.
     *
     * @param path The unsuccessful message comment file path
     */
    public void setBuildUnsuccessfulFilepath(String path) {
        buildUnsuccessfulFilepath = path;
    }

    /**
     * Getter for the triggerOnEvents list.
     * @return the list.
     */
    public List<PluginGerritEvent> getTriggerOnEvents() {
        initializeTriggerOnEvents();
        return triggerOnEvents;
    }

    /**
     * Initializes the triggerOnEvents list.  If it is empty or null, adds patch set created
     * and draft published events (the latter only if supported by the current Gerrit version).
     */
    private void initializeTriggerOnEvents() {
        if (triggerOnEvents == null) {
            triggerOnEvents = new LinkedList<PluginGerritEvent>();
        }
        if (triggerOnEvents.isEmpty()) {
            triggerOnEvents.add(new PluginPatchsetCreatedEvent());
            if (isTriggerOnDraftPublishedEnabled()) {
                triggerOnEvents.add(new PluginDraftPublishedEvent());
            }
        }
    }

    /**
     * If trigger configuration should be fetched from a URL or not.
     *
     * @return true if trigger configuration should be fetched from a URL.
     */
    public boolean isDynamicTriggerConfiguration() {
        return dynamicTriggerConfiguration;
    }

    /**
     * Set if dynamic trigger configuration should be enabled or not.
     *
     * @param dynamicTriggerConfiguration
     *         true if dynamic trigger configuration should be enabled.
     */
    public void setDynamicTriggerConfiguration(boolean dynamicTriggerConfiguration) {
        this.dynamicTriggerConfiguration = dynamicTriggerConfiguration;
    }

    /**
     * Is checking and triggering missed patches allowed when connection is created.
     *
     * @return true if checking and triggering missing patches is allowed.
     */
    public boolean isAllowTriggeringUnreviewedPatches() {
        return allowTriggeringUnreviewedPatches;
    }

    /**
     * Set if triggering missing patches configuration should be enabled or not.
     *
     * @param allowTriggeringUnreviewedPatches
     *         true if triggering missing patches configuration should be enabled.
     */
    public void setAllowTriggeringUnreviewedPatches(boolean allowTriggeringUnreviewedPatches) {
        this.allowTriggeringUnreviewedPatches = allowTriggeringUnreviewedPatches;
    }

    /**
     * The URL where the trigger configuration should be fetched from.
     *
     * @return the URL, or null if this feature is not used.
     */
    public String getTriggerConfigURL() {
        return triggerConfigURL;
    }

    /**
     * Set the URL where the trigger configuration should be fetched from.
     *
     * @param triggerConfigURL
     *         the URL where the trigger configuration should be fetched from.
     */
    public void setTriggerConfigURL(String triggerConfigURL) {
        this.triggerConfigURL = triggerConfigURL;
    }

    /**
     * If silent mode is on or off. When silent mode is on there will be no communication back to Gerrit, i.e. no build
     * started/failed/successful approve messages etc. Default is false.
     *
     * @return true if silent mode is on.
     */
    public boolean isSilentMode() {
        return silentMode;
    }

    /**
     * if escapeQuotes is on or off. When escapeQuotes is on this plugin will escape quotes in Gerrit event parameter
     * string Default is true
     *
     * @return true if escapeQuotes is on.
     */
    public boolean isEscapeQuotes() {

        return escapeQuotes;
    }

    /**
     * Sets escapeQuotes to on or off. When escapeQuotes is on plugin will escape quotes in Gerrit event parameter
     * string. Default is false.
     *
     * @param escapeQuotes is true if escapeQuotes should be on.
     */
    public void setEscapeQuotes(boolean escapeQuotes) {
        this.escapeQuotes = escapeQuotes;
    }

    /**
     * If noNameAndEmailParameters is on or off. When this is set on this plugin will not create parameters
     * which combine a name with an email (this applies change owner, restorer, etc). These parameters cause
     * problems with some configurations.
     *
     * @return true if noNameAndEmailParameters is on.
     */
    public boolean isNoNameAndEmailParameters() {
        return noNameAndEmailParameters;
    }

    /**
     * Sets noNameAndEmailParameters to on or off.  When this is set on this plugin will not create parameters
     * which combine a name with an email (this applies change owner, restorer, etc). These parameters cause
     * problems with some configurations.
     *
     * @param noNameAndEmailParameters is true if problematic parameters should be omitted.
     */
    public void setNoNameAndEmailParameters(boolean noNameAndEmailParameters) {
        this.noNameAndEmailParameters = noNameAndEmailParameters;
    }

    /**
     * The message to show users when a build starts, if custom messages are enabled.
     *
     * @return The build start message
     */
    public String getBuildStartMessage() {
        return buildStartMessage;
    }

    /**
     * The message to show users when a build succeeds, if custom messages are enabled.
     *
     * @return The build successful message
     */
    public String getBuildSuccessfulMessage() {
        return buildSuccessfulMessage;
    }

    /**
     * The message to show users when a build is unstable, if custom messages are enabled.
     *
     * @return The build unstable message
     */
    public String getBuildUnstableMessage() {
        return buildUnstableMessage;
    }

    /**
     * The message to show users when a build finishes, if custom messages are enabled.
     *
     * @return The build failure message
     */
    public String getBuildFailureMessage() {
        return buildFailureMessage;
    }

    /**
     * The message to show users when all builds are not built, if custom messages are enabled.
     *
     * @return The build not built message
     */
    public String getBuildNotBuiltMessage() {
        return buildNotBuiltMessage;
    }

    /**
     * The path to a file that contains the unsuccessful Gerrit comment message.
     *
     * @return The unsuccessful message comment file path
     */
    public String getBuildUnsuccessfulFilepath() {
        return buildUnsuccessfulFilepath;
    }

    /**
     * Sets silent mode to on or off. When silent mode is on there will be no communication back to Gerrit, i.e. no
     * build started/failed/successful approve messages etc. Default is false.
     *
     * @param silentMode true if silent mode should be on.
     */
    public void setSilentMode(boolean silentMode) {
        this.silentMode = silentMode;
    }

    /**
     * URL to send in comment to gerrit.
     *
     * @return custom URL to post back to gerrit
     */
    public String getCustomUrl() {
        return customUrl;
    }

    /**
     * Set custom URL to post back to gerrit.
     *
     * @param customUrl url to set
     */
    public void setCustomUrl(String customUrl) {
        this.customUrl = customUrl;
    }

    /**
     * Convenience method for finding it out if file triggering is enabled in the gerrit version.
     * @return true if file triggering is enabled in the gerrit version.
     */
    public boolean isFileTriggerEnabled() {
        return GerritVersionChecker.isCorrectVersion(GerritVersionChecker.Feature.fileTrigger);
    }

    /**
     * This method is called by the timer thread at regular intervals. It fetches the URL,
     * determines if the result is different than from the last fetch, and if so, replaces
     * the current URL trigger configuration with the fetched one.
     */
    public void updateTriggerConfigURL() {
        if (triggerInformationAction == null) {
            triggerInformationAction = new GerritTriggerInformationAction();
        }
        triggerInformationAction.setErrorMessage("");
        try {

            List<GerritProject> fetchedProjects = GerritDynamicUrlProcessor.fetch(triggerConfigURL);
            dynamicGerritProjects = fetchedProjects;
        } catch (ParseException pe) {
            String logErrorMessage = MessageFormat.format(
                    "ParseException for project: {0} and URL: {1} Message: {2}",
                    new Object[]{myProject.getName(), triggerConfigURL, pe.getMessage()});
            logger.error(logErrorMessage, pe);
            String triggerInformationMessage = MessageFormat.format(
                    "ParseException when fetching dynamic trigger url: {0}", pe.getMessage());
            triggerInformationAction.setErrorMessage(triggerInformationMessage);
        } catch (MalformedURLException mue) {
            String logErrorMessage = MessageFormat.format(
                    "MalformedURLException for project: {0} and URL: {1} Message: {2}",
                    new Object[]{myProject.getName(), triggerConfigURL, mue.getMessage()});
            logger.error(logErrorMessage, mue);
            String triggerInformationMessage = MessageFormat.format(
                    "MalformedURLException when fetching dynamic trigger url: {0}", mue.getMessage());
            triggerInformationAction.setErrorMessage(triggerInformationMessage);
        } catch (SocketTimeoutException ste) {
            String logErrorMessage = MessageFormat.format(
                    "SocketTimeoutException for project: {0} and URL: {1} Message: {2}",
                    new Object[]{myProject.getName(), triggerConfigURL, ste.getMessage()});
            logger.error(logErrorMessage, ste);
            String triggerInformationMessage = MessageFormat.format(
                    "SocketTimeoutException when fetching dynamic trigger url: {0}", ste.getMessage());
            triggerInformationAction.setErrorMessage(triggerInformationMessage);

        } catch (IOException ioe) {
            String logErrorMessage = MessageFormat.format(
                    "IOException for project: {0} and URL: {1} Message: {2}",
                    new Object[]{myProject.getName(), triggerConfigURL, ioe.getMessage()});
            logger.error(logErrorMessage, ioe);
            String triggerInformationMessage = MessageFormat.format(
                    "IOException when fetching dynamic trigger url: {0}", ioe.getMessage());
            triggerInformationAction.setErrorMessage(triggerInformationMessage);
        }
    }

    /**
     * Convenience method for finding it out if triggering on draft published is enabled in the Gerrit version.
     * @return true if triggering on draft published is enabled in the Gerrit version.
     */
    public boolean isTriggerOnDraftPublishedEnabled() {
        return GerritVersionChecker.isCorrectVersion(GerritVersionChecker.Feature.triggerOnDraftPublished);
    }

    @Override
    public List<Action> getProjectActions() {
        List<Action> list = new LinkedList<Action>();
        list.add(triggerInformationAction);
        return list;
    }

    /**
     * The skip vote selection.
     * &quot;Skipping&quot; the vote means that if more than one build of this job is triggered by a Gerrit event
     * the outcome of this build won't be counted when the final vote is sent to Gerrit.
     *
     * @return data structure for what build results to skip.
     */
    public SkipVote getSkipVote() {
        return skipVote;
    }

    /**
     * The Descriptor for the Trigger.
     */
    @Extension
    public static final class DescriptorImpl extends TriggerDescriptor {

        /**
         * Checks that the provided parameter is an empty string or an integer.
         *
         * @param value the value.
         * @return {@link FormValidation#validatePositiveInteger(String)}
         */
        public FormValidation doEmptyOrIntegerCheck(
                @QueryParameter("value")
                final String value) {
            if (value == null || value.length() <= 0) {
                return FormValidation.ok();
            } else {
                try {
                    Integer.parseInt(value);
                    return FormValidation.ok();
                } catch (NumberFormatException e) {
                    return FormValidation.error(hudson.model.Messages.Hudson_NotANumber());
                }
            }
        }

        /**
         * Checks that the provided parameter is nonempty and a valid URL.
         *
         * @param value the value.
         * @return {@link hudson.util.FormValidation#ok()}
         */
        public FormValidation doUrlCheck(
                @QueryParameter("value")
                final String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.error(Messages.EmptyError());
            }

            try {
                URL url = new URL(value); // Check for protocol errors
                url.toURI(); // Perform some extra checking
                return FormValidation.ok();
            } catch (java.net.MalformedURLException e) {
                return FormValidation.error(Messages.BadUrlError());
            } catch (java.net.URISyntaxException e) {
                return FormValidation.error(Messages.BadUrlError());
            }
        }

        /**
         * Default Constructor.
         */
        public DescriptorImpl() {
            super(GerritTrigger.class);
        }

        @Override
        public boolean isApplicable(Item item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.TriggerDisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/plugin/gerrit-trigger/help-whatIsGerritTrigger.html";
        }

        /**
         * Fills the verdict category drop-down list.
         * @return a ListBoxModel for the drop-down list.
         */
        public ListBoxModel doFillVerdictCategoryItems() {
            ListBoxModel m = new ListBoxModel();
            List<VerdictCategory> list = PluginImpl.getInstance().getConfig().getCategories();
            if (list != null && !list.isEmpty()) {
                for (VerdictCategory v : list) {
                    m.add(v.getVerdictDescription(), v.getVerdictValue());
                }
            }
            return m;
        }

        /**
         * A list of CompareTypes for the UI.
         *
         * @return A list of CompareTypes
         */
        public CompareType[] getCompareTypes() {
            return CompareType.values();
        }
        /**
        * Convenience method for the jelly file, accessing the instance proved to be hard.
        * @return true if file triggering is enabled in the gerrit version.
        */
        public boolean isFileTriggerEnabled() {
            return GerritVersionChecker.isCorrectVersion(GerritVersionChecker.Feature.fileTrigger);
        }

        /**
         * Getter for the list of PluginGerritEventDescriptors.
         * @return the list.
         */
        public List<PluginGerritEvent.PluginGerritEventDescriptor> getGerritEventDescriptors() {
            ExtensionList<PluginGerritEvent.PluginGerritEventDescriptor> extensionList =
                    Hudson.getInstance().getExtensionList(PluginGerritEvent.PluginGerritEventDescriptor.class);
            return extensionList;
        }
    }

    /**
     * Class for maintaining and synchronizing the runningJobs info.
     * Association between patches and the jobs that we're running for them.
     */
    public class RunningJobs {
        private final HashMap<GerritTriggeredEvent, ParametersAction> runningJobs =
                new HashMap<GerritTriggeredEvent, ParametersAction>();

        /**
         * Does the needful after a build has been scheduled.
         * I.e. cancelling the old build if configured to do so and removing and storing any references.
         *
         * @param event the event triggering a new build.
         * @param parameters the parameters for the new build, used to find it later.
         * @param projectName the name of the current project for better logging.
         */
        public synchronized void scheduled(ChangeBasedEvent event, ParametersAction parameters, String projectName) {
            if (!PluginImpl.getInstance().getConfig().isGerritBuildCurrentPatchesOnly()) {
                return;
            }
            Iterator<Entry<GerritTriggeredEvent, ParametersAction>> it = runningJobs.entrySet().iterator();
            while (it.hasNext()) {
                Entry<GerritTriggeredEvent, ParametersAction> pairs = it.next();
                // Find all entries in runningJobs with the same Change #.
                if (pairs.getKey() instanceof ChangeBasedEvent) {
                    if (((ChangeBasedEvent)pairs.getKey()).getChange().equals(event.getChange())) {
                        logger.debug("Cancelling build for " + pairs.getKey());
                        try {
                            cancelJob(pairs.getValue());
                        } catch (Exception e) {
                            // Ignore any problems with canceling the job.
                            logger.error("Error canceling job", e);
                        }
                        it.remove();
                    }
                }
            }
            // add our new job
            runningJobs.put(event, parameters);
        }

        /**
         * Tries to cancel any jobs with the specified parameters. We look in
         * both the build queue and currently executing jobs. This extra work is
         * required due to race conditions when calling Future.cancel() - see
         * https://issues.jenkins-ci.org/browse/JENKINS-13829
         *
         * @param parameters
         *            The parameters to match against.
         */
        private void cancelJob(ParametersAction parameters) {
            // Remove any jobs in the build queue.
            List<hudson.model.Queue.Item> itemsInQueue = Queue.getInstance().getItems(myProject);
            for (hudson.model.Queue.Item item  : itemsInQueue) {
                List<ParametersAction> params = item.getActions(ParametersAction.class);
                for (ParametersAction param : params) {
                    if (param.equals(parameters)) {
                        Queue.getInstance().cancel(item);
                    }
                }
            }

            // Interrupt any currently running jobs.
            for (Computer c : Hudson.getInstance().getComputers()) {
                for (Executor e : c.getExecutors()) {
                    if (e.getCurrentExecutable() instanceof Actionable) {
                        Actionable a = (Actionable)e.getCurrentExecutable();
                        List<ParametersAction> params = a.getActions(ParametersAction.class);
                        for (ParametersAction param : params) {
                            if (param.equals(parameters)) {
                                e.interrupt();
                            }
                        }
                    }
                }
            }
        }

        /**
         * Removes any reference to the current build for this change.
         *
         * @param event the event which started the build we want to remove.
         * @return the build that was removed.
         */
        public synchronized ParametersAction remove(ChangeBasedEvent event) {
            logger.debug("Removing future job " + event.getPatchSet().getNumber());
            return runningJobs.remove(event);
        }
    }
}
