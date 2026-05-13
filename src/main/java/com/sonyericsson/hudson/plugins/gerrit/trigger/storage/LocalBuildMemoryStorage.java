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
package com.sonyericsson.hudson.plugins.gerrit.trigger.storage;

import com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.BuildMemoryReport;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint.Entry;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.BuildMemoryStorage;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import hudson.model.Job;
import hudson.model.Run;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Local TreeMap-based implementation of BuildMemoryStorage.
 * <p>
 * This is the default storage backend for standalone Jenkins instances.
 * It uses a TreeMap with {@link BuildMemory.GerritTriggeredEventComparator}
 * to store memory imprints in memory.
 * <p>
 * All operations are synchronized to ensure thread safety.
 *
 */
public class LocalBuildMemoryStorage implements BuildMemoryStorage {

    private static final Logger logger = LoggerFactory.getLogger(LocalBuildMemoryStorage.class);

    /**
     * The in-memory storage map.
     * Uses custom comparator to compare events by hashCode.
     */
    private final TreeMap<GerritTriggeredEvent, MemoryImprint> memory =
            new TreeMap<>(new BuildMemory.GerritTriggeredEventComparator());

    /**
     * Default constructor.
     */
    public LocalBuildMemoryStorage() {
        logger.debug("Initialized LocalBuildMemoryStorage");
    }

    @Override
    @CheckForNull
    public synchronized MemoryImprint getMemoryImprint(@NonNull GerritTriggeredEvent event) {
        return memory.get(event);
    }

    @Override
    public synchronized void triggered(@NonNull GerritTriggeredEvent event, @NonNull Job project) {
        MemoryImprint pb = getOrCreateMemoryImprint(event);
        pb.set(project);
    }

    @Override
    public synchronized void started(@NonNull GerritTriggeredEvent event, @NonNull Run build) {
        MemoryImprint pb = getOrCreateMemoryImprint(event, "Build started without being registered first.");
        pb.set(build.getParent(), build);
    }

    @Override
    public synchronized void completed(@NonNull GerritTriggeredEvent event, @NonNull Run build) {
        MemoryImprint pb = getOrCreateMemoryImprint(event);
        pb.set(build.getParent(), build, true);
    }

    @Override
    public synchronized void retriggered(@NonNull GerritTriggeredEvent event, @NonNull Job project,
                                         @CheckForNull List<Run> otherBuilds) {
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

    @Override
    public synchronized void cancelled(@NonNull GerritTriggeredEvent event, @NonNull Job project) {
        MemoryImprint pb = getOrCreateMemoryImprint(event);
        pb.set(project);
        Entry entry = pb.getEntry(project);
        entry.setCancelled(true);
        entry.setCancelling(false);
        entry.setBuildCompleted(true);
    }

    /**
     * Gets or creates a MemoryImprint for the given event.
     *
     * @param event the event
     * @return the MemoryImprint
     */
    protected MemoryImprint getOrCreateMemoryImprint(@NonNull GerritTriggeredEvent event) {
        return getOrCreateMemoryImprint(event, null);
    }

    /**
     * Gets or creates a MemoryImprint for the given event, logging a warning if created.
     *
     * @param event the event
     * @param warningMessage optional warning message to log if MemoryImprint is created
     * @return the MemoryImprint
     */
    protected MemoryImprint getOrCreateMemoryImprint(
            @NonNull GerritTriggeredEvent event, String warningMessage) {
        MemoryImprint pb = memory.get(event);
        if (pb == null) {
            //Shouldn't happen but just in case, keep the memory.
            pb = new MemoryImprint(event);
            if (warningMessage != null) {
                logger.warn(warningMessage);
            }
            memory.put(event, pb);
        }
        return pb;
    }

    @Override
    public synchronized void forget(@NonNull GerritTriggeredEvent event) {
        memory.remove(event);
    }

    @Override
    public synchronized void removeProject(@NonNull Job project) {
        String projectFullName = project.getFullName();
        for (MemoryImprint memoryImprint : memory.values()) {
            memoryImprint.removeProject(projectFullName);
        }
    }

    @Override
    public synchronized boolean isAllBuildsCompleted(@NonNull GerritTriggeredEvent event) {
        MemoryImprint pb = memory.get(event);
        if (pb != null) {
            return pb.isAllBuildsCompleted();
        } else {
            return false;
        }
    }

    @Override
    public synchronized boolean isAllBuildsStarted(@NonNull GerritTriggeredEvent event) {
        MemoryImprint pb = memory.get(event);
        if (pb != null) {
            return pb.isAllBuildsSet();
        } else {
            return false;
        }
    }

    @Override
    @CheckForNull
    public synchronized BuildsStartedStats getBuildsStartedStats(@NonNull GerritTriggeredEvent event) {
        MemoryImprint pb = memory.get(event);
        if (pb != null) {
            return pb.getBuildsStartedStats();
        } else {
            return null;
        }
    }

    @Override
    @CheckForNull
    public synchronized String getStatusReport(@NonNull GerritTriggeredEvent event) {
        MemoryImprint pb = memory.get(event);
        if (pb != null) {
            return pb.getStatusReport();
        } else {
            return null;
        }
    }

    @Override
    public synchronized boolean isTriggered(@NonNull GerritTriggeredEvent event, @NonNull Job project) {
        MemoryImprint pb = memory.get(event);
        if (pb == null) {
            return false;
        } else {
            String fullName = project.getFullName();
            for (Entry entry : pb.getEntries()) {
                if (entry.isProject(fullName)) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public synchronized boolean isBuilding(@NonNull GerritTriggeredEvent event, @NonNull Job project) {
        MemoryImprint pb = memory.get(event);
        if (pb == null) {
            return false;
        } else {
            String fullName = project.getFullName();
            for (Entry entry : pb.getEntries()) {
                if (entry.isProject(fullName)) {
                    if (entry.getBuild() != null) {
                        return !entry.isBuildCompleted();
                    } else {
                        return !entry.isCancelling() && !entry.isCancelled();
                    }
                }
            }
            return false;
        }
    }

    @Override
    public synchronized boolean isBuilding(@NonNull GerritTriggeredEvent event) {
        MemoryImprint pb = memory.get(event);
        return pb != null;
    }

    @Override
    @CheckForNull
    public synchronized List<Run> getBuilds(@NonNull GerritTriggeredEvent event) {
        MemoryImprint pb = memory.get(event);
        if (pb != null) {
            List<Run> list = new LinkedList<>();
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

    @Override
    public synchronized void setEntryCustomUrl(@NonNull GerritTriggeredEvent event, @NonNull Run run,
                                               @CheckForNull String customUrl) {
        MemoryImprint pb = getMemoryImprint(event);

        if (pb != null) {
            Entry entry = pb.getEntry(run.getParent());

            if (entry != null) {
                logger.trace("Recording custom URL for {}: {}", event, customUrl);
                entry.setCustomUrl(customUrl);
            }
        }
    }

    @Override
    public synchronized void setEntryUnsuccessfulMessage(@NonNull GerritTriggeredEvent event, @NonNull Run run,
                                                         @CheckForNull String unsuccessfulMessage) {
        MemoryImprint pb = getMemoryImprint(event);

        if (pb != null) {
            Entry entry = pb.getEntry(run.getParent());

            if (entry != null) {
                logger.trace("Recording unsuccessful message for {}: {}", event, unsuccessfulMessage);
                entry.setUnsuccessfulMessage(unsuccessfulMessage);
            }
        }
    }

    @Override
    @NonNull
    public synchronized BuildMemoryReport report() {
        BuildMemoryReport report = new BuildMemoryReport();
        for (Map.Entry<GerritTriggeredEvent, MemoryImprint> entry : memory.entrySet()) {
            List<Entry> triggered = new LinkedList<>();
            for (Entry tr : entry.getValue().getEntriesList()) {
                triggered.add(tr.clone());
            }
            report.put(entry.getKey(), triggered);
        }
        return report;
    }

    // ===== Advanced Operations =====

    @Override
    @NonNull
    public synchronized Map<GerritTriggeredEvent, MemoryImprint> getAllEvents() {
        // Return a copy to avoid concurrent modification issues
        return new TreeMap<>(memory);
    }
}
