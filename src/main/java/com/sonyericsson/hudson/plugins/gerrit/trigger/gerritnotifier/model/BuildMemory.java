/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
 *  Copyright 2026 CloudBees, Inc.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model;

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.BuildMemoryReport;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint.Entry;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.AbandonedPatchsetInterruption;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.NewPatchSetInterruption;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.BuildCancellationPolicy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.BuildMemoryStorage;
import com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.CoordinationModeFactory;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import hudson.model.Cause;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.Run;
import jenkins.model.CauseOfInterruption;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl.getServerConfig;
import static com.sonyericsson.hudson.plugins.gerrit.trigger.utils.Logic.shouldSkip;

/**
 * Keeps track of what builds have been triggered and if all builds are done for specific events.
 * <p>
 * This class now delegates all storage operations to a {@link BuildMemoryStorage} implementation,
 * discovered via the Extension Points pattern. This allows switching between local (TreeMap) and
 * distributed (e.g. Hazelcast) storage without changing this class.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class BuildMemory {

    /**
     * Compares GerritTriggeredEvents using the Object.hashCode() method. This ensures that every event received from
     * Gerrit is kept track of individually.
     *
     * @author James E. Blair &lt;jeblair@hp.com&gt;
     */
    public static class GerritTriggeredEventComparator implements Comparator<GerritTriggeredEvent> {
        @Override
        public int compare(GerritTriggeredEvent o1, GerritTriggeredEvent o2) {
            if (o1 == null && o2 == null) {
                return 0;
            }
            if (o1 != null && o2 == null) {
                return 1;
            }
            if (o1 == null && o2 != null) {
                return -1;
            }
            return Integer.compare(o1.hashCode(), o2.hashCode());
        }
    }

    /**
     * The storage backend for build memory.
     * Discovered and created via CoordinationModeFactory on first use.
     */
    private final BuildMemoryStorage storage;

    private static final Logger logger = LoggerFactory.getLogger(BuildMemory.class);

    /**
     * Default constructor.
     * Initializes storage via factory discovery.
     */
    public BuildMemory() {
        this.storage = CoordinationModeFactory.get().getStorage();
    }

    /**
     * Gets the memory of a specific event.
     *
     * @param event the event.
     * @return the memory.
     */
    public MemoryImprint getMemoryImprint(GerritTriggeredEvent event) {
        return storage.getMemoryImprint(event);
    }

    /**
     * Tells if all triggered builds have started for a specific memory imprint.
     *
     * @param event the event.
     * @return true if it is so.
     */
    public boolean isAllBuildsCompleted(GerritTriggeredEvent event) {
        return storage.isAllBuildsCompleted(event);
    }

    /**
     * Gets the statistics of started builds for a specific memory imprint.
     *
     * @param event the event.
     * @return the statistics.
     */
    public BuildsStartedStats getBuildsStartedStats(GerritTriggeredEvent event) {
        return storage.getBuildsStartedStats(event);
    }

    /**
     * Returns the status report for the given MemoryImprint.
     *
     * @param event the event.
     * @return the status as it is now.
     *
     * @see MemoryImprint#getStatusReport()
     */
    public String getStatusReport(GerritTriggeredEvent event) {
        return storage.getStatusReport(event);
    }

    /**
     * Tells if all triggered builds have started for a specific memory imprint.
     *
     * @param event the event.
     * @return true if it is so.
     */
    public boolean isAllBuildsStarted(GerritTriggeredEvent event) {
        return storage.isAllBuildsStarted(event);
    }

    /**
     * Sets the memory that a build is completed for an event.
     *
     * @param event the event
     * @param build the build.
     */
    public void completed(GerritTriggeredEvent event, Run build) {
        storage.completed(event, build);
    }

    /**
     * Sets the memory that a build has started for an event.
     *
     * @param event the event.
     * @param build the build.
     */
    public void started(GerritTriggeredEvent event, Run build) {
        storage.started(event, build);
    }

    /**
     * Adds a new memory about a build that has been/will be triggered.
     *
     * @param event   the event that triggered it.
     * @param project the project that was triggered.
     */
    public void triggered(GerritTriggeredEvent event, Job project) {
        storage.triggered(event, project);
    }

    /**
     * Adds a new memory about a build that has been retriggered. If there is an active memory about the provided event,
     * then the project is reset with no build info. Otherwise the memory is recreated from the list of other builds and
     * their result.
     *
     * @param event       the event to be retriggered.
     * @param project     the project that has been retriggered.
     * @param otherBuilds the list of other builds that was in the "old" memory.
     */
    public void retriggered(
            GerritTriggeredEvent event,
            Job project,
            List<Run> otherBuilds) {
        storage.retriggered(event, project, otherBuilds);
    }

    /**
     * Sets the status if a project to completed when queue is cancelled.
     *
     * @param event       the event to be retriggered.
     * @param project     the project that has been retriggered.
     */
    public void cancelled(GerritTriggeredEvent event, Job project) {
        storage.cancelled(event, project);
    }


    /**
     * Removes the memory for the event.
     *
     * @param event the event.
     */
    public void forget(GerritTriggeredEvent event) {
        storage.forget(event);
    }

    /**
     * Updates the {@link TriggerContext} for the event. The cause and build is the "focal point" for the update, but
     * all memory entities will be updated, but only the current context will be {@link
     * TriggerContext#setThisBuild(hudson.model.Run)}updated.
     *
     * @param cause the cause.
     * @param r     the build the cause is in.
     */
    public synchronized void updateTriggerContext(GerritCause cause, Run r) {
        MemoryImprint imprint = getMemoryImprint(cause.getEvent());
        TriggerContext context = cause.getContext();
        context.setThisBuild(r);
        for (MemoryImprint.Entry entry : imprint.getEntries()) {
            Run build = entry.getBuild();
            if (build != null && !build.equals(r)) {
                context.addOtherBuild(build);
                updateTriggerContext(entry, imprint);
            } else {
                Job project = entry.getProject();
                if (build == null && project != null && !project.equals(r.getParent())) {
                    context.addOtherProject(project);
                }
            }
        }
        if (!r.hasntStartedYet() && !r.isBuilding()) {
            try {
                r.save();
            } catch (IOException ex) {
                logger.error("Could not save build state for build " + r, ex);
            }
        }
    }

    /**
     * Updates the {@link TriggerContext} for the provided entry.
     *
     * @param entryToUpdate the entry to update.
     * @param imprint       the information for the update.
     */
    private synchronized void updateTriggerContext(@NonNull Entry entryToUpdate, @NonNull MemoryImprint imprint) {
        Run build = entryToUpdate.getBuild();
        if (build != null) {
            GerritCause cause = (GerritCause)build.getCause(GerritCause.class);
            if (cause != null) {
                TriggerContext context = cause.getContext();
                for (MemoryImprint.Entry ent : imprint.getEntries()) {
                    Run entBuild = ent.getBuild();
                    if (entBuild != null && !entBuild.equals(build)) {
                        context.addOtherBuild(entBuild);
                    } else {
                        Job entProject = ent.getProject();
                        if (entBuild == null && entProject != null && !entProject.equals(entryToUpdate.getProject())) {
                            context.addOtherProject(entProject);
                        }
                    }
                }
                if (!build.hasntStartedYet() && !build.isBuilding()) {
                    try {
                        build.save();
                    } catch (IOException ex) {
                        logger.error("Could not save state for build " + build, ex);
                    }
                }
            }
        }
    }

    /**
     * Checks in memory if the project has been triggered for the event.
     *
     * @param event   the event.
     * @param project the project.
     * @return true if so.
     */
    public boolean isTriggered(@NonNull GerritTriggeredEvent event, @NonNull Job project) {
        return storage.isTriggered(event, project);
    }

    /**
     * Checks in memory if the project is building the event.
     *
     * @param event   the event.
     * @param project the project.
     * @return true if so.
     */
    public boolean isBuilding(GerritTriggeredEvent event, @NonNull Job project) {
        return storage.isBuilding(event, project);
    }

    /**
     * Checks if the provided event exists in this memory.
     *
     * @param event the event to look for.
     * @return true if so.
     */
    public boolean isBuilding(GerritTriggeredEvent event) {
        return storage.isBuilding(event);
    }

    /**
     * Cancel outdated builds based on policy.
     * Replaces RunningJobs.cancelOutDatedEvents() functionality.
     *
     * @param newEvent the new event that may trigger cancellation
     * @param policy the cancellation policy to apply
     * @param trigger the Gerrit trigger (needed for topic-based cancellation)
     * @param job the job for which to cancel builds
     */
    public void cancelOutdatedEvents(
            @NonNull ChangeBasedEvent newEvent,
            @NonNull BuildCancellationPolicy policy,
            @NonNull GerritTrigger trigger,
            @NonNull Job job) {

        logger.info("cancelOutdatedBuilds called for event {} with policy {}", newEvent, policy);

        Map<GerritTriggeredEvent, MemoryImprint> allEvents = storage.getAllEvents();
        logger.info("BuildMemory has {} events in memory", allEvents.size());

        String jobName = job.getFullName();
        List<ChangeBasedEvent> outdatedEvents = new ArrayList<>();
        CauseOfInterruption cause = new NewPatchSetInterruption();

        synchronized (this) {
            // Find all outdated events
            Iterator<Map.Entry<GerritTriggeredEvent, MemoryImprint>> it = allEvents.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<GerritTriggeredEvent, MemoryImprint> entry = it.next();
                GerritTriggeredEvent runningEvent = entry.getKey();

                if (!(runningEvent instanceof ChangeBasedEvent)) {
                    continue;
                }

                ChangeBasedEvent runningChangeBasedEvent = (ChangeBasedEvent)runningEvent;
                logger.debug("Checking running event: {}", runningChangeBasedEvent);

                if (shouldIgnoreEvent(newEvent, policy, runningChangeBasedEvent, trigger)) {
                    logger.debug("Ignoring event based on policy");
                    continue;
                }

                // Check if this event has active builds for this job
                MemoryImprint imprint = entry.getValue();
                boolean hasActiveBuildsForJob = false;

                for (Entry imprintEntry : imprint.getEntries()) {
                    logger.debug("Checking entry: project={}, completed={}, cancelled={}",
                            imprintEntry.getProject(), imprintEntry.isBuildCompleted(), imprintEntry.isCancelled());
                    if (imprintEntry.isProject(jobName)
                            && !imprintEntry.isBuildCompleted()
                            && !imprintEntry.isCancelled()) {
                        hasActiveBuildsForJob = true;
                        logger.debug("Found active build for job {}", jobName);
                        break;
                    }
                }

                if (hasActiveBuildsForJob) {
                    outdatedEvents.add(runningChangeBasedEvent);
                    logger.debug("Added event to outdated list");

                    // NOTE: Do NOT remove the event from BuildMemory here!
                    // Unlike the old RunningJobs code (which was separate from lifecycle tracking),
                    // BuildMemory needs to keep tracking cancelled events for lifecycle/feedback.
                    // Mark the Entry as cancelled immediately so it won't be considered "active"
                    // in future cancellation checks (prevents state accumulation issues).
                    //
                    // IMPORTANT: We need to get the imprint from storage again to modify the real one,
                    // not the copy from getAllEvents()
                    MemoryImprint storageImprint = storage.getMemoryImprint(runningEvent);
                    if (storageImprint != null) {
                        for (Entry imprintEntry : storageImprint.getEntries()) {
                            if (imprintEntry.isProject(jobName)
                                    && !imprintEntry.isBuildCompleted()
                                    && !imprintEntry.isCancelled()) {
                                imprintEntry.setCancelled(true);
                                imprintEntry.setBuildCompleted(true);
                            }
                        }
                    }
                }
            }

            logger.info("Found {} outdated events to cancel", outdatedEvents.size());

            // Add the new event to BuildMemory for future cancellation checks
            // In silent mode, events won't be added via onTriggered(), so we must add them here
            // In non-silent mode, onTriggered() also adds them, but MemoryImprint.set() is idempotent
            if (!outdatedEvents.contains(newEvent)) {
                if (trigger.isOnlyAbortRunningBuild(newEvent)) {
                    cause = new AbandonedPatchsetInterruption();
                } else {
                    // Add event so it can be found and cancelled by future events
                    // This is critical for silent mode where onTriggered() isn't called
                    triggered(newEvent, job);
                }
            }
        }
        // Cancel the outdated jobs (outside the iterator loop to avoid concurrent modification)
        for (ChangeBasedEvent outdatedEvent : outdatedEvents) {
            logger.info("Cancelling build for event: {}", outdatedEvent);
            try {
                cancelMatchingJobs(outdatedEvent, jobName, job, cause);
            } catch (Exception e) {
                logger.error("Error canceling job", e);
            }
        }
    }

    /**
     * Determines if event should be ignored due to policy.
     * Ported from RunningJobs.shouldIgnoreEvent().
     *
     * @param event event being evaluated
     * @param policy policy to determine cancellation
     * @param runningChangeBasedEvent existing event to compare against
     * @param trigger the Gerrit trigger (for topic-based logic)
     * @return true if event should be ignored for cancellation
     */
    private boolean shouldIgnoreEvent(
            ChangeBasedEvent event,
            BuildCancellationPolicy policy,
            ChangeBasedEvent runningChangeBasedEvent,
            GerritTrigger trigger) {

        boolean abortBecauseOfTopic = trigger.abortBecauseOfTopic(event, policy, runningChangeBasedEvent);

        if (!abortBecauseOfTopic) {

            Change change = runningChangeBasedEvent.getChange();
            if (!change.equals(event.getChange())) {
                return true;
            }

            boolean shouldCancelManual = (!(runningChangeBasedEvent instanceof ManualPatchsetCreated)
                    || policy.isAbortManualPatchsets());
            if (!shouldCancelManual) {
                return true;
            }

            // events of "type": "topic-changed" are not required to set a PatchSet
            boolean hasPatchSets = runningChangeBasedEvent.getPatchSet() != null
                    && event.getPatchSet() != null;

            boolean hasPatchNumbers = hasPatchSets && runningChangeBasedEvent.getPatchSet().getNumber() != null
                    && event.getPatchSet().getNumber() != null;

            boolean isOldPatch = hasPatchSets && hasPatchNumbers
                    && Integer.parseInt(runningChangeBasedEvent.getPatchSet().getNumber())
                    < Integer.parseInt(event.getPatchSet().getNumber());

            boolean shouldCancelPatchsetNumber = policy.isAbortNewPatchsets() || isOldPatch;

            boolean isAbortAbandonedPatchset = policy.isAbortAbandonedPatchsets()
                    && (event instanceof ChangeAbandoned);

            if (!shouldCancelPatchsetNumber && !isAbortAbandonedPatchset) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cancels any jobs that were triggered by the given event.
     * Ported from RunningJobs.cancelMatchingJobs().
     *
     * @param event the event that originally triggered the build
     * @param jobName the job name to match on
     * @param job the job instance (for queue access)
     * @param cause the cause of the build interruption
     */
    private void cancelMatchingJobs(
            GerritTriggeredEvent event,
            String jobName,
            Job job,
            CauseOfInterruption cause) {

        try {
            if (!(job instanceof Queue.Task)) {
                logger.error("Error canceling job. The job is not of type Task. Job name: {}", job.getName());
                return;
            }

            // Remove any jobs in the build queue
            List<Queue.Item> itemsInQueue = Queue.getInstance().getItems((Queue.Task)job);
            for (Queue.Item item : itemsInQueue) {
                if (checkCausedByGerrit(event, item.getCauses())) {
                    Job tJob = (Job)item.task;
                    if (jobName.equals(tJob.getFullName())) {
                        Queue.getInstance().cancel(item);
                    }
                }
            }

            // Interrupt any currently running jobs
            Jenkins jenkins = Jenkins.get();
            for (Computer c : jenkins.getComputers()) {
                for (Executor e : c.getAllExecutors()) {
                    Queue.Executable currentExecutable = e.getCurrentExecutable();
                    if (!(currentExecutable instanceof Run<?, ?>)) {
                        continue;
                    }

                    Run<?, ?> run = (Run<?, ?>)currentExecutable;
                    if (!checkCausedByGerrit(event, run.getCauses())) {
                        continue;
                    }

                    String runningJobName = run.getParent().getFullName();
                    if (!jobName.equals(runningJobName)) {
                        continue;
                    }

                    e.interrupt(Result.ABORTED, cause);
                }
            }
        } catch (Exception e) {
            logger.error("Error canceling job", e);
        }
    }

    /**
     * Checks if any of the given causes references the given event.
     * Ported from RunningJobs.checkCausedByGerrit().
     *
     * @param event the event to check for (checks for identity, not equality)
     * @param causes the list of causes
     * @return true if the list contains a GerritCause with this event
     */
    private boolean checkCausedByGerrit(GerritTriggeredEvent event, Collection<Cause> causes) {
        for (Cause c : causes) {
            if (c instanceof GerritCause) {
                GerritCause gc = (GerritCause)c;
                if (gc.getEvent() == event) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Called when trigger has cancellation policy associated with it.
     *
     *
     * @param event event that is trigger builds
     * @param job job to match for specific cancellation
     * @param trigger the Gerrit trigger (needed for topic-based cancellation)
     * @param policy policy to decide cancelling build or not
     */
    public void cancelTriggeredJob(ChangeBasedEvent event,
                                   BuildCancellationPolicy policy,
                                   GerritTrigger trigger,
                                   Job job) {
        if (policy == null || !policy.isEnabled()) {
            return;
        }

        if ((event instanceof ManualPatchsetCreated && !policy.isAbortManualPatchsets())) {
            return;
        }

        cancelOutdatedEvents(event, policy, trigger, job);
    }

    /**
     * Checks scheduled job and cancels current jobs if needed.
     * I.e. cancelling the old build if configured to do so and removing and storing any references.
     * Only used by Server wide policy.
     *
     * Note: Events are added to BuildMemory for cancellation tracking. In silent mode,
     * onTriggered() won't be called, so this is the only place they're tracked.
     * In non-silent mode, onTriggered() also adds them, but MemoryImprint.set() is idempotent.
     *
     * @param event the event triggering a new build.
     * @param trigger the Gerrit trigger
     * @param job the job for which to cancel builds.
     */
    public void scheduled(ChangeBasedEvent event,
                          GerritTrigger trigger,
                          Job job) {
        IGerritHudsonTriggerConfig serverConfig = getServerConfig(event);
        if (serverConfig == null) {
            // No server config - add event for cancellation tracking
            triggered(event, job);
            return;
        }

        BuildCancellationPolicy serverBuildCurrentPatchesOnly = serverConfig.getBuildCurrentPatchesOnly();
        if (!serverBuildCurrentPatchesOnly.isEnabled()
                || (event instanceof ManualPatchsetCreated
                && !serverBuildCurrentPatchesOnly.isAbortManualPatchsets())) {
            // Policy disabled - add event for cancellation tracking
            triggered(event, job);
            return;
        }

        // Policy enabled - check for outdated events and cancel them
        // cancelOutdatedEvents will add the new event to BuildMemory
        cancelOutdatedEvents(event, serverBuildCurrentPatchesOnly, trigger, job);
    }

    /**
     * Returns all started builds in memory for the event.
     *
     * @param event the event.
     * @return the list of builds, or null if there is no memory.
     */
    public List<Run> getBuilds(GerritTriggeredEvent event) {
        return storage.getBuilds(event);
    }

    /**
     * Records a custom URL for the given build.
     *
     * @param event     the event.
     * @param r         the build that caused the failure.
     * @param customUrl the URL.
     */
    public void setEntryCustomUrl(GerritTriggeredEvent event, Run r, String customUrl) {
        storage.setEntryCustomUrl(event, r, customUrl);
    }

    /**
     * Records the unsuccessful message for the given build.
     *
     * @param event               the event.
     * @param r                   the build that caused the failure.
     * @param unsuccessfulMessage the unsuccessful message
     */
    public void setEntryUnsuccessfulMessage(GerritTriggeredEvent event, Run r, String unsuccessfulMessage) {
        storage.setEntryUnsuccessfulMessage(event, r, unsuccessfulMessage);
    }

    /**
     * Records the failure message for the given build.
     *
     * @param event          the event.
     * @param r              the build that caused the failure.
     * @param failureMessage the failure message
     * @deprecated Use {@link #setEntryUnsuccessfulMessage}
     */
    @Deprecated
    public void setEntryFailureMessage(GerritTriggeredEvent event, Run r, String failureMessage) {
        setEntryUnsuccessfulMessage(event, r, failureMessage);
    }

    /**
     * Removes project from all memory imprints.
     *
     * @param project to be removed.
     */
    public void removeProject(Job project) {
        storage.removeProject(project);
    }

    /**
     * Creates a snapshot clone of the current coordination memory status.
     *
     * @return the report
     */
    @NonNull
    public BuildMemoryReport report() {
        return storage.report();
    }

    /**
     * A holder for all builds triggered by one event.
     */
    public static class MemoryImprint {

        private GerritTriggeredEvent event;
        private List<Entry> list = new ArrayList<Entry>();

        /**
         * Constructor.
         *
         * @param event the event.
         */
        public MemoryImprint(GerritTriggeredEvent event) {
            this.event = event;
        }

        /**
         * Constructor.
         *
         * @param event   the event.
         * @param project the first project.
         */
        public MemoryImprint(GerritTriggeredEvent event, Job project) {
            this.event = event;
            set(project);
        }

        /**
         * The event.
         *
         * @return the event.
         */
        public GerritTriggeredEvent getEvent() {
            return event;
        }

        /**
         * A list of Project-Build tuple entries.
         *
         * @return the memory entries.
         */
        public synchronized Entry[] getEntries() {
            return list.toArray(new Entry[list.size()]);
        }

        /**
         * Gets the internal list of entries.
         * <p>
         * This method is used by storage implementations for operations
         * like report() that need direct access to the list.
         *
         * @return the internal list (not a copy)
         */
        public List<Entry> getEntriesList() {
            return list;
        }

        /**
         * Sets the build to a project or adds the project to the list.
         *
         * @param project the project.
         * @param build   the build.
         */
        public synchronized void set(Job project, Run build) {
            Entry entry = getEntry(project);
            if (entry == null) {
                entry = new Entry(project, build);
                list.add(entry);
            } else {
                entry.setBuild(build);
            }
        }

        /**
         * Adds the project to the list.
         *
         * @param project the project.
         */
        public synchronized void set(Job project) {
            Entry entry = getEntry(project);
            if (entry == null) {
                entry = new Entry(project);
                list.add(entry);
            }
        }

        /**
         * Resets the build info for the project. If the project doesn't exist it would be as if calling {@link
         * #set(hudson.model.Job)}.
         *
         * @param project the project to reset.
         */
        public synchronized void reset(Job project) {
            Entry entry = getEntry(project);
            if (entry == null) {
                entry = new Entry(project);
                list.add(entry);
            } else {
                entry.setBuild(null);
                entry.setBuildCompleted(false);
            }
        }

        /**
         * Removes the specified project from memory.
         * @param project the project to removeProject.
         */
        public synchronized void removeProject(String project) {
            list.removeIf(entry -> entry.isProject(project));
        }

        /**
         * Sets all the values of an entry and adds it if the project has not been added before.
         *
         * @param project        the project
         * @param build          the build
         * @param buildCompleted if the build is finished.
         */
        public synchronized void set(Job project, Run build, boolean buildCompleted) {
            Entry entry = getEntry(project);
            if (entry == null) {
                entry = new Entry(project, build);
                entry.setBuildCompleted(buildCompleted);
                list.add(entry);
            } else {
                if (entry.getBuild() == null) {
                    entry.setBuild(build);
                }
                entry.setBuildCompleted(buildCompleted);
            }
        }

        /**
         * Tells if all builds have a value (not null).
         *
         * @return true if it is so.
         */
        public synchronized boolean isAllBuildsSet() {
            for (Entry entry : list) {
                if (entry.getBuild() == null) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Tells if all builds have Completed.
         *
         * @return true if it is so.
         */
        public synchronized boolean isAllBuildsCompleted() {
            for (Entry entry : list) {
                if (!entry.isBuildCompleted()) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Returns a string describing the projects and builds status in this memory. Good for logging.
         *
         * @return a report.
         */
        public synchronized String getStatusReport() {
            StringBuilder str = new StringBuilder();
            for (Entry entry : list) {
                if (entry == null) {
                    continue;
                }
                Job project = entry.getProject();
                if (project != null) {
                    str.append("  Project/Build: [").append(project.getName()).append("]");
                    str.append(": [#");
                    Run build = entry.getBuild();
                    if (build != null) {
                        str.append(build.getNumber());
                        str.append(": ").append(build.getResult());
                    } else {
                        str.append("XX: NULL");
                    }
                    str.append("] Completed: ").append(entry.isBuildCompleted());
                } else {
                    str.append("  Project/Build: MISSING PROJECT!");
                }
                str.append("\n");
            }
            return str.toString();
        }

        /**
         * Searches the internal list for an entry with the specified project.
         *
         * @param project the project.
         * @return the entry or null if nothing is found.
         */
        public Entry getEntry(@NonNull Job project) {
            for (Entry entry : list) {
                if (entry != null && project.equals(entry.getProject())) {
                    return entry;
                }
            }
            return null;
        }

        /**
         * Gets the statistics about builds started.
         *
         * @return the stats.
         */
        public synchronized BuildsStartedStats getBuildsStartedStats() {
            int started = 0;
            for (Entry entry : list) {
                if (entry.getBuild() != null) {
                    started++;
                }
            }
            return new BuildsStartedStats(event, list.size(), started);
        }

        /**
         * If all entry's results are configured to be skipped.
         *
         * @return true if so.
         * @see #wereAllBuildsSuccessful()
         */
        public synchronized boolean areAllBuildResultsSkipped() {
            for (Entry entry : list) {
                if (entry == null) {
                    continue;
                }
                Run build = entry.getBuild();
                if (build == null) {
                    return false;
                } else if (!entry.isBuildCompleted()) {
                    return false;
                }
                Result buildResult = build.getResult();
                GerritTrigger trigger = GerritTrigger.getTrigger(entry.getProject());
                if (!shouldSkip(trigger.getSkipVote(), buildResult)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Tells if all builds in the memory were successful.
         *
         * @return true if it is so, false if not all builds have started or not completed or have any different result
         *         than {@link Result#SUCCESS}.
         */
        public synchronized boolean wereAllBuildsSuccessful() {
            if (areAllBuildResultsSkipped()) {
                for (Entry entry : list) {
                    if (entry == null) {
                        continue;
                    }
                    Run build = entry.getBuild();
                    if (build == null) {
                        return false;
                    } else if (!entry.isBuildCompleted()) {
                        return false;
                    }
                    Result buildResult = build.getResult();
                    if (buildResult != Result.SUCCESS) {
                        return false;
                    }
                }
            } else {
                for (Entry entry : list) {
                    if (entry == null) {
                        continue;
                    }
                    Run build = entry.getBuild();
                    if (build == null) {
                        return false;
                    } else if (!entry.isBuildCompleted()) {
                        return false;
                    }
                    Result buildResult = build.getResult();
                    if (buildResult != Result.SUCCESS) {
                        GerritTrigger trigger = GerritTrigger.getTrigger(entry.getProject());
                        if (!shouldSkip(trigger.getSkipVote(), buildResult)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        /**
         * Returns if any started and completed build has the result {@link Result#FAILURE}.
         *
         * @return true if it is so.
         */
        public synchronized boolean wereAnyBuildsFailed() {
            for (Entry entry : list) {
                if (entry == null) {
                    continue;
                }
                Run build = entry.getBuild();
                if (build != null && entry.isBuildCompleted()
                        && build.getResult() == Result.FAILURE) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns if any started and completed build has the result {@link Result#UNSTABLE}.
         *
         * @return true if it is so.
         */
        public synchronized boolean wereAnyBuildsUnstable() {
            for (Entry entry : list) {
                if (entry == null) {
                    continue;
                }
                Run build = entry.getBuild();
                if (build != null && entry.isBuildCompleted()
                        && build.getResult() == Result.UNSTABLE) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Tells if all builds in the memory were not built.
         *
         * @return true if it is so, false if not all builds have started or not completed or have any different result
         *         than {@link Result#NOT_BUILT}.
         */
        public synchronized boolean wereAllBuildsNotBuilt() {
            for (Entry entry : list) {
                if (entry == null) {
                    continue;
                }
                if (entry.isCancelled()) {
                    continue;
                }
                Run build = entry.getBuild();
                if (build == null) {
                    return false;
                } else if (!entry.isBuildCompleted()) {
                    return false;
                }
                Result buildResult = build.getResult();
                if (buildResult != Result.NOT_BUILT) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Returns if any started and completed build has the result {@link Result#ABORTED}.
         *
         * @return true if it is so.
         */
        public synchronized boolean wereAnyBuildsAborted() {
            for (Entry entry : list) {
                if (entry == null) {
                    continue;
                }
                Run build = entry.getBuild();
                if (build != null && entry.isBuildCompleted()
                        && build.getResult() == Result.ABORTED) {
                    return true;
                }
            }
            return false;
        }

        //CS IGNORE FinalClass FOR NEXT 5 LINES. REASON: Testability.

        /**
         * A project-build entry in the list of a MemoryImprint.
         */
        public static class Entry implements Cloneable {

            private String project;
            private String build;
            private boolean buildCompleted;
            private boolean cancelled;
            private String customUrl;
            private String unsuccessfulMessage;
            private final long triggeredTimestamp;
            private Long completedTimestamp = null;
            private Long startedTimestamp = null;

            /**
             * Constructor.
             *
             * @param project the project
             * @param build   the build.
             */
            private Entry(Job project, Run build) {
                this.project = project.getFullName();
                this.build = build.getId();
                this.startedTimestamp = System.currentTimeMillis();
                this.triggeredTimestamp = System.currentTimeMillis();
                buildCompleted = false;
            }

            /**
             * Constructor.
             *
             * @param project the project.
             */
            private Entry(Job project) {
                this.project = project.getFullName();
                buildCompleted = false;
                cancelled = false;
                this.triggeredTimestamp = System.currentTimeMillis();
            }

            /**
             * Constructor to create a copy of with the contents of an existing Entry.
             *
             * @param copy the entry to copy.
             * @see #clone()
             */
            public Entry(Entry copy) {
                this.project = copy.project;
                this.build = copy.build;
                this.buildCompleted = copy.buildCompleted;
                this.unsuccessfulMessage = copy.unsuccessfulMessage;
                this.triggeredTimestamp = copy.triggeredTimestamp;
                this.completedTimestamp = copy.completedTimestamp;
                this.startedTimestamp = copy.startedTimestamp;
                this.customUrl = copy.customUrl;
                this.cancelled = copy.cancelled;
            }

            @Override
            public Entry clone() {
                return new Entry(this);
            }

            /**
             * The Project.
             *
             * @return the project.
             */
            @CheckForNull
            public Job getProject() {
                Jenkins jenkins = Jenkins.getInstanceOrNull();
                if (jenkins != null) {
                    return jenkins.getItemByFullName(project, Job.class);
                } else {
                    return null;
                }
            }

            /**
             * The build of a project.
             *
             * @return the build.
             */
            @CheckForNull
            public Run getBuild() {
                if (build != null && project != null) {
                    Job p = getProject();
                    if (p != null) {
                        return p.getBuild(build);
                    }
                }

                return null;
            }

            /**
             * The build of a project.
             *
             * @param build the build.
             */
            private void setBuild(Run build) {
                if (build != null) {
                    this.build = build.getId();
                    this.startedTimestamp = System.currentTimeMillis();
                } else {
                    this.build = null;
                }
            }

            /**
             * Sets the URL to post for an entry.
             *
             * @param customUrl the URL.
             */
            public void setCustomUrl(String customUrl) {
                this.customUrl = customUrl;
            }

            /**
             * Gets the URL to post for an entry.
             *
             * @return the URL.
             */
            public String getCustomUrl() {
                return this.customUrl;
            }

            /**
             * Sets the unsuccessful message for an entry.
             *
             * @param unsuccessfulMessage the message.
             */
            public void setUnsuccessfulMessage(String unsuccessfulMessage) {
                this.unsuccessfulMessage = unsuccessfulMessage;
            }

            /**
             * Gets the unsuccessful message for an entry.
             *
             * @return the message.
             */
            public String getUnsuccessfulMessage() {
                return this.unsuccessfulMessage;
            }

            /**
             * If the build is completed.
             *
             * @return true if the build is completed.
             */
            public boolean isBuildCompleted() {
                return buildCompleted;
            }

            /**
             * If the build is completed.
             *
             * @param buildCompleted true if the build is completed.
             */
            public void setBuildCompleted(boolean buildCompleted) {
                this.buildCompleted = buildCompleted;
                if (buildCompleted) {
                    this.completedTimestamp = System.currentTimeMillis();
                }
            }

            /**
             * If the build was cancelled.
             * @return true if the build was cancelled while in the Queue
             */
            public boolean isCancelled() {
                return cancelled;
            }

            /**
             * If the build was cancelled before if left the queue.
             *
             * @param cancelled true if the build was cancelled while in the queue.
             */
            public void setCancelled(boolean cancelled) {
                this.cancelled = cancelled;
            }

            /**
             * The timestamp when {@link #setBuildCompleted(boolean)} was set to true.
             * <code>null</code> indicates not completed yet.
             *
             * @return the timestamp the build completed.
             */
            @CheckForNull
            public Long getCompletedTimestamp() {
                return completedTimestamp;
            }

            /**
             * The timestamp when {@link #setBuild(Run)} was called with a non null value.
             * <code>null</code> indicates not started yet.
             *
             * @return the timestamp when the build was started.
             */
            @CheckForNull
            public Long getStartedTimestamp() {
                return startedTimestamp;
            }

            /**
             * The timestamp when this entry was created. i.e. when it was triggered.
             *
             * @return the timestamp when the job was triggered.
             */
            public long getTriggeredTimestamp() {
                return triggeredTimestamp;
            }

            @Override
            public String toString() {
                String s = "";
                Run theBuild = getBuild();
                if (theBuild != null) {
                    s = theBuild.toString();
                } else {
                    Job theProject = getProject();
                    if (theProject != null) {
                        s = theProject.getName();
                    }
                }
                if (isBuildCompleted()) {
                    s += " (completed)";
                }
                return s;
            }

            /**
             * If the provided project is the same as this entry is referencing.
             * It does so by checking the fullName for equality.
             *
             * @param otherName the other project name to check
             * @return true if so.
             * @see #getProject()
             */
            public boolean isProject(String otherName) {
                if (this.project != null && otherName != null) {
                    return this.project.equals(otherName);
                } else {
                    return this.project == null && otherName == null;
                }
            }

            /**
             * If the provided project is the same as this entry is referencing.
             * It does so by checking the fullName for equality.
             *
             * @param other the other project to check
             * @return true if so.
             * @deprecated use {@link #isProject(String)} instead
             */
            @Deprecated
            public boolean isProject(Job other) {
                return isProject(other.getFullName());
            }
        }
    }
}
