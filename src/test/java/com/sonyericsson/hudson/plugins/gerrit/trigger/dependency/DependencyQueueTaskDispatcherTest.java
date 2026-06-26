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

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerDescriptor;
import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.Queue.WaitingItem;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.queue.CauseOfBlockage;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import org.junit.jupiter.api.AfterEach;

import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.sonymobile.tools.gerrit.gerritevents.GerritDefaultValues;
import com.sonymobile.tools.gerrit.gerritevents.GerritHandler;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritManualCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;

/**
 * Tests {@link com.sonyericsson.hudson.plugins.gerrit.trigger.dependency.DependencyQueueTaskDispatcher}.
 * @author Yannick Bréhon &lt;yannick.brehon@smartmatic.com&gt;
 */
class DependencyQueueTaskDispatcherTest {

    private DependencyQueueTaskDispatcher dispatcher;
    private Queue queueMock;
    private GerritHandler gerritHandlerMock;
    private Map<TriggerDescriptor, Trigger<?>> triggers;
    private GerritTrigger gerritTriggerMock;
    private AbstractProject<?, ?> abstractProjectMock;
    private AbstractProject<?, ?> abstractProjectDependencyMock;
    private Jenkins jenkinsMock;
    private ToGerritRunListener toGerritRunListenerMock;
    private MockedStatic<Jenkins> jenkinsMockedStatic;
    private MockedStatic<ToGerritRunListener> runListenerMockedStatic;

    /**
     * Create DependencyQueueTaskDispatcher with a mocked GerritHandler.
     */
    @BeforeEach
    void setUp() {
        gerritHandlerMock = mock(GerritHandler.class);
        dispatcher = new DependencyQueueTaskDispatcher(gerritHandlerMock);
        gerritTriggerMock = mock(GerritTrigger.class);
        triggers = new HashMap<>();
        triggers.put(new GerritTriggerDescriptor(), gerritTriggerMock);
        queueMock = mock(Queue.class);
        jenkinsMock = mock(Jenkins.class);
        when(jenkinsMock.getQueue()).thenReturn(queueMock);
        ExtensionList<TransientActionFactory> list = mock(ExtensionList.class);
        List<TransientActionFactory> emptyList = Collections.emptyList();
        Iterator<TransientActionFactory> iterator = emptyList.iterator();
        when(list.iterator()).thenReturn(iterator);
        when(jenkinsMock.getExtensionList(same(TransientActionFactory.class))).thenReturn(list);
        jenkinsMockedStatic = mockStatic(Jenkins.class);
        jenkinsMockedStatic.when(Jenkins::get).thenReturn(jenkinsMock);
        toGerritRunListenerMock = mock(ToGerritRunListener.class);
        runListenerMockedStatic = mockStatic(ToGerritRunListener.class);
        runListenerMockedStatic.when(ToGerritRunListener::getInstance).thenReturn(toGerritRunListenerMock);
    }

    @AfterEach
    void tearDown() {
        jenkinsMockedStatic.close();
        runListenerMockedStatic.close();
    }

    /**
     * Test that it should register as event listener on init.
     */
    @Test
    void shouldRegisterAsEventListenerOnInit() {
        verify(gerritHandlerMock, times(1)).addListener(dispatcher);
    }

    /**
     * Test that it should not block an item the task of which is not an AbstractProject.
     */
    @Test
    void shouldNotBlockNonAbstractProjects() {
        List<Action> actions = new ArrayList<>();
        WaitingItem item = new WaitingItem(Calendar.getInstance(), null, actions);
        CauseOfBlockage cause = dispatcher.canRun(new Queue.BuildableItem(item));
        assertNull(cause, "Build should not be blocked");
    }

    /**
     * Test that it should not block item without a gerritCause.
     */
    @Test
    void shouldNotBlockItemdWithoutGerritCause() {
        assertNull(dispatcher.canRun(mock(Queue.Item.class)), "Build should not be blocked");
    }

    /**
     * Test that it should not block item with GerritCause that does not contain a GerritEvent.
     *
     * Not sure this is a valid use case under normal use but GerritCause have
     * an empty constructor that do not value to GerritEvent so, let's test it.
     */
    @Test
    void shouldNotBlockItemWithGerritCauseWithoutGerritEvent() {
        assertNull(dispatcher.canRun(createItem(new GerritCause(), null)), "Build should not be blocked");
    }

    /**
     * Test that it should not block buildable items.
     *
     * An Item is first checked when in the Queue. Once it canRun(), it is checked again when the executor
     * actually pulls it from the queue. But at that time, dependency-wise, we don't care anymore.
     */
    @Test
    void shouldNotBlockBuildableItem() {
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
            "refs/changes/1/1/1");
        Queue.Item item = createItem(patchsetCreated, null);

        CauseOfBlockage cause = dispatcher.canRun(new Queue.BuildableItem((WaitingItem)item));
        assertNull(cause, "Build should not be blocked");
    }

    /**
     * Test that it should not block item without GerritTrigger configured.
     */
    @Test
    void shouldNotBlockItemWithoutGerritTrigger() {
        Queue.Item item = createItem(new PatchsetCreated(), null);
        when(abstractProjectMock.getTrigger(GerritTrigger.class)).thenReturn(null);
        assertNull(dispatcher.canRun(item), "Build should not be blocked");
    }

    /**
     * Test that it should not block item which has no dependencies.
     */
    @Test
    void shouldNotBlockItemWithNoDependencies() {
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
            "refs/changes/1/1/1");
        Queue.Item item = createItem(patchsetCreated, "");
        assertNull(dispatcher.canRun(item), "Build should not be blocked");
    }

    /**
     * Test that it should not block item which has null dependencies.
     */
    @Test
    void shouldNotBlockItemWithNullDependencies() {
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
            "refs/changes/1/1/1");
        Queue.Item item = createItem(patchsetCreated, null);
        assertNull(dispatcher.canRun(item), "Build should not be blocked");
    }

    /**
     * Test that an event marked as onTriggering blocks any build linked to it, and after
     * onDoneTriggeringAll, the build should wait for the dependency to be done, and then should build.
     */
    @Test
    void shouldBlockTriggeringEvents() {
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
            "refs/changes/1/1/1");
        dispatcher.onTriggeringAll(patchsetCreated);
        Queue.Item item = createItem(patchsetCreated, "upstream");
        CauseOfBlockage cause = dispatcher.canRun(item);
        assertNotNull(cause, "Build should be blocked");
        dispatcher.onDoneTriggeringAll(patchsetCreated);

        //Setting the dependency as "triggered but not built"
        setBuilding(patchsetCreated, true);
        cause = dispatcher.canRun(item);
        assertNotNull(cause, "Build should be blocked");

        //Setting the dependency as "triggered and built"
        setBuilding(patchsetCreated, false);
        cause = dispatcher.canRun(item);
        assertNull(cause, "Build should not be blocked");
    }

    /**
     * Test that an job is waiting for parent if parent was not triggered, but is interested in event.
     */
    @Test
    void shouldBlockNonTriggeringButInterestingEvents() {
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
                "refs/changes/1/1/1");
        Queue.Item item = createItem(patchsetCreated, "upstream");
        makeGerritInterestedInEvent(patchsetCreated);

        setTriggered(patchsetCreated, false);

        CauseOfBlockage cause = dispatcher.canRun(item);
        assertNotNull(cause, "Build should be blocked");
        assertThat(cause, instanceOf(BecauseWaitingForOtherProjectsToTrigger.class));
    }

    /**
     * Sets GerritTrigger interested in specifed gerrit event.
     * @param patchsetCreated patch set.
     */
    public void makeGerritInterestedInEvent(PatchsetCreated patchsetCreated) {
        GerritTrigger upstreamGerritTriggerMock = mock(GerritTrigger.class);
        when(abstractProjectDependencyMock.getTriggers()).thenReturn(
                Collections.singletonMap(
                        new GerritTriggerDescriptor(), upstreamGerritTriggerMock));
        when(abstractProjectDependencyMock.getTrigger(GerritTrigger.class)).thenReturn(upstreamGerritTriggerMock);
        when(upstreamGerritTriggerMock.isInteresting(patchsetCreated)).thenReturn(true);
    }

    /**
     * Moves patchset to specified triggered state.
     * @param patchsetCreated patch set.
     * @param triggered was patch set triggered or not.
     */
    public void setTriggered(PatchsetCreated patchsetCreated, boolean triggered) {
        when(toGerritRunListenerMock.isTriggered(abstractProjectDependencyMock, patchsetCreated)).thenReturn(triggered);
        when(toGerritRunListenerMock.isBuilding(abstractProjectDependencyMock, patchsetCreated)).thenReturn(false);
    }

    /**
     * Moves patchset to state "was triggered" and in specified isBuilding state.
     * @param patchsetCreated patch set.
     * @param isBuilding is patch set still building.
     */
    public void setBuilding(PatchsetCreated patchsetCreated, boolean isBuilding) {
        when(toGerritRunListenerMock.isTriggered(abstractProjectDependencyMock, patchsetCreated)).thenReturn(true);
        when(toGerritRunListenerMock.isBuilding(abstractProjectDependencyMock, patchsetCreated)).thenReturn(isBuilding);
    }

    /**
     * Test that child job contains the list of builds it depends on as a StringParameters.
     */
    @Test
    void unblockedItemContainsParamsFromDependencies() {
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
                "refs/changes/1/1/1");

        Queue.Item item = createItem(patchsetCreated, "upstream");
        dispatcher.onDoneTriggeringAll(patchsetCreated);

        List<Run> runs = new ArrayList<>();
        AbstractBuild build = Mockito.mock(AbstractBuild.class);
        runs.add(build);
        when(build.getResult()).thenReturn(Result.SUCCESS);
        when(build.getNumber()).thenReturn(1);
        when(build.getParent()).thenReturn(abstractProjectDependencyMock);
        EnvVars envVars = Mockito.mock(EnvVars.class);
        when(envVars.put(anyString(), anyString())).thenReturn("");

        doReturn(runs).when(toGerritRunListenerMock).getRuns(same(patchsetCreated));
        dispatcher.canRun(item);

        GerritDependencyAction dependencyAction = item.getAction(GerritDependencyAction.class);
        assertNotNull(dependencyAction);

        dependencyAction.buildEnvVars(build, envVars);

        verify(envVars).put("TRIGGER_upstream_BUILD_NAME", "upstream");
        verify(envVars).put("TRIGGER_upstream_BUILD_NUMBER", "1");
        verify(envVars).put("TRIGGER_upstream_BUILD_RESULT", "SUCCESS");
        verify(envVars).put("TRIGGER_DEPENDENCY_KEYS", "upstream");
    }

    /**
     * Test that it should not block a project whose dependencies are all built.
     */
    @Test
    void shouldNotBlockIfDependenciesAreBuilt() {
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
            "refs/changes/1/1/1");
        Queue.Item item = createItem(patchsetCreated, "upstream");
        makeGerritInterestedInEvent(patchsetCreated);
        //Setting the dependency as "triggered and built"
        setBuilding(patchsetCreated, false);
        CauseOfBlockage cause = dispatcher.canRun(item);
        assertNull(cause, "Build should not be blocked");
    }

    /**
     * Test that it should block currently triggering events of a ManualPatchsetCreated
     * and then let them run free once triggering is done.
     */
    @Test
    void shouldBlockTriggeringManualPatchsetCreated() {
        ManualPatchsetCreated manualPatchsetCreated = spy(Setup.createManualPatchsetCreated());
        //Event notification
        dispatcher.gerritEvent(manualPatchsetCreated);
        //Lifecycle handler should be notified of listening intent
        verify(manualPatchsetCreated, times(1)).addListener(dispatcher);
        Queue.Item item = createItem(manualPatchsetCreated, "upstream");
        CauseOfBlockage cause = dispatcher.canRun(item);
        assertNotNull(cause, "Build should be blocked");
        dispatcher.triggerScanDone(manualPatchsetCreated);
        //Lifecycle handler should be notified of remove listener
        verify(manualPatchsetCreated, times(1)).removeListener(dispatcher);
        //Setting the dependency as "triggered but not built"
        setBuilding(manualPatchsetCreated, true);
        cause = dispatcher.canRun(item);
        assertNotNull(cause, "Build should be blocked");
        //Setting the dependency as "triggered and built"
        doReturn(false).when(toGerritRunListenerMock).
                isBuilding(abstractProjectDependencyMock, manualPatchsetCreated);
        cause = dispatcher.canRun(item);
        assertNull(cause, "Build should not be blocked");
    }

    /**
     * Create a queue item caused by the specified gerritEvent.
     * @param gerritEvent The gerritEvent
     * @param dependency The dependency project list in a comma-separated string to add to the queue item.
     * @return the queue item
     */
    private Queue.Item createItem(GerritTriggeredEvent gerritEvent, String dependency) {
        GerritCause gerritCause;
        if (gerritEvent instanceof ManualPatchsetCreated) {
            gerritCause = new GerritManualCause();
        } else {
            gerritCause = new GerritCause();
        }
        gerritCause.setEvent(gerritEvent);
        return createItem(gerritCause, dependency);
    }

    /**
     * Create a queue item caused by the specified gerritCause.
     * @param gerritCause The gerritCause
     * @param dependency The dependency project list in a comma-separated string to add to the queue item.
     * @return the queue item
     */
    private Queue.Item createItem(GerritCause gerritCause, String dependency) {
        List<Action> actions = new ArrayList<>();
        actions.add(new CauseAction(gerritCause));

        abstractProjectMock = mock(AbstractProject.class);
        when(abstractProjectMock.getTrigger(GerritTrigger.class)).thenReturn(gerritTriggerMock);
        when(abstractProjectMock.getTriggers()).thenReturn(triggers);
        abstractProjectDependencyMock = mock(AbstractProject.class);
        when(abstractProjectDependencyMock.getTrigger(GerritTrigger.class)).thenReturn(gerritTriggerMock);
        when(gerritTriggerMock.getDependencyJobsNames()).thenReturn(dependency);
        when(jenkinsMock.getItem(eq(dependency), any(Item.class), same(Item.class)))
                .thenReturn(abstractProjectDependencyMock);

        //ItemGroup abstractProjectDependencyMockParent = mock(ItemGroup.class);
        //doReturn("").when(abstractProjectDependencyMockParent).getFullName();
        doReturn(jenkinsMock).when(abstractProjectDependencyMock).getParent();
        when(abstractProjectDependencyMock.getName()).thenReturn(dependency);
        when(abstractProjectDependencyMock.getFullName()).thenReturn(dependency);

        WaitingItem waitingItem = spy(new WaitingItem(Calendar.getInstance(),
                abstractProjectMock, actions));
        when(waitingItem.getInQueueSince()).thenReturn(System.currentTimeMillis()
                - TimeUnit.SECONDS.toMillis(GerritDefaultValues.DEFAULT_BUILD_SCHEDULE_DELAY));
        return waitingItem;
    }

}
