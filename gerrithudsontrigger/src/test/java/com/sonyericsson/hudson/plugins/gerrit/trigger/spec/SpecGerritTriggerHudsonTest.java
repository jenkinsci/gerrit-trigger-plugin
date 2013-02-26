/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.SshdServerMock;
import hudson.model.Item;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Queue.QueueDecisionHandler;
import hudson.model.Queue.Task;
import hudson.model.Result;
import hudson.util.RunList;
import org.apache.sshd.SshServer;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;
import java.io.File;
import java.util.List;

//CS IGNORE MagicNumber FOR NEXT 400 LINES. REASON: Testdata.

/**
 * Some full run-through tests from trigger to build finished.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class SpecGerritTriggerHudsonTest extends HudsonTestCase {

    //TODO Fix the SshdServerMock so that asserts can be done on approve commands.

    /**
     * The stream-events command.
     */
    protected static final String GERRIT_STREAM_EVENTS = "gerrit stream-events";
    private SshServer sshd;
    private File sshKey;
    private SshdServerMock server;

    @Override
    protected void setUp() throws Exception {
        sshKey = SshdServerMock.generateKeyPair();
        sshd = SshdServerMock.startServer();
        server = SshdServerMock.getInstance();
        server.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor(GERRIT_STREAM_EVENTS, SshdServerMock.CommandMock.class);
        server.returnCommandFor("gerrit approve.*", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor("gerrit version", SshdServerMock.EofCommandMock.class);
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        sshd.stop(true);
        sshd = null;
    }

    /**
     * Tests to trigger a build with a dynamic configuration.
     * @throws Exception if so.
     */
    @LocalData
    public void testDynamicTriggeredBuild() throws Exception {
        ((Config)PluginImpl.getInstance().getConfig()).setDynamicConfigRefreshInterval(1);
        FreeStyleProject project = DuplicatesUtil.createGerritDynamicTriggeredJob(this, "projectX");
        server.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        waitForDynamicTimer(project, 5000);
        PluginImpl.getInstance().triggerEvent(Setup.createPatchsetCreated());
        RunList<FreeStyleBuild> builds = DuplicatesUtil.waitForBuilds(project, 1, 5000);
        FreeStyleBuild build = builds.get(0);
        assertSame(Result.SUCCESS, build.getResult());
    }

    /**
     * Tests to trigger a build with the same patch set twice. Expecting one build to be scheduled with one cause.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testDoubleTriggeredBuild() throws Exception {
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(this, "projectX");
        server.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        PluginImpl.getInstance().triggerEvent(Setup.createPatchsetCreated());
        PluginImpl.getInstance().triggerEvent(Setup.createPatchsetCreated());

        RunList<FreeStyleBuild> builds = DuplicatesUtil.waitForBuilds(project, 1, 5000);
        FreeStyleBuild build = builds.get(0);
        assertSame(Result.SUCCESS, build.getResult());
        assertEquals(1, project.getBuilds().size());

        int count = 0;
        for (Cause cause : build.getCauses()) {
            if (cause instanceof GerritCause) {
                count++;
                assertNotNull(((GerritCause)cause).getContext());
                assertNotNull(((GerritCause)cause).getEvent());
            }
        }
        assertEquals(1, count);
    }

    /**
     * Tests to trigger a build with the same patch set twice, one is a manual event and the other a normal. Expecting
     * one build to be scheduled with two causes of different type.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testDoubleTriggeredBuildOfDifferentType() throws Exception {
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(this, "projectX");
        server.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated();
        ManualPatchsetCreated mpc = new ManualPatchsetCreated();
        mpc.setChange(patchsetCreated.getChange());
        mpc.setPatchset(patchsetCreated.getPatchSet());
        mpc.setAccount(patchsetCreated.getAccount());
        mpc.setUserName("bobby");
        PluginImpl.getInstance().triggerEvent(Setup.createPatchsetCreated());
        PluginImpl.getInstance().triggerEvent(mpc);

        RunList<FreeStyleBuild> builds = DuplicatesUtil.waitForBuilds(project, 1, 5000);
        FreeStyleBuild build = builds.get(0);
        assertSame(Result.SUCCESS, build.getResult());
        assertEquals(1, builds.size());

        int count = 0;
        for (Cause cause : build.getCauses()) {
            if (cause instanceof GerritCause) {
                count++;
                assertNotNull(((GerritCause)cause).getContext());
                assertNotNull(((GerritCause)cause).getContext().getThisBuild());
                assertNotNull(((GerritCause)cause).getEvent());
            }
        }
        assertEquals(2, count);
    }

    /**
     * Tests to trigger builds with two diffetent patch sets. Expecting two build to be scheduled with one cause each.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testDoubleTriggeredBuildsOfDifferentChange() throws Exception {
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(this, "projectX");
        server.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        PluginImpl.getInstance().triggerEvent(Setup.createPatchsetCreated());
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated();
        patchsetCreated.getChange().setNumber("2000");
        PluginImpl.getInstance().triggerEvent(patchsetCreated);

        RunList<FreeStyleBuild> builds = DuplicatesUtil.waitForBuilds(project, 2, 5000);
        assertEquals(2, builds.size());
        assertSame(Result.SUCCESS, builds.get(0).getResult());
        assertSame(Result.SUCCESS, builds.get(1).getResult());

        int count = 0;
        for (Cause cause : builds.get(0).getCauses()) {
            if (cause instanceof GerritCause) {
                count++;
            }
        }
        assertEquals(1, count);
        count = 0;
        for (Cause cause : builds.get(1).getCauses()) {
            if (cause instanceof GerritCause) {
                count++;
                assertNotNull(((GerritCause)cause).getContext());
                assertNotNull(((GerritCause)cause).getEvent());
            }
        }
        assertEquals(1, count);
    }

    /**
     * Tests that a build for a patch set of the gets canceled when a new patch set of the same change arrives.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testBuildLatestPatchsetOnly() throws Exception {
        ((Config)PluginImpl.getInstance().getConfig()).setGerritBuildCurrentPatchesOnly(true);
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(this, "projectX");
        project.getBuildersList().add(new SleepBuilder(2000));
        server.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        PatchsetCreated firstEvent = Setup.createPatchsetCreated();
        PluginImpl.getInstance().triggerEvent(firstEvent);
        AbstractBuild firstBuild = DuplicatesUtil.waitForBuildToStart(firstEvent, 5000);
        PatchsetCreated secondEvent = Setup.createPatchsetCreated();
        if (null != secondEvent.getPatchSet()) {
            secondEvent.getPatchSet().setNumber("2");
        }
        PluginImpl.getInstance().triggerEvent(secondEvent);
        RunList<FreeStyleBuild> builds = DuplicatesUtil.waitForBuilds(project, 2, 10000);
        assertEquals(2, builds.size());
        assertSame(Result.ABORTED, firstBuild.getResult());
        assertSame(Result.ABORTED, builds.getFirstBuild().getResult());
        assertSame(Result.SUCCESS, builds.getLastBuild().getResult());
    }

    /**
     * Same test logic as {@link #testBuildLatestPatchsetOnly()}
     * except the trigger is configured to not cancel the previous build.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testNotBuildLatestPatchsetOnly() throws Exception {
        ((Config)PluginImpl.getInstance().getConfig()).setGerritBuildCurrentPatchesOnly(false);
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(this, "projectX");
        project.getBuildersList().add(new SleepBuilder(2000));
        server.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        PatchsetCreated firstEvent = Setup.createPatchsetCreated();
        PluginImpl.getInstance().triggerEvent(firstEvent);
        AbstractBuild firstBuild = DuplicatesUtil.waitForBuildToStart(firstEvent, 5000);
        PatchsetCreated secondEvent = Setup.createPatchsetCreated();
        if (null != secondEvent.getPatchSet()) {
            secondEvent.getPatchSet().setNumber("2");
        }
        PluginImpl.getInstance().triggerEvent(secondEvent);
        RunList<FreeStyleBuild> builds = DuplicatesUtil.waitForBuilds(project, 2, 10000);
        assertEquals(2, builds.size());
        assertSame(Result.SUCCESS, firstBuild.getResult());
        assertSame(Result.SUCCESS, builds.getFirstBuild().getResult());
        assertSame(Result.SUCCESS, builds.getLastBuild().getResult());
    }

    /**
     * Tests that a comment added triggers a build correctly.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testTriggerOnCommentAdded() throws Exception {
        PluginImpl.getInstance().getConfig().setCategories(Setup.createCodeReviewVerdictCategoryList());
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJobForCommentAdded(this, "projectX");
        project.getBuildersList().add(new SleepBuilder(2000));
        server.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        CommentAdded firstEvent = Setup.createCommentAdded();
        PluginImpl.getInstance().triggerEvent(firstEvent);
        RunList<FreeStyleBuild> builds = DuplicatesUtil.waitForBuilds(project, 1, 10000);
        assertEquals(1, builds.size());
        assertSame(Result.SUCCESS, builds.getLastBuild().getResult());
    }

    /**
     * Tests that two comments added during the same time only triggers one build.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testDoubleTriggeredOnCommentAdded() throws Exception {
        PluginImpl.getInstance().getConfig().setCategories(Setup.createCodeReviewVerdictCategoryList());
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJobForCommentAdded(this, "projectX");
        project.getBuildersList().add(new SleepBuilder(2000));
        server.waitForCommand(GERRIT_STREAM_EVENTS, 2000);

        PluginImpl.getInstance().triggerEvent(Setup.createCommentAdded());
        PluginImpl.getInstance().triggerEvent(Setup.createCommentAdded());
        RunList<FreeStyleBuild> builds = DuplicatesUtil.waitForBuilds(project, 1, 10000);
        assertEquals(1, builds.size());
        assertSame(Result.SUCCESS, builds.getLastBuild().getResult());
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

        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(this, "projectX");

        project.renameTo("anotherName");
        configRoundtrip(((Item)project));

        assertEquals(0, h.countTrigger);

        PluginImpl p = PluginImpl.getInstance();
        p.triggerEvent(Setup.createPatchsetCreated());

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
