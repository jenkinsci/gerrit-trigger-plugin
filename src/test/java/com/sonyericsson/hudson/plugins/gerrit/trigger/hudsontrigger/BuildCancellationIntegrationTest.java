package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.BuildCancellationPolicy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.TestUtils;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;

import java.util.Collections;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import hudson.model.Result;
import org.apache.sshd.server.SshServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.recipes.LocalData;

import static com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock.GERRIT_STREAM_EVENTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for build cancellation feature that verify actual job cancellation
 * in Jenkins queue and running executors.
 *
 * @author Ignacio Roncero &lt;ironcero@cloudbees.com&gt;
 */
public class BuildCancellationIntegrationTest {

    /**
     * An instance of Jenkins Rule.
     */
    //CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule jenkins = new JenkinsRule();

    /**
     * Outputs build logs to std out.
     */
    //CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final BuildWatcher watcher = new BuildWatcher();

    private SshServer sshd;
    private SshdServerMock serverMock;
    private GerritServer gerritServer;
    private static final int BUILD_TIMEOUT = 30000;
    private static final int SERVER_WAIT = 2000;
    private static final int SLEEP_10_SEC = 10000;
    private static final int SLEEP_5_SEC = 5000;
    private static final int SLEEP_HALF_SEC = 500;

    /**
     * Sets up the SSH server mock before each test.
     *
     * @throws Exception if setup fails
     */
    @Before
    public void setUp() throws Exception {
        SshdServerMock.generateKeyPair();
        serverMock = new SshdServerMock();
        sshd = SshdServerMock.startServer(serverMock);
        serverMock.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        serverMock.returnCommandFor(GERRIT_STREAM_EVENTS, SshdServerMock.CommandMock.class);
        serverMock.returnCommandFor("gerrit review.*", SshdServerMock.EofCommandMock.class);
        serverMock.returnCommandFor("gerrit version", SshdServerMock.SendVersionCommand.class);
        gerritServer = PluginImpl.getFirstServer_();
        if (gerritServer != null) {
            SshdServerMock.configureFor(sshd, gerritServer, true);
        }
    }

    /**
     * Tears down the SSH server.
     *
     * @throws Exception if teardown fails
     */
    @After
    public void tearDown() throws Exception {
        if (sshd != null) {
            sshd.stop(true);
            sshd = null;
        }
    }

    /**
     * Waits for a build to start (but not necessarily complete).
     *
     * @param project the project to check
     * @param timeoutMs the timeout in milliseconds
     * @throws InterruptedException if interrupted while sleeping
     */
    private void waitForBuildToStart(FreeStyleProject project, long timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (project.getLastBuild() == null || !project.getLastBuild().isBuilding()) {
            if (System.currentTimeMillis() - startTime >= timeoutMs) {
                throw new RuntimeException("Timeout waiting for build to start!");
            }
            Thread.sleep(SLEEP_HALF_SEC);
        }
    }

    /**
     * Tests that a new patchset cancels a running build of an old patchset.
     * This is the most common cancellation scenario.
     *
     * @throws Exception if the test faced issues running jobs or working with the System.
     */
    @Test
    @LocalData
    public void testNewPatchsetCancelsRunningBuild() throws Exception {
        // Create a job with a long-running build and cancellation policy
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.getBuildersList().add(new SleepBuilder(SLEEP_10_SEC)); // 10 second build

        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        trigger.setGerritProjects(Collections.singletonList(
            new GerritProject(CompareType.ANT, "**",
                Collections.singletonList(new Branch(CompareType.ANT, "**")),
                null, null, null, false)));
        BuildCancellationPolicy policy = new BuildCancellationPolicy(false, false, false, false);
        policy.setEnabled(true);
        trigger.setBuildCancellationPolicy(policy);
        project.addTrigger(trigger);
        trigger.start(project, false);

        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, SERVER_WAIT);

        // Trigger build with patchset 1
        PatchsetCreated patchset1 = Setup.createPatchsetCreated();
        patchset1.getChange().setId("Iabc123");
        patchset1.getChange().setNumber("1000");
        patchset1.getPatchSet().setNumber("1");

        gerritServer.triggerEvent(patchset1);

        // Wait for build to start (not complete)
        waitForBuildToStart(project, BUILD_TIMEOUT);
        FreeStyleBuild build1 = project.getLastBuild();
        assertNotNull(build1);
        assertTrue(build1.isBuilding());

        // Trigger new patchset 2 (should cancel build1)
        PatchsetCreated patchset2 = Setup.createPatchsetCreated();
        patchset2.getChange().setId("Iabc123");
        patchset2.getChange().setNumber("1000");
        patchset2.getPatchSet().setNumber("2");

        gerritServer.triggerEvent(patchset2);

        // Wait for build1 to be aborted
        jenkins.waitUntilNoActivity();

        // Verify build1 was aborted
        assertEquals(Result.ABORTED, build1.getResult());

        // Verify it was aborted (the interruption cause is set internally)
        // Note: Interruption causes are stored in InterruptedBuildAction, not in Cause list

        // Verify build2 completed successfully
        TestUtils.waitForBuilds(project, 2, BUILD_TIMEOUT);
        FreeStyleBuild build2 = project.getLastBuild();
        assertNotNull(build2);
        jenkins.assertBuildStatusSuccess(build2);
    }

    /**
     * Tests that abortNewPatchsets policy cancels even newer patchsets with older ones.
     *
     * @throws Exception if test fails
     */
    @Test
    @LocalData
    public void testAbortNewPatchsetsPolicyCancelsAnyPatchset() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.getBuildersList().add(new SleepBuilder(SLEEP_10_SEC));

        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        trigger.setGerritProjects(Collections.singletonList(
            new GerritProject(CompareType.ANT, "**",
                Collections.singletonList(new Branch(CompareType.ANT, "**")),
                null, null, null, false)));
        BuildCancellationPolicy policy = new BuildCancellationPolicy(true, false, false, false);
        policy.setEnabled(true);
        trigger.setBuildCancellationPolicy(policy);
        project.addTrigger(trigger);
        trigger.start(project, false);

        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, SERVER_WAIT);

        // Trigger build with patchset 2
        PatchsetCreated patchset2 = Setup.createPatchsetCreated();
        patchset2.getChange().setId("Iabc123");
        patchset2.getChange().setNumber("1000");
        patchset2.getPatchSet().setNumber("2");

        gerritServer.triggerEvent(patchset2);

        waitForBuildToStart(project, BUILD_TIMEOUT);
        FreeStyleBuild build1 = project.getLastBuild();
        assertTrue(build1.isBuilding());

        // Trigger patchset 1 (older, but should still cancel build1 due to policy)
        PatchsetCreated patchset1 = Setup.createPatchsetCreated();
        patchset1.getChange().setId("Iabc123");
        patchset1.getChange().setNumber("1000");
        patchset1.getPatchSet().setNumber("1");

        gerritServer.triggerEvent(patchset1);

        jenkins.waitUntilNoActivity();

        // Verify build1 was aborted
        assertEquals(Result.ABORTED, build1.getResult());
    }

    /**
     * Tests that builds in queue are canceled before they start.
     *
     * @throws Exception if test fails
     */
    @Test
    @LocalData
    public void testCancelsQueuedBuilds() throws Exception {
        // Create a job that will queue builds (only 1 executor available)
        jenkins.jenkins.setNumExecutors(1);

        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.getBuildersList().add(new SleepBuilder(SLEEP_5_SEC));

        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        trigger.setGerritProjects(Collections.singletonList(
            new GerritProject(CompareType.ANT, "**",
                Collections.singletonList(new Branch(CompareType.ANT, "**")),
                null, null, null, false)));
        BuildCancellationPolicy policy = new BuildCancellationPolicy(false, false, false, false);
        policy.setEnabled(true);
        trigger.setBuildCancellationPolicy(policy);
        project.addTrigger(trigger);
        trigger.start(project, false);

        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, SERVER_WAIT);

        // Trigger build with patchset 1 (will start immediately)
        PatchsetCreated patchset1 = Setup.createPatchsetCreated();
        patchset1.getChange().setId("Iabc123");
        patchset1.getChange().setNumber("1000");
        patchset1.getPatchSet().setNumber("1");

        gerritServer.triggerEvent(patchset1);

        // Wait for build to START (not complete)
        waitForBuildToStart(project, BUILD_TIMEOUT);

        // Trigger patchset 1 again (will be queued due to single executor)
        gerritServer.triggerEvent(patchset1);

        // Wait a bit for it to enter queue
        Thread.sleep(SLEEP_HALF_SEC);

        // Verify there's a build in queue
        Queue.Item[] items = jenkins.jenkins.getQueue().getItems();
        assertTrue("Should have queued build", items.length > 0);

        // Trigger patchset 2 (should cancel queued build)
        PatchsetCreated patchset2 = Setup.createPatchsetCreated();
        patchset2.getChange().setId("Iabc123");
        patchset2.getChange().setNumber("1000");
        patchset2.getPatchSet().setNumber("2");

        gerritServer.triggerEvent(patchset2);

        // Wait for all builds to complete
        jenkins.waitUntilNoActivity();

        // Verify first build completed (was running)
        FreeStyleBuild build1 = project.getBuilds().get(1);
        assertNotNull(build1);
        assertEquals(Result.ABORTED, build1.getResult());

        // Verify new patchset build completed successfully
        FreeStyleBuild lastBuild = project.getLastBuild();
        assertNotNull(lastBuild);
        jenkins.assertBuildStatusSuccess(lastBuild);
    }

    /**
     * Tests that abandoned patchsets cancel running builds.
     *
     * @throws Exception if test fails
     */
    @Test
    @LocalData
    public void testAbandonedPatchsetCancelsRunningBuild() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.getBuildersList().add(new SleepBuilder(SLEEP_10_SEC));

        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        trigger.setGerritProjects(Collections.singletonList(
            new GerritProject(CompareType.ANT, "**",
                Collections.singletonList(new Branch(CompareType.ANT, "**")),
                null, null, null, false)));
        BuildCancellationPolicy policy = new BuildCancellationPolicy(false, false, false, true);
        policy.setEnabled(true);
        trigger.setBuildCancellationPolicy(policy);
        project.addTrigger(trigger);
        trigger.start(project, false);

        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, SERVER_WAIT);

        // Trigger build with patchset 1
        PatchsetCreated patchset1 = Setup.createPatchsetCreated();
        patchset1.getChange().setId("Iabc123");
        patchset1.getChange().setNumber("1000");
        patchset1.getPatchSet().setNumber("1");

        gerritServer.triggerEvent(patchset1);

        waitForBuildToStart(project, BUILD_TIMEOUT);
        FreeStyleBuild build1 = project.getLastBuild();
        assertTrue(build1.isBuilding());

        // Send abandoned event
        ChangeAbandoned abandoned = Setup.createChangeAbandoned();
        abandoned.getChange().setId("Iabc123");
        abandoned.getChange().setNumber("1000");
        abandoned.getPatchSet().setNumber("1");

        gerritServer.triggerEvent(abandoned);

        jenkins.waitUntilNoActivity();

        // Verify build was aborted
        assertEquals(Result.ABORTED, build1.getResult());
    }

    /**
     * Tests that different changes don't interfere with each other.
     *
     * @throws Exception if test fails
     */
    @Test
    @LocalData
    public void testDifferentChangesDoNotCancel() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.getBuildersList().add(new SleepBuilder(SLEEP_5_SEC));

        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        trigger.setGerritProjects(Collections.singletonList(
            new GerritProject(CompareType.ANT, "**",
                Collections.singletonList(new Branch(CompareType.ANT, "**")),
                null, null, null, false)));
        BuildCancellationPolicy policy = new BuildCancellationPolicy(true, true, false, false);
        policy.setEnabled(true);
        trigger.setBuildCancellationPolicy(policy);
        project.addTrigger(trigger);
        trigger.start(project, false);

        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, SERVER_WAIT);

        // Trigger build for change 1000
        PatchsetCreated change1000 = Setup.createPatchsetCreated();
        change1000.getChange().setId("Iabc123");
        change1000.getChange().setNumber("1000");
        change1000.getPatchSet().setNumber("1");

        gerritServer.triggerEvent(change1000);

        waitForBuildToStart(project, BUILD_TIMEOUT);
        FreeStyleBuild build1 = project.getLastBuild();
        assertTrue(build1.isBuilding());

        // Trigger build for change 2000 (different change)
        PatchsetCreated change2000 = Setup.createPatchsetCreated();
        change2000.getChange().setId("Idef456");
        change2000.getChange().setNumber("2000");
        change2000.getPatchSet().setNumber("1");

        gerritServer.triggerEvent(change2000);

        jenkins.waitUntilNoActivity();

        // Verify both builds completed successfully (no cancellation)
        assertEquals(Result.SUCCESS, build1.getResult());

        FreeStyleBuild build2 = project.getLastBuild();
        jenkins.assertBuildStatusSuccess(build2);
    }

    /**
     * Tests that policy disabled does not cancel builds.
     *
     * @throws Exception if test fails
     */
    @Test
    @LocalData
    public void testPolicyDisabledDoesNotCancel() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.getBuildersList().add(new SleepBuilder(SLEEP_5_SEC));

        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        trigger.setGerritProjects(Collections.singletonList(
            new GerritProject(CompareType.ANT, "**",
                Collections.singletonList(new Branch(CompareType.ANT, "**")),
                null, null, null, false)));
        BuildCancellationPolicy policy = new BuildCancellationPolicy(false, false, false, false);
        policy.setEnabled(false); // Disabled
        trigger.setBuildCancellationPolicy(policy);
        project.addTrigger(trigger);
        trigger.start(project, false);

        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, SERVER_WAIT);

        // Trigger build with patchset 1
        PatchsetCreated patchset1 = Setup.createPatchsetCreated();
        patchset1.getChange().setId("Iabc123");
        patchset1.getChange().setNumber("1000");
        patchset1.getPatchSet().setNumber("1");

        gerritServer.triggerEvent(patchset1);

        waitForBuildToStart(project, BUILD_TIMEOUT);
        FreeStyleBuild build1 = project.getLastBuild();
        assertTrue(build1.isBuilding());

        // Trigger patchset 2 (should NOT cancel build1 because policy is disabled)
        PatchsetCreated patchset2 = Setup.createPatchsetCreated();
        patchset2.getChange().setId("Iabc123");
        patchset2.getChange().setNumber("1000");
        patchset2.getPatchSet().setNumber("2");

        gerritServer.triggerEvent(patchset2);

        jenkins.waitUntilNoActivity();

        // Verify both builds completed successfully (no cancellation)
        assertEquals(Result.SUCCESS, build1.getResult());

        FreeStyleBuild build2 = project.getLastBuild();
        jenkins.assertBuildStatusSuccess(build2);
    }
}
