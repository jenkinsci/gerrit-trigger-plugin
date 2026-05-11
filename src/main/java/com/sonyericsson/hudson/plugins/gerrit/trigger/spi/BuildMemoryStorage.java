/*
 *  The MIT License
 *
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.spi;

import com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.BuildMemoryReport;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import hudson.model.Job;
import hudson.model.Run;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;

/**
 * Storage interface for BuildMemory operations.
 * <p>
 * This interface defines all storage operations needed by BuildMemory to track builds
 * triggered by Gerrit events. Implementations provide different storage backends, for example:
 * <ul>
 *   <li>{@link com.sonyericsson.hudson.plugins.gerrit.trigger.storage.LocalBuildMemoryStorage}: TreeMap-based storage (standalone Jenkins)</li>
 * </ul>
 * <p>
 * Implementations are discovered via Jenkins Extension Points pattern using
 * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.spi.GerritTriggerModeProvider}.
 *
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.spi.GerritTriggerModeProvider
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory
 */
public interface BuildMemoryStorage {

    /**
     * Gets the memory imprint for a specific event.
     * <p>
     * The memory imprint contains all builds triggered by this event,
     * including their status and completion state.
     *
     * @param event the event to look up
     * @return the memory imprint, or null if the event is not being tracked
     */
    @CheckForNull
    MemoryImprint getMemoryImprint(@NonNull GerritTriggeredEvent event);

    /**
     * Records that a build has been triggered for an event.
     * <p>
     * This is the first lifecycle event when a Gerrit event triggers a Jenkins job.
     * If the event is already being tracked, the project is added to its memory imprint.
     *
     * @param event the event that triggered the build
     * @param project the project that was triggered
     */
    void triggered(@NonNull GerritTriggeredEvent event, @NonNull Job project);

    /**
     * Records that a build has started for an event.
     * <p>
     * Called when the build leaves the queue and begins execution.
     *
     * @param event the event
     * @param build the build that started
     */
    void started(@NonNull GerritTriggeredEvent event, @NonNull Run build);

    /**
     * Records that a build has completed for an event.
     * <p>
     * Called when the build finishes (success, failure, or aborted).
     *
     * @param event the event
     * @param build the build that completed
     */
    void completed(@NonNull GerritTriggeredEvent event, @NonNull Run build);

    /**
     * Records that a build has been retriggered.
     * <p>
     * When a user manually retriggers a build, we need to reset the build info
     * while preserving information about other builds for the same event.
     *
     * @param event the event being retriggered
     * @param project the project that has been retriggered
     * @param otherBuilds the list of other builds from the "old" memory, or null
     */
    void retriggered(@NonNull GerritTriggeredEvent event, @NonNull Job project,
                     @CheckForNull List<Run> otherBuilds);

    /**
     * Records that a build was cancelled while in the queue.
     * <p>
     * Marks the project as both cancelled and completed so it doesn't block
     * feedback notification.
     *
     * @param event the event
     * @param project the project that was cancelled
     */
    void cancelled(@NonNull GerritTriggeredEvent event, @NonNull Job project);

    /**
     * Removes the memory for an event.
     * <p>
     * Called after feedback has been sent to Gerrit to clean up the memory.
     *
     * @param event the event to forget
     */
    void forget(@NonNull GerritTriggeredEvent event);

    /**
     * Removes a project from all memory imprints.
     * <p>
     * Called when a project is deleted or renamed in Jenkins.
     *
     * @param project the project to remove from all tracked events
     */
    void removeProject(@NonNull Job project);

    /**
     * Checks if all builds have completed for an event.
     *
     * @param event the event to check
     * @return true if all builds for this event have completed
     */
    boolean isAllBuildsCompleted(@NonNull GerritTriggeredEvent event);

    /**
     * Checks if all builds have started for an event.
     * <p>
     * A build is considered "started" when it has a Run object (left the queue).
     *
     * @param event the event to check
     * @return true if all triggered builds have started
     */
    boolean isAllBuildsStarted(@NonNull GerritTriggeredEvent event);

    /**
     * Gets the statistics of started builds for an event.
     *
     * @param event the event
     * @return the statistics, or null if the event is not tracked
     */
    @CheckForNull
    BuildsStartedStats getBuildsStartedStats(@NonNull GerritTriggeredEvent event);

    /**
     * Returns a status report for the given event.
     * <p>
     * The report includes information about all projects/builds triggered by the event.
     *
     * @param event the event
     * @return the status report as a string, or null if not found
     */
    @CheckForNull
    String getStatusReport(@NonNull GerritTriggeredEvent event);

    /**
     * Checks if a project has been triggered for an event.
     *
     * @param event the event
     * @param project the project to check
     * @return true if the project was triggered by this event
     */
    boolean isTriggered(@NonNull GerritTriggeredEvent event, @NonNull Job project);

    /**
     * Checks if a project is currently building an event.
     * <p>
     * A build is considered "building" if it has started but not yet completed.
     *
     * @param event the event
     * @param project the project to check
     * @return true if the project is actively building this event
     */
    boolean isBuilding(@NonNull GerritTriggeredEvent event, @NonNull Job project);

    /**
     * Checks if an event exists in memory.
     * <p>
     * This is a simple existence check - it doesn't verify if builds are still running.
     *
     * @param event the event to look for
     * @return true if the event is being tracked
     */
    boolean isBuilding(@NonNull GerritTriggeredEvent event);

    /**
     * Returns all started builds for an event.
     * <p>
     * Only includes builds that have Run objects (have started execution).
     *
     * @param event the event
     * @return list of builds, or null if the event is not tracked
     */
    @CheckForNull
    List<Run> getBuilds(@NonNull GerritTriggeredEvent event);

    /**
     * Records a custom URL for a build entry.
     * <p>
     * Used to override the default Jenkins build URL in Gerrit feedback.
     *
     * @param event the event
     * @param run the build
     * @param customUrl the custom URL to use, or null to clear
     */
    void setEntryCustomUrl(@NonNull GerritTriggeredEvent event, @NonNull Run run,
                           @CheckForNull String customUrl);

    /**
     * Records an unsuccessful message for a build entry.
     * <p>
     * Used to provide additional context in Gerrit feedback when a build fails.
     *
     * @param event the event
     * @param run the build
     * @param unsuccessfulMessage the message, or null to clear
     */
    void setEntryUnsuccessfulMessage(@NonNull GerritTriggeredEvent event, @NonNull Run run,
                                     @CheckForNull String unsuccessfulMessage);

    /**
     * Creates a snapshot of the current memory state.
     * <p>
     * Used for diagnostics and debugging. The returned report is a clone,
     * safe to use without affecting the actual memory.
     *
     * @return a snapshot report of all tracked events and builds
     */
    @NonNull
    BuildMemoryReport report();

    /**
     * Gets all events currently in memory.
     * <p>
     * This method is primarily used by the build cancellation logic to iterate
     * over all tracked events and find outdated builds to cancel.
     * <p>
     * <strong>Important:</strong> Implementations should return a snapshot/copy
     * to avoid concurrent modification issues when the caller iterates over the map.
     *
     * @return a map of all events to their memory imprints (should be a copy/snapshot)
     */
    @NonNull
    Map<GerritTriggeredEvent, MemoryImprint> getAllEvents();
}
