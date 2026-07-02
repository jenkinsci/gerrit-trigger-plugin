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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.CoordinationModeFactory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.hazelcast.HazelcastTestHelper;
import com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.hazelcast.HazelcastTestRule;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock.GERRIT_STREAM_EVENTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for build cancellation in Hazelcast distributed mode.
 * <p>
 * These tests verify that build cancellation works correctly when using
 * Hazelcast-backed BuildMemoryStorage instead of local TreeMap storage.
 * <p>
 * This is critical for distributed scenarios where multiple Jenkins
 * instances share state via Hazelcast.
 *
 */
public class BuildCancellationHazelcastIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(
            BuildCancellationHazelcastIntegrationTest.class);

    /**
     * Hazelcast test infrastructure - MUST be declared before JenkinsRule.
     * This ensures Hazelcast is initialized before Jenkins starts.
     */
    //CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JUnit Rule.
    @Rule
    public final HazelcastTestRule hazelcast = new HazelcastTestRule();

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

        // Verify Hazelcast mode is active
        verifyHazelcastMode();
    }

    /**
     * Tears down the SSH server.
     *
     * @throws Exception if teardown fails
     */
    @After
    public void tearDown() throws Exception {
        // Clear Hazelcast state to prevent pollution between tests
        HazelcastTestHelper.clearAllMaps();

        if (sshd != null) {
            sshd.stop(true);
            sshd = null;
        }
    }

    /**
     * Verifies that Hazelcast coordination mode is actually active.
     * This prevents false positives from tests running in local mode.
     */
    private void verifyHazelcastMode() {
        CoordinationModeFactory factory = CoordinationModeFactory.get();

        // Trigger mode discovery by accessing storage
        String storageClass = factory.getStorage().getClass().getSimpleName();

        // Now get the selected mode (will not be null after storage access)
        String modeName;
        if (factory.getSelectedMode() != null) {
            modeName = factory.getSelectedMode().getModeName();
        } else {
            modeName = "UNKNOWN";
        }

        logger.info("=== COORDINATION MODE VERIFICATION ===");
        logger.info("Mode: {}", modeName);
        logger.info("Storage: {}", storageClass);
        logger.info("======================================");

        assertEquals("Expected Hazelcast storage", "HazelcastBuildMemoryStorage", storageClass);
        assertEquals("Expected Hazelcast mode", "Hazelcast (Distributed)", modeName);
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
     * Verifies that a build was interrupted with the expected cause by checking the build log.
     *
     * @param build the build to check
     * @param expectedMessage the expected interruption message
     * @throws Exception if unable to read build log
     */
    private void assertInterruptionCause(FreeStyleBuild build, String expectedMessage) throws Exception {
        String log = jenkins.getLog(build);
        assertNotNull("Build log should not be null", log);
        assertTrue("Build log should contain interruption message: " + expectedMessage,
                   log.contains(expectedMessage));
    }

    /**
     * Tests that a new patchset cancels a running build of an old patchset in Hazelcast mode.
     * This verifies the atomic TriggeredProcessor fix works correctly in distributed mode.
     *
     * @throws Exception if unexpected errors appear.
     */
    @Test
    @LocalData("common")
    public void testNewPatchsetCancelsRunningBuildHazelcast() throws Exception {
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
        assertEquals("Build should be ABORTED (Hazelcast mode)", Result.ABORTED, build1.getResult());

        // Verify it was aborted with the correct interruption cause
        assertInterruptionCause(build1, Messages.AbortedByNewPatchSet());

        // Verify build2 completed successfully
        TestUtils.waitForBuilds(project, 2, BUILD_TIMEOUT);
        FreeStyleBuild build2 = project.getLastBuild();
        assertNotNull(build2);
        jenkins.assertBuildStatusSuccess(build2);
    }

    /**
     * Tests that abandoned patchsets cancel running builds in Hazelcast mode.
     *
     * @throws Exception if unexpected errors appear.
     */
    @Test
    @LocalData("common")
    public void testAbandonedPatchsetCancelsRunningBuildHazelcast() throws Exception {
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
        assertEquals("Build should be ABORTED (Hazelcast mode)", Result.ABORTED, build1.getResult());

        // Verify it was aborted with the correct interruption cause
        assertInterruptionCause(build1, Messages.AbortedByAbandonedPatchset());
    }

    /**
     * Tests that abortNewPatchsets policy cancels even newer patchsets in Hazelcast mode.
     *
     * @throws Exception if unexpected errors appear.
     */
    @Test
    @LocalData("common")
    public void testAbortNewPatchsetsPolicyCancelsAnyPatchsetHazelcast() throws Exception {
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
        assertEquals("Build should be ABORTED (Hazelcast mode)", Result.ABORTED, build1.getResult());
    }
}
