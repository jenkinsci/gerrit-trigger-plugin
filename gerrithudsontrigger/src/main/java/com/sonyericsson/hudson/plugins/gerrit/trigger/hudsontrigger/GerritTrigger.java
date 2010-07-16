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

import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Triggers a build based on Gerrit events.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritTrigger extends Trigger<AbstractProject> implements GerritEventListener {

    /**
     * The schedule delay for a build so there is some time to save the trigger event.
     * 3 is a MagicNumber.
     */
    public static final int BUILD_SCHEDULE_DELAY = 3;
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

    /**
     * Default DataBound Constructor.
     * @param gerritProjects                        the set of triggering rules.
     * @param gerritBuildStartedVerifiedValue       Job specific Gerrit verified vote when a build is started,
     *                                               null means that the global value should be used.
     * @param gerritBuildStartedCodeReviewValue     Job specific Gerrit code review vote when a build is started,
     *                                               null means that the global value should be used.
     * @param gerritBuildSuccessfulVerifiedValue    Job specific Gerrit verified vote when a build is successful,
     *                                               null means that the global value should be used.
     * @param gerritBuildSuccessfulCodeReviewValue  Job specific Gerrit code review vote when a build is successful,
     *                                              null means that the global value should be used.
     * @param gerritBuildFailedVerifiedValue        Job specific Gerrit verified vote when a build is failed,
     *                                               null means that the global value should be used.
     * @param gerritBuildFailedCodeReviewValue      Job specific Gerrit code review vote when a build is failed,
     *                                               null means that the global value should be used.
     * @param gerritBuildUnstableVerifiedValue      Job specific Gerrit verified vote when a build is unstable,
     *                                               null means that the global value should be used.
     * @param gerritBuildUnstableCodeReviewValue    Job specific Gerrit code review vote when a build is unstable,
     *                                               null means that the global value should be used.
     * @param silentMode                            Silent Mode on or off.
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
            boolean silentMode) {

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
            final GerritCause cause = new GerritCause(event, silentMode);
            schedule(cause, event);
        }
    }

    /**
     * Schedules a build with parameters from the event.
     * With {@link #myProject} as the project to build.
     * @param cause the cause of the build.
     * @param event the event.
     */
    protected void schedule(GerritCause cause, PatchsetCreated event) {
        schedule(cause, event, myProject);
    }

    /**
     * Schedules a build with parameters from the event.
     * @param cause the cause of the build.
     * @param event the event.
     * @param project the project to build.
     */
    protected void schedule(GerritCause cause, PatchsetCreated event, AbstractProject project) {
        //during low traffic we still don't want to spam Gerrit, 3 is a nice number, isn't it?
        boolean ok = project.scheduleBuild(
                BUILD_SCHEDULE_DELAY,
                cause,
                new BadgeAction(event),
                createParameters(event, cause, project));

        logger.info("Project {} Build Scheduled: {} By event: {}",
                new Object[]{project.getName(),
                    ok,
                    event.getChange().getNumber() + "/" + event.getPatchSet().getNumber(), });
    }

    /**
     * Creates a ParameterAction and fills it with the project's default parameters + the Standard Gerrit parameters.
     * @param event the event.
     * @param cause the cause.
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
     * @param parameters the list of existing parameters.
     * @param name the name of the parameter.
     * @param value the value.
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
        parameter = new StringParameterValue(name, value, description);
        parameters.add(parameter);
    }

    /**
     * Retrieves all default parameter values for a project.
     * Copied from {@link AbstractProject#getDefaultParametersValues()} version 1.362.
     * TODO: This is not a good way to solve the problem.
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
     * @param context the previous context.
     */
    public void retriggerThisBuild(TriggerContext context) {
        if (context.getThisBuild().getProject().isBuildable()
            && !ToGerritRunListener.getInstance().isBuilding(context.getThisBuild().getProject(), context.getEvent())) {

            if (!silentMode) {
                ToGerritRunListener.getInstance().onRetriggered(
                        context.getThisBuild().getProject(),
                        context.getEvent(),
                        context.getOtherBuilds());
            }
            final GerritUserCause cause = new GerritUserCause(context.getEvent(), silentMode);
            schedule(cause, context.getEvent());
        }
    }

    /**
     * Called when a ChangeAbandoned event arrives.
     * Should probably not be listening on this here.
     * @param event the event.
     */
    @Override
    public void gerritEvent(ChangeAbandoned event) {
        //TODO Implement
    }

    /**
     * The list of GerritProject triggering rules.
     * @return the rule-set.
     */
    public List<GerritProject> getGerritProjects() {
        return gerritProjects;
    }

    /**
     * The list of GerritProject triggering rules.
     * @param gerritProjects the rule-set
     */
    public void setGerritProjects(List<GerritProject> gerritProjects) {
        this.gerritProjects = gerritProjects;
    }

    /**
     * Job specific Gerrit code review vote when a build is failed,
     * null means that the global value should be used.
     * @return the vote value.
     */
    public Integer getGerritBuildFailedCodeReviewValue() {
        return gerritBuildFailedCodeReviewValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is failed,
     * providing null means that the global value should be used.
     * @param gerritBuildFailedCodeReviewValue the vote value.
     */
    public void setGerritBuildFailedCodeReviewValue(Integer gerritBuildFailedCodeReviewValue) {
        this.gerritBuildFailedCodeReviewValue = gerritBuildFailedCodeReviewValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is failed,
     * null means that the global value should be used.
     * @return the vote value.
     */
    public Integer getGerritBuildFailedVerifiedValue() {
        return gerritBuildFailedVerifiedValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is failed,
     * providing null means that the global value should be used.
     * @param gerritBuildFailedVerifiedValue the vote value.
     */
    public void setGerritBuildFailedVerifiedValue(Integer gerritBuildFailedVerifiedValue) {
        this.gerritBuildFailedVerifiedValue = gerritBuildFailedVerifiedValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is started,
     * null means that the global value should be used.
     * @return the vote value.
     */
    public Integer getGerritBuildStartedCodeReviewValue() {
        return gerritBuildStartedCodeReviewValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is started,
     * providing null means that the global value should be used.
     * @param gerritBuildStartedCodeReviewValue the vote value.
     */
    public void setGerritBuildStartedCodeReviewValue(Integer gerritBuildStartedCodeReviewValue) {
        this.gerritBuildStartedCodeReviewValue = gerritBuildStartedCodeReviewValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is started,
     * null means that the global value should be used.
     * @return the vote value.
     */
    public Integer getGerritBuildStartedVerifiedValue() {
        return gerritBuildStartedVerifiedValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is started,
     * providing null means that the global value should be used.
     * @param gerritBuildStartedVerifiedValue the vote value.
     */
    public void setGerritBuildStartedVerifiedValue(Integer gerritBuildStartedVerifiedValue) {
        this.gerritBuildStartedVerifiedValue = gerritBuildStartedVerifiedValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is successful,
     * null means that the global value should be used.
     * @return the vote value.
     */
    public Integer getGerritBuildSuccessfulCodeReviewValue() {
        return gerritBuildSuccessfulCodeReviewValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is successful,
     * providing null means that the global value should be used.
     * @param gerritBuildSuccessfulCodeReviewValue the vote value.
     */
    public void setGerritBuildSuccessfulCodeReviewValue(Integer gerritBuildSuccessfulCodeReviewValue) {
        this.gerritBuildSuccessfulCodeReviewValue = gerritBuildSuccessfulCodeReviewValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is successful,
     * null means that the global value should be used.
     * @return the vote value.
     */
    public Integer getGerritBuildSuccessfulVerifiedValue() {
        return gerritBuildSuccessfulVerifiedValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is successful,
     * providing null means that the global value should be used.
     * @param gerritBuildSuccessfulVerifiedValue the vote value.
     */
    public void setGerritBuildSuccessfulVerifiedValue(Integer gerritBuildSuccessfulVerifiedValue) {
        this.gerritBuildSuccessfulVerifiedValue = gerritBuildSuccessfulVerifiedValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is unstable,
     * null means that the global value should be used.
     * @return the vote value.
     */
    public Integer getGerritBuildUnstableCodeReviewValue() {
        return gerritBuildUnstableCodeReviewValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is unstable,
     * providing null means that the global value should be used.
     * @param gerritBuildUnstableCodeReviewValue  the vote value.
     */
    public void setGerritBuildUnstableCodeReviewValue(Integer gerritBuildUnstableCodeReviewValue) {
        this.gerritBuildUnstableCodeReviewValue = gerritBuildUnstableCodeReviewValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is unstable,
     * null means that the global value should be used.
     * @return the vote value.
     */
    public Integer getGerritBuildUnstableVerifiedValue() {
        return gerritBuildUnstableVerifiedValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is unstable,
     * providing null means that the global value should be used.
     * @param gerritBuildUnstableVerifiedValue the vote value.
     */
    public void setGerritBuildUnstableVerifiedValue(Integer gerritBuildUnstableVerifiedValue) {
        this.gerritBuildUnstableVerifiedValue = gerritBuildUnstableVerifiedValue;
    }

    /**
     * If silent mode is on or off.
     * When silent mode is on there will be no communication back to Gerrit,
     * i.e. no build started/failed/sucessfull approve messages etc.
     * Default is false.
     * @return true if silent mode is on.
     */
    public boolean isSilentMode() {
        return silentMode;
    }

    /**
     * Sets silent mode to on or off.
     * When silent mode is on there will be no communication back to Gerrit,
     * i.e. no build started/failed/sucessfull approve messages etc.
     * Default is false.
     * @param silentMode true if silent mode should be on.
     */
    public void setSilentMode(boolean silentMode) {
        this.silentMode = silentMode;
    }

    /**
     * Should we trigger on this event?
     * @param event the event
     * @return true if we should.
     */
    private boolean isInteresting(PatchsetCreated event) {
        logger.trace("entering isInteresting projects configured: {} the event: {}", gerritProjects.size(), event);
        for (GerritProject p : gerritProjects) {
            if (p.isInteresting(event.getChange().getProject(), event.getChange().getBranch())) {
                logger.trace("According to {} the event is interesting.", p);
                return true;
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
         * @return A list of CompareTypes
         */
        public CompareType[] getCompareTypes() {
            return CompareType.values();
        }
    }
}
