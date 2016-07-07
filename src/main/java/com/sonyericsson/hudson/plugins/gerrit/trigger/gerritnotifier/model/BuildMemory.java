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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model;

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint.Entry;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import jenkins.model.Jenkins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import static com.sonyericsson.hudson.plugins.gerrit.trigger.utils.Logic.shouldSkip;

/**
 * Keeps track of what builds have been triggered and if all builds are done for specific events.
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
    static class GerritTriggeredEventComparator implements Comparator<GerritTriggeredEvent> {
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
            return Integer.valueOf(o1.hashCode()).compareTo(o2.hashCode());
        }
    }

    private TreeMap<GerritTriggeredEvent, MemoryImprint> memory =
            new TreeMap<GerritTriggeredEvent, MemoryImprint>(
                    new GerritTriggeredEventComparator());
    private static final Logger logger = LoggerFactory.getLogger(BuildMemory.class);

    /**
     * Gets the memory of a specific event.
     *
     * @param event the event.
     * @return the memory.
     */
    public synchronized MemoryImprint getMemoryImprint(GerritTriggeredEvent event) {
        return memory.get(event);
    }

    /**
     * Tells if all triggered builds have started for a specific memory imprint.
     *
     * @param event the event.
     * @return true if it is so.
     */
    public synchronized boolean isAllBuildsCompleted(GerritTriggeredEvent event) {
        MemoryImprint pb = memory.get(event);
        if (pb != null) {
            return pb.isAllBuildsCompleted();
        } else {
            return false;
        }
    }

    /**
     * Gets the statistics of started builds for a specific memory imprint.
     *
     * @param event the event.
     * @return the statistics.
     */
    public synchronized BuildsStartedStats getBuildsStartedStats(GerritTriggeredEvent event) {
        MemoryImprint pb = memory.get(event);
        if (pb != null) {
            return pb.getBuildsStartedStats();
        } else {
            return null;
        }
    }

    /**
     * Returns the status report for the given MemoryImprint.
     *
     * @param event the event.
     * @return the status as it is now.
     *
     * @see MemoryImprint#getStatusReport()
     */
    public synchronized String getStatusReport(GerritTriggeredEvent event) {
        MemoryImprint pb = memory.get(event);
        if (pb != null) {
            return pb.getStatusReport();
        } else {
            return null;
        }
    }

    /**
     * Tells if all triggered builds have started for a specific memory imprint.
     *
     * @param event the event.
     * @return true if it is so.
     */
    public synchronized boolean isAllBuildsStarted(GerritTriggeredEvent event) {
        MemoryImprint pb = memory.get(event);
        if (pb != null) {
            return pb.isAllBuildsSet();
        } else {
            return false;
        }
    }

    /**
     * Sets the memory that a build is completed for an event.
     *
     * @param event the event
     * @param build the build.
     */
    public synchronized void completed(GerritTriggeredEvent event, Run build) {
        MemoryImprint pb = memory.get(event);
        if (pb == null) {
            //Shoudn't happen but just in case, keep the memory.
            pb = new MemoryImprint(event);
            memory.put(event, pb);
        }
        pb.set(build.getParent(), build, true);
    }

    /**
     * Sets the memory that a build has started for an event.
     *
     * @param event the event.
     * @param build the build.
     */
    public synchronized void started(GerritTriggeredEvent event, Run build) {
        MemoryImprint pb = memory.get(event);
        if (pb == null) {
            //A build should not start for a job that hasn't been registered. Keep the memory anyway.
            pb = new MemoryImprint(event);
            logger.warn("Build started without being registered first.");
            memory.put(event, pb);
        }
        pb.set(build.getParent(), build);
    }

    /**
     * Adds a new memory about a build that has been/will be triggered.
     *
     * @param event   the event that triggered it.
     * @param project the project that was triggered.
     */
    public synchronized void triggered(GerritTriggeredEvent event, Job project) {
        MemoryImprint pb = memory.get(event);
        if (pb == null) {
            pb = new MemoryImprint(event);
            memory.put(event, pb);
        }
        pb.set(project);
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
    public synchronized void retriggered(
            GerritTriggeredEvent event,
            Job project,
            List<Run> otherBuilds) {
        MemoryImprint pb = memory.get(event);
        if (pb == null) {
            pb = new MemoryImprint(event);
            memory.put(event, pb);
            if (otherBuilds != null) {
                //It is a new memory so it wasn't building, let's populate with old build info
                for (Run build : otherBuilds) {
                    pb.set(build.getParent(), build, !build.isBuilding());
                }
            }
        }
        pb.reset(project);
    }

    /**
     * Removes the memory for the event.
     *
     * @param event the event.
     */
    public synchronized void forget(GerritTriggeredEvent event) {
        memory.remove(event);
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
    private synchronized void updateTriggerContext(@Nonnull Entry entryToUpdate, @Nonnull MemoryImprint imprint) {
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
    public synchronized boolean isTriggered(@Nonnull GerritTriggeredEvent event, @Nonnull Job project) {
        MemoryImprint pb = memory.get(event);
        if (pb == null) {
            return false;
        } else {
            for (Entry entry : pb.getEntries()) {
                if (project.equals(entry.getProject())) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Checks in memory if the project is building the event.
     *
     * @param event   the event.
     * @param project the project.
     * @return true if so.
     */
    public synchronized boolean isBuilding(GerritTriggeredEvent event, Job project) {
        MemoryImprint pb = memory.get(event);
        if (pb == null) {
            return false;
        } else {
            for (Entry entry : pb.getEntries()) {
                if (entry.isProject(project)) {
                    if (entry.getBuild() != null) {
                        return !entry.isBuildCompleted();
                    } else {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * Checks if the provided event exists in this memory.
     *
     * @param event the event to look for.
     * @return true if so.
     */
    public synchronized boolean isBuilding(GerritTriggeredEvent event) {
        MemoryImprint pb = memory.get(event);
        return pb != null;
    }

    /**
     * Returns all started builds in memory for the event.
     *
     * @param event the event.
     * @return the list of builds, or null if there is no memory.
     */
    public synchronized List<Run> getBuilds(GerritTriggeredEvent event) {
        MemoryImprint pb = memory.get(event);
        if (pb != null) {
            List<Run> list = new LinkedList<Run>();
            for (Entry entry : pb.getEntries()) {
                if (entry.getBuild() != null) {
                    list.add(entry.getBuild());
                }
            }
            return list;
        } else {
            return null;
        }
    }

    /**
     * Records a custom URL for the given build.
     *
     * @param event     the event.
     * @param r         the build that caused the failure.
     * @param customUrl the URL.
     */
    public void setEntryCustomUrl(GerritTriggeredEvent event, Run r, String customUrl) {
        MemoryImprint pb = getMemoryImprint(event);

        if (pb != null) {
            Entry entry = pb.getEntry(r.getParent());

            if (entry != null) {
                logger.trace("Recording custom URL for {}: {}", event, customUrl);
                entry.setCustomUrl(customUrl);
            }
        }
    }

    /**
     * Records the unsuccessful message for the given build.
     *
     * @param event               the event.
     * @param r                   the build that caused the failure.
     * @param unsuccessfulMessage the unsuccessful message
     */
    public void setEntryUnsuccessfulMessage(GerritTriggeredEvent event, Run r, String unsuccessfulMessage) {
        MemoryImprint pb = getMemoryImprint(event);

        if (pb != null) {
            Entry entry = pb.getEntry(r.getParent());

            if (entry != null) {
                logger.trace("Recording unsuccessful message for {}: {}", event, unsuccessfulMessage);
                entry.setUnsuccessfulMessage(unsuccessfulMessage);
            }
        }
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
         * Sets the build to a project or adds the project to the list.
         *
         * @param project the project.
         * @param build   the build.
         */
        protected synchronized void set(Job project, Run build) {
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
        protected synchronized void set(Job project) {
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
        protected synchronized void reset(Job project) {
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
         * Sets all the values of an entry and adds it if the project has not been added before.
         *
         * @param project        the project
         * @param build          the build
         * @param buildCompleted if the build is finished.
         */
        private synchronized void set(Job project, Run build, boolean buildCompleted) {
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
            StringBuilder str = new StringBuilder("");
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
        private Entry getEntry(@Nonnull Job project) {
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

        //CS IGNORE FinalClass FOR NEXT 5 LINES. REASON: Testability.

        /**
         * A project-build entry in the list of a MemoryImprint.
         */
        public static class Entry {

            private String project;
            private String build;
            private boolean buildCompleted;
            private String customUrl;
            private String unsuccessfulMessage;

            /**
             * Constructor.
             *
             * @param project the project
             * @param build   the build.
             */
            private Entry(Job project, Run build) {
                this.project = project.getFullName();
                this.build = build.getId();
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
            }

            /**
             * The Project.
             *
             * @return the project.
             */
            @CheckForNull
            @WithBridgeMethods(AbstractProject.class)
            public Job getProject() {
                Jenkins jenkins = Jenkins.getInstance();
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
            @WithBridgeMethods(AbstractBuild.class)
            public Run getBuild() {
                Job p = getProject();
                if (p != null && build != null) {
                    return p.getBuild(build);
                } else {
                    return null;
                }
            }

            /**
             * The build of a project.
             *
             * @param build the build.
             */
            private void setBuild(Run build) {
                if (build != null) {
                    this.build = build.getId();
                } else {
                    this.build = null;
                }
            }

            /**
             * Sets the URL to post for an entry.
             *
             * @param customUrl the URL.
             */
            private void setCustomUrl(String customUrl) {
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
            private void setUnsuccessfulMessage(String unsuccessfulMessage) {
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
            private void setBuildCompleted(boolean buildCompleted) {
                this.buildCompleted = buildCompleted;
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
             * @param other the other project to check
             * @return true if so.
             * @see #getProject()
             */
            public boolean isProject(Job other) {
                if (this.project != null && other != null) {
                    return this.project.equals(other.getFullName());
                } else {
                    return this.project == null && other == null;
                }
            }
        }
    }
}
