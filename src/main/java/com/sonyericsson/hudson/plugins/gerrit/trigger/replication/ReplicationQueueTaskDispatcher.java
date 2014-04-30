/*
 * The MIT License
 *
 * Copyright 2014 Ericsson.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.replication;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Queue;
import hudson.model.Queue.Item;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.model.queue.CauseOfBlockage;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonymobile.tools.gerrit.gerritevents.GerritEventListener;
import com.sonymobile.tools.gerrit.gerritevents.GerritHandler;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.RepositoryModifiedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.RefReplicated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritSlave;

/**
 * Block builds until replication is completed if configured to wait for replication.
 *
 * @author Hugo Arès &lt;hugo.ares@ericsson.com&gt;
 */
@Extension
public class ReplicationQueueTaskDispatcher extends QueueTaskDispatcher implements GerritEventListener {

    private static final Logger logger = LoggerFactory.getLogger(ReplicationQueueTaskDispatcher.class);
    private final Map<Integer, BlockedItem> blockedItems;
    private final ReplicationCache replicationCache;

    /**
     * Default constructor.
     */
    public ReplicationQueueTaskDispatcher() {
        this(PluginImpl.getInstance().getHandler(),
            ReplicationCache.Factory.createCache(
                    PluginImpl.getInstance().getPluginConfig().getReplicationCacheExpirationInMinutes(),
                    TimeUnit.MINUTES));
    }

    /**
     * Constructor use by default constructor and for unit tests.
     *
     * @param gerritHandler the handler
     * @param replicationCache the replication cache
     */
    ReplicationQueueTaskDispatcher(GerritHandler gerritHandler, ReplicationCache replicationCache) {
        blockedItems = new ConcurrentHashMap<Integer, BlockedItem>();
        this.replicationCache = replicationCache;
        gerritHandler.addListener(this);
        logger.info("Registered to gerrit events");
    }

    @Override
    public CauseOfBlockage canRun(Item item) {
        logger.trace("Evaluating in item id {} can run", item.id);
        //we do not block item when it reached the buildable state, a buildable item is an item that
        //passed the waiting and the blocked state.
        if (item.isBuildable()) {
            return null;
        }
        Integer itemId = new Integer(item.id);
        if (blockedItems.containsKey(itemId)) {
            BlockedItem blockedItem = blockedItems.get(itemId);
            if (blockedItem.canRunWithTimeoutCheck()) {
                if (blockedItem.replicationFailedMessage != null) {
                    item.addAction(new ReplicationFailedAction(blockedItem.replicationFailedMessage));
                }
                blockedItems.remove(itemId);
                return null;
            } else {
                logger.trace("item id {} is still waiting replication to {} gerrit slaves, ", itemId,
                    blockedItem.slavesWaitingFor.size());
                return new WaitingForReplication(blockedItem.slavesWaitingFor.values());
            }
        } else {
            BlockedItem blockedItem = getBlockedItem(item);
            if (blockedItem != null) {
                //before blocking the build, lets check if we already received the replication events
                updateFromReplicationCache(blockedItem);
                // store the info to be able to unblock the build
                // later without having to iterate through all the builds in the queue
                blockedItems.put(itemId, blockedItem);
                return canRun(item);
            }
        }
        return null;
    }

    /**
     * Update the blocked item with any cached RefReplicated that are interesting to that item.
     * @param blockedItem The blocked item to update
     */
    private void updateFromReplicationCache(BlockedItem blockedItem) {
        Iterator<GerritSlave> it = blockedItem.slavesWaitingFor.values().iterator();
        while (it.hasNext()) {
            RefReplicated refReplicated = replicationCache.getIfPresent(blockedItem.gerritServer,
                blockedItem.gerritProject, blockedItem.ref, it.next().getHost());
            if (refReplicated != null) {
                blockedItem.processRefReplicatedEvent(refReplicated);
                logger.trace("processed a replication event from the cache, remaining number of events waiting for: "
                    + blockedItem.slavesWaitingFor.size());
            }
        }
    }
    /**
     * Return the blocked item if caused by a gerritEvent that must wait
     * for replication and if replication is configured.
     * @param item The item
     * @return blockedItem or null if build do not need to be blocked
     */
    private BlockedItem getBlockedItem(Item item) {
        GerritCause gerritCause = getGerritCause(item);
        if (gerritCause == null) {
            return null;
        }
        if (gerritCause.getEvent() != null && gerritCause.getEvent() instanceof RepositoryModifiedEvent
            && item.task instanceof AbstractProject<?, ?>) {

            if (replicationCache.isExpired(gerritCause.getEvent().getReceivedOn())) {
                return null;
            }

            GerritTrigger gerritTrigger = GerritTrigger.getTrigger((AbstractProject<?, ?>)item.task);
            if (gerritTrigger == null) {
                return null;
            }

            String gerritServer = null;
            if (gerritCause.getEvent().getProvider() != null) {
                gerritServer = gerritCause.getEvent().getProvider().getName();
            }
            if (gerritServer == null) {
                return null;
            }

            List<GerritSlave> slaves = gerritTrigger.gerritSlavesToWaitFor(gerritServer);
            if (!slaves.isEmpty()) {
                RepositoryModifiedEvent repositoryModifiedEvent = (RepositoryModifiedEvent)gerritCause.getEvent();
                if (repositoryModifiedEvent.getModifiedProject() != null
                    && repositoryModifiedEvent.getModifiedRef() != null) {
                    return new BlockedItem(repositoryModifiedEvent.getModifiedProject(),
                        repositoryModifiedEvent.getModifiedRef(),
                        gerritServer,
                        slaves,
                        gerritCause.getEvent().getReceivedOn());
                }
            }
        }
        return null;
    }

    /**
     * Return the GerritCause of the specific item if any, otherwise return null.
     * @param item The item
     * @return the GerritCause
     */
    private GerritCause getGerritCause(Item item) {
        for (Cause cause : item.getCauses()) {
            if (cause.getClass().equals(GerritCause.class)) { // we only block the exact type, not sub classes
                return (GerritCause)cause;
            }
        }
        return null;
    }

    @Override
    public void gerritEvent(GerritEvent event) {
       //not interested in the other events, only RefReplicated
    }

    /**
     * Process RefReplicated events.
     * @param refReplicated the event
     */
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
    }

    /**
     * Item blocked because of replication.
     * @author Hugo Arès &lt;hugo.ares@ericsson.com&gt;
     */
    private static class BlockedItem {
        private String gerritProject;
        private String ref;
        private String gerritServer;
        private ConcurrentMap<String, GerritSlave> slavesWaitingFor;
        private boolean canRun = false;
        private long eventTimeStamp;
        private String replicationFailedMessage;

        /**
         * Standard constructor.
         * @param gerritProject The gerrit project
         * @param ref The ref
         * @param gerritServer The gerrit server
         * @param gerritSlaves The gerrit slaves
         * @param eventTimeStamp The original event time stamp.
         */
        public BlockedItem(String gerritProject, String ref, String gerritServer, List<GerritSlave> gerritSlaves,
            long eventTimeStamp) {
            this.gerritProject = gerritProject;
            this.ref = ref;
            this.gerritServer = gerritServer;
            this.slavesWaitingFor = new ConcurrentHashMap<String, GerritSlave>(gerritSlaves.size());
            for (GerritSlave gerritSlave : gerritSlaves) {
                slavesWaitingFor.put(gerritSlave.getHost(), gerritSlave);
            }
            this.eventTimeStamp = eventTimeStamp;
        }

        /**
         * Return if this block item is ready to run.
         *
         * Item can be ready to run if the replication events are received for all the slaves, if a replication failed
         * or if we reached the wait time out for a slave. If a replication failed or if time out is reached,
         * replicationFailedMessage will be set with message.
         *
         * @return true if ready to run, otherwise false
         */
        public boolean canRunWithTimeoutCheck() {
            if (canRun) {
                return true;
            }
            // check if any Gerrit Slave reached its timeout
            for (GerritSlave slave : slavesWaitingFor.values()) {
                if (slave.getTimeoutInSeconds() != GerritSlave.DISABLED_TIMEOUT_VALUE
                    && TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - eventTimeStamp) > slave
                        .getTimeoutInSeconds()) {
                    replicationFailedMessage = Messages.WaitingForReplicationTimeout(ref, slave.getName());
                    return true;
                }
            }
            return false;
        }

        /**
         * Process the RefReplicated to and if related to this blocked item, update the slaves
         * list and canRun flag accordingly.
         * @param refReplicated The refReplicated
         */
        public void processRefReplicatedEvent(RefReplicated refReplicated) {
            if (canRun || refReplicated.getProvider() == null) {
                return;
            }
            if (gerritProject.equals(refReplicated.getProject())
                && gerritServer.equals(refReplicated.getProvider().getName())
                && ref.equals(refReplicated.getRef())
                && slavesWaitingFor.containsKey(refReplicated.getTargetNode())
                && eventTimeStamp < refReplicated.getReceivedOn()) {
                if (refReplicated.getStatus().equals(RefReplicated.FAILED_STATUS)) {
                    replicationFailedMessage = Messages.ReplicationFailed(ref,
                        slavesWaitingFor.get(refReplicated.getTargetNode()).getName());
                    slavesWaitingFor.clear();
                } else {
                    slavesWaitingFor.remove(refReplicated.getTargetNode());
                }

                if (slavesWaitingFor.size() == 0) {
                    canRun = true;
                }
            }
        }
    }
}
