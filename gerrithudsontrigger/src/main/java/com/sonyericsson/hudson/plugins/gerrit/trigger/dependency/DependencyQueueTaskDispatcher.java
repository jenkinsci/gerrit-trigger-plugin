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
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
//import hudson.model.Queue;
import hudson.model.Queue.Item;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.model.queue.CauseOfBlockage;

//import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.ConcurrentMap;
//import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.lifecycle.GerritEventLifecycleListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.lifecycle.GerritEventLifecycle;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritHandlerLifecycle;
//import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritHandler;
//import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;
//import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.RepositoryModifiedEvent;
//import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.RefReplicated;
//import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
//import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritSlave;

/**
 * Blocks builds from running until their dependency projects have finished building.
 * This applies on a per-event basis, so for each event, the plugin will wait for
 * dependency projects which also trigger for the same event, to finish building before
 * building a dependent project.
 *
 * @author Yannick Bréhon &lt;yannick.brehon@smartmatic.com&gt;
 */
@Extension
public class DependencyQueueTaskDispatcher extends QueueTaskDispatcher
    implements GerritEventLifecycleListener, GerritEventListener {

    private static final Logger logger = LoggerFactory.getLogger(DependencyQueueTaskDispatcher.class);
    private Set<GerritEvent> currentlyScanningEvents;
    private Set<GerritEvent> scannedEvents;

    /**
     * Default constructor.
     */
    public DependencyQueueTaskDispatcher() {
        this.currentlyScanningEvents = new HashSet<GerritEvent>();
        this.scannedEvents = new HashSet<GerritEvent>();
        GerritHandlerLifecycle handler = (GerritHandlerLifecycle)PluginImpl.getInstance().getHandler();
        handler.addListener(this);
    }

    @Override
    public synchronized CauseOfBlockage canRun(Item item) {
        GerritCause cause = getGerritCause(item);
        GerritEvent event = (GerritEvent)cause.getEvent();
        boolean newEvent = isNewEvent(event);
        if (newEvent) {
            addNewEvent(event);
        }

        //Not gerrit-triggered
        if (cause == null) {
            logger.info("*** Not a gerrit cause: {}", cause);
            return null;
        }
        //Dependant projects
        if (!(item.task instanceof AbstractProject)) {
            logger.info("*** Not an abstract project: {}", item.task);
            return null;
        }
        AbstractProject p = (AbstractProject)item.task;
        GerritTrigger trigger = GerritTrigger.getTrigger(p);
        List<AbstractProject> dependencies = trigger.getDependencyJobs();
        if ((dependencies.size() == 0) || (dependencies == null)) {
            logger.info("*** No dependencies on project: {}", p);
            return null;
        }
        logger.info("*** We have dependencies on project {} : {}", p, trigger.getDependencyJobsNames());

        // Let's see if this event has finished triggering all projects it needs to trigger.
        if (newEvent) {
            logger.info("*** new event, we should wait");
            return null;
            //return Waiting for other triggered projects
        } else if (currentlyScanning(event)) {
            logger.info("*** event is scanning, we should wait");
            return null;
            //return Waiting for other triggered projects
        }


        return null;
    }

    /**
     * Adds the event to the currentlyScanningEvents.
     * And adds this Dispatcher as a listener to the event.
     * @param gerritEventLifecycle the event.
     *//*
    public synchronized void add(GerritEventLifecycle gerritEventLifecycle) {
        if (!contains(gerritEventLifecycle)) {
            gerritEventLifecycle.addListener(this);
            events.add(new EventState(gerritEventLifecycle));
        }
    }*/


    /**
     * Return the GerritCause of the specific item if any, otherwise return null.
     * @param item The item
     * @return the GerritCause
     */
    private GerritCause getGerritCause(Item item) {
        for (Cause cause : item.getCauses()) {
            //logger.info("*** Iterating cause: {}", cause, cause.getClass());
            if (cause instanceof GerritCause) {
                return (GerritCause)cause;
            }
        }
        return null;
    }

    /**
     * Whether this event is a newcomer.
     * @param event the event.
     * @return if is new or not.
     */
    private synchronized boolean isNewEvent(GerritEvent event) {
        return (!(currentlyScanningEvents.contains(event) || scannedEvents.contains(event)));
    }

    /**
     * Whether this event is currently scanning.
     * @param event the event.
     * @return if is scanning or not.
     */
    private synchronized boolean currentlyScanning(GerritEvent event) {
        return (currentlyScanningEvents.contains(event));
    }

    /**
     * Marks an event as having been scanned completely.
     * @param event the event.
     */
    private synchronized void markEventScanned(GerritEvent event) {
        currentlyScanningEvents.remove(event);
        scannedEvents.add(event);
        logger.info("*** Marked event: {} , as scanned", event);
    }

    /**
     * Removes event from monitored events.
     * @param event the event.
     */
    private synchronized void removeEvent(GerritEvent event) {
        currentlyScanningEvents.remove(event);
        scannedEvents.remove(event);
        logger.info("*** Removed event: {}", event);
    }

    /**
     * adds a new event in the list, and registers for events.
     * @param event the event.
     */
    private synchronized void addNewEvent(GerritEvent event) {
        currentlyScanningEvents.add(event);
        logger.info("*** Added event: {}", event);
        if (event instanceof GerritEventLifecycle) {
            ((GerritEventLifecycle)event).addListener(this);
            logger.info("*** registered to event: {}", event);
        }
    }

    @Override
    public synchronized void gerritEvent(GerritEvent event) {
        //we are only interested in changes
    }

    /**
     * Process patchset created events.
     * @param event the event
     */
    public synchronized void gerritEvent(PatchsetCreated event) {
       if (isNewEvent(event)) {
           addNewEvent(event);
           logger.info("*** add event via global listener: {}", event);
       }
    }

    /**
     * Process manual patchset created events.
     * @param event the event
     */
    public synchronized void gerritEvent(ManualPatchsetCreated event) {
       if (isNewEvent(event)) {
           addNewEvent(event);
           logger.info("*** add manual patchset event via global listener: {}", event);
       }
    }

    /**
     * Process RefReplicated events.
     * @param refReplicated the event
     *//*
    public void gerritEvent(RefReplicated refReplicated) {
        replicationCache.put(refReplicated);
        boolean queueMaintenanceRequired = false;
        for (BlockedItem blockedItem : blockedItems.values()) {
            if (!blockedItem.canRun) {
                blockedItem.processRefReplicatedEvent(refReplicated);
                if (blockedItem.canRun) {
                    queueMaintenanceRequired = true;
                }
            }
        }
        if (queueMaintenanceRequired) {
            // force a maintenance of the queue to unblock builds
            Queue.getInstance().maintain();
        }
    }*/

    @Override
    public synchronized void triggerScanStarting(GerritEvent event) {
        addNewEvent(event);
    }

    @Override
    public synchronized void triggerScanDone(GerritEvent event) {
        markEventScanned(event);
    }

    @Override
    public synchronized void projectTriggered(GerritEvent event, AbstractProject project) {
        logger.info("*** event triggered project: {}", event);
    }

    @Override
    public synchronized void buildStarted(GerritEvent event, AbstractBuild build) {
        logger.info("*** event started build: {}", event);
    }

    @Override
    public synchronized void buildCompleted(GerritEvent event, AbstractBuild build) {
        logger.info("*** event finished build: {}", event);
    }

    @Override
    public synchronized void allBuildsCompleted(GerritEvent event) {
        removeEvent(event);
    }
}
