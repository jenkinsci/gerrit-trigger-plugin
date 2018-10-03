/*
 *  The MIT License
 *
 *  Copyright (c) 2010, 2014 Sony Mobile Communications Inc. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.spec;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.BuildCancellationPolicy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginTopicChangedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.TestUtils;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.TopicChanged;
import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;

import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TopLevelItem;

import org.apache.sshd.SshServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock.GERRIT_STREAM_EVENTS;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

//CS IGNORE MagicNumber FOR NEXT 800 LINES. REASON: Testdata.

/**
 * Some full run-through tests from trigger to build finished.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class SpecGerritTriggerHudsonTest {

    /**
     * An instance of Jenkins Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule j = new JenkinsRule();

    //TODO Fix the SshdServerMock so that asserts can be done on review commands.

    private SshServer sshd;
    private SshdServerMock serverMock;
    private GerritServer gerritServer;

    /**
     * Runs before test method.
     *
     * @throws Exception throw if so.
     */
    @Before
    public void setUp() throws Exception {
        SshdServerMock.generateKeyPair();

        serverMock = new SshdServerMock();
        sshd = SshdServerMock.startServer(serverMock);
        serverMock.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        serverMock.returnCommandFor(GERRIT_STREAM_EVENTS, SshdServerMock.CommandMock.class);
        serverMock.returnCommandFor("gerrit review.*", SshdServerMock.EofCommandMock.class);
        serverMock.returnCommandFor("gerrit approve.*", SshdServerMock.EofCommandMock.class);
        serverMock.returnCommandFor("gerrit version", SshdServerMock.SendVersionCommand.class);
        serverMock.returnCommandFor("gerrit approve.*", SshdServerMock.EofCommandMock.class);
        gerritServer = PluginImpl.getFirstServer_();
        SshdServerMock.configureFor(sshd, gerritServer, true);
    }

    /**
     * Runs after test method.
     *
     * @throws Exception throw if so.
     */
    @After
    public void tearDown() throws Exception {
        sshd.stop(true);
        sshd = null;
    }

    /**
     * Tests that a triggered build in silent start mode does not emit any build started messages.
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testTriggeredSilentStartModeBuild() throws Exception {
        FreeStyleProject project = new TestUtils.JobBuilder(j).silentStartMode(true).build();

        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        gerritServer.triggerEvent(Setup.createPatchsetCreated());
        TestUtils.waitForBuilds(project, 1, 20000);

        List<SshdServerMock.CommandMock> commands = serverMock.getCommandHistory();
        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i).getCommand();
            assertFalse(command.toLowerCase().contains("build started"));
        }
    }

    /**
     * Tests that a triggered build without silent start mode does emit build started messages.
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testTriggeredNoSilentStartModeBuild() throws Exception {
        FreeStyleProject project = new TestUtils.JobBuilder(j).name("projectX").silentStartMode(false).build();

        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        gerritServer.triggerEvent(Setup.createPatchsetCreated());
        TestUtils.waitForBuilds(project, 1, 20000);

        try {
            serverMock.waitForNrCommands("Build Started", 1, 5000);
        } catch (Exception e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            fail("Should not throw exception.");
        }
    }

    /**
     * Trigger several builds and test that the one in silent start mode does not emit any build started messages
     * while the ones without silent start mode does.
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testTriggeredSilentStartModeMixedBuild() throws Exception {
        gerritServer.getConfig().setNumberOfSendingWorkerThreads(3);

        final int nrOfJobs = 3;
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(j, "projectX");
        project.getBuildersList().add(new SleepBuilder(1000));
        project.save();
        int nrOfSilentStartJobs = 0;
        for (int i = 0; i < nrOfJobs; i++) {
            String name = String.format("project%d", i);
            FreeStyleProject copyProject = (FreeStyleProject)j.jenkins.copy((TopLevelItem)project, name);
            boolean mode = (i & 1) == 0; // true for even numbers
            copyProject.getTrigger(GerritTrigger.class).setSilentStartMode(mode);
            if (mode) {
                nrOfSilentStartJobs++;
            }
            copyProject.save();
        }
        assertEquals("Wrong number of jobs", nrOfJobs + 1, j.jenkins.getAllItems().size());
        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        gerritServer.triggerEvent(Setup.createPatchsetCreated());
        int expectedBuildStarted = nrOfJobs + 1 - nrOfSilentStartJobs;
        System.out.println("Expected nr of start commands: " + expectedBuildStarted);
        serverMock.waitForNrCommands("Build Started", expectedBuildStarted, nrOfJobs * 5000);
        j.waitUntilNoActivity();
        for (FreeStyleProject fp : j.jenkins.getAllItems(FreeStyleProject.class)) {
            assertEquals(1, fp.getLastBuild().getNumber());
        }
        StringBuilder cmdSearch = new StringBuilder("Build Successful[\\s\\S]+projectX/1/[\\s\\S]+SUCCESS[\\s\\S]+");
        for (int i = 0; i < nrOfJobs; i++) {
            cmdSearch.append("project" + i + "/1/[\\s\\S]+SUCCESS[\\s\\S]+");
        }
        System.out.println("Waiting for: " + cmdSearch);
        serverMock.waitForNrCommands(cmdSearch.toString(), 1, 5000);
    }

    /**
     * Tests to trigger a build with a dynamic plain branch configuration.
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testDynamicTriggeredBuildWithPlainBranch() throws Exception {
        testDynamicTriggeredBuild("=branch");
    }

    /**
     * Tests to trigger a build with a dynamic ant branch configuration.
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testDynamicTriggeredBuildWithAntBranch() throws Exception {
        testDynamicTriggeredBuild("^**");
    }

    /**
     * Tests to trigger a build with a dynamic configuration.
     * @param branchSetting  the dynamic branch setting with operator e.g. "^**" or "=branch"
     *
     * @throws Exception if so.
     */
    private void testDynamicTriggeredBuild(String branchSetting) throws Exception {
        ((Config)gerritServer.getConfig()).setDynamicConfigRefreshInterval(1);

        FreeStyleProject project = DuplicatesUtil.createGerritDynamicTriggeredJob(j, "projectX", branchSetting);
        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        waitForDynamicTimer(project, 8000);
        gerritServer.triggerEvent(Setup.createPatchsetCreated());
        TestUtils.waitForBuilds(project, 1);
        FreeStyleBuild build = project.getLastCompletedBuild();
        assertSame(Result.SUCCESS, build.getResult());
    }

    /**
     * Tests to trigger a build with the same patch set twice. Expecting one build to be scheduled with one cause.
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testDoubleTriggeredBuild() throws Exception {

        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(j, "projectX");
        project.getBuildersList().add(new SleepBuilder(5000));
        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        boolean started = false;

        gerritServer.triggerEvent(Setup.createPatchsetCreated());
        while (!started) {
            if (project.isBuilding()) {
                started = true;
            }
            Thread.sleep(1000);
        }
        gerritServer.triggerEvent(Setup.createPatchsetCreated());

        while (project.isBuilding() || project.isInQueue()) {
            Thread.sleep(1000);
        }

        int size = 0;
        for (FreeStyleBuild build : project.getBuilds()) {
            assertSame(Result.SUCCESS, build.getResult());
            int count = 0;
            for (Cause cause : build.getCauses()) {
                if (cause instanceof GerritCause) {
                    count++;
                    assertNotNull(((GerritCause)cause).getContext());
                    assertNotNull(((GerritCause)cause).getEvent());
                }
            }
            assertEquals(1, count);
            size++;
        }
        assertEquals(1, size);
    }

    /**
     * Tests to trigger a build from a project with the same patch set twice.
     * Expecting one build to be scheduled with one cause.
     * And builds are not triggered if build is not building but other builds triggered by a event is building.
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testDoubleTriggeredBuildWithProjects() throws Exception {
        FreeStyleProject project1 = DuplicatesUtil.createGerritTriggeredJob(j, "projectX");
        FreeStyleProject project2 = DuplicatesUtil.createGerritTriggeredJob(j, "projectY");
        project1.getBuildersList().add(new SleepBuilder(5000));
        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        boolean started = false;

        gerritServer.triggerEvent(Setup.createPatchsetCreated());
        while (!started) {
            if (project1.isBuilding()) {
                started = true;
            }
            Thread.sleep(1000);
        }
        gerritServer.triggerEvent(Setup.createPatchsetCreated());

        while (project1.isBuilding() || project1.isInQueue()) {
            Thread.sleep(1000);
        }

        int size = 0;
        for (FreeStyleProject project : Arrays.asList(project1, project2)) {
            for (FreeStyleBuild build : project.getBuilds()) {
                assertSame(Result.SUCCESS, build.getResult());
                int count = 0;
                for (Cause cause : build.getCauses()) {
                    if (cause instanceof GerritCause) {
                        count++;
                        assertNotNull(((GerritCause)cause).getContext());
                        assertNotNull(((GerritCause)cause).getEvent());
                    }
                }
                assertEquals(1, count);
                size++;
            }
        }
        assertEquals(2, size);
    }

    /**
     * Tests to trigger builds with two different patch sets. Expecting two build to be scheduled with one cause each.
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testDoubleTriggeredBuildsOfDifferentChange() throws Exception {
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(j, "projectX");
        project.getBuildersList().add(new SleepBuilder(5000));
        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        boolean started = false;

        gerritServer.triggerEvent(Setup.createPatchsetCreated());
        while (!started) {
            if (project.isBuilding()) {
                started = true;
            }
            Thread.sleep(1000);
        }
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated();
        patchsetCreated.getChange().setNumber("2000");
        gerritServer.triggerEvent(patchsetCreated);

        while (project.isBuilding() || project.isInQueue()) {
            Thread.sleep(1000);
        }

        int size = 0;
        for (FreeStyleBuild build : project.getBuilds()) {
            assertSame(Result.SUCCESS, build.getResult());
            int count = 0;
            for (Cause cause : build.getCauses()) {
                if (cause instanceof GerritCause) {
                    count++;
                    assertNotNull(((GerritCause)cause).getContext());
                    assertNotNull(((GerritCause)cause).getEvent());
                }
            }
            assertEquals(1, count);
            size++;
        }
        assertEquals(2, size);
    }

    /**
     * Tests to trigger a build from a project with the same patch set twice.
     * Expecting one build to be scheduled with one cause.
     * And builds are not triggered if build is not building but other builds triggered by a event is building.
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testTripleTriggeredBuildWithProjects() throws Exception {
        FreeStyleProject project1 = DuplicatesUtil.createGerritTriggeredJob(j, "projectX");
        FreeStyleProject project2 = DuplicatesUtil.createGerritTriggeredJob(j, "projectY");
        project1.getBuildersList().add(new SleepBuilder(5000));
        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        boolean started = false;

        gerritServer.triggerEvent(Setup.createPatchsetCreated());
        while (!started) {
            if (project1.isBuilding()) {
                started = true;
            } else {
                Thread.sleep(1000);
            }
        }
        gerritServer.triggerEvent(Setup.createPatchsetCreated());
        while (project1.isBuilding() || project1.isInQueue()) {
            Thread.sleep(1000);
        }

        started = false;
        gerritServer.triggerEvent(Setup.createPatchsetCreated());
        while (!started) {
            if (project1.isBuilding()) {
                started = true;
            } else {
                Thread.sleep(1000);
            }
        }
        while (project1.isBuilding() || project1.isInQueue()) {
            Thread.sleep(1000);
        }

        int size = 0;
        for (FreeStyleProject project : Arrays.asList(project1, project2)) {
            for (FreeStyleBuild build : project.getBuilds()) {
                assertSame(Result.SUCCESS, build.getResult());
                int count = 0;
                for (Cause cause : build.getCauses()) {
                    if (cause instanceof GerritCause) {
                        count++;
                        assertNotNull(((GerritCause)cause).getContext());
                        assertNotNull(((GerritCause)cause).getEvent());
                    }
                }
                assertEquals(1, count);
                size++;
            }
        }
        assertEquals(4, size);
    }

    /**
     * Tests the behavior of the "Build Current Patches Only" functionality when:
     *  - Abort manual patch sets is true.
     *  - Abort new patch sets is false.
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testBuildLatestPatchsetOnlyAbortManual() throws Exception {
        buildLatestPatchsetOnlyAndReport(true, false, Result.ABORTED, Result.SUCCESS, Result.SUCCESS);
    }


    /**
     * Tests the behavior of the "Build Current Patches Only" functionality when:
     *  - Abort manual patch sets is true.
     *  - Abort new patch sets is true.
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testBuildLatestPatchsetOnlyAbortManualAndNew() throws Exception {
        buildLatestPatchsetOnlyAndReport(true, true, Result.ABORTED, Result.ABORTED, Result.SUCCESS);
    }

    /**
     * Tests the behavior of the "Build Current Patches Only" functionality when:
     *  - Abort manual patch sets is false.
     *  - Abort new patch sets is false.
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testBuildLatestPatchsetOnlyAbortNeitherManualNorNew() throws Exception {
        buildLatestPatchsetOnlyAndReport(false, false, Result.SUCCESS, Result.SUCCESS, Result.SUCCESS);
    }

    /**
     * Tests the behavior of the "Build Current Patches Only" functionality when:
     *  - Abort manual patch sets is false.
     *  - Abort new patch sets is true.
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testBuildLatestPatchsetOnlyAbortNew() throws Exception {
        buildLatestPatchsetOnlyAndReport(false, true, Result.SUCCESS, Result.ABORTED, Result.SUCCESS);
    }

    /**
     * Test the behaviour of "Build Current Patches Only" functionality when:
     *  - Abort manual patch sets is false.
     *  - Abort new patch sets is true.
     *  - 2 changes with different branches trigger builds.
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testBuildLatestPatchsetOnlyNonRelatedChangeCannotAbort() throws Exception {
        BuildCancellationPolicy policy = gerritServer.getConfig().getBuildCurrentPatchesOnly();
        policy.setEnabled(true);
        policy.setAbortManualPatchsets(false);
        policy.setAbortNewPatchsets(false);
        Random rand = new Random();
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(j, "project" + rand.nextInt());
        project.getBuildersList().add(new SleepBuilder(2000));
        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);

        PatchsetCreated firstEvent = Setup.createPatchsetCreated(PluginImpl.DEFAULT_SERVER_NAME,
                "gerrit-project-1", "refabc");
        gerritServer.triggerEvent(firstEvent);

        PatchsetCreated secondEvent = Setup.createPatchsetCreated(PluginImpl.DEFAULT_SERVER_NAME,
                "gerrit-project-2", "refabc");
        gerritServer.triggerEvent(secondEvent);
        TestUtils.waitForBuilds(project, 2);

        //both should succeed since gerrit project not the same
        assertEquals(Result.SUCCESS, project.getFirstBuild().getResult());
        assertEquals(Result.SUCCESS, project.getBuildByNumber(2).getResult());
    }

    /**
     * Test the behaviour of "Build Current Patches Only" functionality when:
     *  - Abort manual patch sets is false.
     *  - Abort new patch sets is false.
     *  - 2 changes with same gerrit project BUT different branches trigger builds.
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testBuildLatestPatchsetOnlyNonRelatedChangeDifferentBranchCannotAbort() throws Exception {
        BuildCancellationPolicy policy = gerritServer.getConfig().getBuildCurrentPatchesOnly();
        policy.setEnabled(true);
        policy.setAbortManualPatchsets(false);
        policy.setAbortNewPatchsets(false);
        Random rand = new Random();
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(j, "project" + rand.nextInt());
        project.getBuildersList().add(new SleepBuilder(2000));

        GerritTrigger trigger = project.getTrigger(GerritTrigger.class);
        trigger.setSilentMode(true);

        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);

        PatchsetCreated firstEvent = Setup.createPatchsetCreated();
        firstEvent.getChange().setBranch("abc");
        gerritServer.triggerEvent(firstEvent);
        Thread.sleep(1000);

        PatchsetCreated secondEvent = Setup.createPatchsetCreated();
        secondEvent.getPatchSet().setNumber("2");
        secondEvent.getChange().setBranch("def");
        gerritServer.triggerEvent(secondEvent);

        TestUtils.waitForBuilds(project, 2);

        //both should succeed since branches not the same
        assertEquals(Result.SUCCESS, project.getFirstBuild().getResult());
        assertEquals(Result.SUCCESS, project.getBuildByNumber(2).getResult());
    }


    /**
     * Test the behaviour of "Build Current Patches Only" functionality when:
     *  - Abort manual patch sets is false.
     *  - Abort new patch sets is false.
     *  - 2 changes with same gerrit project and same topic trigger builds.
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testOngoingBuildWithSameTopicWillBeAborted() throws Exception {
        BuildCancellationPolicy policy = gerritServer.getConfig().getBuildCurrentPatchesOnly();
        policy.setEnabled(true);
        policy.setAbortManualPatchsets(false);
        policy.setAbortNewPatchsets(false);
        policy.setAbortSameTopic(true);
        Random rand = new Random();
        FreeStyleProject project = new TestUtils.JobBuilder(j).name("project" + rand.nextInt()).build();
        project.getBuildersList().add(new SleepBuilder(3000));

        GerritTrigger trigger = project.getTrigger(GerritTrigger.class);
        trigger.setSilentMode(true);

        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);

        PatchsetCreated firstEvent = Setup.createPatchsetCreated();
        firstEvent.getChange().setTopic("abc");
        gerritServer.triggerEvent(firstEvent);
        TestUtils.waitForNonManualBuildToStart(project, firstEvent, 10000);

        PatchsetCreated secondEvent = Setup.createPatchsetCreated();
        secondEvent.getPatchSet().setNumber("2");
        secondEvent.getChange().setTopic("abc");
        gerritServer.triggerEvent(secondEvent);

        TestUtils.waitForBuilds(project, 2);

        //both should succeed since branches not the same
        assertEquals(Result.ABORTED, project.getFirstBuild().getResult());
        assertEquals(Result.SUCCESS, project.getBuildByNumber(2).getResult());
    }

    /**
     * Test the behaviour of "Build Current Patches Only" functionality when:
     *  - Abort manual patch sets is false.
     *  - Abort new patch sets is false.
     *  - First change is triggered by new patch set and just after that topic was modified
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testOngoingBuildWithSameTopicWillBeAbortedByTopicChangedEvent() throws Exception {
        BuildCancellationPolicy policy = gerritServer.getConfig().getBuildCurrentPatchesOnly();
        policy.setEnabled(true);
        policy.setAbortManualPatchsets(false);
        policy.setAbortNewPatchsets(false);
        policy.setAbortSameTopic(true);
        Random rand = new Random();

        FreeStyleProject project = new TestUtils.JobBuilder(j).name("project" + rand.nextInt()).build();
        project.getBuildersList().add(new SleepBuilder(3000));

        GerritTrigger trigger = project.getTrigger(GerritTrigger.class);
        trigger.setSilentMode(true);
        trigger.getTriggerOnEvents().add(new PluginTopicChangedEvent());

        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);

        PatchsetCreated firstEvent = Setup.createPatchsetCreated();
        firstEvent.getChange().setTopic("abc");
        gerritServer.triggerEvent(firstEvent);
        TestUtils.waitForNonManualBuildToStart(project, firstEvent, 10000);

        TopicChanged secondEvent = Setup.createTopicChanged();
        secondEvent.getPatchSet().setNumber("2");
        secondEvent.setOldTopic("abc");
        gerritServer.triggerEvent(secondEvent);

        TestUtils.waitForBuilds(project, 2);

        //both should succeed since branches not the same
        assertEquals(Result.ABORTED, project.getFirstBuild().getResult());
        assertEquals(Result.SUCCESS, project.getBuildByNumber(2).getResult());
    }

    /**
     * Helper method to test the behavior of the "Build Current Patches Only" functionality.
     * Parameterized so that the different combinations can be tested.
     * @param abortManual true if manual patchsets should be cancelled and be able to cancel builds, false if not.
     * @param abortNew true if new patchsets should be cancelled when older patchsets are retriggered, false if not.
     * @param firstExpected expected result for the first build.
     * @param secondExpected expected result for the second build.
     * @param thirdExpected expected result for the third build
     *
     * @throws Exception if so.
     */
    public void buildLatestPatchsetOnlyAndReport(boolean abortManual, boolean abortNew, Result firstExpected,
                                                 Result secondExpected, Result thirdExpected) throws Exception {
        BuildCancellationPolicy policy = gerritServer.getConfig().getBuildCurrentPatchesOnly();
        policy.setEnabled(true);
        policy.setAbortManualPatchsets(abortManual);
        policy.setAbortNewPatchsets(abortNew);
        Random rand = new Random();
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(j, "project" + rand.nextInt());
        project.getBuildersList().add(new SleepBuilder(2000));
        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        ManualPatchsetCreated firstEvent = Setup.createManualPatchsetCreated();
        firstEvent.getPatchSet().setNumber("1");
        AtomicReference<Run> firstBuildRef = TestUtils.getFutureBuildToStart(firstEvent);
        gerritServer.triggerEvent(firstEvent);
        TestUtils.waitForBuildToStart(firstBuildRef);
        PatchsetCreated secondEvent = Setup.createPatchsetCreated();
        if (null != secondEvent.getPatchSet()) {
            secondEvent.getPatchSet().setNumber("3");
        }
        gerritServer.triggerEvent(secondEvent);
        TestUtils.waitForNonManualBuildToStart(project, secondEvent, 10000);
        PatchsetCreated thirdEvent = Setup.createPatchsetCreated();
        if (null != thirdEvent.getPatchSet()) {
            thirdEvent.getPatchSet().setNumber("2");
        }
        gerritServer.triggerEvent(thirdEvent);
        TestUtils.waitForBuilds(project, 3);
        assertEquals(3, project.getLastCompletedBuild().getNumber());
        assertSame(firstExpected, project.getFirstBuild().getResult());
        assertSame(secondExpected, project.getBuildByNumber(2).getResult());
        assertSame(thirdExpected, project.getBuildByNumber(3).getResult());
    }

    /**
     * Tests that builds are not aborted when "build current patch sets only" is set to false.
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testNotBuildLatestPatchsetOnly() throws Exception {
        gerritServer.getConfig().getBuildCurrentPatchesOnly().setEnabled(false);

        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(j, "projectX");
        project.getBuildersList().add(new SleepBuilder(2000));
        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        ManualPatchsetCreated firstEvent = Setup.createManualPatchsetCreated();
        AtomicReference<Run> firstBuildRef = TestUtils.getFutureBuildToStart(firstEvent);
        gerritServer.triggerEvent(firstEvent);
        Run firstBuild = TestUtils.waitForBuildToStart(firstBuildRef);
        PatchsetCreated secondEvent = Setup.createPatchsetCreated();
        if (null != secondEvent.getPatchSet()) {
            secondEvent.getPatchSet().setNumber("2");
        }
        gerritServer.triggerEvent(secondEvent);
        TestUtils.waitForBuilds(project, 2);
        assertEquals(2, project.getLastCompletedBuild().getNumber());
        assertSame(Result.SUCCESS, firstBuild.getResult());
        assertSame(Result.SUCCESS, project.getFirstBuild().getResult());
        assertSame(Result.SUCCESS, project.getLastBuild().getResult());
    }

    /**
     * Tests that a comment added triggers a build correctly.
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testTriggerOnCommentAdded() throws Exception {
        gerritServer.getConfig().setCategories(Setup.createCodeReviewVerdictCategoryList());
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJobForCommentAdded(j, "projectX");
        project.getBuildersList().add(new SleepBuilder(2000));
        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        CommentAdded firstEvent = Setup.createCommentAdded();
        gerritServer.triggerEvent(firstEvent);
        TestUtils.waitForBuilds(project, 1);
        assertEquals(1, project.getLastCompletedBuild().getNumber());
        assertSame(Result.SUCCESS, project.getLastCompletedBuild().getResult());
    }

    /**
     * Tests that two comments added during the same time only triggers one build.
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testDoubleTriggeredOnCommentAdded() throws Exception {
        gerritServer.getConfig().setCategories(Setup.createCodeReviewVerdictCategoryList());

        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJobForCommentAdded(j, "projectX");
        project.getBuildersList().add(new SleepBuilder(2000));
        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);

        gerritServer.triggerEvent(Setup.createCommentAdded());
        gerritServer.triggerEvent(Setup.createCommentAdded());
        TestUtils.waitForBuilds(project, 1);
        assertEquals(1, project.getLastCompletedBuild().getNumber());
        assertSame(Result.SUCCESS, project.getLastCompletedBuild().getResult());
    }

    /**

    /**
     * Waits for the dynamic trigger cache to be updated.
     * @param project the project to check the dynamic triggering for.
     * @param timeoutMs th amount of milliseconds to wait.
     */
    private void waitForDynamicTimer(FreeStyleProject project, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime <= timeoutMs) {
            GerritTrigger trigger = project.getTrigger(GerritTrigger.class);
            List<GerritProject> dynamicGerritProjects = trigger.getDynamicGerritProjects();
            if (dynamicGerritProjects != null && !dynamicGerritProjects.isEmpty()) {
                return;
            }
        }
        throw new RuntimeException("Timeout!");
    }

    /**
     * Tests that there are no duplicated listeners when a project is renamed.
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testProjectRename() throws Exception {
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(j, "projectX");
        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);

        gerritServer.triggerEvent(Setup.createPatchsetCreated());

        TestUtils.waitForBuilds(project, 1, 60000);

        project.renameTo("anotherName");
        project = j.configRoundtrip(project);

        gerritServer.triggerEvent(Setup.createPatchsetCreated());

        TestUtils.waitForBuilds(project, 2, 60000);
    }
}
