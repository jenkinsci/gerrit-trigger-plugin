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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint.Entry;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

/**
 * Keeps track of what builds have been triggered and if all builds are done for specific events.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class BuildMemory {

    /**
     * Compares GerritTriggeredEvents using the Object.hashCode() method.
     * This ensures that every event received from Gerrit is kept track of individually.
     *
     * @author James E. Blair &lt;jeblair@hp.com&gt;
     *
     */
    private class GerritTriggeredEventComparator implements Comparator<GerritTriggeredEvent> {
        @Override
        public int compare(GerritTriggeredEvent o1, GerritTriggeredEvent o2) {
            return new Integer(((java.lang.Object)o1).hashCode()).compareTo(
                    new Integer(((java.lang.Object)o2).hashCode()));
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
    public synchronized void completed(GerritTriggeredEvent event, AbstractBuild build) {
        MemoryImprint pb = memory.get(event);
        if (pb == null) {
            //Shoudn't happen but just in case, keep the memory.
            pb = new MemoryImprint(event);
            memory.put(event, pb);
        }
        pb.set(build.getProject(), build, true);
    }

    /**
     * Sets the memory that a build has started for an event.
     *
     * @param event the event.
     * @param build the build.
     */
    public synchronized void started(GerritTriggeredEvent event, AbstractBuild build) {
        MemoryImprint pb = memory.get(event);
        if (pb == null) {
            //A build should not start for a job that hasn't been registered. Keep the memory anyway.
            pb = new MemoryImprint(event);
            logger.warn("Build started without being registered first.");
            memory.put(event, pb);
        }
        pb.set(build.getProject(), build);
    }

    /**
     * Adds a new memory about a build that has been/will be triggered.
     *
     * @param event   the event that triggered it.
     * @param project the project that was triggered.
     */
    public synchronized void triggered(GerritTriggeredEvent event, AbstractProject project) {
        MemoryImprint pb = memory.get(event);
        if (pb == null) {
            pb = new MemoryImprint(event);
            memory.put(event, pb);
        }
        pb.set(project);
    }

    /**
     * Adds a new memory about a build that has been retriggered.
     * If there is an active memory about the provided event, then the project is reset with no build info.
     * Otherwise the memory is recreated from the list of other builds and their result.
     *
     * @param event       the event to be retriggered.
     * @param project     the project that has been retriggered.
     * @param otherBuilds the list of other builds that was in the "old" memory.
     */
    public synchronized void retriggered(
            GerritTriggeredEvent event,
            AbstractProject project,
            List<AbstractBuild> otherBuilds) {
        MemoryImprint pb = memory.get(event);
        if (pb == null) {
            pb = new MemoryImprint(event);
            memory.put(event, pb);
            if (otherBuilds != null) {
                //It is a new memory so it wasn't building, let's populate with old build info
                for (AbstractBuild build : otherBuilds) {
                    pb.set(build.getProject(), build, !build.isBuilding());
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
     * Updates the {@link TriggerContext} for the event.
     * The cause and build is the "focal point" for the update, but all memory entities will be updated,
     * but only the current context will be {@link TriggerContext#setThisBuild(hudson.model.AbstractBuild)}updated.
     *
     * @param cause the cause.
     * @param r     the build the cause is in.
     */
    public synchronized void updateTriggerContext(GerritCause cause, AbstractBuild r) {
        MemoryImprint imprint = getMemoryImprint(cause.getEvent());
        TriggerContext context = cause.getContext();
        context.setThisBuild(r);
        for (MemoryImprint.Entry entry : imprint.getEntries()) {
            if (entry.getBuild() != null && !entry.getBuild().equals(r)) {
                context.addOtherBuild(entry.getBuild());
                updateTriggerContext(entry, imprint);
            } else if (entry.getBuild() == null && !entry.getProject().equals(r.getProject())) {
                context.addOtherProject(entry.getProject());
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
    private synchronized void updateTriggerContext(Entry entryToUpdate, MemoryImprint imprint) {
        if (entryToUpdate.getBuild() != null) {
            GerritCause cause = (GerritCause)entryToUpdate.getBuild().getCause(GerritCause.class);
            if (cause != null) {
                TriggerContext context = cause.getContext();
                for (MemoryImprint.Entry ent : imprint.getEntries()) {
                    if (ent.getBuild() != null && !ent.getBuild().equals(entryToUpdate.getBuild())) {
                        context.addOtherBuild(ent.getBuild());
                    } else if (ent.getBuild() == null && !ent.getProject().equals(entryToUpdate.getProject())) {
                        context.addOtherProject(ent.getProject());
                    }
                }
                if (!entryToUpdate.getBuild().hasntStartedYet() && !entryToUpdate.getBuild().isBuilding()) {
                    try {
                        entryToUpdate.getBuild().save();
                    } catch (IOException ex) {
                        logger.error("Could not save state for build " + entryToUpdate.getBuild(), ex);
                    }
                }
            }
        }
    }

    /**
     * Checks in memory if the project is building the event.
     *
     * @param event   the event.
     * @param project the project.
     * @return true if so.
     */
    public synchronized boolean isBuilding(GerritTriggeredEvent event, AbstractProject project) {
        MemoryImprint pb = memory.get(event);
        if (pb == null) {
            return false;
        } else {
            for (Entry entry : pb.getEntries()) {
                if (entry.getProject().equals(project)) {
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
    public synchronized List<AbstractBuild> getBuilds(GerritTriggeredEvent event) {
        MemoryImprint pb = memory.get(event);
        if (pb != null) {
            List<AbstractBuild> list = new LinkedList<AbstractBuild>();
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
        public MemoryImprint(GerritTriggeredEvent event, AbstractProject project) {
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
        protected synchronized void set(AbstractProject project, AbstractBuild build) {
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
        protected synchronized void set(AbstractProject project) {
            Entry entry = getEntry(project);
            if (entry == null) {
                entry = new Entry(project);
                list.add(entry);
            }
        }

        /**
         * Resets the build info for the project.
         * If the project doesn't exist it would be as if calling {@link #set(hudson.model.AbstractProject)}.
         *
         * @param project the project to reset.
         */
        protected synchronized void reset(AbstractProject project) {
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
        private synchronized void set(AbstractProject project, AbstractBuild build, boolean buildCompleted) {
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
         * Returns a string describing the projects and builds status in this memory.
         * Good for logging.
         *
         * @return a report.
         */
        public synchronized String getStatusReport() {
            StringBuilder str = new StringBuilder("");
            for (Entry entry : list) {
                if (entry.getProject() != null) {
                    str.append("  Project/Build: [").append(entry.getProject().getName()).append("]");
                    str.append(": [#");
                    if (entry.getBuild() != null) {
                        str.append(entry.getBuild().getNumber());
                        str.append(": ").append(entry.getBuild().getResult());
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
         * @return the entry or null if nothis is found.
         */
        private Entry getEntry(AbstractProject project) {
            for (Entry entry : list) {
                if (entry.getProject().equals(project)) {
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
         * Tells if all builds in the memory were successful.
         *
         * @return true if it is so, false if not all builds have started or not completed or have any different
         *         result than {@link Result#SUCCESS}.
         */
        public synchronized boolean whereAllBuildsSuccessful() {
            for (Entry entry : list) {
                if (entry.getBuild() == null) {
                    return false;
                } else if (!entry.isBuildCompleted()) {
                    return false;
                }
                if (entry.getBuild().getResult() != Result.SUCCESS) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Returns if any started and completed build has the result {@link Result#FAILURE}.
         *
         * @return true if it is so.
         */
        public synchronized boolean whereAnyBuildsFailed() {
            for (Entry entry : list) {
                if (entry.getBuild() != null && entry.isBuildCompleted()
                        && entry.getBuild().getResult() == Result.FAILURE) {
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
        public synchronized boolean whereAnyBuildsUnstable() {
            for (Entry entry : list) {
                if (entry.getBuild() != null && entry.isBuildCompleted()
                        && entry.getBuild().getResult() == Result.UNSTABLE) {
                    return true;
                }
            }
            return false;
        }

        //CS IGNORE FinalClass FOR NEXT 5 LINES. REASON: Testability.

        /**
         * A project-build entry in the list of a MemoryImprint.
         */
        public static class Entry {

            private AbstractProject project;
            private AbstractBuild build;
            private boolean buildCompleted;

            /**
             * Constructor.
             *
             * @param project the project
             * @param build   the build.
             */
            private Entry(AbstractProject project, AbstractBuild build) {
                this.project = project;
                this.build = build;
                buildCompleted = false;
            }

            /**
             * Constructor.
             *
             * @param project the project.
             */
            private Entry(AbstractProject project) {
                this.project = project;
                buildCompleted = false;
            }

            /**
             * The Project.
             *
             * @return the project.
             */
            public AbstractProject getProject() {
                return project;
            }

            /**
             * The build of a project.
             *
             * @return the build.
             */
            public AbstractBuild getBuild() {
                return build;
            }

            /**
             * The build of a project.
             *
             * @param build the build.
             */
            private void setBuild(AbstractBuild build) {
                this.build = build;
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
        }
    }
}
