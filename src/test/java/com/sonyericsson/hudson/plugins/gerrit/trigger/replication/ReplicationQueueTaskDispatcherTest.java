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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.ExtensionList;
import hudson.model.Action;
import hudson.model.AbstractProject;
import hudson.model.CauseAction;
import hudson.model.MockBuildableItem;
import hudson.model.MockWaitingItem;
import hudson.model.Queue;
import hudson.model.Queue.Item;
import hudson.model.queue.CauseOfBlockage;
import org.junit.jupiter.api.AfterEach;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jenkins.model.Jenkins;

import jenkins.model.TransientActionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sonymobile.tools.gerrit.gerritevents.GerritHandler;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeMerged;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeRestored;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.DraftPublished;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.RefReplicated;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.RefUpdated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritManualCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritSlave;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import org.mockito.MockedStatic;

/**
 * Tests {@link com.sonyericsson.hudson.plugins.gerrit.trigger.replication.ReplicationQueueTaskDispatcher}.
 * @author Hugo Arès &lt;hugo.ares@ericsson.com&gt;
 */
class ReplicationQueueTaskDispatcherTest {

    private ReplicationQueueTaskDispatcher dispatcher;
    private Queue queueMock;
    private GerritHandler gerritHandlerMock;
    private GerritTrigger gerritTriggerMock;
    private AbstractProject<?, ?> abstractProjectMock;

    private static final int HOURSBEFORECHANGEMERGEDFORPATCHSET = -8;
    private static final int HOURBEFOREREPLICATIONCACHECREATED = -1;
    private MockedStatic<Jenkins> jenkinsMockedStatic;

    /**
     * Create ReplicationQueueTaskDispatcher with a mocked GerritHandler.
     */
    @BeforeEach
    void setUp() {
        gerritHandlerMock = mock(GerritHandler.class);
        dispatcher = new ReplicationQueueTaskDispatcher(gerritHandlerMock, ReplicationCache.Factory.createCache());
        gerritTriggerMock = mock(GerritTrigger.class);
        queueMock = mock(Queue.class);
        Jenkins jenkinsMock = mock(Jenkins.class);
        when(jenkinsMock.getQueue()).thenReturn(queueMock);
        ExtensionList<TransientActionFactory> list = mock(ExtensionList.class);
        List<TransientActionFactory> emptyList = Collections.emptyList();
        Iterator<TransientActionFactory> iterator = emptyList.iterator();
        when(list.iterator()).thenReturn(iterator);
        when(jenkinsMock.getExtensionList(same(TransientActionFactory.class))).thenReturn(list);
        jenkinsMockedStatic = mockStatic(Jenkins.class);
        jenkinsMockedStatic.when(Jenkins::get).thenReturn(jenkinsMock);
    }

    @AfterEach
    void tearDown() {
        jenkinsMockedStatic.close();
    }

    /**
     * Test that it should register as event listener on init.
     */
    @Test
    void shouldRegisterAsEventListenerOnInit() {
        verify(gerritHandlerMock, times(1)).addListener(dispatcher);
    }

    /**
     * Test that it should not block item without a gerritCause.
     */
    @Test
    void shouldNotBlockItemdWithoutGerritCause() {
        assertNull(dispatcher.canRun(mock(Item.class)), "Build should not be blocked");
    }

    /**
     * Test that it should not block item caused by ChangeAbandoned.
     */
    @Test
    void shouldNotBlockItemCausedByChangeAbandoned() {
        assertNull(dispatcher.canRun(createItemWithOneSlave(new ChangeAbandoned())), "Build should not be blocked");
    }

    /**
     * Test that it should not block item caused by ChangeRestored.
     */
    @Test
    void shouldNotBlockItemCausedByChangeRestored() {
        assertNull(dispatcher.canRun(createItemWithOneSlave(new ChangeRestored())), "Build should not be blocked");
    }

    /**
     * Test that it should not block item caused by CommentAdded.
     */
    @Test
    void shouldNotBlockItemCausedByCommentAdded() {
        assertNull(dispatcher.canRun(createItemWithOneSlave(new CommentAdded())), "Build should not be blocked");
    }

    /**
     * Test that it should not block item caused by ManualPatchsetCreated.
     */
    @Test
    void shouldNotBlockItemCausedByManualPatchsetCreated() {
        assertNull(dispatcher.canRun(createItemWithOneSlave(new ManualPatchsetCreated())),
                "Build should not be blocked");
    }

    /**
     * Test that it should not block item with GerritCause that does not contain a GerritEvent.
     *
     * Not sure this is a valid use case under normal use but GerritCause have
     * an empty constructor that do not value to GerritEvent so, let's test it.
     */
    @Test
    void shouldNotBlockItemWithGerritCauseWithoutGerritEvent() {
        assertNull(dispatcher.canRun(createItemWithOneSlave(new GerritCause())), "Build should not be blocked");
    }

    /**
     * Test that it should not block item without GerritTrigger configured.
     */
    @Test
    void shouldNotBlockItemWithoutGerritTrigger() {
        Item item = createItemWithOneSlave(new PatchsetCreated());
        when(abstractProjectMock.getTrigger(GerritTrigger.class)).thenReturn(null);
        assertNull(dispatcher.canRun(item), "Build should not be blocked");
    }

    /**
     * Test that it should not block item when it not configured to wait for slaves.
     */
    @Test
    void shouldNotBlockItemWhenNotConfiguredToWaitForSlaves() {
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
                "refs/changes/1/1/1");
        Item item = createItem(patchsetCreated, null);
        assertNull(dispatcher.canRun(item), "Build should not be blocked");
    }

    /**
     * Test that it should not block buildable items.
     *
     * If you look at the Queue documentation, build enter the queue as waiting item,
     * then they become blocked item and finally the become buildable items. QueueTaskDispatcher.canRun
     * will get called one last time on buildable item before they can leave the queue but we do not
     * want ReplicationQueueTaskDispatcher to block them at that point.
     */
    @Test
    void shouldNotBlockBuildableItem() {
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
                "refs/changes/1/1/1");
        Item item = createItem(patchsetCreated, new String[] {"slaveA", "slaveB", "slaveC"});

        CauseOfBlockage cause = dispatcher.canRun(new MockBuildableItem(item));
        assertNull(cause, "Build should not be blocked");
    }

    /**
     * Test that it should block item until replication completes to slave.
     *
     * For this test case, item is configured with only one slave.
     */
    @Test
    void shouldBlockedItemUntilPatchsetIsReplicatedToSlave() {
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
                "refs/changes/1/1/1");
        Item item = createItem(patchsetCreated, new String[] {"slaveA"});

        //item is blocked
        CauseOfBlockage cause = dispatcher.canRun(item);
        assertNotNull(cause, "The item should be blocked");
        assertInstanceOf(WaitingForReplication.class, cause,
                "Should have returned a WaitingForReplication as CauseOfBlockage");
        assertTrue(cause.getShortDescription().contains("slaveA"));

        //send an unrelated event (not RefReplicated)
        dispatcher.gerritEvent(new ChangeAbandoned());
        assertNotNull(dispatcher.canRun(item), "the item should be blocked");

        //send an unrelated replication event (other server)
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/changes/1/1/1",
                "someOtherGerritServer", "slaveA", RefReplicated.SUCCEEDED_STATUS));
        assertNotNull(dispatcher.canRun(item), "the item should be blocked");

        //send an unrelated replication event (other project)
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someOtherProject", "refs/changes/1/1/1",
                "someGerritServer", "slaveA", RefReplicated.SUCCEEDED_STATUS));
        assertNotNull(dispatcher.canRun(item), "the item should be blocked");

        //send an unrelated replication event (other ref)
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/changes/2/2/2", "someGerritServer",
                "slaveA", RefReplicated.SUCCEEDED_STATUS));
        assertNotNull(dispatcher.canRun(item), "the item should be blocked");

        //send an unrelated replication event (other slave)
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/changes/1/1/1", "someGerritServer",
                "slaveB", RefReplicated.SUCCEEDED_STATUS));
        assertNotNull(dispatcher.canRun(item), "the item should be blocked");

        //send a malformed replication event (missing provider)
        RefReplicated refReplicated = Setup.createRefReplicatedEvent("someProject", "refs/changes/1/1/1",
                "someGerritServer", "slaveB", RefReplicated.SUCCEEDED_STATUS);
        refReplicated.setProvider(null);
        dispatcher.gerritEvent(refReplicated);
        assertNotNull(dispatcher.canRun(item), "the item should be blocked");

        //send the proper replication event
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/changes/1/1/1", "someGerritServer",
                "slaveA", RefReplicated.SUCCEEDED_STATUS));

        assertNull(dispatcher.canRun(item), "Item should not be blocked");
        assertNull(item.getAction(ReplicationFailedAction.class),
                "Item should not be tagged with replicationFailedAction");
        verify(queueMock, times(1)).maintain();
    }

    /**
     * Test that it should NOT block item if patchset has expired compared to
     * when the Change Merged event is received.
     */
    @Test
    void shouldNotBlockItemSinceChangeMergedIsReplicatedViaExpiredPatchset() {

        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.HOUR, HOURSBEFORECHANGEMERGEDFORPATCHSET);
        Date dateBefore8Hours = cal.getTime();

        ChangeMerged changeMerged = Setup.createChangeMergedWithPatchSetDate("someGerritServer", "someProject",
                "refs/changes/1/1/1", dateBefore8Hours);
        Item item = createItem(changeMerged, new String[] {"slaveA"});

        // item is NOT blocked since patchset createdOn date is older than cache
        // we assume it was
        CauseOfBlockage cause = dispatcher.canRun(item);
        assertNull(cause, "The item should NOT be blocked");

    }

    /**
     * Test that it should NOT block item if patchset has expired compared to
     * when the Replication Cache was first created.
     */
    @Test
    void shouldNotBlockItemSinceChangeBasedEventIsReplicatedViaCreationDateBasedExpiredPatchset() {

        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.HOUR, HOURBEFOREREPLICATIONCACHECREATED);
        Date dateBefore1Hours = cal.getTime();

        ChangeMerged changeMerged = Setup.createChangeMergedWithPatchSetDate("someGerritServer", "someProject",
                "refs/changes/1/1/1", dateBefore1Hours);
        Item item = createItem(changeMerged, new String[] {"slaveA"});

        // item is NOT blocked since patchset createdOn date is older than cache
        // we assume it was
        CauseOfBlockage cause = dispatcher.canRun(item);
        assertNull(cause, "The item should NOT be blocked");

    }

    /**
     * Test that it should NOT block item if patchset has expired compared to
     * when the Change Merged event is received.
     */
    @Test
    void shouldNotBlockItemSinceDraftPublishedIsReplicatedViaExpiredPatchset() {

        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.HOUR, HOURSBEFORECHANGEMERGEDFORPATCHSET);
        Date dateBefore8Hours = cal.getTime();

        DraftPublished dp = Setup.createDraftPublishedWithPatchSetDate("someGerritServer", "someProject",
                "refs/changes/1/1/1", dateBefore8Hours);
        Item item = createItem(dp, new String[] {"slaveA"});

        // item is NOT blocked since patchset createdOn date is older than cache
        // we assume it was
        CauseOfBlockage cause = dispatcher.canRun(item);
        assertNull(cause, "The item should NOT be blocked");

    }

    /**
     * Helper method for ChangeBasedEvent replication
     * @param cbe Change Based event
     * @param reference Reference to send the replication event for
     */
    private void patchSetIsReplicatedToOneSlaveAfterChangeBasedEvent(ChangeBasedEvent cbe, String reference) {

        Item item = createItem(cbe, new String[] {"slaveA"});

        //item is blocked
        CauseOfBlockage cause = dispatcher.canRun(item);
        assertNotNull(cause, "The item should be blocked");
        assertInstanceOf(WaitingForReplication.class, cause,
                "Should have returned a WaitingForReplication as CauseOfBlockage");
        assertTrue(cause.getShortDescription().contains("slaveA"));

        //send an unrelated event (not RefReplicated)
        dispatcher.gerritEvent(new ChangeAbandoned());
        assertNotNull(dispatcher.canRun(item), "the item should be blocked");

        //send the proper replication event
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", reference, "someGerritServer",
                "slaveA", RefReplicated.SUCCEEDED_STATUS));

        assertNull(dispatcher.canRun(item), "Item should not be blocked");
        assertNull(item.getAction(ReplicationFailedAction.class),
                "Item should not be tagged with replicationFailedAction");
        verify(queueMock, times(1)).maintain();
    }

    /**
     * Test that it should block item if patchset is replicated to a slave
     * After the Change Merged event is received.
     */
    @Test
    void shouldBlockItemUntilIfPatchSetIsReplicatedToOneSlaveAfterChangeMerged() {

        ChangeMerged changeMerged = Setup.createChangeMerged("someGerritServer", "someProject",
                "refs/changes/1/1/1");

        //note for change merged event, the expected replica event is for the head of the branch as the replica
        //event for the reference would have been already sent when the patch was pushed for review.
        patchSetIsReplicatedToOneSlaveAfterChangeBasedEvent(changeMerged, "refs/heads/branch");

    }

    /**
     * Test that it should block item if patchset is replicated to a slave
     * After the Draft Published event is received.
     */
    @Test
    void shouldBlockItemUntilIfPatchSetIsReplicatedToOneSlaveAfterDraftPublished() {

        DraftPublished draftPublished = Setup.createDraftPublished("someGerritServer", "someProject",
                "refs/changes/1/1/1");
        patchSetIsReplicatedToOneSlaveAfterChangeBasedEvent(draftPublished, "refs/changes/1/1/1");

    }

    /**
     * Test that it should block item if patchset is replicated to a slave
     * Before the Change Merged event is received.
     */
    @Test
    void shouldBlockItemUntilIfPatchSetIsReplicatedToOneSlaveBeforeChangeMerged() {

        //send the replication event before change merged event.
        //note that the ref is 'refs/heads/branch' because for change merged events that's the only replica event
        //that Gerrit will send (as the replica event for the ref would have been sent when the patch was pushed
        //to Gerrit for review).
        //For change merged event, a check on the event timestamp is done to make sure not to unblock the build for
        //a replica event that was fired before the current change merged event.
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/heads/branch", "someGerritServer",
                "slaveA", RefReplicated.SUCCEEDED_STATUS));

        ChangeMerged changeMerged = Setup.createChangeMerged("someGerritServer", "someProject",
                "refs/changes/1/1/1");
        Item item = createItem(changeMerged, new String[] {"slaveA"});

        assertNotNull(dispatcher.canRun(item),
                "Item should be blocked as the replica event happened before the change event");

        //fire the replica event that will unblock the item
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/heads/branch", "someGerritServer",
                "slaveA", RefReplicated.SUCCEEDED_STATUS));

        assertNull(dispatcher.canRun(item),
                "Item should not be blocked anymore as a newer replica event was received");
    }

    /**
     * Test that it should block item if patchset is replicated to a slave
     * Before the Change Merged event is received.
     */
    @Test
    void shouldBlockItemUntilIfPatchSetIsReplicatedToOneSlaveBeforeDraftPublished() {

        //send the replication event before draft published event
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/changes/1/1/1", "someGerritServer",
                "slaveA", RefReplicated.SUCCEEDED_STATUS));

        DraftPublished dp = Setup.createDraftPublished("someGerritServer", "someProject",
                "refs/changes/1/1/1");
        Item item = createItem(dp, new String[] {"slaveA"});

        assertNull(dispatcher.canRun(item), "Item should not be blocked");
        assertNull(item.getAction(ReplicationFailedAction.class),
                "Item should not be tagged with replicationFailedAction");

    }

    /**
     * Test that it should block item until replication completes to all slaves.
     *
     * For this test case, item is configured with more than one slave(3).
     */
    @Test
    void shouldBlockItemUntilPatchsetIsReplicatedToAllSlaves() {
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
                "refs/changes/1/1/1");
        Item item = createItem(patchsetCreated, new String[] {"slaveA", "slaveB", "slaveC"});

        //item is blocked
        CauseOfBlockage cause = dispatcher.canRun(item);
        assertNotNull(cause, "the item should be blocked");
        assertInstanceOf(WaitingForReplication.class, cause,
                "Should have returned a WaitingForReplication as CauseOfBlockage");
        assertTrue(cause.getShortDescription().contains("slaveA"));
        assertTrue(cause.getShortDescription().contains("slaveB"));
        assertTrue(cause.getShortDescription().contains("slaveC"));

        //send replication event for slaveB
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent(
                "someProject", "refs/changes/1/1/1", "someGerritServer",
                "slaveB", RefReplicated.SUCCEEDED_STATUS));

        //item is still blocked
        cause = dispatcher.canRun(item);
        assertNotNull(cause, "the item should be blocked");
        assertInstanceOf(WaitingForReplication.class, cause,
                "Should have returned a WaitingForReplication as CauseOfBlockage");
        assertTrue(cause.getShortDescription().contains("slaveA"));
        assertFalse(cause.getShortDescription().contains("slaveB"));
        assertTrue(cause.getShortDescription().contains("slaveC"));

        //send replication event for slaveA
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent(
                "someProject", "refs/changes/1/1/1", "someGerritServer",
                "slaveA", RefReplicated.SUCCEEDED_STATUS));

        //item is still blocked
        cause = dispatcher.canRun(item);
        assertNotNull(cause, "the item should be blocked");
        assertInstanceOf(WaitingForReplication.class, cause,
                "Should have returned a WaitingForReplication as CauseOfBlockage");
        assertFalse(cause.getShortDescription().contains("slaveA"));
        assertFalse(cause.getShortDescription().contains("slaveB"));
        assertTrue(cause.getShortDescription().contains("slaveC"));

        //send replication event for slaveC
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/changes/1/1/1", "someGerritServer",
                "slaveC", RefReplicated.SUCCEEDED_STATUS));

        assertNull(dispatcher.canRun(item), "Item should not be blocked");
        assertNull(item.getAction(ReplicationFailedAction.class),
                "Item should not be tagged with replicationFailedAction");
        verify(queueMock, times(1)).maintain();
    }

    /**
     * Test that it should block item until slave replication timeout is reached.
     * @throws InterruptedException if test fails
     */
    @Test
    void shouldBlockItemUntilSlaveReplicationTimeoutIsReached() throws InterruptedException {
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
                "refs/changes/1/1/1");
        Item item = createItem(patchsetCreated, null);
        //setup slaves to have one with timeout
        List<GerritSlave> gerritSlaves = new ArrayList<>();
        gerritSlaves.add(new GerritSlave("slave1", "host1", 0));
        gerritSlaves.add(new GerritSlave("slave2", "host2", 1)); // slave timeout is 1 second
        when(gerritTriggerMock.gerritSlavesToWaitFor("someGerritServer")).thenReturn(gerritSlaves);

        //item is blocked
        CauseOfBlockage cause = dispatcher.canRun(item);
        assertNotNull(cause, "the item should be blocked");
        assertInstanceOf(WaitingForReplication.class, cause,
                "Should have returned a WaitingForReplication as CauseOfBlockage");
        assertTrue(cause.getShortDescription().contains("slave1"));
        assertTrue(cause.getShortDescription().contains("slave2"));

        //wait to reach the timeout
        Thread.sleep(TimeUnit.SECONDS.toMillis(2));

        //timeout reached
        cause = dispatcher.canRun(item);
        assertNull(cause, "Item should not be blocked");
        ReplicationFailedAction replicationFailedAction = item.getAction(ReplicationFailedAction.class);
        assertNotNull(replicationFailedAction, "Item should be tagged with replicationFailedAction");
        assertTrue(replicationFailedAction.getReason().contains("slave2"));
        verify(queueMock, times(0)).maintain();
    }

    /**
     * Test that it should block item until replication fails.
     */
    @Test
    void shouldBlockItemUntilReplicationSucceeds() {
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
                "refs/changes/1/1/1");
        Item item = createItem(patchsetCreated, new String[] {"slaveA", "slaveB", "slaveC"});

        //item is blocked
        CauseOfBlockage cause = dispatcher.canRun(item);
        assertNotNull(cause, "the item should be blocked");
        assertInstanceOf(WaitingForReplication.class, cause,
                "Should have returned a WaitingForReplication as CauseOfBlockage");
        assertTrue(cause.getShortDescription().contains("slaveA"));
        assertTrue(cause.getShortDescription().contains("slaveB"));
        assertTrue(cause.getShortDescription().contains("slaveC"));

        //send replication event for slaveB
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/changes/1/1/1", "someGerritServer",
                "slaveB", RefReplicated.SUCCEEDED_STATUS));

        //item is still blocked
        cause = dispatcher.canRun(item);
        assertNotNull(cause, "the item should be blocked");
        assertInstanceOf(WaitingForReplication.class, cause,
                "Should have returned a WaitingForReplication as CauseOfBlockage");
        assertTrue(cause.getShortDescription().contains("slaveA"));
        assertFalse(cause.getShortDescription().contains("slaveB"));
        assertTrue(cause.getShortDescription().contains("slaveC"));

        //send failed replication event for slaveA
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/changes/1/1/1", "someGerritServer",
                "slaveA", RefReplicated.FAILED_STATUS));

        //send replication event for slaveC
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/changes/1/1/1", "someGerritServer",
                "slaveC", RefReplicated.SUCCEEDED_STATUS));

        assertNotNull(cause, "the item should be blocked");
        assertInstanceOf(WaitingForReplication.class, cause,
                "Should have returned a WaitingForReplication as CauseOfBlockage");
        assertTrue(cause.getShortDescription().contains("slaveA"));

        //send success replication event for slaveA
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/changes/1/1/1", "someGerritServer",
                "slaveA", RefReplicated.SUCCEEDED_STATUS));

        //item is no longer blocked and is tagged to succeed
        cause = dispatcher.canRun(item);
        assertNull(cause, "Item should not be blocked");

        verify(queueMock, times(1)).maintain();
    }

    /**
     * Test that it should not block item when replication is completed before the queue task dispatcher
     * is called to evaluate that queued item.
     *
     * This scenario is very likely if another QueueTaskDispatcher is registered, gets called before our and block the
     * build for sometime(e.g. ThrottleConcurrentBuild plugin).
     */
    @Test
    void shouldNotBlockItemWhenReplicationIsCompletedBeforeDispatcherIsCalled() {
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
                "refs/changes/1/1/1");
        Item item = createItem(patchsetCreated, new String[] {"slaveA", "slaveB"});

        //send replication event for slaveB
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/changes/1/1/1", "someGerritServer",
                "slaveB", RefReplicated.SUCCEEDED_STATUS));

        //send replication event for slaveA
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/changes/1/1/1", "someGerritServer",
                "slaveA", RefReplicated.SUCCEEDED_STATUS));

        assertNull(dispatcher.canRun(item), "Item should not be blocked");
        verify(queueMock, times(0)).maintain();
    }

    /**
     * Test that it should block item until the proper replication event is received.A proper replication event
     * is one that its timestamp is after the event timestamp.
     *
     * This scenario is only likely to happen for RefUpdated event, for example, getting subsequent refReplicated
     * events for the same ref (e.g. /refs/heads/master).
     */
    @Test
    void shouldBlockItemUntilProperReplicationEventIsReceived() {
        //send replication events created before the actual event
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/heads/master", "someGerritServer",
                "slaveB", RefReplicated.SUCCEEDED_STATUS));
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/heads/master", "someGerritServer",
                "slaveA", RefReplicated.SUCCEEDED_STATUS));

        RefUpdated refUpdated = Setup.createRefUpdated("someGerritServer", "someProject",
                "master");
        Item item = createItem(refUpdated, new String[] {"slaveA", "slaveB"});

        //item is blocked since the cached replication events are time stamped before the actual event
        CauseOfBlockage cause = dispatcher.canRun(item);
        assertNotNull(cause, "the item should be blocked");
        assertInstanceOf(WaitingForReplication.class, cause,
                "Should have returned a WaitingForReplication as CauseOfBlockage");
        assertTrue(cause.getShortDescription().contains("slaveA"));
        assertTrue(cause.getShortDescription().contains("slaveB"));

        //send replication events created after the actual event
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/heads/master", "someGerritServer",
                "slaveB", RefReplicated.SUCCEEDED_STATUS));
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/heads/master", "someGerritServer",
                "slaveA", RefReplicated.SUCCEEDED_STATUS));

        assertNull(dispatcher.canRun(item), "Item should not be blocked");
        verify(queueMock, times(1)).maintain();
    }

    /**
     * Test that it should not block item when the elapsed time between the event reception time stamp and the
     * dispatcher get called is greater than the cache expiration time.
     *
     * Lets say that the cache expires after 6 hours, a build enter the queue and gets blocked by another
     * QueueTaskDispatcher for 7 hours and this dispatcher gets called after, we assume that we received the
     * replication event. We cannot cache the events forever otherwise will end up with serious memory issue.
     */
    @Test
    void shouldNotBlockItemWhenElapsedTimeIsGreaterThenCacheExpirationTime() {
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
                "refs/heads/master");
        patchsetCreated.setReceivedOn(System.currentTimeMillis()
                - TimeUnit.MINUTES.toMillis(ReplicationCache.DEFAULT_EXPIRATION_IN_MINUTES));
        Item item = createItem(patchsetCreated, new String[] {"slaveA", "slaveB"});
        assertNull(dispatcher.canRun(item), "Item should not be blocked");
        verify(queueMock, times(0)).maintain();
    }

    /**
     * Test that it should fire queue maintenance maximum one time per replication event received.
     *
     * If more than one item is unblocked by a replication event, the dispatcher should update all the blocked
     * item before calling the maintenance of the queue.
     */
    @Test
    void shouldFireQueueMaintenanceMaximumOneTimePerReplicationEventReceived() {
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
                "refs/changes/1/1/1");
        Item item1 = createItem(patchsetCreated, new String[] {"slaveA"});
        Item item2 = createItem(patchsetCreated, new String[] {"slaveA"});

        //items are blocked
        CauseOfBlockage cause = dispatcher.canRun(item1);
        assertNotNull(cause, "the item should be blocked");
        assertInstanceOf(WaitingForReplication.class, cause,
                "Should have returned a WaitingForReplication as CauseOfBlockage");
        cause = dispatcher.canRun(item2);
        assertNotNull(cause, "the item should be blocked");
        assertInstanceOf(WaitingForReplication.class, cause,
                "Should have returned a WaitingForReplication as CauseOfBlockage");

        //send replication event for slaveA
        dispatcher.gerritEvent(Setup.createRefReplicatedEvent("someProject", "refs/changes/1/1/1", "someGerritServer",
                "slaveA", RefReplicated.SUCCEEDED_STATUS));


        assertNull(dispatcher.canRun(item1), "Item should not be blocked");
        assertNull(item1.getAction(ReplicationFailedAction.class),
                "Item should not be tagged with replicationFailedAction");
        assertNull(dispatcher.canRun(item2), "Item should not be blocked");
        assertNull(item2.getAction(ReplicationFailedAction.class),
                "Item should not be tagged with replicationFailedAction");
        verify(queueMock, times(1)).maintain();
    }

    /**
     * Create a queue item caused by the specified gerritEvent configure to wait for replication
     * to one slave.
     * @param gerritEvent The gerritEvent
     * @return the queue item
     */
    private Item createItemWithOneSlave(GerritTriggeredEvent gerritEvent) {
        return createItem(gerritEvent, new String[]{"someSlave"});
    }

    /**
     * Create a queue item caused by the specified gerritEvent configure to wait for replication
     * to specified slaves.
     * @param gerritEvent The gerritEvent
     * @param slaves The slaves
     * @return the queue item
     */
    private Item createItem(GerritTriggeredEvent gerritEvent, String[] slaves) {
        GerritCause gerritCause;
        if (gerritEvent instanceof ManualPatchsetCreated) {
            gerritCause = new GerritManualCause();
        } else {
            gerritCause = new GerritCause();
        }
        gerritCause.setEvent(gerritEvent);
        return createItem(gerritCause, slaves);
    }

    /**
     * Create a queue item caused by the specified gerritCause configure to wait for replication
     * to one slave.
     * @param gerritCause The gerritCause
     * @return the queue item
     */
    private Item createItemWithOneSlave(GerritCause gerritCause) {
        return createItem(gerritCause, new String[]{"someSlave"});
    }

    /**
     * Create a queue item caused by the specified gerritCause configure to wait for replication
     * to specified slaves.
     * @param gerritCause The gerritCause
     * @param slaves The slaves
     * @return the queue item
     */
    private Item createItem(GerritCause gerritCause, String[] slaves) {
        List<Action> actions = new ArrayList<>();
        actions.add(new CauseAction(gerritCause));

        abstractProjectMock = mock(AbstractProject.class);
        Setup.setTrigger(gerritTriggerMock, abstractProjectMock);
        if (slaves != null && slaves.length > 0) {
            List<GerritSlave> gerritSlaves = new ArrayList<>();
            for (String slave : slaves) {
                gerritSlaves.add(new GerritSlave(slave + " display name", slave , 0));
            }
            when(gerritTriggerMock.gerritSlavesToWaitFor(any(String.class))).thenReturn(gerritSlaves);
        }
        return new MockWaitingItem(abstractProjectMock, actions);
    }
}
