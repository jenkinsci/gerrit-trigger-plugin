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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.PatchSetKey;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;

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
@Extension
public class ToGerritRunListener extends RunListener<AbstractBuild> {

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
        logger.info("Completed. Build: {} Cause: {}", r, cause);
        if (cause != null) {
            cleanUpGerritCauses(cause, r);
            PatchsetCreated event = cause.getEvent();
            if (GerritTrigger.getTrigger(r.getProject()) != null) {
                // There won't be a trigger if this job was run through a unit test
                GerritTrigger.getTrigger(r.getProject()).notifyBuildEnded(event);
            }
            event.fireBuildCompleted(r);
            if (!cause.isSilentMode()) {
                PatchSetKey key = memory.completed(event, r);

                if (r.getResult().isWorseThan(Result.SUCCESS)) {
                    try {
                        // Attempt to record the failure message, if applicable
                        String failureMessage = this.obtainFailureMessage(event, r, listener);
                        logger.info("Obtained failure message: {}", failureMessage);
                        memory.setEntryFailureMessage(key, r, failureMessage);
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

                updateTriggerContexts(r, key);
                if (memory.isAllBuildsCompleted(key)) {
                    try {
                        logger.info("All Builds are completed for cause: {}", cause);
                        event.fireAllBuildsCompleted();
                        NotificationFactory.getInstance().queueBuildCompleted(memory.getMemoryImprint(key), listener);
                    } finally {
                        memory.forget(key);
                    }
                } else {
                    logger.info("Waiting for more builds to complete for cause [{}]. Status: \n{}",
                            cause, memory.getStatusReport(key));
                }
            }
        }
    }

    @Override
    public synchronized void onStarted(AbstractBuild r, TaskListener listener) {
        GerritCause cause = getCause(r);
        logger.debug("Started. Build: {} Cause: {}", r, cause);
        if (cause != null) {
            cleanUpGerritCauses(cause, r);
            setThisBuild(r);
            PatchSetKey key = null;
            if (cause.getEvent() != null) {
                cause.getEvent().fireBuildStarted(r);
            }
            if (!cause.isSilentMode()) {
                key = memory.started(cause.getEvent(), r);
                updateTriggerContexts(r, key);
                BuildsStartedStats stats = memory.getBuildsStartedStats(key);
                NotificationFactory.getInstance().queueBuildStarted(r, listener, cause.getEvent(), stats);
            }
            logger.info("Gerrit build [{}] Started for cause: [{}].", r, cause);
            if (key != null) {
                logger.info("MemoryStatus:\n{}", memory.getStatusReport(key));
            }
        }
    }

    /**
     * Updates the {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext}s for all the
     * {@link GerritCause}s in the build.
     *
     * @param r   the build.
     * @param key the memory key to update.
     * @see BuildMemory#updateTriggerContext(
     *      com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.PatchSetKey,
     *      com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause, hudson.model.AbstractBuild)
     */
    protected void updateTriggerContexts(AbstractBuild r, PatchSetKey key) {
        List<Cause> causes = r.getCauses();
        for (Cause cause : causes) {
            if (cause instanceof GerritCause) {
                memory.updateTriggerContext(key, (GerritCause)cause, r);
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
    public synchronized void onTriggered(AbstractProject project, PatchsetCreated event) {
        //TODO stop builds for earlier patch-sets on same change.
        memory.triggered(event, project);
        event.fireProjectTriggered(project);
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
                                           PatchsetCreated event,
                                           List<AbstractBuild> otherBuilds) {
        memory.retriggered(event, project, otherBuilds);
        event.fireProjectTriggered(project);
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
     * @see BuildMemory#isBuilding(PatchsetCreated, hudson.model.AbstractProject)
     */
    public boolean isBuilding(AbstractProject project, PatchsetCreated event) {
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
     * @see BuildMemory#isBuilding(com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated)
     */
    public boolean isBuilding(PatchsetCreated event) {
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
    private String obtainFailureMessage(PatchsetCreated event, AbstractBuild build, TaskListener listener)
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
