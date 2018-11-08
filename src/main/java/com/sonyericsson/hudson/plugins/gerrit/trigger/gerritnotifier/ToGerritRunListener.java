/*
 *  The MIT License
 *
 *  Copyright (c) 2010, 2014 Sony Mobile Communications Inc. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.BuildMemoryReport;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.lifecycle.GerritEventLifecycle;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;

import hudson.EnvVars;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The Big RunListener in charge of coordinating build results and reporting back to Gerrit.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@Extension(ordinal = ToGerritRunListener.ORDINAL)
public final class ToGerritRunListener extends RunListener<Run> {

    /**
     * The ordering of this extension.
     */
    public static final int ORDINAL = 10003;
    private static final Logger logger = LoggerFactory.getLogger(ToGerritRunListener.class);
    private final transient BuildMemory memory = new BuildMemory();

    /**
     * Returns the registered instance of this class from the list of all listeners.
     *
     * @return the instance.
     */
    @CheckForNull
    public static ToGerritRunListener getInstance() {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            logger.error("Jenkins instance is not available, are we not fully live yet?");
            return null;
        }
        ExtensionList<ToGerritRunListener> listeners =
                jenkins.getExtensionList(ToGerritRunListener.class);
        if (listeners == null || listeners.isEmpty()) {
            logger.error("INITIALIZATION ERROR? Could not find the registered instance.");
            return null;
        }
        return listeners.get(0);
    }

    /**
     * Records a custom URL for the given build.
     *
     * @param r         the build.
     * @param customUrl the URL.
     */
    public void setBuildCustomUrl(@Nonnull Run r, @Nonnull String customUrl) {
        GerritCause cause = getCause(r);
        if (cause != null) {
            cleanUpGerritCauses(cause, r);
            memory.setEntryCustomUrl(cause.getEvent(), r, customUrl);
        }
    }

    /**
     * Records the unsuccessful message for the given build.
     *
     * @param r                   the build that caused the failure.
     * @param unsuccessfulMessage the unsuccessful message
     */
    public void setBuildUnsuccessfulMessage(@Nonnull Run r, @Nonnull String unsuccessfulMessage) {
        GerritCause cause = getCause(r);
        if (cause != null) {
            cleanUpGerritCauses(cause, r);
            memory.setEntryUnsuccessfulMessage(cause.getEvent(), r, unsuccessfulMessage);
        }
    }

    @Override
    public synchronized void onCompleted(@Nonnull Run r, @Nonnull TaskListener listener) {
        GerritCause cause = getCause(r);
        logger.debug("Completed. Build: {} Cause: {}", r, cause);
        if (cause != null) {
            cleanUpGerritCauses(cause, r);
            GerritTriggeredEvent event = cause.getEvent();
            GerritTrigger trigger = GerritTrigger.getTrigger(r.getParent());
            if (trigger != null) {
                // There won't be a trigger if this job was run through a unit test
                trigger.notifyBuildEnded(event);
            }
            if (event instanceof GerritEventLifecycle) {
                ((GerritEventLifecycle)event).fireBuildCompleted(r);
            }
            if (!cause.isSilentMode()) {
                memory.completed(event, r);

                Result result = r.getResult();
                if (result != null && result.isWorseThan(Result.SUCCESS)) {
                    try {
                        // Attempt to record the unsuccessful message, if applicable
                        String failureMessage = this.obtainUnsuccessfulMessage(event, r, listener);
                        logger.info("Obtained unsuccessful message: {}", failureMessage);
                        if (failureMessage != null) {
                            memory.setEntryUnsuccessfulMessage(event, r, failureMessage);
                        }
                    } catch (IOException e) {
                        listener.error("[gerrit-trigger] Unable to read unsuccessful message from the workspace.");
                        logger.warn("IOException while obtaining unsuccessful message for build: "
                                + r.getDisplayName(), e);
                    } catch (InterruptedException e) {
                        listener.error("[gerrit-trigger] Unable to read unsuccessful message from the workspace.");
                        logger.warn("InterruptedException while obtaining unsuccessful message for build: "
                                + r.getDisplayName(), e);
                    }
                }

                updateTriggerContexts(r);
                allBuildsCompleted(event, cause, listener);
            }
        }
    }

    /**
     * Creates a snapshot report of the current contents of the {@link BuildMemory}.
     *
     * @return the report.
     * @see com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.Diagnostics
     */
    @Nonnull
    public synchronized BuildMemoryReport report() {
        return memory.report();
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
    public synchronized boolean isProjectTriggeredAndIncomplete(Job p, GerritTriggeredEvent event) {
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
    public synchronized void onStarted(Run r, TaskListener listener) {
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
                GerritTrigger trigger = GerritTrigger.getTrigger(r.getParent());
                boolean silentStartMode = false;
                if (trigger != null) {
                    silentStartMode = trigger.isSilentStartMode();
                }
                if (!silentStartMode) {
                    BuildsStartedStats stats = memory.getBuildsStartedStats(cause.getEvent());
                    NotificationFactory.getInstance().queueBuildStarted(r, listener, cause.getEvent(), stats);
                }
            }
            logger.info("Gerrit build [{}] Started for cause: [{}].", r, cause);
            logger.info("MemoryStatus:\n{}", memory.getStatusReport(cause.getEvent()));
        }
    }

    /**
     * Get runs triggered for event.
     *
     * @param event   the Gerrit Event which is being checked.
     * @return the list of triggered runs for the event.
     */
    @Nullable
    public List<Run> getRuns(GerritTriggeredEvent event) {
        return memory.getBuilds(event);
    }

    /**
     * Updates the {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext}s for all the
     * {@link GerritCause}s in the build.
     *
     * @param r   the build.
     * @see BuildMemory#updateTriggerContext(
     *                  com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause,
     *                  hudson.model.Run)
     */
    protected void updateTriggerContexts(Run r) {
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
    protected void setThisBuild(Run r) {
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
     * @param firstFound the cause first returned by {@link Run#getCause(Class)}.
     * @param build      the build to clean up.
     */
    protected void cleanUpGerritCauses(GerritCause firstFound, Run build) {
        List<Cause> causes = build.getAction(CauseAction.class).getCauses();
        try {
            int pos = causes.indexOf(firstFound) + 1;
            while (pos < causes.size()) {
                Cause c = causes.get(pos);
                if (c.equals(firstFound)) {
                    causes.remove(pos);
                } else {
                    pos++;
                }
            }
        } catch (UnsupportedOperationException ignored) {
            logger.debug("Got smashed by JENKINS-33467, but it shouldn't do any harm", ignored);
            /*
            TODO Something better should be done here, this is to prevent totally breaking when JENKINS-33467 hits.
                 But since JENKINS-33467 is aimed at preventing this case maybe it's no longer needed at all.
            */
        }
    }

    /**
     * Called just before a build is scheduled by the trigger.
     *
     * @param project the project that will be built.
     * @param event   the event that caused the build to be scheduled.
     */
    public synchronized void onTriggered(Job project, GerritTriggeredEvent event) {
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
    public synchronized void onRetriggered(Job project,
                                           GerritTriggeredEvent event,
                                           List<Run> otherBuilds) {
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
     * @see BuildMemory#isBuilding(GerritTriggeredEvent, hudson.model.Job)
     */
    public boolean isBuilding(Job project, GerritTriggeredEvent event) {
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
     * Checks the memory if the project is triggered by the event.
     *
     * @param project the project.
     * @param event the event.
     * @return true if so.
     */
    public boolean isTriggered(Job project, GerritTriggeredEvent event) {
        if (project == null || event == null) {
            return false;
        } else {
            return memory.isTriggered(event, project);
        }
    }

    /**
     * Sets the memory of the project to buildCompleted. Used when the entry is canceled in the Queue.
     *
     * @param project the project
     * @param event the event
     */
    public void setQueueCancelled(Job project, GerritTriggeredEvent event) {
        if (project == null || event == null) {
            return;
        } else {
            memory.cancelled(event, project);
        }
    }

    /**
     * Cleans the project from run listener related data structures.
     *
     * @param project the project to be removed.
     */
    public void notifyProjectRemoved(Job project) {
        memory.removeProject(project);
    }

    /**
     * Finds the GerritCause for a build if there is one.
     *
     * @param build the build to look in.
     * @return the GerritCause or null if there is none.
     */
    private GerritCause getCause(Run build) {
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
    @Nonnull
    protected FilePath[] getMatchingWorkspaceFiles(@Nullable FilePath ws, @Nonnull String filepath)
            throws IOException, InterruptedException {
        if (ws == null) {
            return new FilePath[0];
        }
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
     * Attempt to obtain the unsuccessful message for a build.
     *
     * @param event The event that triggered this build
     * @param build The build being executed
     * @param listener The build listener
     * @return Message content from the configured unsuccessful message file
     * @throws IOException In case of an error communicating with the {@link FilePath} or {@link EnvVars Environment}
     * @throws InterruptedException If interrupted while working with the {@link FilePath} or {@link EnvVars Environment}
     */
    private String obtainUnsuccessfulMessage(@Nullable GerritTriggeredEvent event,
                                             @Nonnull Run build,
                                             @Nullable TaskListener listener)
            throws IOException, InterruptedException {
        Job project = build.getParent();
        String content = null;

        GerritTrigger trigger = GerritTrigger.getTrigger(project);

        // trigger will be null in unit tests
        if (trigger != null) {
            String filepath = trigger.getBuildUnsuccessfulFilepath();
            logger.debug("Looking for unsuccessful message in file glob: {}", filepath);


            if (filepath != null && !filepath.isEmpty()) {
                EnvVars envVars;

                if (listener == null) {
                    envVars = build.getEnvironment();
                } else {
                    envVars = build.getEnvironment(listener);
                }

                // The filename may contain environment variables
                filepath = envVars.expand(filepath);

                if (build instanceof AbstractBuild) {
                    // Check for ANT-style file path
                    FilePath[] matches = this.getMatchingWorkspaceFiles(((AbstractBuild)build).getWorkspace(), filepath);
                    logger.debug("Found matching workspace files: {}", matches);

                    if (matches.length > 0) {
                        // Use the first match
                        FilePath path = matches[0];
                        content = this.getExpandedContent(path, envVars);
                        logger.info("Obtained unsuccessful message from file: {}", content);
                    }
                } else {
                    logger.warn("Unable to find matching workspace files for job {}, type {}",
                            build.getDisplayName(), build.getClass().getName());
                }
            }
        }

        return content;
    }
}
