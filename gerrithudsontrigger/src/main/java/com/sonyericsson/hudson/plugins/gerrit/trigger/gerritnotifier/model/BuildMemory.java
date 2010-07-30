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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint.Entry;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Result;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps track of what builds has been triggered and if all builds are done for specific events.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class BuildMemory {

    private TreeMap<PatchSetKey, MemoryImprint> memory = new TreeMap<PatchSetKey, MemoryImprint>();
    private static final Logger logger = LoggerFactory.getLogger(BuildMemory.class);

    /**
     * Gets the memory of a specific key.
     * @param key the key
     * @return the memory.
     */
    public synchronized MemoryImprint getMemoryImprint(PatchSetKey key) {
        return memory.get(key);
    }

    /**
     * Tells if all triggered builds has started for a specific event.
     * This is a bit slower than
     * {@link #isAllBuildsCompleted(PatchSetKey)}
     * since an internal key needs to be created.
     * @param event the event.
     * @return true if it is so.
     */
    public synchronized boolean isAllBuildsCompleted(PatchsetCreated event) {
        PatchSetKey key = createKey(event);
        return isAllBuildsCompleted(key);
    }

    /**
     * Tells if all triggered builds has started for a specific memory imprint.
     * @param key the key to the memory.
     * @return true if it is so.
     */
    public synchronized boolean isAllBuildsCompleted(PatchSetKey key) {
        MemoryImprint pb = memory.get(key);
        if (pb != null) {
            return pb.isAllBuildsCompleted();
        } else {
            return false;
        }
    }

    /**
     * Gets the statistics of started builds for a specific memory imprint.
     * @param key the memory key.
     * @return the statistics.
     */
    public synchronized BuildsStartedStats getBuildsStartedStats(PatchSetKey key) {
        MemoryImprint pb = memory.get(key);
        if (pb != null) {
            return pb.getBuildsStartedStats();
        } else {
            return null;
        }
    }

    /**
     * Returns the status report for the given MemoryImprint.
     * @param key the key to the memory.
     * @return the status as it is now.
     * @see MemoryImprint#getStatusReport()
     */
    public synchronized String getStatusReport(PatchSetKey key) {
        MemoryImprint pb = memory.get(key);
        if (pb != null) {
            return pb.getStatusReport();
        } else {
            return null;
        }
    }

    /**
     * Tells if all triggered builds has started for a specific event.
     * This is a bit slower than
     * {@link #isAllBuildsStarted(PatchSetKey)}
     * since an internal key needs to be created.
     * @param event the event.
     * @return true if it is so.
     */
    public synchronized boolean isAllBuildsStarted(PatchsetCreated event) {
        PatchSetKey key = createKey(event);
        return isAllBuildsStarted(key);
    }

    /**
     * Tells if all triggered builds has started for a specific memory imprint.
     * @param key the key to the memory.
     * @return true if it is so.
     */
    public synchronized boolean isAllBuildsStarted(PatchSetKey key) {
        MemoryImprint pb = memory.get(key);
        if (pb != null) {
            return pb.isAllBuildsSet();
        } else {
            return false;
        }
    }

    /**
     * Sets the memory that a build is completed for an event.
     * @param event the event
     * @param build the build.
     * @return the key to the memory.
     */
    public synchronized PatchSetKey completed(PatchsetCreated event, AbstractBuild build) {
        PatchSetKey key = createKey(event);
        MemoryImprint pb = memory.get(key);
        if (pb == null) {
            //Shoudn't happen but just in case, keep the memory.
            pb = new MemoryImprint(event);
            memory.put(key, pb);
        }
        pb.set(build.getProject(), build, true);
        return key;
    }

    /**
     * Sets the memory that a build has started for an event.
     * @param event the event.
     * @param build the build.
     * @return the key to the memory.
     */
    public synchronized PatchSetKey started(PatchsetCreated event, AbstractBuild build) {
        PatchSetKey key = createKey(event);
        MemoryImprint pb = memory.get(key);
        if (pb == null) {
            //Shoudn't happen but just in case, keep the memory.
            pb = new MemoryImprint(event);
            memory.put(key, pb);
        }
        pb.set(build.getProject(), build);
        return key;
    }

    /**
     * Adds a new memory about a build that has been/will be triggered.
     * @param event the event that triggered it.
     * @param project the project that was triggered.
     * @return the key to the memory.
     */
    public synchronized PatchSetKey triggered(PatchsetCreated event, AbstractProject project) {
        PatchSetKey key = createKey(event);
        MemoryImprint pb = memory.get(key);
        if (pb == null) {
            pb = new MemoryImprint(event);
            memory.put(key, pb);
        }
        pb.set(project);
        return key;
    }

    /**
     * Adds a new memory about a build that has been retriggered.
     * If there is an active memory about the provided event, then the project is reset with no build info.
     * Otherwise the memory is recreated from the list of other builds and their result.
     * @param event the event to be retriggered.
     * @param project the project that has been retriggered.
     * @param otherBuilds the list of other builds that was in the "old" memory.
     * @return the key to the memory.
     */
    public synchronized PatchSetKey retriggered(
            PatchsetCreated event,
            AbstractProject project,
            List<AbstractBuild> otherBuilds) {
        PatchSetKey key = createKey(event);
        MemoryImprint pb = memory.get(key);
        if (pb == null) {
            pb = new MemoryImprint(event);
            memory.put(key, pb);
            if (otherBuilds != null) {
                //It is a new memory so it wasn't building, lets populate with old build info
                for (AbstractBuild build : otherBuilds) {
                    pb.set(build.getProject(), build, !build.isBuilding());
                }
            }
        }
        pb.reset(project);

        return key;
    }

    /**
     * Creates a key out of an event to use for storing memory imprints.
     * @param event the event.
     * @return the key.
     */
    private PatchSetKey createKey(PatchsetCreated event) {
        return new PatchSetKey(event.getChange().getNumber(), event.getPatchSet().getNumber());
    }

    /**
     * Remooves the memory for the provided key.
     * @param key the key to the memory.
     */
    public synchronized void forget(PatchSetKey key) {
        memory.remove(key);
    }

    /**
     * Updates the {@link TriggerContext} for the provided key.
     * The cause and build is the "focal point" for the update, but all memory entities will be updated,
     * but only the current context will be get {@link TriggerContext#setThisBuild(hudson.model.AbstractBuild)}updated.
     * @param key the key to have as "focus" for the update.
     * @param cause the cause.
     * @param r the build the cause is in.
     */
    public synchronized void updateTriggerContext(PatchSetKey key, GerritCause cause, AbstractBuild r) {
        MemoryImprint imprint = getMemoryImprint(key);
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
     * @param entryToUpdate the entry to update.
     * @param imprint the information for the update.
     */
    private synchronized void updateTriggerContext(Entry entryToUpdate, MemoryImprint imprint) {
        if (entryToUpdate.getBuild() != null) {
            GerritCause cause = null;
            //TODO: Uppgrade to Hudson 1.362 and use getCause(GerritCause.class)
            List<Cause> causes = entryToUpdate.getBuild().getCauses();
            for (Cause c : causes) {
                if (c instanceof GerritCause) {
                    cause = (GerritCause)c;
                    break;
                }
            }
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
     * @param event the event.
     * @param project the project.
     * @return true if so.
     */
    public synchronized boolean isBuilding(PatchsetCreated event, AbstractProject project) {
        PatchSetKey key = createKey(event);
        MemoryImprint pb = memory.get(key);
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
     * @param event the event to look for.
     * @return true if so.
     */
    public synchronized boolean isBuilding(PatchsetCreated event) {
        PatchSetKey key = createKey(event);
        MemoryImprint pb = memory.get(key);
        return pb != null;
    }

    /**
     * Returns all started builds in memory for the given key.
     * @param key the key for the memory.
     * @return the list of builds, or null if there is no memory.
     */
    public synchronized List<AbstractBuild> getBuilds(PatchSetKey key) {
        MemoryImprint pb = memory.get(key);
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

        private PatchsetCreated event;
        private List<Entry> list = new ArrayList<Entry>();

        /**
         * Constructor.
         * @param event the event.
         */
        public MemoryImprint(PatchsetCreated event) {
            this.event = event;
        }

        /**
         * Constructor.
         * @param event the event.
         * @param project the first project.
         */
        public MemoryImprint(PatchsetCreated event, AbstractProject project) {
            this.event = event;
            set(project);
        }

        /**
         * The event.
         * @return the event.
         */
        public PatchsetCreated getEvent() {
            return event;
        }

        /**
         * A list of Project-Build tuple entries.
         * @return the memory entries.
         */
        public synchronized Entry[] getEntries() {
            return list.toArray(new Entry[list.size()]);
        }

        /**
         * Sets the build to a project or adds the project to the list.
         * @param project the project.
         * @param build the build.
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
         * Sets all the values of an entry annd adds it if the project has not been added before.
         * @param project the project
         * @param build the build
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
         * Tells if all builds has a value (not null).
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
         * Tells if all builds has Completed.
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
         * Tells if all builds in the memory where successful.
         * @return true if it is so, false if not all builds has started or not completed or has any different
         *          result than {@link Result#SUCCESS}.
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
             * @param project the project
             * @param build the build.
             */
            private Entry(AbstractProject project, AbstractBuild build) {
                this.project = project;
                this.build = build;
                buildCompleted = false;
            }

            /**
             * Constructor.
             * @param project the project.
             */
            private Entry(AbstractProject project) {
                this.project = project;
                buildCompleted = false;
            }

            /**
             * The Project.
             * @return the project.
             */
            public AbstractProject getProject() {
                return project;
            }

            /**
             * The build of a project.
             * @return the build.
             */
            public AbstractBuild getBuild() {
                return build;
            }

            /**
             * The build of a project.
             * @param build the build.
             */
            private void setBuild(AbstractBuild build) {
                this.build = build;
            }

            /**
             * If the build is completed.
             * @return true if the build is completed.
             */
            public boolean isBuildCompleted() {
                return buildCompleted;
            }

            /**
             * If the build is completed.
             * @param buildCompleted true if the build is completed.
             */
            private void setBuildCompleted(boolean buildCompleted) {
                this.buildCompleted = buildCompleted;
            }
        }
    }

    /**
     * The Key to use to map events and builds.
     */
    public static final class PatchSetKey implements Comparable<PatchSetKey> {

        private final Integer changeNumber;
        private final Integer patchSetNumber;

        /**
         * Constructs a Key.
         * @param changeNumber the Gerrit changeNumber
         * @param patchSetNumber the Gerrit patch-set number.
         */
        public PatchSetKey(Integer changeNumber, Integer patchSetNumber) {
            this.changeNumber = changeNumber;
            this.patchSetNumber = patchSetNumber;
        }

        //CS IGNORE RedundantThrows FOR NEXT 10 LINES. REASON: Informative.
        /**
         * Constructs a Key.
         * @param changeNumber the Gerrit changeNumber
         * @param patchSetNumber the Gerrit patch-set number.
         * @throws NumberFormatException if one of the parameters is not parseable to an {@link java.lang.Integer}.
         */
        public PatchSetKey(String changeNumber, String patchSetNumber) throws NumberFormatException {
            this.changeNumber = new Integer(changeNumber);
            this.patchSetNumber = new Integer(patchSetNumber);
        }

        /**
         * Quick Constructor.
         * @param event the event to get chencge and revision from.
         * @see #PatchSetKey(java.lang.String, java.lang.String)
         */
        PatchSetKey(PatchsetCreated event) {
            this(event.getChange().getNumber(), event.getPatchSet().getNumber());
        }

        /**
         * The Gerrit change number.
         * @return the Gerrit change number.
         */
        public Integer getChangeNumber() {
            return changeNumber;
        }

        /**
         * The Gerrit patch-set number.
         * @return the Gerrit patch-set number.
         */
        public Integer getPatchSetNumber() {
            return patchSetNumber;
        }

        @Override
        public boolean equals(Object obj) {
            //CS IGNORE ParenPad FOR NEXT 20 LINES. REASON: Autogenerated Code.
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final PatchSetKey other = (PatchSetKey)obj;

            if (( this.changeNumber != null && !this.changeNumber.equals(other.changeNumber) )
                    || ( this.changeNumber == null && other.changeNumber != null )) {
                return false;
            }

            if (( this.patchSetNumber != null && !this.patchSetNumber.equals(other.patchSetNumber) )
                    || ( this.patchSetNumber == null && other.patchSetNumber != null )) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            //CS IGNORE MagicNumber FOR NEXT 5 LINES. REASON: Autogenerated Code.
            //CS IGNORE AvoidInlineConditionals FOR NEXT 5 LINES. REASON: Autogenerated Code.
            //CS IGNORE ParenPad FOR NEXT 5 LINES. REASON: Autogenerated Code.
            int hash = 3;
            hash = 37 * hash + ( this.changeNumber != null ? this.changeNumber.hashCode() : 0 );
            hash = 37 * hash + ( this.patchSetNumber != null ? this.patchSetNumber.hashCode() : 0 );
            return hash;
        }

        @Override
        public int compareTo(PatchSetKey o) {
            int changeComp = 0;

            if (this.changeNumber != null) {
                changeComp = this.changeNumber.compareTo(o.changeNumber);
            } else if (o.changeNumber != null) {
                return -1;
            }

            if (changeComp != 0) {
                return changeComp;
            }

            if (this.patchSetNumber != null) {
                return this.patchSetNumber.compareTo(o.patchSetNumber);
            } else if (o.patchSetNumber != null) {
                return -1;
            } else {
                return 0;
            }
        }
    }
}
