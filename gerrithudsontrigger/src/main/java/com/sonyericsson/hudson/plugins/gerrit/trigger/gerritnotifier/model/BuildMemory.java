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
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Keeps track of what builds has been triggered and if all builds are done for specific events.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class BuildMemory {

    private TreeMap<PatchSetKey, MemoryImprint> memory = new TreeMap<PatchSetKey, MemoryImprint>();

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
     * {@link #isAllBuildsCompleted(com.sonyericsson.gerrithudsontrigger.gerritnotifier.model.BuildMemory.PatchSetKey)}
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
     * {@link #isAllBuildsStarted(com.sonyericsson.gerrithudsontrigger.gerritnotifier.model.BuildMemory.PatchSetKey)}
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
        private synchronized void set(AbstractProject project, AbstractBuild build) {
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
        private synchronized void set(AbstractProject project) {
            Entry entry = getEntry(project);
            if (entry == null) {
                entry = new Entry(project);
                list.add(entry);
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
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final PatchSetKey other = (PatchSetKey)obj;

            if ((this.changeNumber != null && !this.changeNumber.equals(other.changeNumber))
                    || (this.changeNumber == null && other.changeNumber != null)) {
                return false;
            }

            if ((this.patchSetNumber != null && !this.patchSetNumber.equals(other.patchSetNumber))
                    || (this.patchSetNumber == null && other.patchSetNumber != null)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            //CS IGNORE MagicNumber FOR NEXT 5 LINES. REASON: Autogenerated Code.
            //CS IGNORE AvoidInlineConditionals FOR NEXT 5 LINES. REASON: Autogenerated Code.
            int hash = 3;
            hash = 37 * hash + (this.changeNumber != null ? this.changeNumber.hashCode() : 0);
            hash = 37 * hash + (this.patchSetNumber != null ? this.patchSetNumber.hashCode() : 0);
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
