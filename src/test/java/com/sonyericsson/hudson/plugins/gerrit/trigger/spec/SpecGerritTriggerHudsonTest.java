/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012, 2013 Sony Mobile Communications AB. All rights reserved.
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
import hudson.util.RunList;

import org.apache.sshd.SshServer;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SleepBuilder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.Arrays;
import java.util.List;

import static com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock.GERRIT_STREAM_EVENTS;

//CS IGNORE MagicNumber FOR NEXT 400 LINES. REASON: Testdata.

/**
 * Some full run-through tests from trigger to build finished.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class SpecGerritTriggerHudsonTest extends HudsonTestCase {

    //TODO Fix the SshdServerMock so that asserts can be done on review commands.

    private SshServer sshd;
    private SshdServerMock.KeyPairFiles sshKey;
    private SshdServerMock server;

    @Override
    protected void setUp() throws Exception {
        sshKey = SshdServerMock.generateKeyPair();
        server = new SshdServerMock();
        sshd = SshdServerMock.startServer(server);
        server.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor(GERRIT_STREAM_EVENTS, SshdServerMock.CommandMock.class);
        server.returnCommandFor("gerrit review.*", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor("gerrit approve.*", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor("gerrit version", SshdServerMock.EofCommandMock.class);
        System.setProperty(PluginImpl.TEST_SSH_KEYFILE_LOCATION_PROPERTY, sshKey.getPrivateKey().getAbsolutePath());
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
        GerritServer gerritServer = PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME);
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(this, "projectX");
        project.getBuildersList().add(new SleepBuilder(5000));
        server.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
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
    public void testTripleTriggeredBuildWithProjects() throws Exception {
        GerritServer gerritServer = PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME);
        FreeStyleProject project1 = DuplicatesUtil.createGerritTriggeredJob(this, "projectX");
        FreeStyleProject project2 = DuplicatesUtil.createGerritTriggeredJob(this, "projectY");
        project1.getBuildersList().add(new SleepBuilder(5000));
        server.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        boolean started = false;

        gerritServer.triggerEvent(Setup.createPatchsetCreated());
        while (!started) {
            if (project1.isBuilding()) {
                started = true;
            } else {
                System.out.println("SLEEP 1");
                Thread.sleep(1000);
            }
        }
        gerritServer.triggerEvent(Setup.createPatchsetCreated());
        while (project1.isBuilding() || project1.isInQueue()) {
            System.out.println("SLEEP 2");
            Thread.sleep(1000);
        }

        Thread.sleep(3000);
        started = false;
        gerritServer.triggerEvent(Setup.createPatchsetCreated());
        while (!started) {
            if (project1.isBuilding()) {
                started = true;
            } else {
                System.out.println("SLEEP 3");
                Thread.sleep(1000);
            }
        }
        while (project1.isBuilding() || project1.isInQueue()) {
            System.out.println("SLEEP 4");
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
}
