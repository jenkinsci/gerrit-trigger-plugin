/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.GerritEventLifeCycleAdaptor;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.SshdServerMock;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.util.RunList;
import org.apache.sshd.SshServer;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

//CS IGNORE MagicNumber FOR NEXT 300 LINES. REASON: Testdata.

/**
 * Some full run-through tests from trigger to build finished.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class SpecGerritHudsonTrigger extends HudsonTestCase {

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
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        sshd.stop(true);
        sshd = null;
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

        RunList<FreeStyleBuild> builds = waitForBuilds(project, 1, 5000);
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
     * one build to be scheduled with two causes of different type..
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

        RunList<FreeStyleBuild> builds = waitForBuilds(project, 1, 5000);
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

        RunList<FreeStyleBuild> builds = waitForBuilds(project, 2, 5000);
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
        AbstractBuild firstBuild = waitForBuildToStart(firstEvent, 2000);
        PatchsetCreated secondEvent = Setup.createPatchsetCreated();
        secondEvent.getPatchSet().setNumber("2");
        PluginImpl.getInstance().triggerEvent(secondEvent);
        RunList<FreeStyleBuild> builds = waitForBuilds(project, 2, 5000);
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
        AbstractBuild firstBuild = waitForBuildToStart(firstEvent, 2000);
        PatchsetCreated secondEvent = Setup.createPatchsetCreated();
        secondEvent.getPatchSet().setNumber("2");
        PluginImpl.getInstance().triggerEvent(secondEvent);
        RunList<FreeStyleBuild> builds = waitForBuilds(project, 2, 5000);
        assertEquals(2, builds.size());
        assertSame(Result.SUCCESS, firstBuild.getResult());
        assertSame(Result.SUCCESS, builds.getFirstBuild().getResult());
        assertSame(Result.SUCCESS, builds.getLastBuild().getResult());
    }

    /**
     * Waits for a build to start for the specified event.
     *
     * @param event     the event to monitor.
     * @param timeoutMs the maximum time in ms to wait for the build to start.
     * @return the build that started.
     */
    private AbstractBuild waitForBuildToStart(PatchsetCreated event, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        final AtomicReference<AbstractBuild> ref = new AtomicReference<AbstractBuild>();
        event.addListener(new GerritEventLifeCycleAdaptor() {
            @Override
            public void buildStarted(PatchsetCreated event, AbstractBuild build) {
                ref.getAndSet(build);
            }
        });
        while (ref.get() == null) {
            if (startTime - System.currentTimeMillis() >= timeoutMs) {
                throw new RuntimeException("Timeout!");
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                System.err.println("Interrupted while waiting!");
            }
        }
        return ref.get();
    }

    /**
     * Utility method that returns when the expected number of builds are done, or the timeout has expired.
     *
     * @param project   the project to check
     * @param number    the number of builds to wait for.
     * @param timeoutMs the timeout in ms.
     * @return the builds.
     */
    private RunList<FreeStyleBuild> waitForBuilds(FreeStyleProject project, int number, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        while (project.getBuilds().size() < number) {
            if (startTime - System.currentTimeMillis() >= timeoutMs) {
                throw new RuntimeException("Timeout!");
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                System.err.println("Interrupted while waiting!");
            }
        }
        boolean allDone = false;
        do {
            boolean thisTime = true;
            for (AbstractBuild b : project.getBuilds()) {
                if (b.isBuilding()) {
                    thisTime = false;
                }
            }
            if (thisTime) {
                allDone = true;
            } else {
                if (startTime - System.currentTimeMillis() >= timeoutMs) {
                    throw new RuntimeException("Timeout!");
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    System.err.println("Interrupted while waiting!");
                }
            }
        } while (!allDone);
        return project.getBuilds();
    }
}
