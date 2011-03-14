/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.RetriggerAction;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.RetriggerAllAction;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import org.kohsuke.stapler.QueryParameter;

/**
 * Triggers a build based on Gerrit events.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritTrigger extends Trigger<AbstractProject> implements GerritEventListener {

    /**
     * Parameter name for the commit subject (commit message's 1st. line).
     */
    public static final String GERRIT_CHANGE_SUBJECT = "GERRIT_CHANGE_SUBJECT";
    /**
     * Parameter name for the branch.
     */
    public static final String GERRIT_BRANCH = "GERRIT_BRANCH";
    /**
     * Parameter name for the change-id.
     */
    public static final String GERRIT_CHANGE_ID = "GERRIT_CHANGE_ID";
    /**
     * Parameter name for the change number.
     */
    public static final String GERRIT_CHANGE_NUMBER = "GERRIT_CHANGE_NUMBER";
    /**
     * Parameter name for the url to the change.
     */
    public static final String GERRIT_CHANGE_URL = "GERRIT_CHANGE_URL";
    /**
     * Parameter name for the patch set number.
     */
    public static final String GERRIT_PATCHSET_NUMBER = "GERRIT_PATCHSET_NUMBER";
    /**
     * Parameter name for the patch set revision.
     */
    public static final String GERRIT_PATCHSET_REVISION = "GERRIT_PATCHSET_REVISION";
    /**
     * Parameter name for the gerrit project name.
     */
    public static final String GERRIT_PROJECT = "GERRIT_PROJECT";
    /**
     * Parameter name for the refspec.
     */
    public static final String GERRIT_REFSPEC = "GERRIT_REFSPEC";
    private static final Logger logger = LoggerFactory.getLogger(GerritTrigger.class);
    private transient AbstractProject myProject;
    private List<GerritProject> gerritProjects;
    private Integer gerritBuildStartedVerifiedValue;
    private Integer gerritBuildStartedCodeReviewValue;
    private Integer gerritBuildSuccessfulVerifiedValue;
    private Integer gerritBuildSuccessfulCodeReviewValue;
    private Integer gerritBuildFailedVerifiedValue;
    private Integer gerritBuildFailedCodeReviewValue;
    private Integer gerritBuildUnstableVerifiedValue;
    private Integer gerritBuildUnstableCodeReviewValue;
    private boolean silentMode;
    private boolean escapeQuotes;
    private String buildStartMessage;
    private String buildFailureMessage;
    private String buildSuccessfulMessage;
    private String buildUnstableMessage;

    /**
     * Default DataBound Constructor.
     *
     * @param gerritProjects                 the set of triggering rules.
     * @param gerritBuildStartedVerifiedValue
     *                                       Job specific Gerrit verified vote when a build is started,
     *                                       null means that the global value should be used.
     * @param gerritBuildStartedCodeReviewValue
     *                                       Job specific Gerrit code review vote when a build is started,
     *                                       null means that the global value should be used.
     * @param gerritBuildSuccessfulVerifiedValue
     *                                       Job specific Gerrit verified vote when a build is successful,
     *                                       null means that the global value should be used.
     * @param gerritBuildSuccessfulCodeReviewValue
     *                                       Job specific Gerrit code review vote when a build is successful,
     *                                       null means that the global value should be used.
     * @param gerritBuildFailedVerifiedValue Job specific Gerrit verified vote when a build is failed,
     *                                       null means that the global value should be used.
     * @param gerritBuildFailedCodeReviewValue
     *                                       Job specific Gerrit code review vote when a build is failed,
     *                                       null means that the global value should be used.
     * @param gerritBuildUnstableVerifiedValue
     *                                       Job specific Gerrit verified vote when a build is unstable,
     *                                       null means that the global value should be used.
     * @param gerritBuildUnstableCodeReviewValue
     *                                       Job specific Gerrit code review vote when a build is unstable,
     *                                       null means that the global value should be used.
     * @param silentMode                     Silent Mode on or off.
     * @param escapeQuotes                   EscapeQuotes on or off.
     * @param buildStartMessage              Message to write to Gerrit when a build begins
     * @param buildSuccessfulMessage         Message to write to Gerrit when a build succeeds
     * @param buildUnstableMessage           Message to write to Gerrit when a build is unstable
     * @param buildFailureMessage            Message to write to Gerrit when a build fails
     */
    @DataBoundConstructor
    public GerritTrigger(
            List<GerritProject> gerritProjects,
            Integer gerritBuildStartedVerifiedValue,
            Integer gerritBuildStartedCodeReviewValue,
            Integer gerritBuildSuccessfulVerifiedValue,
            Integer gerritBuildSuccessfulCodeReviewValue,
            Integer gerritBuildFailedVerifiedValue,
            Integer gerritBuildFailedCodeReviewValue,
            Integer gerritBuildUnstableVerifiedValue,
            Integer gerritBuildUnstableCodeReviewValue,
            boolean silentMode,
            boolean escapeQuotes,
            String buildStartMessage,
            String buildSuccessfulMessage,
            String buildUnstableMessage,
            String buildFailureMessage) {

        this.gerritProjects = gerritProjects;
        this.gerritBuildStartedVerifiedValue = gerritBuildStartedVerifiedValue;
        this.gerritBuildStartedCodeReviewValue = gerritBuildStartedCodeReviewValue;
        this.gerritBuildSuccessfulVerifiedValue = gerritBuildSuccessfulVerifiedValue;
        this.gerritBuildSuccessfulCodeReviewValue = gerritBuildSuccessfulCodeReviewValue;
        this.gerritBuildFailedVerifiedValue = gerritBuildFailedVerifiedValue;
        this.gerritBuildFailedCodeReviewValue = gerritBuildFailedCodeReviewValue;
        this.gerritBuildUnstableVerifiedValue = gerritBuildUnstableVerifiedValue;
        this.gerritBuildUnstableCodeReviewValue = gerritBuildUnstableCodeReviewValue;
        this.silentMode = silentMode;
        this.escapeQuotes = escapeQuotes;
        this.buildStartMessage = buildStartMessage;
        this.buildSuccessfulMessage = buildSuccessfulMessage;
        this.buildUnstableMessage = buildUnstableMessage;
        this.buildFailureMessage = buildFailureMessage;
    }

    @Override
    public void start(AbstractProject project, boolean newInstance) {
        logger.debug("Start project: {}", project);
        super.start(project, newInstance);
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
     * Schedules a build with parameters from the event.
     * With {@link #myProject} as the project to build.
     *
     * @param cause the cause of the build.
     * @param event the event.
     */
    protected void schedule(GerritCause cause, PatchsetCreated event) {
        schedule(cause, event, myProject);
    }

    /**
     * Schedules a build with parameters from the event.
     *
     * @param cause   the cause of the build.
     * @param event   the event.
     * @param project the project to build.
     */
    protected void schedule(GerritCause cause, PatchsetCreated event, AbstractProject project) {
        //during low traffic we still don't want to spam Gerrit, 3 is a nice number, isn't it?
        boolean ok = project.scheduleBuild(
                getBuildScheduleDelay(),
                cause,
                new BadgeAction(event),
                new RetriggerAction(cause.getContext()),
                new RetriggerAllAction(cause.getContext()),
                createParameters(event, cause, project));

        logger.info("Project {} Build Scheduled: {} By event: {}",
                new Object[]{project.getName(), ok,
                    event.getChange().getNumber() + "/" + event.getPatchSet().getNumber(), });
    }

    /**
     * getBuildScheduleDelay method will return configured
     * buildScheduledelay value.
     * If the value is missing or invalid it the method
     * will return default schedule delay or
     * {@link GerritDefaultValues#DEFAULT_BUILD_SCHEDULE_DELAY}.
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
     * @param cause   the cause.
     * @param project the project.
     * @return the ParameterAction.
     */
    protected ParametersAction createParameters(PatchsetCreated event, GerritCause cause, AbstractProject project) {
        List<ParameterValue> parameters = getDefaultParametersValues(project);
        setOrCreateStringParameterValue(parameters, GERRIT_BRANCH, event.getChange().getBranch());
        setOrCreateStringParameterValue(parameters, GERRIT_BRANCH, event.getChange().getBranch());
        setOrCreateStringParameterValue(parameters, GERRIT_CHANGE_NUMBER, event.getChange().getNumber());
        setOrCreateStringParameterValue(parameters, GERRIT_CHANGE_ID, event.getChange().getId());
        setOrCreateStringParameterValue(parameters, GERRIT_PATCHSET_NUMBER, event.getPatchSet().getNumber());
        setOrCreateStringParameterValue(parameters, GERRIT_PATCHSET_REVISION, event.getPatchSet().getRevision());
        setOrCreateStringParameterValue(parameters, GERRIT_REFSPEC, StringUtil.makeRefSpec(event));
        setOrCreateStringParameterValue(parameters, GERRIT_PROJECT, event.getChange().getProject());
        setOrCreateStringParameterValue(parameters, GERRIT_CHANGE_SUBJECT, event.getChange().getSubject());
        setOrCreateStringParameterValue(parameters, GERRIT_CHANGE_URL, cause.getUrl());
        return new ParametersAction(parameters);
    }

    /**
     * Creates a {@link StringParameterValue} and adds it to the provided list.
     * If the parameter with the same name already exists in the list it will be replaces by the new parameter,
     * but its description will be used, unless the parameter type is something else than a StringParameterValue.
     *
     * @param parameters the list of existing parameters.
     * @param name       the name of the parameter.
     * @param value      the value.
     */
    private void setOrCreateStringParameterValue(List<ParameterValue> parameters, String name, String value) {
        ParameterValue parameter = null;
        for (ParameterValue p : parameters) {
            if (p.getName().toUpperCase().equals(name)) {
                parameter = p;
                break;
            }
        }
        String description = null;
        if (parameter != null) {
            if (parameter instanceof StringParameterValue) {
                //Perhaps it is manually added to remind the user of what it is for.
                description = parameter.getDescription();
            }
            parameters.remove(parameter);
        }
        if (this.isEscapeQuotes()) {
            value = StringUtil.escapeQuotes(value);
        }

        parameter = new StringParameterValue(name, value, description);
        parameters.add(parameter);
    }

    /**
     * Retrieves all default parameter values for a project.
     * Copied from {@link AbstractProject#getDefaultParametersValues()} version 1.362.
     * TODO: This is not a good way to solve the problem.
     *
     * @param project the project.
     * @return the default parameter values.
     */
    private List<ParameterValue> getDefaultParametersValues(AbstractProject project) {
        ParametersDefinitionProperty paramDefProp =
                (ParametersDefinitionProperty)project.getProperty(ParametersDefinitionProperty.class);
        List<ParameterValue> defValues = new ArrayList<ParameterValue>();

        /*
         * This check is made ONLY if someone will call this method even if isParametrized() is false.
         */
        if (paramDefProp == null) {
            return defValues;
        }

        /* Scan for all parameter with an associated default values */
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
     * Will not do any
     * {@link #isInteresting(com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated)}
     * checks.
     * If more than one build was triggered by the event the results from those builds will be counted again,
     * but they won't be re-triggered.
     * If any builds for the event are still running, this new scheduled build will replace it's predesessor.
     * If the project is currently building the event, no scheduling will be done.
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
     * Retriggers all builds in the given context.
     * The builds will only be triggered if no builds for the event are building.
     *
     * @param context the context to rebuild.
     * @see ToGerritRunListener#isBuilding(com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated)
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
    private void retrigger(AbstractProject project, PatchsetCreated event) {
        if (project.isBuildable()) {
            if (!silentMode) {
                ToGerritRunListener.getInstance().onRetriggered(project, event, null);
            }
            GerritUserCause cause = new GerritUserCause(event, silentMode);
            schedule(cause, event, project);
        }
    }

    /**
     * Called when a ChangeAbandoned event arrives.
     * Should probably not be listening on this here.
     *
     * @param event the event.
     */
    @Override
    public void gerritEvent(ChangeAbandoned event) {
        //TODO Implement
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
     * The list of GerritProject triggering rules.
     *
     * @param gerritProjects the rule-set
     */
    public void setGerritProjects(List<GerritProject> gerritProjects) {
        this.gerritProjects = gerritProjects;
    }

    /**
     * Job specific Gerrit code review vote when a build is failed,
     * null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildFailedCodeReviewValue() {
        return gerritBuildFailedCodeReviewValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is failed,
     * providing null means that the global value should be used.
     *
     * @param gerritBuildFailedCodeReviewValue
     *         the vote value.
     */
    public void setGerritBuildFailedCodeReviewValue(Integer gerritBuildFailedCodeReviewValue) {
        this.gerritBuildFailedCodeReviewValue = gerritBuildFailedCodeReviewValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is failed,
     * null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildFailedVerifiedValue() {
        return gerritBuildFailedVerifiedValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is failed,
     * providing null means that the global value should be used.
     *
     * @param gerritBuildFailedVerifiedValue the vote value.
     */
    public void setGerritBuildFailedVerifiedValue(Integer gerritBuildFailedVerifiedValue) {
        this.gerritBuildFailedVerifiedValue = gerritBuildFailedVerifiedValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is started,
     * null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildStartedCodeReviewValue() {
        return gerritBuildStartedCodeReviewValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is started,
     * providing null means that the global value should be used.
     *
     * @param gerritBuildStartedCodeReviewValue
     *         the vote value.
     */
    public void setGerritBuildStartedCodeReviewValue(Integer gerritBuildStartedCodeReviewValue) {
        this.gerritBuildStartedCodeReviewValue = gerritBuildStartedCodeReviewValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is started,
     * null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildStartedVerifiedValue() {
        return gerritBuildStartedVerifiedValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is started,
     * providing null means that the global value should be used.
     *
     * @param gerritBuildStartedVerifiedValue
     *         the vote value.
     */
    public void setGerritBuildStartedVerifiedValue(Integer gerritBuildStartedVerifiedValue) {
        this.gerritBuildStartedVerifiedValue = gerritBuildStartedVerifiedValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is successful,
     * null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildSuccessfulCodeReviewValue() {
        return gerritBuildSuccessfulCodeReviewValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is successful,
     * providing null means that the global value should be used.
     *
     * @param gerritBuildSuccessfulCodeReviewValue
     *         the vote value.
     */
    public void setGerritBuildSuccessfulCodeReviewValue(Integer gerritBuildSuccessfulCodeReviewValue) {
        this.gerritBuildSuccessfulCodeReviewValue = gerritBuildSuccessfulCodeReviewValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is successful,
     * null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildSuccessfulVerifiedValue() {
        return gerritBuildSuccessfulVerifiedValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is successful,
     * providing null means that the global value should be used.
     *
     * @param gerritBuildSuccessfulVerifiedValue
     *         the vote value.
     */
    public void setGerritBuildSuccessfulVerifiedValue(Integer gerritBuildSuccessfulVerifiedValue) {
        this.gerritBuildSuccessfulVerifiedValue = gerritBuildSuccessfulVerifiedValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is unstable,
     * null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildUnstableCodeReviewValue() {
        return gerritBuildUnstableCodeReviewValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is unstable,
     * providing null means that the global value should be used.
     *
     * @param gerritBuildUnstableCodeReviewValue
     *         the vote value.
     */
    public void setGerritBuildUnstableCodeReviewValue(Integer gerritBuildUnstableCodeReviewValue) {
        this.gerritBuildUnstableCodeReviewValue = gerritBuildUnstableCodeReviewValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is unstable,
     * null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildUnstableVerifiedValue() {
        return gerritBuildUnstableVerifiedValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is unstable,
     * providing null means that the global value should be used.
     *
     * @param gerritBuildUnstableVerifiedValue
     *         the vote value.
     */
    public void setGerritBuildUnstableVerifiedValue(Integer gerritBuildUnstableVerifiedValue) {
        this.gerritBuildUnstableVerifiedValue = gerritBuildUnstableVerifiedValue;
    }

    /**
     * If silent mode is on or off.
     * When silent mode is on there will be no communication back to Gerrit,
     * i.e. no build started/failed/sucessfull approve messages etc.
     * Default is false.
     *
     * @return true if silent mode is on.
     */
    public boolean isSilentMode() {
        return silentMode;
    }

    /**
     *  if escapeQuotes is on or off.
     *  When escapeQuotes is on this plugin will escape quotes in Gerrit event parameter string
     *  Default is true
     *
     *  @return true if escapeQuotes is on.
     */
    public boolean isEscapeQuotes() {

        return escapeQuotes;
    }

    /**
     * Sets escapeQuotes to on or off.
     * When escapeQuotes is on plugin will escape quotes in Gerrit event parameter string.
     * Default is false.
     *
     * @param escapeQuotes is true if escapeQuotes should be on.
     */
    public void setEscapeQuotes(boolean escapeQuotes) {
        this.escapeQuotes = escapeQuotes;
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
     * Sets silent mode to on or off.
     * When silent mode is on there will be no communication back to Gerrit,
     * i.e. no build started/failed/sucessfull approve messages etc.
     * Default is false.
     *
     * @param silentMode true if silent mode should be on.
     */
    public void setSilentMode(boolean silentMode) {
        this.silentMode = silentMode;
    }

    /**
     * Should we trigger on this event?
     *
     * @param event the event
     * @return true if we should.
     */
    private boolean isInteresting(PatchsetCreated event) {
        if (gerritProjects != null) {
            logger.trace("entering isInteresting projects configured: {} the event: {}", gerritProjects.size(), event);
            for (GerritProject p : gerritProjects) {
                if (p.isInteresting(event.getChange().getProject(), event.getChange().getBranch())) {
                    logger.trace("According to {} the event is interesting.", p);
                    return true;
                }
            }
        }
        logger.trace("Nothing interesting here, move along folks!");
        return false;
    }

    /**
     * The Descriptor for the Trigger.
     */
    @Extension
    public static final class DescriptorImpl extends TriggerDescriptor {

        /**
         * Checks that the provided parameter is an empty string or an integer.
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
         * A list of CompareTypes for the UI.
         *
         * @return A list of CompareTypes
         */
        public CompareType[] getCompareTypes() {
            return CompareType.values();
        }
    }
}
