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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier;

import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.PluginConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.lifecycle.GerritEventLifecycle;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * The Big RunListener in charge of coordinating build results and reporting back to Gerrit.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@Extension(ordinal = ToGerritRunListener.ORDINAL)
public class ToGerritRunListener extends RunListener<AbstractBuild> {

    /**
     * The ordering of this extension.
     */
    public static final int ORDINAL = 10003;
    private static final Logger logger = LoggerFactory.getLogger(ToGerritRunListener.class);
    private static ToGerritRunListener instance;
    private transient BuildMemory memory;

    /**
     * Default Constructor.
     */
    public ToGerritRunListener() {
        super(AbstractBuild.class);
        memory = new BuildMemory();
    }

    /**
     * Returns the registered instance of this class from the list of all listeners.
     *
     * @return the instance.
     */
    public static ToGerritRunListener getInstance() {
        if (instance == null) {
            for (RunListener listener : all()) {
                if (listener instanceof ToGerritRunListener) {
                    instance = (ToGerritRunListener)listener;
                    break;
                }
            }
        }
        return instance;
    }

    @Override
    public synchronized void onCompleted(AbstractBuild r, TaskListener listener) {
        GerritCause cause = getCause(r);
        logger.debug("Completed. Build: {} Cause: {}", r, cause);
        if (cause != null) {
            GerritTrigger trigger = GerritTrigger.getTrigger(r.getProject());
            cleanUpGerritCauses(cause, r);
            GerritTriggeredEvent event = cause.getEvent();
            if (trigger != null) {
                // There won't be a trigger if this job was run through a unit test
                GerritTrigger.getTrigger(r.getProject()).notifyBuildEnded(event);
            }
            if (event instanceof GerritEventLifecycle) {
                ((GerritEventLifecycle)event).fireBuildCompleted(r);
            }
            if (!cause.isSilentMode()) {
                memory.completed(event, r);

                if (r.getResult().isWorseThan(Result.SUCCESS)) {
                    try {
                        // Attempt to record the failure message, if applicable
                        String failureMessage = this.obtainFailureMessage(event, r, listener);
                        logger.info("Obtained failure message: {}", failureMessage);
                        memory.setEntryFailureMessage(event, r, failureMessage);
                    } catch (IOException e) {
                        listener.error("[gerrit-trigger] Unable to read failure message from the workspace.");
                        logger.warn("IOException while obtaining failure message for build: "
                                + r.getDisplayName(), e);
                    } catch (InterruptedException e) {
                        listener.error("[gerrit-trigger] Unable to read failure message from the workspace.");
                        logger.warn("InterruptedException while obtaining failure message for build: "
                                + r.getDisplayName(), e);
                    }
                }

                updateTriggerContexts(r);
                boolean delayedApprovalFeatureEnabledFlag = true;
                PluginConfig config = PluginImpl.getInstance().getPluginConfig();
                if (config != null) {
                    // There won't be a config if this job was run through a unit test
                    delayedApprovalFeatureEnabledFlag = config.getDelayedApprovalFeatureEnabledFlag();
                }
                if (hasDelayedApproval(trigger) && delayedApprovalFeatureEnabledFlag) {
                    logger.info("Delayed approval set. Waiting for delayed approval for cause [{}]. Status: \n{}",
                            cause, memory.getStatusReport(event));
                } else {
                    allBuildsCompleted(event, cause, listener);
                }
            }
        }
    }

    /**
     * Whether the trigger has a delayed approval.
     *
     * @param trigger the trigger to look in.
     * @return True only if the trigger is non null and has a delayed approval.
     */
    private boolean hasDelayedApproval(GerritTrigger trigger) {
        if (trigger != null) {
            if (trigger.isDelayedApproval()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Manages the end of a Gerrit Event. Should be called after each build related to an event completes if that build
     * should report back to Gerrit.
     *
     * @param event   the Gerrit Event which may need to be completed.
     * @param cause   the Gerrit Cause which triggered the build initially.
     * @param listener   the Jenkins listener.
     */
    public synchronized void allBuildsCompleted(GerritTriggeredEvent event, GerritCause cause, TaskListener listener) {
        if (memory.isAllBuildsCompleted(event)) {
            try {
                logger.info("All Builds are completed for cause: {}", cause);
                if (event instanceof GerritEventLifecycle) {
                    ((GerritEventLifecycle)event).fireAllBuildsCompleted();
                }
                NotificationFactory.getInstance().queueBuildCompleted(memory.getMemoryImprint(event), listener);
            } finally {
                memory.forget(event);
            }
        } else {
            logger.info("Waiting for more builds to complete for cause [{}]. Status: \n{}",
                    cause, memory.getStatusReport(event));
        }
    }

    /**
     * Checks whether a project has triggered for an event but hasn't yet finished building.
     *
     * @param event   the Gerrit Event which is being checked.
     * @param p   the Gerrit project being checked.
     * @return true if so.
     */
    public synchronized boolean isProjectTriggeredAndIncomplete(AbstractProject p, GerritTriggeredEvent event) {
        if (!memory.isTriggered(event, p)) {
            return false;
        }
        //misnomer: the project is considered "building", even if the build hasn't been created
        //for that project yet. As long as the project exists, and does not have a completed
        //build, it is "building".
        if (memory.isBuilding(event, p)) {
            return true;
        }
        return false;
    }

    @Override
    public synchronized void onStarted(AbstractBuild r, TaskListener listener) {
        GerritCause cause = getCause(r);
        logger.debug("Started. Build: {} Cause: {}", r, cause);
        if (cause != null) {
            cleanUpGerritCauses(cause, r);
            setThisBuild(r);
            if (cause.getEvent() != null) {
                if (cause.getEvent() instanceof GerritEventLifecycle) {
                    ((GerritEventLifecycle)cause.getEvent()).fireBuildStarted(r);
                }
            }
            if (!cause.isSilentMode()) {
                memory.started(cause.getEvent(), r);
                updateTriggerContexts(r);
                BuildsStartedStats stats = memory.getBuildsStartedStats(cause.getEvent());
                NotificationFactory.getInstance().queueBuildStarted(r, listener, cause.getEvent(), stats);
            }
            logger.info("Gerrit build [{}] Started for cause: [{}].", r, cause);
            logger.info("MemoryStatus:\n{}", memory.getStatusReport(cause.getEvent()));
        }
    }

    /**
     * Updates the {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext}s for all the
     * {@link GerritCause}s in the build.
     *
     * @param r   the build.
     * @see BuildMemory#updateTriggerContext(
     *                  com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause,
     *                  hudson.model.AbstractBuild)
     */
    protected void updateTriggerContexts(AbstractBuild r) {
        List<Cause> causes = r.getCauses();
        for (Cause cause : causes) {
            if (cause instanceof GerritCause) {
                memory.updateTriggerContext((GerritCause)cause, r);
            }
        }
    }

    /**
     * Updates all {@link GerritCause}s
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext#thisBuild}
     * in the build.
     *
     * @param r the build to update.
     */
    protected void setThisBuild(AbstractBuild r) {
        List<Cause> causes = r.getCauses();
        for (Cause cause : causes) {
            if (cause instanceof GerritCause) {
                ((GerritCause)cause).getContext().setThisBuild(r);
            }
        }
    }

    /**
     * Workaround for builds that are triggered by the same Gerrit cause but multiple times in the same quiet period.
     *
     * @param firstFound the cause first returned by {@link AbstractBuild#getCause(Class)}.
     * @param build      the build to clean up.
     */
    protected void cleanUpGerritCauses(GerritCause firstFound, AbstractBuild build) {
        List<Cause> causes = build.getAction(CauseAction.class).getCauses();
        int pos = causes.indexOf(firstFound) + 1;
        while (pos < causes.size()) {
            Cause c = causes.get(pos);
            if (c.equals(firstFound)) {
                causes.remove(pos);
            } else {
                pos++;
            }
        }
    }

    /**
     * Called just before a build is scheduled by the trigger.
     *
     * @param project the project that will be built.
     * @param event   the event that caused the build to be scheduled.
     */
    public synchronized void onTriggered(AbstractProject project, GerritTriggeredEvent event) {
        //TODO stop builds for earlier patch-sets on same change.
        memory.triggered(event, project);
        if (event instanceof GerritEventLifecycle) {
            ((GerritEventLifecycle)event).fireProjectTriggered(project);
        }
        //Logging
        String name = null;
        if (project != null) {
            name = project.getName();
        }
        logger.info("Project [{}] triggered by Gerrit: [{}]", name, event);
    }

    /**
     * Called just before a build is scheduled by the user to retrigger.
     *
     * @param project     the project.
     * @param event       the event.
     * @param otherBuilds the list of other builds in the previous context.
     */
    public synchronized void onRetriggered(AbstractProject project,
                                           GerritTriggeredEvent event,
                                           List<AbstractBuild> otherBuilds) {
        memory.retriggered(event, project, otherBuilds);
        if (event instanceof GerritEventLifecycle) {
            ((GerritEventLifecycle)event).fireProjectTriggered(project);
        }
        //Logging
        String name = null;
        if (project != null) {
            name = project.getName();
        }
        logger.info("Project [{}] re-triggered by Gerrit-User: [{}]", name, event);
    }

    /**
     * Checks the memory if the project is currently building the event.
     *
     * @param project the project.
     * @param event   the event.
     * @return true if so.
     *
     * @see BuildMemory#isBuilding(GerritTriggeredEvent, hudson.model.AbstractProject)
     */
    public boolean isBuilding(AbstractProject project, GerritTriggeredEvent event) {
        if (project == null || event == null) {
            return false;
        } else {
            return memory.isBuilding(event, project);
        }
    }

    /**
     * Checks the memory if the event is building.
     *
     * @param event the event.
     * @return true if so.
     *
     * @see BuildMemory#isBuilding(com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent)
     */
    public boolean isBuilding(GerritTriggeredEvent event) {
        if (event == null) {
            return false;
        } else {
            return memory.isBuilding(event);
        }
    }

    /**
     * Finds the GerritCause for a build if there is one.
     *
     * @param build the build to look in.
     * @return the GerritCause or null if there is none.
     */
    private GerritCause getCause(AbstractBuild build) {
        return (GerritCause)build.getCause(GerritCause.class);
    }

    /**
     * Searches the <code>workspace</code> for files matching the <code>filepath</code> glob.
     *
     * @param ws The workspace
     * @param filepath The filepath glob pattern
     * @return List of matching {@link FilePath}s. Guaranteed to be non-null.
     * @throws IOException if an error occurs while reading the workspace
     * @throws InterruptedException if an error occurs while reading the workspace
     */
    protected FilePath[] getMatchingWorkspaceFiles(FilePath ws, String filepath)
            throws IOException, InterruptedException {
        return ws.list(filepath);
    }

    /**
     * Returns the expanded file contents using the provided environment variables.
     * <code>null</code> will be returned if the path does not exist.
     *
     * @param path The file path being read.
     * @param envVars The environment variables to use during expansion.
     * @return The string file contents, or <code>null</code> if it does not exist.
     * @throws IOException if an error occurs while reading the file
     * @throws InterruptedException if an error occurs while checking the status of the file
     */
    protected String getExpandedContent(FilePath path, EnvVars envVars) throws IOException, InterruptedException {
        if (path.exists()) {
            return envVars.expand(path.readToString());
        }

        return null;
    }

    /**
     * Attempt to obtain the failure message for a build.
     *
     * @param event The event that triggered this build
     * @param build The build being executed
     * @param listener The build listener
     * @return Message content from the configured unsuccessful message file
     * @throws IOException In case of an error communicating with the {@link FilePath} or {@link EnvVars Environment}
     * @throws InterruptedException If interrupted while working with the {@link FilePath} or {@link EnvVars Environment}
     */
    private String obtainFailureMessage(GerritTriggeredEvent event, AbstractBuild build, TaskListener listener)
            throws IOException, InterruptedException {
        AbstractProject project = build.getProject();
        String content = null;

        GerritTrigger trigger = GerritTrigger.getTrigger(project);

        // trigger will be null in unit tests
        if (trigger != null) {
            String filepath = trigger.getBuildUnsuccessfulFilepath();
            logger.debug("Looking for failure message in file glob: {}", filepath);


            if (filepath != null && !filepath.isEmpty()) {
                EnvVars envVars;

                if (listener == null) {
                    envVars = build.getEnvironment();
                } else {
                    envVars = build.getEnvironment(listener);
                }

                // The filename may contain environment variables
                filepath = envVars.expand(filepath);

                // Check for ANT-style file path
                FilePath[] matches = this.getMatchingWorkspaceFiles(build.getWorkspace(), filepath);
                logger.debug("Found matching workspace files: {}", matches);

                if (matches.length > 0) {
                    // Use the first match
                    FilePath path = matches[0];
                    content = this.getExpandedContent(path, envVars);
                    logger.info("Obtained failure message from file: {}", content);
                }
            }
        }

        return content;
    }
}
