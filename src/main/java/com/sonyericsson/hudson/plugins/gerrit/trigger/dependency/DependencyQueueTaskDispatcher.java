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
import hudson.ExtensionList;
import hudson.model.Cause;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.model.queue.CauseOfBlockage;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonymobile.tools.gerrit.gerritevents.GerritDefaultValues;
import com.sonymobile.tools.gerrit.gerritevents.GerritEventListener;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.GerritHandler;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.lifecycle.GerritEventLifecycleListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.lifecycle.GerritEventLifecycle;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;

/**
 * Blocks builds from running until the projects on which they depend have finished building.
 * This applies on a per-event basis, so for each event, the plugin will wait for
 * dependency projects (i.e., projects on which it depends) which also trigger for the same
 * event, to finish building before building a dependent project.
 *
 * @author Yannick Bréhon &lt;yannick.brehon@smartmatic.com&gt;
 */
@Extension
public final class DependencyQueueTaskDispatcher extends QueueTaskDispatcher
    implements GerritEventLifecycleListener, GerritEventListener {

    private static final Logger logger = LoggerFactory.getLogger(DependencyQueueTaskDispatcher.class);
    private Set<GerritTriggeredEvent> currentlyTriggeringEvents;

    /**
     * Default constructor.
     */
    public DependencyQueueTaskDispatcher() {
        this(PluginImpl.getHandler_());
    }

    /**
     * Constructor use by default constructor and for unit tests.
     *
     * @param gerritHandler the handler
     */
    DependencyQueueTaskDispatcher(GerritHandler gerritHandler) {
        this.currentlyTriggeringEvents = Collections.newSetFromMap(
                new ConcurrentHashMap<GerritTriggeredEvent, Boolean>());
        if (gerritHandler == null) {
            logger.error("Gerrit Handler was not available to construct DependencyQueueTaskDispatcher");
        } else {
            gerritHandler.addListener(this);
        }
        logger.debug("Registered to gerrit events");
    }

    /**
     * Returns the registered instance of this class from the list of all listeners.
     *
     * @return the instance.
     */
    public static DependencyQueueTaskDispatcher getInstance() {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            logger.error("INITIALIZATION ERROR? Could not find the Jenkins instance.");
            return null;
        }
        ExtensionList<DependencyQueueTaskDispatcher> dispatchers =
                jenkins.getExtensionList(DependencyQueueTaskDispatcher.class);
        if (dispatchers == null || dispatchers.isEmpty()) {
            logger.error("INITIALIZATION ERROR? Could not find the registered instance.");
            return null;
        }
        return dispatchers.get(0);
    }

    @Override
    public CauseOfBlockage canRun(Queue.Item item) {
        //Job check
        if (!(item.task instanceof Job)) {
            logger.debug("Not an Job instance: {}", item.task);
            return null;
        }

        GerritCause cause = getGerritCause(item);
        //Not gerrit-triggered
        if (cause == null) {
            logger.debug("Not Gerrit cause");
            return null;
        }
        GerritTriggeredEvent event = cause.getEvent();
        //The GerritCause should contain an event, but just in case.
        if (event == null) {
            logger.debug("Does not contain an event");
            return null;
        }
        //we do not block an item when it reached the buildable state: a buildable item is
        //an item for which it has already been determined it canRun, and it is only
        //waiting for an executor. Once the executor is avail, an extra check is done, but
        //we already determined in the previous canRun checks that its dependencies were done.
        if (item.isBuildable()) {
            logger.debug("Item is already buildable");
            return null;
        }
        Job p = (Job)item.task;
        GerritTrigger trigger = GerritTrigger.getTrigger(p);
        //The project being checked has no Gerrit Trigger
        if (trigger == null) {
            logger.debug("Project does not contain a trigger");
            return null;
        }
        //Dependency projects in the build queue
        List<Job> dependencies = getProjectsFromString(trigger.getDependencyJobsNames(),
                p);
        if ((dependencies == null) || (dependencies.size() == 0)) {
            logger.debug("No dependencies on project: {}", p);
            return null;
        }
        //logger.debug("We have dependencies on project {} : {}", p, trigger.getDependencyJobsNames());

        // We ensure that we wait until other jobs have been put into queue.
        // We use the default Gerrit Build Schedule Delay value
        long inQueueSince = item.getInQueueSince();
        if (System.currentTimeMillis() - inQueueSince < TimeUnit.SECONDS
                .toMillis(GerritDefaultValues.DEFAULT_BUILD_SCHEDULE_DELAY)) {
            logger.debug("We need to wait to ensure dependent jobs {} are in queue", event);
            return new BecauseWaitingToEnsureOtherJobsAreInQueue();
        }

        /* we really should first check for other projects which will be triggered
         * for the same event, and haven't yet. Unfortunately, this requires some kind
         * of event notification - GerritEventLifecycle like - which is not available
         * at this time. Fortunately, triggering is near instant, and projects are kept
         * long enough in the queue  for us to not worry too much about this at this time.
         * We can do this check for the retrigger.all action however, which does not have a quiet
         * time, because specific code exists for it in GerritTrigger, fortunately.
         */
        if (currentlyTriggeringEvents.contains(event)) {
            logger.debug("We need to wait while {} is being triggered", event);
            return new BecauseWaitingForOtherProjectsToTrigger();
        }


        List<Job> blockingProjects = getBlockingDependencyProjects(dependencies, event);

        if (blockingProjects.size() > 0) {
            return new BecauseDependentBuildIsBuilding(blockingProjects);
        } else {
            logger.info("No active dependencies on project: {} , it will now build", p);
            return null;
        }
    }

    /**
     * Gets the subset of projects which have a building element needing to complete for the same event.
     * @param dependencies The list of projects which need to be checked
     * @param event The event should have also caused the blocking builds.
     * @return the sublist of dependencies which need to be completed before this event is resolved.
     */
    protected List<Job> getBlockingDependencyProjects(List<Job> dependencies,
            GerritTriggeredEvent event) {
        List<Job> blockingProjects = new ArrayList<Job>();
        ToGerritRunListener toGerritRunListener = ToGerritRunListener.getInstance();
        if (toGerritRunListener != null) {
            for (Job dependency : dependencies) {
                if (toGerritRunListener.isProjectTriggeredAndIncomplete(dependency, event)) {
                    blockingProjects.add(dependency);
                }
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
     * @param context The context in which to read the string
     * @return the list of projects
     */
    public static List<Job> getProjectsFromString(String projects, Item context) {
        List<Job> dependencyJobs = new ArrayList<Job>();
        if ((projects == null) || projects.equals("")) {
            return null;
        } else {
            Jenkins jenkins = Jenkins.getInstance();
            assert jenkins != null;
            StringTokenizer tokens = new StringTokenizer(projects, ",");
            while (tokens.hasMoreTokens()) {
                String projectName = tokens.nextToken().trim();
                if (!projectName.equals("")) {
                    Item item = jenkins.getItem(projectName, context, Item.class);
                    if ((item != null) && (item instanceof Job)) {
                        dependencyJobs.add((Job)item);
                        logger.debug("project dependency job added : {}", item);
                    }
                }
            }
        }
        return dependencyJobs;
    }

    /**
     * Signals this event started retriggering all its projects.
     * In the meantime, no builds with dependencies should be allowed to start.
     * @param event the event triggering
     */
    public void onTriggeringAll(GerritTriggeredEvent event) {
        currentlyTriggeringEvents.add(event);
        logger.debug("Triggering all projects for {}", event);
    }

    /**
     * Signals this event is done retriggering all its projects.
     * Builds with dependencies may be allowed to start once their dependencies are built..
     * @param event the event done triggering
     */
    public void onDoneTriggeringAll(GerritTriggeredEvent event) {
        currentlyTriggeringEvents.remove(event);
        logger.debug("Done triggering all projects for {}", event);
    }

    /*
     * GerritEventListener interface
     */

    /**
     * Process lifecycle events. We register to these events
     * so we can get notified of the beginning of the scanning and end of
     * scanning.
     * @param event the event to which we subscribe.
     */
    @Override
    public void gerritEvent(GerritEvent event) {
        //we are only interested in the ManualPatchsetCreated events for now
        //as they are the only ones which have event scanning information.
        if (event instanceof GerritEventLifecycle) {
            logger.debug("registering to lifecycle");
            // Registering to get the ScanDone event.
            ((GerritEventLifecycle)event).addListener(this);
            // while this is most likely a ManualPatchSetCreated, which is
            // a GerritTriggeredEvent, we don't have a guarantee that this
            // will necessarily be the case in the future.
            if (event instanceof GerritTriggeredEvent) {
                //We only can setup this "barrier" if we are certain to be able to
                //lift it, which only happens for Lifecycle events for which we
                //will get the ScanDone.
                onTriggeringAll((GerritTriggeredEvent)event);
            }
        }
    }



    /*
     * GerritEventLifecycleListener interface
     */

    @Override
    public void triggerScanStarting(GerritEvent event) {
        // While it would make sense to call the onTriggeringAll, this event (ScanStarting)
        // is fired before the event even makes it to the gerritEvent above, meaning
        // nothing here will execute because we aren't registered yet.
    }

    @Override
    public void triggerScanDone(GerritEvent event) {
        // while this is most likely a ManualPatchSetCreated, which is
        // a GerritTriggeredEvent, we don't have a guarantee that this
        // will necessarily be the case in the future.
        logger.debug("trigger scan done");
        if (event instanceof GerritTriggeredEvent) {
            onDoneTriggeringAll((GerritTriggeredEvent)event);
        }
        // But, we do know this is a Lifecycle because this method is for lifecyle events
        ((GerritEventLifecycle)event).removeListener(this);
    }

    @Override
    public void projectTriggered(GerritEvent event, Job project) {
    }

    @Override
    public void buildStarted(GerritEvent event, Run build) {
    }

    @Override
    public void buildCompleted(GerritEvent event, Run build) {
    }

    @Override
    public void allBuildsCompleted(GerritEvent event) {
    }
}
