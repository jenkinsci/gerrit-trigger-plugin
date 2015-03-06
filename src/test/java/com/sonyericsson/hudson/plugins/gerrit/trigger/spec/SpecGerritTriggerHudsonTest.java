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
import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Queue.QueueDecisionHandler;
import hudson.model.Queue.Task;
import hudson.model.Result;
import hudson.model.TopLevelItem;
import org.apache.sshd.SshServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock.GERRIT_STREAM_EVENTS;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.netbeans.insane.live.LiveReferences;
import org.powermock.reflect.Whitebox;

//CS IGNORE AvoidStarImport FOR NEXT 1 LINES. REASON: UnitTest.
import static org.junit.Assert.*;

//CS IGNORE MagicNumber FOR NEXT 600 LINES. REASON: Testdata.

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
    private SshdServerMock.KeyPairFiles sshKey;
    private SshdServerMock serverMock;

    /**
     * Runs before test method.
     *
     * @throws Exception throw if so.
     */
    @Before
    public void setUp() throws Exception {
        sshKey = SshdServerMock.generateKeyPair();
        serverMock = new SshdServerMock();
        sshd = SshdServerMock.startServer(serverMock);
        serverMock.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        serverMock.returnCommandFor(GERRIT_STREAM_EVENTS, SshdServerMock.CommandMock.class);
        serverMock.returnCommandFor("gerrit review.*", SshdServerMock.EofCommandMock.class);
        serverMock.returnCommandFor("gerrit approve.*", SshdServerMock.EofCommandMock.class);
        serverMock.returnCommandFor("gerrit version", SshdServerMock.EofCommandMock.class);
        serverMock.returnCommandFor("gerrit approve.*", SshdServerMock.EofCommandMock.class);
        System.setProperty(PluginImpl.TEST_SSH_KEYFILE_LOCATION_PROPERTY, sshKey.getPrivateKey().getAbsolutePath());
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
    @LocalData
    public void testTriggeredSilentStartModeBuild() throws Exception {
        GerritServer gerritServer = PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME);
        ((Config)gerritServer.getConfig()).setDynamicConfigRefreshInterval(1);
        FreeStyleProject project = DuplicatesUtil.createGerritDynamicTriggeredJob(j, "projectX");

        GerritTrigger trigger = project.getTrigger(GerritTrigger.class);
        trigger.setSilentStartMode(true);

        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        waitForDynamicTimer(project, 5000);
        gerritServer.triggerEvent(Setup.createPatchsetCreated());
        TestUtils.waitForBuilds(project, 1, 5000);

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
    @LocalData
    public void testTriggeredNoSilentStartModeBuild() throws Exception {
        GerritServer gerritServer = PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME);
        ((Config)gerritServer.getConfig()).setDynamicConfigRefreshInterval(1);
        FreeStyleProject project = DuplicatesUtil.createGerritDynamicTriggeredJob(j, "projectX");

        GerritTrigger trigger = project.getTrigger(GerritTrigger.class);
        trigger.setSilentStartMode(false);

        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        waitForDynamicTimer(project, 5000);
        gerritServer.triggerEvent(Setup.createPatchsetCreated());
        TestUtils.waitForBuilds(project, 1, 5000);

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
    @LocalData
    public void testTriggeredSilentStartModeMixedBuild() throws Exception {
        GerritServer gerritServer = PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME);
        gerritServer.getConfig().setNumberOfSendingWorkerThreads(3);
        final int nrOfJobs = 3;
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(j, "projectX");
        project.getBuildersList().add(new SleepBuilder(1000));
        project.save();
        for (int i = 0; i < nrOfJobs; i++) {
            String name = String.format("project%d", i);
            FreeStyleProject copyProject = (FreeStyleProject)j.jenkins.copy((TopLevelItem)project, name);
            boolean mode = (i & 1) == 0; // true for even numbers
            copyProject.getTrigger(GerritTrigger.class).setSilentStartMode(mode);
        }
        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        gerritServer.triggerEvent(Setup.createPatchsetCreated());
        try {
            serverMock.waitForNrCommands("Build Started", 2, nrOfJobs * 5000);
        } catch (Exception e) {
            System.out.println(e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            fail("Should not throw exception.");
        }
    }

    /**
     * Tests to trigger a build with a dynamic configuration.
     * @throws Exception if so.
     */
    @LocalData
    public void testDynamicTriggeredBuild() throws Exception {
        GerritServer gerritServer = PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME);
        ((Config)gerritServer.getConfig()).setDynamicConfigRefreshInterval(1);
        FreeStyleProject project = DuplicatesUtil.createGerritDynamicTriggeredJob(j, "projectX");
        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        waitForDynamicTimer(project, 5000);
        gerritServer.triggerEvent(Setup.createPatchsetCreated());
        TestUtils.waitForBuilds(project, 1);
        FreeStyleBuild build = project.getLastCompletedBuild();
        assertSame(Result.SUCCESS, build.getResult());
        // JENKINS-23152
        WeakReference<FreeStyleProject> old = new WeakReference<>(project);
        project = null;
        //builds = null;
        build = null;
        gerritServer = null;
        // clear out any BoundObjectTable references from config screen
        Whitebox.getInternalState(j, org.mortbay.jetty.Server.class).stop();
        j.jenkins.reload();
        /* TODO https://netbeans.org/bugzilla/show_bug.cgi?id=244668 blocking further progress this way:
        assertGC(old);
        */
        project = j.jenkins.getItemByFullName("projectX", FreeStyleProject.class);
        assertEquals(1, project.getBuilds().size());
        gerritServer = PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME);
        gerritServer.triggerEvent(Setup.createPatchsetCreated());
        //builds = DuplicatesUtil.waitForBuilds(project, 2, 5000);
    }

    /**
     * Forces GC by causing an OOM and then verifies the given {@link WeakReference} has been garbage collected.
     * @param reference object used to verify garbage collection.
     */
    @SuppressWarnings("DLS_DEAD_LOCAL_STORE_OF_NULL")
    // TODO use version from MemoryAssert in 1.519+
    private static void assertGC(WeakReference<?> reference) {
        assertTrue(true); reference.get(); // preload any needed classes!
        Set<Object[]> objects = new HashSet<Object[]>();
        while (true) {
            try {
                objects.add(new Object[1024]);
            } catch (OutOfMemoryError ignore) {
                break;
            }
        }
        objects = null;
        System.gc();
        Object obj = reference.get();
        if (obj != null) {
            fail(LiveReferences.fromRoots(Collections.singleton(obj)).toString());
        }
    }

    /**
     * Tests to trigger a build with the same patch set twice. Expecting one build to be scheduled with one cause.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testDoubleTriggeredBuild() throws Exception {
        GerritServer gerritServer = PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME);
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
    @LocalData
    public void testDoubleTriggeredBuildWithProjects() throws Exception {
        GerritServer gerritServer = PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME);
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
    @LocalData
    public void testDoubleTriggeredBuildsOfDifferentChange() throws Exception {
        GerritServer gerritServer = PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME);
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
    @LocalData
    public void testTripleTriggeredBuildWithProjects() throws Exception {
        GerritServer gerritServer = PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME);
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
     * Tests that a build for a patch set gets cancelled when a new patch set of the same change arrives.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testBuildLatestPatchsetOnly() throws Exception {
        GerritServer gerritServer = PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME);
        ((Config)gerritServer.getConfig()).setGerritBuildCurrentPatchesOnly(true);
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(j, "projectX");
        project.getBuildersList().add(new SleepBuilder(2000));
        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        ManualPatchsetCreated firstEvent = Setup.createManualPatchsetCreated();
        AtomicReference<AbstractBuild> firstBuildRef = TestUtils.getFutureBuildToStart(firstEvent);
        gerritServer.triggerEvent(firstEvent);
        AbstractBuild firstBuild = TestUtils.waitForBuildToStart(firstBuildRef);
        PatchsetCreated secondEvent = Setup.createPatchsetCreated();
        if (null != secondEvent.getPatchSet()) {
            secondEvent.getPatchSet().setNumber("2");
        }
        gerritServer.triggerEvent(secondEvent);
        TestUtils.waitForBuilds(project, 2);
        assertEquals(2, project.getLastCompletedBuild().getNumber());
        assertSame(Result.ABORTED, firstBuild.getResult());
        assertSame(Result.ABORTED, project.getFirstBuild().getResult());
        assertSame(Result.SUCCESS, project.getLastBuild().getResult());
    }

    /**
     * Same test logic as {@link #testBuildLatestPatchsetOnly()}
     * except the trigger is configured to not cancel the previous build.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testNotBuildLatestPatchsetOnly() throws Exception {
        GerritServer gerritServer = PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME);
        ((Config)gerritServer.getConfig()).setGerritBuildCurrentPatchesOnly(false);
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(j, "projectX");
        project.getBuildersList().add(new SleepBuilder(2000));
        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        ManualPatchsetCreated firstEvent = Setup.createManualPatchsetCreated();
        AtomicReference<AbstractBuild> firstBuildRef = TestUtils.getFutureBuildToStart(firstEvent);
        gerritServer.triggerEvent(firstEvent);
        AbstractBuild firstBuild = TestUtils.waitForBuildToStart(firstBuildRef);
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
    @LocalData
    public void testTriggerOnCommentAdded() throws Exception {
        GerritServer gerritServer = PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME);
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
    @LocalData
    public void testDoubleTriggeredOnCommentAdded() throws Exception {
        GerritServer gerritServer = PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME);
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
    @LocalData
    public void testProjectRename() throws Exception {
        QueueDecisionHandlerImpl h = QueueDecisionHandlerImpl.all().get(QueueDecisionHandlerImpl.class);

        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(j, "projectX");

        project.renameTo("anotherName");
        j.configRoundtrip((Item)project);

        assertEquals(0, h.countTrigger);

        PluginImpl p = PluginImpl.getInstance();
        p.getServer(PluginImpl.DEFAULT_SERVER_NAME).triggerEvent(Setup.createPatchsetCreated());

        Thread.sleep(3000); // TODO: is there a better way to wait for the completion of asynchronous event processing?

        assertEquals(1, h.countTrigger);
    }

    /**
     * {@link QueueDecisionHandler} for aid in {@link #testProjectRename}.
     */
    @TestExtension("testProjectRename")
    public static class QueueDecisionHandlerImpl extends QueueDecisionHandler {
        private int countTrigger = 0;

        @Override
        public boolean shouldSchedule(Task p, List<Action> actions) {
            countTrigger++;
            return false;
        }
    }

}
