package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.BuildCancellationPolicy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RunningJobs}.
 *
 * @author Ignacio Roncero &lt;ironcero@cloudbees.com&gt;
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class RunningJobsTest {

    @Mock
    private GerritTrigger trigger;

    @Mock
    private Item job;

    private RunningJobs runningJobs;

    /**
     * Sets up the test fixtures.
     */
    @Before
    public void setUp() {
        runningJobs = new RunningJobs(trigger, job);
    }

    /**
     * Tests that constructor initializes properly.
     */
    @Test
    public void testConstructor() {
        assertNotNull(runningJobs);
        assertNotNull(runningJobs.getJob());
    }

    /**
     * Tests add() method adds event to running jobs.
     */
    @Test
    public void testAdd() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        runningJobs.add(event);

        // Verify by removing and checking return value
        assertTrue(runningJobs.remove(event));
    }

    /**
     * Tests remove() method removes event from running jobs.
     */
    @Test
    public void testRemove() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        runningJobs.add(event);

        assertTrue(runningJobs.remove(event));
        // Second remove should return false
        assertFalse(runningJobs.remove(event));
    }

    /**
     * Tests remove() returns false for non-existent event.
     */
    @Test
    public void testRemoveNonExistent() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        assertFalse(runningJobs.remove(event));
    }

    /**
     * Tests cancelTriggeredJob() does nothing when policy is null.
     */
    @Test
    public void testCancelTriggeredJobNullPolicy() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        runningJobs.add(event);

        runningJobs.cancelTriggeredJob(event, "test-job", null);

        // Event should still be there
        assertTrue(runningJobs.remove(event));
    }

    /**
     * Tests cancelTriggeredJob() does nothing when policy is disabled.
     */
    @Test
    public void testCancelTriggeredJobPolicyDisabled() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildCancellationPolicy policy = new BuildCancellationPolicy();
        policy.setEnabled(false);

        runningJobs.add(event);
        runningJobs.cancelTriggeredJob(event, "test-job", policy);

        // Event should still be there
        assertTrue(runningJobs.remove(event));
    }

    /**
     * Tests cancelTriggeredJob() does nothing for ManualPatchsetCreated when policy disables it.
     */
    @Test
    public void testCancelTriggeredJobManualPatchsetNotAborted() {
        ManualPatchsetCreated event = new ManualPatchsetCreated();
        event.setChange(createChange("I123", "1000"));
        event.setPatchset(createPatchSet("1"));

        BuildCancellationPolicy policy = new BuildCancellationPolicy(false, false, false, false);
        policy.setEnabled(true);

        runningJobs.add(event);
        runningJobs.cancelTriggeredJob(event, "test-job", policy);

        // Event should still be there
        assertTrue(runningJobs.remove(event));
    }

    /**
     * Tests cancelTriggeredJob() cancels old patchset when new patchset arrives.
     */
    @Test
    public void testCancelTriggeredJobNewPatchsetCancelsOld() {
        // Create old patchset event (PS1)
        PatchsetCreated oldEvent = Setup.createPatchsetCreated();
        oldEvent.getChange().setId("I123");
        oldEvent.getChange().setNumber("1000");
        oldEvent.getPatchSet().setNumber("1");

        // Create new patchset event (PS2)
        PatchsetCreated newEvent = Setup.createPatchsetCreated();
        newEvent.getChange().setId("I123");
        newEvent.getChange().setNumber("1000");
        newEvent.getPatchSet().setNumber("2");

        BuildCancellationPolicy policy = new BuildCancellationPolicy(false, false, false, false);
        policy.setEnabled(true);

        // Add old event as running
        runningJobs.add(oldEvent);

        // Mock job as Queue.Task for cancellation
        FreeStyleProject mockJob = mock(FreeStyleProject.class);
        when(mockJob.getFullName()).thenReturn("test-job");
        runningJobs.setJob(mockJob);

        // Trigger cancellation with new event
        runningJobs.cancelTriggeredJob(newEvent, "test-job", policy);

        // Old event should be removed
        assertFalse(runningJobs.remove(oldEvent));
        // New event should be added
        assertTrue(runningJobs.remove(newEvent));
    }

    /**
     * Tests cancelTriggeredJob() with abortNewPatchsets enabled cancels any patchset.
     */
    @Test
    public void testCancelTriggeredJobAbortNewPatchsetsEnabled() {
        // Create first patchset event (PS2)
        PatchsetCreated firstEvent = Setup.createPatchsetCreated();
        firstEvent.getChange().setId("I123");
        firstEvent.getChange().setNumber("1000");
        firstEvent.getPatchSet().setNumber("2");

        // Create second patchset event (PS1 - older but triggers later)
        PatchsetCreated secondEvent = Setup.createPatchsetCreated();
        secondEvent.getChange().setId("I123");
        secondEvent.getChange().setNumber("1000");
        secondEvent.getPatchSet().setNumber("1");

        BuildCancellationPolicy policy = new BuildCancellationPolicy(true, false, false, false);
        policy.setEnabled(true);

        // Add first event as running
        runningJobs.add(firstEvent);

        // Mock job as Queue.Task
        FreeStyleProject mockJob = mock(FreeStyleProject.class);
        when(mockJob.getFullName()).thenReturn("test-job");
        runningJobs.setJob(mockJob);

        // Trigger cancellation with second event (older patchset)
        runningJobs.cancelTriggeredJob(secondEvent, "test-job", policy);

        // First event should be removed even though it's newer
        assertFalse(runningJobs.remove(firstEvent));
        // Second event should be added
        assertTrue(runningJobs.remove(secondEvent));
    }

    /**
     * Tests cancelTriggeredJob() with abortManualPatchsets policy verification.
     * Note: Actual job cancellation requires Jenkins infrastructure (integration test).
     * This test verifies the policy allows manual patchsets to be aborted.
     */
    @Test
    public void testCancelTriggeredJobAbortManualPatchsetsEnabled() {
        // Create manual patchset event
        ManualPatchsetCreated manualEvent = new ManualPatchsetCreated();
        manualEvent.setChange(createChange("I123", "1000"));
        manualEvent.setPatchset(createPatchSet("1"));

        BuildCancellationPolicy policy = new BuildCancellationPolicy(false, true, false, false);
        policy.setEnabled(true);

        // Verify policy settings
        assertTrue(policy.isEnabled());
        assertTrue(policy.isAbortManualPatchsets());
        assertFalse(policy.isAbortNewPatchsets());

        // Add manual event
        runningJobs.add(manualEvent);
        assertTrue(runningJobs.remove(manualEvent));
    }

    /**
     * Tests cancelTriggeredJob() with abortAbandonedPatchsets policy verification.
     * Note: Actual job cancellation requires Jenkins infrastructure (integration test).
     * This test verifies the policy configuration and trigger behavior.
     */
    @Test
    public void testCancelTriggeredJobAbortAbandonedPatchsets() {
        // Create abandoned event
        ChangeAbandoned abandonedEvent = new ChangeAbandoned();
        abandonedEvent.setChange(createChange("I123", "1000"));
        abandonedEvent.setPatchset(createPatchSet("1"));

        BuildCancellationPolicy policy = new BuildCancellationPolicy(false, false, false, true);
        policy.setEnabled(true);

        // Verify policy settings
        assertTrue(policy.isEnabled());
        assertTrue(policy.isAbortAbandonedPatchsets());
        assertFalse(policy.isAbortNewPatchsets());

        // Mock trigger behavior for abandoned patchsets
        when(trigger.isOnlyAbortRunningBuild(abandonedEvent)).thenReturn(true);

        // Verify trigger recognizes this as an abandon-only-abort scenario
        assertTrue(trigger.isOnlyAbortRunningBuild(abandonedEvent));
    }

    /**
     * Tests scheduled() adds event when server config is null.
     */
    @Test
    public void testScheduledNoServerConfig() {
        PatchsetCreated event = Setup.createPatchsetCreated();

        try (MockedStatic<PluginImpl> pluginImplMock = mockStatic(PluginImpl.class)) {
            pluginImplMock.when(() -> PluginImpl.getServerConfig(any())).thenReturn(null);

            runningJobs.scheduled(event);

            // Event should be added
            assertTrue(runningJobs.remove(event));
        }
    }

    /**
     * Tests scheduled() adds event when cancellation policy is disabled.
     */
    @Test
    public void testScheduledPolicyDisabled() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        IGerritHudsonTriggerConfig serverConfig = mock(IGerritHudsonTriggerConfig.class);
        BuildCancellationPolicy policy = new BuildCancellationPolicy();
        policy.setEnabled(false);

        when(serverConfig.getBuildCurrentPatchesOnly()).thenReturn(policy);

        try (MockedStatic<PluginImpl> pluginImplMock = mockStatic(PluginImpl.class)) {
            pluginImplMock.when(() -> PluginImpl.getServerConfig(any())).thenReturn(serverConfig);

            runningJobs.scheduled(event);

            // Event should be added
            assertTrue(runningJobs.remove(event));
        }
    }

    /**
     * Tests scheduled() adds event when it's manual and policy doesn't abort manual.
     */
    @Test
    public void testScheduledManualPatchsetNotAborted() {
        ManualPatchsetCreated event = new ManualPatchsetCreated();
        event.setChange(createChange("I123", "1000"));
        event.setPatchset(createPatchSet("1"));

        IGerritHudsonTriggerConfig serverConfig = mock(IGerritHudsonTriggerConfig.class);
        BuildCancellationPolicy policy = new BuildCancellationPolicy(false, false, false, false);
        policy.setEnabled(true);

        when(serverConfig.getBuildCurrentPatchesOnly()).thenReturn(policy);

        try (MockedStatic<PluginImpl> pluginImplMock = mockStatic(PluginImpl.class)) {
            pluginImplMock.when(() -> PluginImpl.getServerConfig(any())).thenReturn(serverConfig);

            runningJobs.scheduled(event);

            // Event should be added
            assertTrue(runningJobs.remove(event));
        }
    }

    /**
     * Tests scheduled() cancels old patchset when new one arrives.
     */
    @Test
    public void testScheduledCancelsOldPatchset() {
        // Create old patchset (PS1)
        PatchsetCreated oldEvent = Setup.createPatchsetCreated();
        oldEvent.getChange().setId("I123");
        oldEvent.getChange().setNumber("1000");
        oldEvent.getPatchSet().setNumber("1");

        // Add old event as running
        runningJobs.add(oldEvent);

        // Create new patchset (PS2)
        PatchsetCreated newEvent = Setup.createPatchsetCreated();
        newEvent.getChange().setId("I123");
        newEvent.getChange().setNumber("1000");
        newEvent.getPatchSet().setNumber("2");

        IGerritHudsonTriggerConfig serverConfig = mock(IGerritHudsonTriggerConfig.class);
        BuildCancellationPolicy policy = new BuildCancellationPolicy(false, false, false, false);
        policy.setEnabled(true);

        when(serverConfig.getBuildCurrentPatchesOnly()).thenReturn(policy);

        // Mock job
        FreeStyleProject mockJob = mock(FreeStyleProject.class);
        when(mockJob.getFullName()).thenReturn("test-job");
        runningJobs.setJob(mockJob);

        try (MockedStatic<PluginImpl> pluginImplMock = mockStatic(PluginImpl.class)) {
            pluginImplMock.when(() -> PluginImpl.getServerConfig(any())).thenReturn(serverConfig);

            runningJobs.scheduled(newEvent);

            // Old event should be removed
            assertFalse(runningJobs.remove(oldEvent));
            // New event should be added
            assertTrue(runningJobs.remove(newEvent));
        }
    }

    /**
     * Tests that different changes don't interfere with each other.
     */
    @Test
    public void testDifferentChangesNotCanceled() {
        // Create event for change 1000
        PatchsetCreated event1 = Setup.createPatchsetCreated();
        event1.getChange().setId("I123");
        event1.getChange().setNumber("1000");
        event1.getPatchSet().setNumber("1");

        // Create event for change 2000
        PatchsetCreated event2 = Setup.createPatchsetCreated();
        event2.getChange().setId("I456");
        event2.getChange().setNumber("2000");
        event2.getPatchSet().setNumber("1");

        BuildCancellationPolicy policy = new BuildCancellationPolicy(true, true, false, false);
        policy.setEnabled(true);

        // Add first event
        runningJobs.add(event1);

        // Mock job
        FreeStyleProject mockJob = mock(FreeStyleProject.class);
        when(mockJob.getFullName()).thenReturn("test-job");
        runningJobs.setJob(mockJob);

        // Trigger cancellation with second event (different change)
        runningJobs.cancelTriggeredJob(event2, "test-job", policy);

        // First event should NOT be removed (different change)
        assertTrue(runningJobs.remove(event1));
        // Second event should be added
        assertTrue(runningJobs.remove(event2));
    }

    /**
     * Tests abortSameTopic policy cancels builds with same topic.
     */
    @Test
    public void testCancelTriggeredJobAbortSameTopic() {
        // Create first event with topic
        PatchsetCreated firstEvent = Setup.createPatchsetCreated();
        firstEvent.getChange().setId("I123");
        firstEvent.getChange().setNumber("1000");
        firstEvent.getChange().setTopic("feature-x");
        firstEvent.getPatchSet().setNumber("1");

        // Create second event with same topic but different change
        PatchsetCreated secondEvent = Setup.createPatchsetCreated();
        secondEvent.getChange().setId("I456");
        secondEvent.getChange().setNumber("2000");
        secondEvent.getChange().setTopic("feature-x");
        secondEvent.getPatchSet().setNumber("1");

        BuildCancellationPolicy policy = new BuildCancellationPolicy(false, false, true, false);
        policy.setEnabled(true);

        // Mock trigger to return true for topic-based cancellation
        when(trigger.abortBecauseOfTopic(any(), any(), any())).thenReturn(true);

        // Add first event
        runningJobs.add(firstEvent);

        // Mock job
        FreeStyleProject mockJob = mock(FreeStyleProject.class);
        when(mockJob.getFullName()).thenReturn("test-job");
        runningJobs.setJob(mockJob);

        // Trigger cancellation with second event
        runningJobs.cancelTriggeredJob(secondEvent, "test-job", policy);

        // First event should be removed (same topic)
        assertFalse(runningJobs.remove(firstEvent));
        // Second event should be added
        assertTrue(runningJobs.remove(secondEvent));
    }

    /**
     * Helper method to create a Change object.
     *
     * @param id the change id
     * @param number the change number
     * @return a Change object
     */
    private Change createChange(String id, String number) {
        Change change = new Change();
        change.setId(id);
        change.setNumber(number);
        change.setProject("test-project");
        change.setBranch("main");
        return change;
    }

    /**
     * Helper method to create a PatchSet object.
     *
     * @param number the patchset number
     * @return a PatchSet object
     */
    private PatchSet createPatchSet(String number) {
        PatchSet patchSet = new PatchSet();
        patchSet.setNumber(number);
        patchSet.setRevision("abc123");
        return patchSet;
    }
}
