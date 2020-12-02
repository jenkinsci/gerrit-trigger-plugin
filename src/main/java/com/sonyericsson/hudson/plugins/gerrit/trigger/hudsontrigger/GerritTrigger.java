/*
 *  The MIT License
 *
 *  Copyright (c) 2010, 2014 Sony Mobile Communications Inc. All rights reserved.
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

import com.google.common.collect.Iterators;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.VerdictCategory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.ReplicationConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.dependency.DependencyQueueTaskDispatcher;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.GerritTriggerInformationAction;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.BuildCancellationPolicy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritSlave;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.SkipVote;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginCommentAddedContainsEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginCommentAddedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginDraftPublishedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginGerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginPatchsetCreatedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.version.GerritVersionChecker;
import com.sonymobile.tools.gerrit.gerritevents.GerritHandler;
import com.sonymobile.tools.gerrit.gerritevents.GerritQueryHandler;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Approval;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Provider;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.RefUpdated;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.TopicChanged;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectStreamException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.regex.PatternSyntaxException;

import static com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer.ANY_SERVER;
import static com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl.getServerConfig;
import static com.sonymobile.tools.gerrit.gerritevents.GerritDefaultValues.DEFAULT_BUILD_SCHEDULE_DELAY;

/**
 * Triggers a build based on Gerrit events.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritTrigger extends Trigger<Job> {

    private static final Logger logger = LoggerFactory.getLogger(GerritTrigger.class);

    /**
     * Default 'true'.
     *
     * As a workaround for https://issues.jenkins-ci.org/browse/JENKINS-17116 it is
     * possible to only remove pending jobs from the queue, but not to
     * abort running jobs by setting this to 'false'.
     */
    public static final String JOB_ABORT = GerritTrigger.class.getName() + "_job_abort";

    //! Association between patches and the jobs that we're running for them
    private transient RunningJobs runningJobs = new RunningJobs(this, this.job);
    //! This latch will be used to signal that the project list is ready for use.
    //! For static configuration, this will be ready immediately.
    //! For dynamic configuration, this will be ready after the first time that
    //! the project list has been fetched.
    //!
    //! Default the latch to the non-waiting zero state, which corresponds to
    //! static project configurations.
    private transient CountDownLatch projectListIsReady = new CountDownLatch(0);
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
    private Integer gerritBuildAbortedVerifiedValue;
    private Integer gerritBuildAbortedCodeReviewValue;
    private boolean silentMode;
    private String notificationLevel;
    private boolean silentStartMode;
    private boolean escapeQuotes;
    private BuildCancellationPolicy buildCancellationPolicy;
    private GerritTriggerParameters.ParameterMode nameAndEmailParameterMode;
    private String dependencyJobsNames;
    private GerritTriggerParameters.ParameterMode commitMessageParameterMode;
    private GerritTriggerParameters.ParameterMode changeSubjectParameterMode;
    private GerritTriggerParameters.ParameterMode commentTextParameterMode;
    private String buildStartMessage;
    private String buildFailureMessage;
    private String buildSuccessfulMessage;
    private String buildUnstableMessage;
    private String buildNotBuiltMessage;
    private String buildAbortedMessage;
    private String buildUnsuccessfulFilepath;
    private String customUrl;
    private String serverName;
    private String gerritSlaveId;
    private List<PluginGerritEvent> triggerOnEvents;
    private boolean dynamicTriggerConfiguration;
    private String triggerConfigURL;

    private GerritTriggerTimerTask gerritTriggerTimerTask;
    private GerritTriggerInformationAction triggerInformationAction;

    /**
     * Default DataBound Constructor.
     * @param gerritProjects the set of triggering rules.
     */
    @DataBoundConstructor
    public GerritTrigger(List<GerritProject> gerritProjects) {
        this.gerritProjects = gerritProjects;
        this.gerritTriggerTimerTask = null;
        this.triggerInformationAction = new GerritTriggerInformationAction();
        this.skipVote = new SkipVote(false, false, false, false, false);
        this.escapeQuotes = true;
        this.serverName = ANY_SERVER;
        try {
            GerritTriggerDescriptor descriptor = (GerritTriggerDescriptor)getDescriptor();
            if (descriptor != null) {
                ListBoxModel options = descriptor.doFillNotificationLevelItems(this.serverName);
                if (!options.isEmpty()) {
                    this.notificationLevel = options.get(0).value;
                }
            }
            //CS IGNORE EmptyBlock FOR NEXT 1 LINES. REASON: Handled one row below
        } catch (NullPointerException ignored) { /*Could happen during testing*/ }
        if (this.notificationLevel == null) {
            this.notificationLevel = "";
        }

        this.commitMessageParameterMode = GerritTriggerParameters.ParameterMode.BASE64;
        this.nameAndEmailParameterMode = GerritTriggerParameters.ParameterMode.PLAIN;
        this.changeSubjectParameterMode = GerritTriggerParameters.ParameterMode.PLAIN;
        this.commentTextParameterMode = GerritTriggerParameters.ParameterMode.BASE64;

        this.dependencyJobsNames = "";
        this.buildStartMessage = "";
        this.buildSuccessfulMessage = "";
        this.buildUnstableMessage = "";
        this.buildFailureMessage = "";
        this.buildNotBuiltMessage = "";
        this.buildAbortedMessage = "";
        this.buildUnsuccessfulFilepath = "";
        this.triggerConfigURL = "";
    }

    /**
     * Old DataBound Constructor. Replaced with {@link #GerritTrigger(List)} and {@link DataBoundSetter}s.
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
     * @param silentStartMode                Silent Start Mode on or off.
     * @param escapeQuotes                   EscapeQuotes on or off.
     * @param noNameAndEmailParameters       Whether to create parameters containing name and email
     * @param readableMessage                Human readable message or not.
     * @param dependencyJobsNames            The list of jobs on which this job depends
     * @param buildStartMessage              Message to write to Gerrit when a build begins
     * @param buildSuccessfulMessage         Message to write to Gerrit when a build succeeds
     * @param buildUnstableMessage           Message to write to Gerrit when a build is unstable
     * @param buildFailureMessage            Message to write to Gerrit when a build fails
     * @param buildNotBuiltMessage           Message to write to Gerrit when all builds are not built
     * @param buildUnsuccessfulFilepath      Filename to retrieve Gerrit comment message from, in the case of an
     *                                       unsuccessful build.
     * @param customUrl                      Custom URL to send to Gerrit instead of build URL
     * @param serverName                     The selected server
     * @param gerritSlaveId                  The selected slave associated to this job, if enabled in server configs
     * @param triggerOnEvents                The list of event types to trigger on.
     * @param dynamicTriggerConfiguration    Dynamic trigger configuration on or off
     * @param triggerConfigURL               Where to fetch the configuration file from
     * @param notificationLevel              Whom to notify.
     */
    @Deprecated
    public GerritTrigger(List<GerritProject> gerritProjects, SkipVote skipVote, Integer gerritBuildStartedVerifiedValue,
                         Integer gerritBuildStartedCodeReviewValue, Integer gerritBuildSuccessfulVerifiedValue,
                         Integer gerritBuildSuccessfulCodeReviewValue, Integer gerritBuildFailedVerifiedValue,
                         Integer gerritBuildFailedCodeReviewValue, Integer gerritBuildUnstableVerifiedValue,
                         Integer gerritBuildUnstableCodeReviewValue, Integer gerritBuildNotBuiltVerifiedValue,
            Integer gerritBuildNotBuiltCodeReviewValue, boolean silentMode, boolean silentStartMode,
            boolean escapeQuotes, boolean noNameAndEmailParameters, boolean readableMessage, String dependencyJobsNames,
                         String buildStartMessage, String buildSuccessfulMessage, String buildUnstableMessage,
            String buildFailureMessage, String buildNotBuiltMessage, String buildUnsuccessfulFilepath, String customUrl,
                         String serverName, String gerritSlaveId, List<PluginGerritEvent> triggerOnEvents,
                         boolean dynamicTriggerConfiguration, String triggerConfigURL, String notificationLevel) {
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
        this.silentStartMode = silentStartMode;
        this.escapeQuotes = escapeQuotes;
        if (noNameAndEmailParameters) {
            nameAndEmailParameterMode = GerritTriggerParameters.ParameterMode.NONE;
        } else {
            nameAndEmailParameterMode = GerritTriggerParameters.ParameterMode.PLAIN;
        }
        if (readableMessage) {
            commitMessageParameterMode = GerritTriggerParameters.ParameterMode.PLAIN;
        } else {
            commitMessageParameterMode = GerritTriggerParameters.ParameterMode.BASE64;
        }
        this.changeSubjectParameterMode = GerritTriggerParameters.ParameterMode.PLAIN;
        this.commentTextParameterMode = GerritTriggerParameters.ParameterMode.BASE64;
        this.dependencyJobsNames = dependencyJobsNames;
        this.buildStartMessage = buildStartMessage;
        this.buildSuccessfulMessage = buildSuccessfulMessage;
        this.buildUnstableMessage = buildUnstableMessage;
        this.buildFailureMessage = buildFailureMessage;
        this.buildNotBuiltMessage = buildNotBuiltMessage;
        this.buildUnsuccessfulFilepath = buildUnsuccessfulFilepath;
        this.customUrl = customUrl;
        this.serverName = serverName;
        this.gerritSlaveId = gerritSlaveId;
        this.triggerOnEvents = triggerOnEvents;
        this.dynamicTriggerConfiguration = dynamicTriggerConfiguration;
        this.triggerConfigURL = triggerConfigURL;
        this.gerritTriggerTimerTask = null;
        this.triggerInformationAction = new GerritTriggerInformationAction();
        this.notificationLevel = notificationLevel;
    }

    /**
     * The parameter mode for the compound "name and email" parameters.
     *
     * Replaces {@link #isNoNameAndEmailParameters()}.
     * @return the mode
     * @see GerritTriggerParameters#GERRIT_CHANGE_ABANDONER
     * @see GerritTriggerParameters#GERRIT_CHANGE_OWNER
     * @see GerritTriggerParameters#GERRIT_CHANGE_RESTORER
     * @see GerritTriggerParameters#GERRIT_EVENT_ACCOUNT
     * @see GerritTriggerParameters#GERRIT_SUBMITTER
     */
    @Nonnull
    public GerritTriggerParameters.ParameterMode getNameAndEmailParameterMode() {
        return nameAndEmailParameterMode;
    }

    /**
     * The parameter mode for the compound "name and email" parameters.
     * Replaces {@link #isNoNameAndEmailParameters()}.
     *
     * @param nameAndEmailParameterMode the mode
     * @see GerritTriggerParameters#GERRIT_CHANGE_ABANDONER
     * @see GerritTriggerParameters#GERRIT_CHANGE_OWNER
     * @see GerritTriggerParameters#GERRIT_CHANGE_RESTORER
     * @see GerritTriggerParameters#GERRIT_EVENT_ACCOUNT
     * @see GerritTriggerParameters#GERRIT_SUBMITTER
     */
    @DataBoundSetter
    public void setNameAndEmailParameterMode(@Nonnull GerritTriggerParameters.ParameterMode nameAndEmailParameterMode) {
        this.nameAndEmailParameterMode = nameAndEmailParameterMode;
    }

    /**
     * What mode the commit message parameter {@link GerritTriggerParameters#GERRIT_CHANGE_COMMIT_MESSAGE} should be used
     * when adding it.
     *
     * @return the mode
     */
    @Nonnull
    public GerritTriggerParameters.ParameterMode getCommitMessageParameterMode() {
        return commitMessageParameterMode;
    }

    /**
     * What mode the commit message parameter {@link GerritTriggerParameters#GERRIT_CHANGE_COMMIT_MESSAGE} should be used
     * when adding it.
     * @param commitMessageParameterMode the mode
     */
    @DataBoundSetter
    public void setCommitMessageParameterMode(
            @Nonnull GerritTriggerParameters.ParameterMode commitMessageParameterMode) {
        this.commitMessageParameterMode = commitMessageParameterMode;
    }

    /**
     * What mode the change subject parameter {@link GerritTriggerParameters#GERRIT_CHANGE_SUBJECT} should be used
     * when adding it.
     *
     * @return the mode
     */
    @Nonnull
    public GerritTriggerParameters.ParameterMode getChangeSubjectParameterMode() {
        return changeSubjectParameterMode;
    }

    /**
     * What mode the change subject parameter {@link GerritTriggerParameters#GERRIT_CHANGE_SUBJECT} should be used
     * when adding it.
     *
     * @param changeSubjectParameterMode the mode
     */
    @DataBoundSetter
    public void setChangeSubjectParameterMode(
            @Nonnull GerritTriggerParameters.ParameterMode changeSubjectParameterMode) {
        this.changeSubjectParameterMode = changeSubjectParameterMode;
    }

    /**
     * What mode the comment text parameter {@link GerritTriggerParameters#GERRIT_EVENT_COMMENT_TEXT} should be used
     * when adding it.
     *
     * @return the mode
     */
    @Nonnull
    public GerritTriggerParameters.ParameterMode getCommentTextParameterMode() {
        return commentTextParameterMode;
    }

    /**
     * What mode the comment text parameter {@link GerritTriggerParameters#GERRIT_EVENT_COMMENT_TEXT} should be used
     * when adding it.
     *
     * @param commentTextParameterMode the mode
     */
    @DataBoundSetter
    public void setCommentTextParameterMode(
            @Nonnull GerritTriggerParameters.ParameterMode commentTextParameterMode) {
        this.commentTextParameterMode = commentTextParameterMode;
    }

    /**
     * The skip vote selection.
     * &quot;Skipping&quot; the vote means that if more than one build of this job is triggered by a Gerrit event
     * the outcome of this build won't be counted when the final vote is sent to Gerrit.
     *
     * @param skipVote what votes if any should be skipped in the final
     */
    @DataBoundSetter
    public void setSkipVote(SkipVote skipVote) {
        this.skipVote = skipVote;
    }

    /**
     * Provides package access to the internal {@link #job} reference.
     * @return the job
     */
    Job getJob() {
        return job;
    }

    /**
     * Returns name of server.
     *
     * @return the server name
     *
     */
    public String getServerName() {
        return this.serverName;
    }

    /**
     * Set the selected server.
     *
     * @param name the name of the newly selected server.
     *
     */
    @DataBoundSetter
    public void setServerName(String name) {
        this.serverName = name;
        if (this.notificationLevel == null) {
            ListBoxModel options =
                ((GerritTriggerDescriptor)getDescriptor()).doFillNotificationLevelItems(this.serverName);
            if (!options.isEmpty()) {
                notificationLevel = options.get(0).value;
            }
        }
    }

    /**
     * The selected slave associated to this job, if enabled in server configs.
     *
     * @return the id of the gerrit slave
     * @see GerritSlave
     */
    public String getGerritSlaveId() {
        return gerritSlaveId;
    }

    /**
     * The selected slave associated to this job, if enabled in server configs.
     *
     * @param gerritSlaveId the id of the gerrit slave
     * @see GerritSlave
     */
    @DataBoundSetter
    public void setGerritSlaveId(String gerritSlaveId) {
        this.gerritSlaveId = gerritSlaveId;
    }

    /**
     * Notify trigger for job being renamed
     * @param oldFullName the former {@link Item#getFullName}
     * @param newFullName the current {@link Item#getFullName}
     */
    void onJobRenamed(String oldFullName, String newFullName) {
        PluginImpl plugin = PluginImpl.getInstance();
        if (plugin != null) {
            GerritHandler handler = plugin.getHandler();
            handler.removeListener(new EventListener(oldFullName));
            handler.addListener(createListener());
        }
    }

    /**
     * Finds the GerritTrigger in a project.
     *
     * @param project the project.
     * @return the trigger if there is one, null otherwise.
     */
    public static GerritTrigger getTrigger(@Nullable Job project) {
        if (project == null) {
            return null;
        }

        return ParameterizedJobMixIn.getTrigger(project, GerritTrigger.class);
    }

    /**
     * Cancels the timerTask, if it exists.
     */
    public void cancelTimer() {
        if (gerritTriggerTimerTask != null) {
            String name = "N/A";
            if (job != null) {
                name = job.getName();
            }
            logger.trace("GerritTrigger.cancelTimer(): {}", name);
            gerritTriggerTimerTask.cancel();
            gerritTriggerTimerTask = null;
        }
    }

    /**
    * Adds this trigger as listener to the Gerrit server.
    *
    * @param project the project associated with the trigger.
    */
    private void addThisTriggerAsListener(Job project) {
        PluginImpl plugin = PluginImpl.getInstance();
        if (plugin != null) {
            GerritHandler handler = plugin.getHandler();
            handler.addListener(createListener(project));
        } else {
            logger.warn("The plugin instance could not be found! Project {} will not be triggered!",
                    project.getFullDisplayName());
        }
    }

    /**
     * Creates an {@link EventListener} for the provided project.
     * @param project the project
     * @return a new listener instance
     */
    /*package*/ static EventListener createListener(Job project) {
        return new EventListener(project);
    }

    /**
     * Creates an {@link EventListener} for this trigger's job.
     * @return a new listener instance.
     * @see #createListener(hudson.model.Job)
     */
    /*package*/ EventListener createListener() {
        return createListener(job);
    }

    @Override
    public void start(Job project, boolean newInstance) {
        logger.debug("Start project: {}", project);
        super.start(project, newInstance);
        initializeServerName();
        initializeTriggerOnEvents();
        try {
            addThisTriggerAsListener(project);
        } catch (IllegalStateException e) {
            logger.error("I am too early!", e);
        }

        // Create a new timer task if there is a URL
        if (dynamicTriggerConfiguration) {
            // Set up the latch so that the EventListener thread has to wait for
            // the project list to be ready before processing any events.
            logger.debug("Start project: {}; dynamic project list; setting latch to 1", project);
            projectListIsReady = new CountDownLatch(1);
            gerritTriggerTimerTask = new GerritTriggerTimerTask(this);
        } else {
            logger.debug("Start project: {}; static project list; setting latch to 0", project);
            // Set up the latch so that the EventListener thread doesn't have to
            // wait at all and can immediately begin to process events.
            projectListIsReady = new CountDownLatch(0);
        }

        GerritProjectList.removeTriggerFromProjectList(this);
    }

    @Override
    public void stop() {
        logger.debug("Stop");
        GerritProjectList.removeTriggerFromProjectList(this);
        super.stop();
        try {
            removeListener();
        } catch (IllegalStateException e) {
            logger.error("I am too late!", e);
        }

        cancelTimer();
    }

    /**
     * Removes listener from the server.
     */
    private void removeListener() {
        GerritHandler handler = PluginImpl.getHandler_();
        if (handler != null) {
            if (job != null) {
                handler.removeListener(createListener());
            }
        } else {
            logger.error("The Gerrit handler has not been initialized. BUG!");
        }
    }

    /**
     * Initializes the event's provider and pass it the server name info if necessary.
     *
     * @param tEvent the event.
     * @return the initialized provider.
     */
    private Provider initializeProvider(GerritTriggeredEvent tEvent) {
        Provider provider = tEvent.getProvider();
        if (!isAnyServer()) {
            if (provider == null) {
                provider = new Provider();
                provider.setName(serverName);
            } else if (provider.getName() == null) {
                provider.setName(serverName);
            }
        }
        return provider;
    }

    /**
     * If {@link GerritServer#ANY_SERVER} is selected as {@link #serverName}.
     * Or if serverName is null or empty.
     *
     * @return true if so.
     * @see GerritServer#isAnyServer(String)
     */
    boolean isAnyServer() {
        return GerritServer.isAnyServer(serverName);
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
            if (e.shouldTriggerOn(event)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Schedules a build with parameters from the event. With {@link #job} as the project to build.
     *
     * @param cause the cause of the build.
     * @param event the event.
     * @deprecated
     *    moved to {@link EventListener#schedule(GerritTrigger, GerritCause, GerritTriggeredEvent)}
     */
    @Deprecated
    protected void schedule(GerritCause cause, GerritTriggeredEvent event) {
        createListener().schedule(this, cause, event, job);
    }

    /**
     * Schedules a build with parameters from the event.
     *
     * @param cause   the cause of the build.
     * @param event   the event.
     * @param project the project to build.
     * @deprecated
     *    moved to {@link EventListener#schedule(GerritTrigger, GerritCause, GerritTriggeredEvent, Job)}
     */
    @Deprecated
    protected void schedule(GerritCause cause, GerritTriggeredEvent event, Job project) {
        createListener().schedule(this, cause, event, project);
    }

    /**
     * Creates a ParameterAction and fills it with the project's default parameters + the Standard Gerrit parameters.
     *
     * @param event   the event.
     * @param project the project.
     * @return the ParameterAction.
     * @deprecated
     *     moved to {@link EventListener#createParameters(GerritTriggeredEvent, Job)}
     */
    @Deprecated
    protected ParametersAction createParameters(GerritTriggeredEvent event, Job project) {
        return createListener().createParameters(event, project);
    }


    /**
     * Get the list of verdict categories available on the selected GerritServer.
     *
     * @return the list of verdict categories, or an empty linkedlist if server not found.
     */
    private List<VerdictCategory> getVerdictCategoriesList() {
        GerritServer server = PluginImpl.getServer_(serverName);
        if (server != null) {
            return server.getConfig().getCategories();
         } else {
            logger.error("Could not find server {}", serverName);
            return new LinkedList<VerdictCategory>();
         }
    }

    /**
     * Fills the verdict category drop-down list for the comment-added events.
     * @return a ListBoxModel for the drop-down list.
     */
    public ListBoxModel doFillVerdictCategoryItems() {
        ListBoxModel m = new ListBoxModel();
        List<VerdictCategory> list = getVerdictCategoriesList();
        for (VerdictCategory v : list) {
            m.add(v.getVerdictDescription(), v.getVerdictValue());
        }
        return m;
    }

    /**
     * Gives you {@link #runningJobs}. It makes sure that the reference is not null.
     *
     * @param job - job that calling event trigger is attached to
     * @return the store of running jobs.
     */
    /*package*/ synchronized RunningJobs getRunningJobs(Job job) {
        if (runningJobs == null) {
            runningJobs = new RunningJobs(this, this.job);
        } else {
            runningJobs.setJob(job);
        }
        return runningJobs;
    }

    /**
     * Used to inform the server that the builds for a job have ended. This allows us to clean up our list of what jobs
     * we're running.
     *
     * @param event the event.
     */
    public void notifyBuildEnded(GerritTriggeredEvent event) {
        if (event instanceof ChangeBasedEvent) {
            IGerritHudsonTriggerConfig serverConfig = getServerConfig(event);
            if ((serverConfig != null && serverConfig.isGerritBuildCurrentPatchesOnly())
                    || (this.getBuildCancellationPolicy() != null && this.getBuildCancellationPolicy().isEnabled())) {
                getRunningJobs(this.job).remove((ChangeBasedEvent)event);
            }
        }
    }

    /**
     * getBuildScheduleDelay method will return configured buildScheduledelay value. If the value is missing or invalid
     * it the method will return default schedule delay or
     * {@link com.sonymobile.tools.gerrit.gerritevents.GerritDefaultValues#DEFAULT_BUILD_SCHEDULE_DELAY}.
     *
     * @return buildScheduleDelay.
     */
    public int getBuildScheduleDelay() {
        if (isAnyServer()) {
            int max = 0;
            for (GerritServer server : PluginImpl.getServers_()) {
                if (server.getConfig() != null) {
                    max = Math.max(max, server.getConfig().getBuildScheduleDelay());
                }
            }
            return max;
        } else {
            GerritServer server = PluginImpl.getServer_(serverName);
            if (server == null || server.getConfig() == null) {
                return DEFAULT_BUILD_SCHEDULE_DELAY;
            } else {
                int buildScheduleDelay = server.getConfig().getBuildScheduleDelay();
                //check if less than zero
                return Math.max(0, buildScheduleDelay);
            }
        }

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
        if (context.getThisBuild().getProject().isBuildable()) {
            ToGerritRunListener listener = ToGerritRunListener.getInstance();
            if (listener != null) {
                if (!listener.isBuilding(context.getThisBuild().getProject(),
                        context.getEvent())) {

                    Provider provider = initializeProvider(context.getEvent());

                    // If serverName in event no longer exists, server may have been renamed/removed,
                    // so use current serverName
                    if (!isAnyServer() && !PluginImpl.containsServer_(provider.getName())) {
                        provider.setName(serverName);
                    }

                    if (!silentMode) {
                        listener.onRetriggered(
                                context.getThisBuild().getProject(),
                                context.getEvent(),
                                context.getOtherBuilds());
                    }
                    final GerritUserCause cause = new GerritUserCause(context.getEvent(), silentMode);
                    createListener().schedule(this, cause, context.getEvent(), context.getThisBuild().getProject());
                }
            }
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
        DependencyQueueTaskDispatcher dependencyQueueTaskDispatcher = DependencyQueueTaskDispatcher.getInstance();
        if (dependencyQueueTaskDispatcher != null) {
            ToGerritRunListener listener = ToGerritRunListener.getInstance();
            if (listener != null) {
                if (!listener.isBuilding(context.getEvent())) {
                    dependencyQueueTaskDispatcher.onTriggeringAll(context.getEvent());
                    retrigger(context.getThisBuild().getProject(), context.getEvent());
                    for (Run build : context.getOtherBuilds()) {
                        GerritTrigger trigger = getTrigger(build.getParent());
                        if (trigger != null) {
                            trigger.retrigger(build.getParent(), context.getEvent());
                        }
                    }
                    dependencyQueueTaskDispatcher.onDoneTriggeringAll(context.getEvent());
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
    private void retrigger(Job project, GerritTriggeredEvent event) {
        if (project.isBuildable()) {
            initializeProvider(event);
            if (!silentMode) {
                ToGerritRunListener listener = ToGerritRunListener.getInstance();
                if (listener != null) {
                    listener.onRetriggered(project, event, null);
                }
            }
            GerritUserCause cause = new GerritUserCause(event, silentMode);
            schedule(cause, event, project);
        }
    }

    @Override
    public int hashCode() {
        if (job == null) {
            return super.hashCode();
        } else {
            return job.getFullName().hashCode();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GerritTrigger) {
            GerritTrigger that = (GerritTrigger)obj;
            if (job == null || that.job == null) {
                return super.equals(obj);
            } else {
                return job.getFullName().equals(that.job.getFullName());
            }
        }
        return false;
    }

    /**
     * Should we trigger on this event?
     *
     * @param event the event
     * @return true if we should.
     */
    public boolean isInteresting(GerritTriggeredEvent event) {
        if (job == null) {
            logger.trace("Job is not fully initialised.");
            return false;
        }

        if (!job.isBuildable()) {
            logger.trace("Disabled.");
            return false;
        }

        ToGerritRunListener listener = ToGerritRunListener.getInstance();
        if (listener != null) {
            if (listener.isProjectTriggeredAndIncomplete(job, event)) {
                logger.trace("Already triggered and incomplete.");
                return false;
            } else if (listener.isTriggered(job, event)) {
                logger.trace("Already triggered.");
                return false;
            }
        }

        if (!shouldTriggerOnEventType(event)) {
            return false;
        }

        logger.trace("entering isInteresting for the event: {}", event);

        Iterator<GerritProject> allGerritProjects = getAllGerritProjectsIterator();
        while (allGerritProjects.hasNext()) {
            GerritProject p = allGerritProjects.next();
            try {
                if (event instanceof ChangeBasedEvent) {
                    ChangeBasedEvent changeBasedEvent = (ChangeBasedEvent)event;
                    if (isServerInteresting(event)
                         && p.isInteresting(changeBasedEvent.getChange().getProject(),
                                            changeBasedEvent.getChange().getBranch(),
                                            changeBasedEvent.getChange().getTopic())) {

                        boolean containsFilePathsOrForbiddenFilePaths =
                                ((p.getFilePaths() != null && p.getFilePaths().size() > 0)
                                        || (p.getForbiddenFilePaths() != null && p.getForbiddenFilePaths().size() > 0));

                        if (isFileTriggerEnabled() && containsFilePathsOrForbiddenFilePaths) {
                            if (isServerInteresting(event)
                                 && p.isInteresting(changeBasedEvent.getChange().getProject(),
                                                    changeBasedEvent.getChange().getBranch(),
                                                    changeBasedEvent.getChange().getTopic(),
                                                    changeBasedEvent.getFiles(
                                                        new GerritQueryHandler(getServerConfig(event))))) {
                                logger.trace("According to {} the event is interesting; event: {}", p, event);
                                return true;
                            }
                        } else {
                            logger.trace("According to {} the event is interesting; event: {}", p, event);
                            return true;
                        }
                    }
                } else if (event instanceof RefUpdated) {
                    RefUpdated refUpdated = (RefUpdated)event;
                    if (isServerInteresting(event) && p.isInteresting(refUpdated.getRefUpdate().getProject(),
                                                                      refUpdated.getRefUpdate().getRefName(), null)) {
                        logger.trace("According to {} the event is interesting; event: {}", p, event);
                        return true;
                    }
                }
            } catch (PatternSyntaxException pse) {
                logger.error(MessageFormat.format("Exception caught for project {0} and pattern {1}, message: {2}",
                       new Object[]{job.getName(), p.getPattern(), pse.getMessage()}));
            }
        }
        logger.trace("Event is not interesting; event: {}", event);
        return false;
    }

    /**
     * Check whether the event provider contains the same server name as the serverName field.
     *
     * @param event the event
     * @return true if same server name
     */
    private boolean isServerInteresting(GerritTriggeredEvent event) {
        if (isAnyServer()) {
            return true;
        }
        Provider provider = initializeProvider(event);
        return provider.getName().equals(serverName);
    }

    /**
     * Checks if the approvals associated with this comment-added event match what
     * this trigger is configured to look for.
     *
     * @param event the event.
     * @return true if the event matches the approval category and value configured.
     */
    /*package*/ boolean commentAddedMatch(CommentAdded event) {
        PluginCommentAddedEvent commentAdded = null;
        for (PluginGerritEvent e : triggerOnEvents) {
            if (e instanceof PluginCommentAddedEvent) {
                commentAdded = (PluginCommentAddedEvent)e;
                for (Approval approval : event.getApprovals()) {
                    /* Ensure that this trigger is backwards compatible.
                     * Gerrit stream events changed to append approval info to
                     * every comment-added event. We need to exclude snapshot
                     * versions from this check. Otherwise, Gerrit snapshot
                     * versions that are < 2.13 will handle comment added event
                     * the way they are supposed to be for Gerrit >= 2.13.
                     */
                    if (GerritVersionChecker.isCorrectVersion(
                                GerritVersionChecker.Feature.commentAlwaysApproval,
                                serverName, true)) {
                        if (approval.isUpdated()
                                && approval.getType().equals(
                                    commentAdded.getVerdictCategory())
                                && (approval.getValue().equals(
                                    commentAdded.getCommentAddedTriggerApprovalValue())
                                || ("+" + approval.getValue()).equals(
                                    commentAdded.getCommentAddedTriggerApprovalValue()))) {
                                return true;
                        }
                    } else {
                        if (approval.getType().equals(
                                commentAdded.getVerdictCategory())
                            && (approval.getValue().equals(
                                commentAdded.getCommentAddedTriggerApprovalValue())
                            || ("+" + approval.getValue()).equals(
                                commentAdded.getCommentAddedTriggerApprovalValue()))) {
                            return true;
                        }
                    }
                }
            }
            if (e instanceof PluginCommentAddedContainsEvent) {
                if (((PluginCommentAddedContainsEvent)e).match(event)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The list of GerritProject triggering rules.
     *
     * @return the rule-set.
     */
    public List<GerritProject> getGerritProjects() {
        if (gerritProjects == null) {
            return null;
        }
        return Collections.unmodifiableList(gerritProjects);
    }

    /**
     * The list of dynamically configured triggering rules.
     *
     *  @return the rule-set.
     */
    public List<GerritProject> getDynamicGerritProjects() {
        if (dynamicGerritProjects == null) {
            return null;
        }
        return Collections.unmodifiableList(dynamicGerritProjects);
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
    @DataBoundSetter
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
    @DataBoundSetter
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
    @DataBoundSetter
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
    @DataBoundSetter
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
    @DataBoundSetter
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
    @DataBoundSetter
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
    @DataBoundSetter
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
    @DataBoundSetter
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
    @DataBoundSetter
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
    @DataBoundSetter
    public void setGerritBuildNotBuiltVerifiedValue(Integer gerritBuildNotBuiltVerifiedValue) {
        this.gerritBuildNotBuiltVerifiedValue = gerritBuildNotBuiltVerifiedValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is aborted, null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildAbortedCodeReviewValue() {
        return gerritBuildAbortedCodeReviewValue;
    }

    /**
     * Job specific Gerrit code review vote when a build is aborted, providing null means that the global value should be
     * used.
     *
     * @param gerritBuildAbortedCodeReviewValue the vote value.
     */
    @DataBoundSetter
    public void setGerritBuildAbortedCodeReviewValue(Integer gerritBuildAbortedCodeReviewValue) {
        this.gerritBuildAbortedCodeReviewValue = gerritBuildAbortedCodeReviewValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is aborted, null means that the global value should be used.
     *
     * @return the vote value.
     */
    public Integer getGerritBuildAbortedVerifiedValue() {
        return gerritBuildAbortedVerifiedValue;
    }

    /**
     * Job specific Gerrit verified vote when a build is aborted, providing null means that the global value should be
     * used.
     *
     * @param gerritBuildAbortedVerifiedValue the vote value.
     */
    @DataBoundSetter
    public void setGerritBuildAbortedVerifiedValue(Integer gerritBuildAbortedVerifiedValue) {
        this.gerritBuildAbortedVerifiedValue = gerritBuildAbortedVerifiedValue;
    }

    /**
     * Sets the path to a file that contains the unsuccessful Gerrit comment message.
     * Filename to retrieve Gerrit comment message from, in the case of an unsuccessful build.
     *
     * @param buildUnsuccessfulFilepath The unsuccessful message comment file path
     */
    @DataBoundSetter
    public void setBuildUnsuccessfulFilepath(String buildUnsuccessfulFilepath) {
        this.buildUnsuccessfulFilepath = buildUnsuccessfulFilepath;
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
     * The list of event types to trigger on.
     *
     * @param triggerOnEvents the list
     */
    @DataBoundSetter
    public void setTriggerOnEvents(List<PluginGerritEvent> triggerOnEvents) {
        this.triggerOnEvents = triggerOnEvents;
    }

    /**
     * Returns an iterator over the all gerrit projects configured for the trigger.
     *
     * @return an iterator over the all gerrit projects configured for the trigger.
     */
    private Iterator<GerritProject> getAllGerritProjectsIterator() {
        if (gerritProjects != null && dynamicGerritProjects != null) {
            return Iterators.concat(gerritProjects.iterator(), dynamicGerritProjects.iterator());
        }

        if (gerritProjects == null && dynamicGerritProjects != null) {
            return dynamicGerritProjects.iterator();
        }

        if (gerritProjects != null) {
            return gerritProjects.iterator();
        }

        return Iterators.emptyIterator();
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
     * Initializes serverName if the field cannot be resolved from the config.xml file.
     */
    private void initializeServerName() {
        if (serverName == null) {
            serverName = ANY_SERVER;
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
    @DataBoundSetter
    public void setDynamicTriggerConfiguration(boolean dynamicTriggerConfiguration) {
        if (!dynamicTriggerConfiguration) {
            dynamicGerritProjects = Collections.emptyList();
        }

        this.dynamicTriggerConfiguration = dynamicTriggerConfiguration;
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
     * @see #dynamicTriggerConfiguration
     * @see #dynamicGerritProjects
     */
    @DataBoundSetter
    public void setTriggerConfigURL(String triggerConfigURL) {
        this.triggerConfigURL = triggerConfigURL;
    }

    /**
     * The list of dependency jobs, ie jobs on which this job depends.
     *
     * @return the string of jobs, or null if this feature is not used.
     */
    public String getDependencyJobsNames() {
        return dependencyJobsNames;
    }

    /**
     * The list of jobs on which this job depends.
     *
     * @param dependencyJobsNames
     *         the string containing a comma-separated list of job names.
     */
    @DataBoundSetter
    public void setDependencyJobsNames(String dependencyJobsNames) {
        this.dependencyJobsNames = dependencyJobsNames;
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
     * If silent start mode is on or off. When silent start mode is on there will be no 'build started' message back
     * to Gerrit. Default is false.
     *
     * @return true if silent start mode is on.
     */
    public boolean isSilentStartMode() {
        return silentStartMode;
    }

    /**
     * Whom to notify.
     *
     * @return the notification level value
     */
    public String getNotificationLevel() {
        return notificationLevel;
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
    @DataBoundSetter
    public void setEscapeQuotes(boolean escapeQuotes) {
        this.escapeQuotes = escapeQuotes;
    }

    /**
     * If noNameAndEmailParameters is on or off. When this is set on this plugin will not create parameters
     * which combine a name with an email (this applies change owner, restorer, etc). These parameters cause
     * problems with some configurations.
     *
     * @return true if noNameAndEmailParameters is on.
     * @deprecated replaced with {@link #getNameAndEmailParameterMode()}
     */
    @Deprecated
    public boolean isNoNameAndEmailParameters() {
        return nameAndEmailParameterMode == GerritTriggerParameters.ParameterMode.NONE;
    }

    /**
     * Sets noNameAndEmailParameters to on or off.  When this is set on this plugin will not create parameters
     * which combine a name with an email (this applies change owner, restorer, etc). These parameters cause
     * problems with some configurations.
     *
     * @param noNameAndEmailParameters is true if problematic parameters should be omitted.
     * @deprecated replaced with {@link #setNameAndEmailParameterMode(GerritTriggerParameters.ParameterMode)}
     */
    @Deprecated
    public void setNoNameAndEmailParameters(boolean noNameAndEmailParameters) {
        if (noNameAndEmailParameters) {
            this.nameAndEmailParameterMode = GerritTriggerParameters.ParameterMode.NONE;
        } else {
            this.nameAndEmailParameterMode = GerritTriggerParameters.ParameterMode.PLAIN;
        }
    }

    /**
     * If readableMessage is on or off. When this is set on this plugin will create parameters
     * for multiline text, e.g. commit message, as human readable message. When this is set off,
     * it will be encoded.
     *
     * @return true if readableMessage is on.
     * @deprecated replaced with {@link #getCommitMessageParameterMode()}
     */
    @Deprecated
    public boolean isReadableMessage() {
        return this.commitMessageParameterMode == GerritTriggerParameters.ParameterMode.PLAIN;
    }

    /**
     * Sets readableMessage to on or off. When this is set on this plugin will create parameters
     * for multiline text, e.g. commit message, as human readable message. when this is set off,
     * it will be encoded.
     *
     * @param readableMessage is true if human readable message is set.
     * @deprecated replaced with {@link #setCommitMessageParameterMode(GerritTriggerParameters.ParameterMode)}.
     */
    @Deprecated
    public void setReadableMessage(boolean readableMessage) {
        if (readableMessage) {
            this.commitMessageParameterMode = GerritTriggerParameters.ParameterMode.PLAIN;
        } else {
            this.commitMessageParameterMode = GerritTriggerParameters.ParameterMode.BASE64;
        }
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
     * Message to write to Gerrit when a build begins.
     *
     * @param buildStartMessage The build start message
     */
    @DataBoundSetter
    public void setBuildStartMessage(String buildStartMessage) {
        this.buildStartMessage = buildStartMessage;
    }

    /**
     * @return the buildCurrentPatchesOnly
     */
    public BuildCancellationPolicy getBuildCancellationPolicy() {
        return buildCancellationPolicy;
    }

    /**
     * The build cancellation policy regarding building current patch sets only.
     * @return the policy
     */
    public boolean isBuildCurrentPatchesOnly() {
        return buildCancellationPolicy != null && buildCancellationPolicy.isEnabled();
    }

    /**
     * The build cancellation policy regarding building current patch sets only.
     *
     * @param buildCancellationPolicy the policy
     */
    @DataBoundSetter
    public void setBuildCancellationPolicy(final BuildCancellationPolicy buildCancellationPolicy) {
        this.buildCancellationPolicy = buildCancellationPolicy;
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
     * Message to write to Gerrit when a build succeeds.
     *
     * @param buildSuccessfulMessage The build successful message
     */
    @DataBoundSetter
    public void setBuildSuccessfulMessage(String buildSuccessfulMessage) {
        this.buildSuccessfulMessage = buildSuccessfulMessage;
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
     * Message to write to Gerrit when a build is unstable.
     * @param buildUnstableMessage The build unstable message
     */
    @DataBoundSetter
    public void setBuildUnstableMessage(String buildUnstableMessage) {
        this.buildUnstableMessage = buildUnstableMessage;
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
     * Message to write to Gerrit when a build fails.
     *
     * @param buildFailureMessage The build failure message
     */
    @DataBoundSetter
    public void setBuildFailureMessage(String buildFailureMessage) {
        this.buildFailureMessage = buildFailureMessage;
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
     * Message to write to Gerrit when all builds are not built.
     *
     * @param buildNotBuiltMessage The build not built message
     */
    @DataBoundSetter
    public void setBuildNotBuiltMessage(String buildNotBuiltMessage) {
        this.buildNotBuiltMessage = buildNotBuiltMessage;
    }

    /**
     * The message to show users when a build is aborted, if custom messages are enabled.
     *
     * @return The build aborted message
     */
    public String getBuildAbortedMessage() {
        return buildAbortedMessage;
    }

    /**
     * Message to write to Gerrit when a build is aborted.
     *
     * @param buildAbortedMessage The build aborted message
     */
    @DataBoundSetter
    public void setBuildAbortedMessage(String buildAbortedMessage) {
        this.buildAbortedMessage = buildAbortedMessage;
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
    @DataBoundSetter
    public void setSilentMode(boolean silentMode) {
        this.silentMode = silentMode;
    }

    /**
     * Sets silent start mode to on or off. When silent start mode is on there will be no 'silent start' message
     * back to Gerrit. Default is false.
     *
     * @param silentStartMode true if silent start mode should be on.
     */
    @DataBoundSetter
    public void setSilentStartMode(boolean silentStartMode) {
        this.silentStartMode = silentStartMode;
    }

    /**
     * Whom to notify.
     *
     * @param notificationLevel the notification level.
     */
    @DataBoundSetter
    public void setNotificationLevel(String notificationLevel) {
        this.notificationLevel = notificationLevel;
    }

    /**
     * URL to send in comment to Gerrit.
     *
     * @return custom URL to post back to Gerrit
     */
    public String getCustomUrl() {
        return customUrl;
    }

    /**
     * Custom URL to send to Gerrit instead of build URL.
     *
     * @param customUrl URL to set
     */
    @DataBoundSetter
    public void setCustomUrl(String customUrl) {
        this.customUrl = customUrl;
    }

    /**
     * Convenience method for finding it out if file triggering is enabled in the Gerrit version.
     * @return true if file triggering is enabled in the Gerrit version.
     */
    public boolean isFileTriggerEnabled() {
        return GerritVersionChecker.isCorrectVersion(GerritVersionChecker.Feature.fileTrigger, serverName);
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
            // Check if dynamic trigger was disabled in the meantime
            if (!dynamicTriggerConfiguration || job == null || !job.isBuildable()) {
                dynamicGerritProjects = Collections.emptyList();
            } else {
                dynamicGerritProjects = DynamicConfigurationCacheProxy.getInstance().fetchThroughCache(triggerConfigURL);
            }
        } catch (ParseException pe) {
            String logErrorMessage = MessageFormat.format(
                    "ParseException for project: {0} and URL: {1} Message: {2}",
                    job.getName(), triggerConfigURL, pe.getMessage());
            logger.error(logErrorMessage, pe);
            String triggerInformationMessage = MessageFormat.format(
                    "ParseException when fetching dynamic trigger url: {0}", pe.getMessage());
            triggerInformationAction.setErrorMessage(triggerInformationMessage);
        } catch (MalformedURLException mue) {
            String logErrorMessage = MessageFormat.format(
                    "MalformedURLException for project: {0} and URL: {1} Message: {2}",
                    job.getName(), triggerConfigURL, mue.getMessage());
            logger.error(logErrorMessage, mue);
            String triggerInformationMessage = MessageFormat.format(
                    "MalformedURLException when fetching dynamic trigger url: {0}", mue.getMessage());
            triggerInformationAction.setErrorMessage(triggerInformationMessage);
        } catch (SocketTimeoutException ste) {
            String logErrorMessage = MessageFormat.format(
                    "SocketTimeoutException for project: {0} and URL: {1} Message: {2}",
                    job.getName(), triggerConfigURL, ste.getMessage());
            logger.error(logErrorMessage, ste);
            String triggerInformationMessage = MessageFormat.format(
                    "SocketTimeoutException when fetching dynamic trigger url: {0}", ste.getMessage());
            triggerInformationAction.setErrorMessage(triggerInformationMessage);

        } catch (IOException ioe) {
            String logErrorMessage = MessageFormat.format(
                    "IOException for project: {0} and URL: {1} Message: {2}",
                    job.getName(), triggerConfigURL, ioe.getMessage());
            logger.error(logErrorMessage, ioe);
            String triggerInformationMessage = MessageFormat.format(
                    "IOException when fetching dynamic trigger url: {0}", ioe.getMessage());
            triggerInformationAction.setErrorMessage(triggerInformationMessage);
        } finally {
            // Now that the dynamic project list has been loaded, we can "count down"
            // the latch so that the EventListener thread can begin to process events.
            if (projectListIsReady.getCount() > 0) {
                logger.debug("Trigger config URL updated: {}; latch is currently {}; decrementing it.", job.getName(),
                        projectListIsReady.getCount());
            }
            // Always release all locks otherwise workers will be stuck forever
            projectListIsReady.countDown();
        }
    }

    /**
     * Convenience method for finding it out if triggering on draft published is enabled in the Gerrit version.
     * @return true if triggering on draft published is enabled in the Gerrit version.
     */
    public boolean isTriggerOnDraftPublishedEnabled() {
        return GerritVersionChecker
                .isCorrectVersion(GerritVersionChecker.Feature.triggerOnDraftPublished, serverName);
    }

    /**
     * Convenience method to get the list of GerritSlave to which replication
     * should be done before letting the build execute.
     * @param gerritServerName The Gerrit server name
     * @return list of GerritSlave (can be empty but never null)
     */
    public List<GerritSlave> gerritSlavesToWaitFor(String gerritServerName) {
        List<GerritSlave> gerritSlaves = new ArrayList<GerritSlave>();

        GerritServer gerritServer = PluginImpl.getServer_(gerritServerName);
        if (gerritServer == null) {
            logger.warn("Could not find server: {}", serverName);
            return gerritSlaves;
        }

        ReplicationConfig replicationConfig = gerritServer.getConfig().getReplicationConfig();
        if (replicationConfig != null && replicationConfig.isEnableReplication()) {
            if (replicationConfig.isEnableSlaveSelectionInJobs()) {
                GerritSlave gerritSlave = replicationConfig.getGerritSlave(gerritSlaveId, true);
                if (gerritSlave != null) {
                    gerritSlaves.add(gerritSlave);
                }
            } else {
                List<GerritSlave> globalSlaves = replicationConfig.getGerritSlaves();
                if (globalSlaves != null) {
                    gerritSlaves.addAll(globalSlaves);
                }
            }
        }
        return gerritSlaves;
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
     * Wait for the project list to be ready.
     *
     * This is here so that the EventListener can call it before it asks if an
     * event is interesting (via {@link #isInteresting(GerritTriggeredEvent)}).
     *
     * @throws InterruptedException if the thread was interrupted while waiting.
     */
    public void waitForProjectListToBeReady() throws InterruptedException {
        projectListIsReady.await();
    }

    /*
     * DEPRECATION HANDLING
     */

    /**
     * Replaced with {@link #nameAndEmailParameterMode}
     */
    @Deprecated
    private transient boolean noNameAndEmailParameters;
    /**
     * Replaced with {@link #commitMessageParameterMode}
     */
    @Deprecated
    private transient boolean readableMessage;
    @SuppressWarnings("unused")
    @Deprecated
    private transient boolean allowTriggeringUnreviewedPatches;

    /**
     * Converts old trigger configs when only patchset created was available as event
     * and when jobs were not associated to Gerrit servers.
     *
     * @return the resolved instance.
     * @throws ObjectStreamException if something beneath goes wrong.
     */
    @Override
    public Object readResolve() throws ObjectStreamException {
        initializeServerName();
        initializeTriggerOnEvents();
        if (commitMessageParameterMode == null) {
            if (readableMessage) {
                commitMessageParameterMode = GerritTriggerParameters.ParameterMode.PLAIN;
            } else {
                commitMessageParameterMode = GerritTriggerParameters.ParameterMode.BASE64;
            }
        }
        if (nameAndEmailParameterMode == null) {
            if (noNameAndEmailParameters) {
                nameAndEmailParameterMode = GerritTriggerParameters.ParameterMode.NONE;
            } else {
                nameAndEmailParameterMode = GerritTriggerParameters.ParameterMode.PLAIN;
            }
        }
        if (changeSubjectParameterMode == null) {
            changeSubjectParameterMode = GerritTriggerParameters.ParameterMode.PLAIN;
        }
        if (commentTextParameterMode == null) {
            commentTextParameterMode = GerritTriggerParameters.ParameterMode.PLAIN;
        }
        if (projectListIsReady == null) {
            projectListIsReady = new CountDownLatch(0);
        }
        return super.readResolve();
    }
    /*
     * /DEPRECATION HANDLING
     */

    @Override
    public TriggerDescriptor getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(GerritTriggerDescriptor.class);
    }


    /**
     * Checks that execution must be aborted because of topic.
     * @param event the event.
     * @param policy the cancellation policy.
     * @param runningChange the ongoing change.
     * @return true if so.
     */
    protected boolean abortBecauseOfTopic(ChangeBasedEvent event,
                                        BuildCancellationPolicy policy,
                                        ChangeBasedEvent runningChange) {
        String topicName = event.getChange().getTopic();

        if (event instanceof TopicChanged) {
            topicName = ((TopicChanged)event).getOldTopic();
        }

        return policy.isAbortSameTopic()
                && topicName != null
                && !topicName.isEmpty()
                && topicName.equals(runningChange.getChange().getTopic());
    }
}
