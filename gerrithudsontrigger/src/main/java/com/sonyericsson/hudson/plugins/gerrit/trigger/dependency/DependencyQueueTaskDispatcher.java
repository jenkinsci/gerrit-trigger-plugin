/*
 * The MIT License
 *
 * Copyright 2014 Smartmatic International Corporation. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.dependency;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.model.queue.CauseOfBlockage;
import hudson.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;

/**
 * Blocks builds from running until the projects on which they depend have finished building.
 * This applies on a per-event basis, so for each event, the plugin will wait for
 * dependency projects (i.e., projects on which it depends) which also trigger for the same
 * event, to finish building before building a dependent project.
 *
 * @author Yannick Bréhon &lt;yannick.brehon@smartmatic.com&gt;
 */
@Extension
public class DependencyQueueTaskDispatcher extends QueueTaskDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(DependencyQueueTaskDispatcher.class);
    private Set<GerritTriggeredEvent> currentlyScanningEvents;
    private Set<GerritTriggeredEvent> scannedEvents;
    private static DependencyQueueTaskDispatcher instance;

    /**
     * Default constructor.
     */
    public DependencyQueueTaskDispatcher() {
        this.currentlyScanningEvents = new HashSet<GerritTriggeredEvent>();
        this.scannedEvents = new HashSet<GerritTriggeredEvent>();
    }

    /**
     * Returns the registered instance of this class from the list of all listeners.
     *
     * @return the instance.
     */
    public static DependencyQueueTaskDispatcher getInstance() {
        if (instance == null) {
            for (QueueTaskDispatcher listener : all()) {
                if (listener instanceof DependencyQueueTaskDispatcher) {
                    instance = (DependencyQueueTaskDispatcher)listener;
                    break;
                }
            }
        }
        if (instance == null) {
            logger.info("*** SINGLETON NOT INITIALIZED ON TIME ***");
        }
        return instance;
    }

    @Override
    public synchronized CauseOfBlockage canRun(Queue.Item item) {
        GerritCause cause = getGerritCause(item);

        //Not gerrit-triggered
        if (cause == null) {
            //logger.info("*** Not a gerrit cause: {}", cause);
            return null;
        }
        GerritTriggeredEvent event = cause.getEvent();
        if (!(item.task instanceof AbstractProject)) {
            //logger.info("*** Not an abstract project: {}", item.task);
            return null;
        }
        //Dependency projects in the build queue
        AbstractProject p = (AbstractProject)item.task;
        GerritTrigger trigger = GerritTrigger.getTrigger(p);
        List<AbstractProject> dependencies = getProjectsFromString(trigger.getDependencyJobsNames());
        if ((dependencies.size() == 0) || (dependencies == null)) {
            logger.info("*** No dependencies on project: {}", p);
            return null;
        }
        //logger.info("*** We have dependencies on project {} : {}", p, trigger.getDependencyJobsNames());

        /* we really should first check for other projects which will be triggered
         * for the same event, and haven't yet. Unfortunately, this requires some kind
         * of event notification - GerritEventLifecycle like - which is not available
         * at this time. Fortunately, triggering is near instant, and projects are kept
         * long enough in the queue  for us to not worry too much about this at this time.
         */

        List<AbstractProject> blockingProjects = getBlockingDependencyProjects(dependencies, event);

        if (blockingProjects.size() > 0) {
            return new BecauseDependantBuildIsBuilding(blockingProjects);
        } else {
            logger.info("*** No active dependencies on project: {}", p);
            return null;
        }
    }

    /**
     * Gets the subset of projects which have a building element needing to complete for the same event.
     * @param dependencies The list of projects which need to be checked
     * @param event The event should have also caused the blocking builds.
     * @return the sublist of dependencies which need to be completed before this event is resolved.
     */
    protected List<AbstractProject> getBlockingDependencyProjects(List<AbstractProject> dependencies,
            GerritTriggeredEvent event) {
        List<AbstractProject> blockingProjects = new ArrayList<AbstractProject>();
        ToGerritRunListener toGerritRunListener = ToGerritRunListener.getInstance();
        for (AbstractProject dependency : dependencies) {
            if (toGerritRunListener.isProjectTriggeredAndIncomplete(dependency, event)) {
                blockingProjects.add(dependency);
            }
        }
        return blockingProjects;
    }

    /**
     * Return the GerritCause of the specific item if any, otherwise return null.
     * @param item The item
     * @return the GerritCause
     */
    private GerritCause getGerritCause(Queue.Item item) {
        for (Cause cause : item.getCauses()) {
            if (cause instanceof GerritCause) {
                return (GerritCause)cause;
            }
        }
        return null;
    }

    /**
     * Return a list of Abstract Projects from their string names.
     * @param projects The string containing the projects, comma-separated.
     * @return the list of projects
     */
    public static List<AbstractProject> getProjectsFromString(String projects) {
        List<AbstractProject> dependencyJobs = new ArrayList<AbstractProject>();
        if (projects == null) {
            return null;
        } else {
            StringTokenizer tokens = new StringTokenizer(Util.fixNull(projects), ",");
            while (tokens.hasMoreTokens()) {
                String projectName = tokens.nextToken().trim();
                if (!projectName.equals("")) {
                    Item context = null;
                    Item item = Hudson.getInstance().getItem(projectName, context, Item.class);
                    if ((item != null) && (item instanceof AbstractProject)) {
                        dependencyJobs.add((AbstractProject)item);
                    }
                }
            }
        }
        return dependencyJobs;
    }
}
