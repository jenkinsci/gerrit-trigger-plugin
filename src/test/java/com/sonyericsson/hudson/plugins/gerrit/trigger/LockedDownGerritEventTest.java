/*
 * The MIT License
 *
 * Copyright 2015 Ericsson.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger;

import static com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock.GERRIT_STREAM_EVENTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.concurrent.TimeUnit;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;

import org.apache.sshd.SshServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.TestUtils;
import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;

/**
 * Unit test to ensure build can be triggered even if
 * security is enabled and only authenticated users can login.
 *
 * @author Scott Hebert &lt;scott.hebert@ericsson.com&gt;
 */

public class LockedDownGerritEventTest {

    /**
     * An instance of Jenkins Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule j = new JenkinsRule();

    //private final String gerritServerName = "testServer";
    private final String projectName = "testProject";
    private final int port = 29418;
    private static final int NUMBEROFSENDERTHREADS = 1;

    private SshdServerMock server;
    private SshServer sshd;
    private SshdServerMock.KeyPairFiles sshKey;

    /**
     * Runs before test method.
     *
     * @throws Exception throw if so.
     */
    @Before
    public void setUp() throws Exception {
        sshKey = SshdServerMock.generateKeyPair();
        System.setProperty(PluginImpl.TEST_SSH_KEYFILE_LOCATION_PROPERTY, sshKey.getPrivateKey().getAbsolutePath());
        server = new SshdServerMock();
        sshd = SshdServerMock.startServer(server);
        server.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor(GERRIT_STREAM_EVENTS, SshdServerMock.CommandMock.class);
        server.returnCommandFor("gerrit review.*", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor("gerrit version", SshdServerMock.EofCommandMock.class);
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
     * Test that a build can still be triggered if only authenticated
     * users can login.
     *
     * Given a secured Jenkins instance
     * And a permission scheme that does not provide any permissions to Anonymous
     * And a configured Gerrit Server
     * And a Gerrit Triggered Job
     * When an event triggers a build
     * Then the build completes successfully.
     *
     * @throws Exception throw if so.
     */
    @Test
    public void testTriggerWithLockedDownInstance() throws Exception {
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(j, projectName);

        Setup.lockDown(j);

        GerritTrigger trigger = project.getTrigger(GerritTrigger.class);
        trigger.setSilentStartMode(false);

        GerritServer gerritServer = new GerritServer(PluginImpl.DEFAULT_SERVER_NAME);
        SshdServerMock.configureFor(sshd, gerritServer);
        PluginImpl.getInstance().addServer(gerritServer);
        gerritServer.getConfig().setNumberOfSendingWorkerThreads(NUMBEROFSENDERTHREADS);
        ((Config)gerritServer.getConfig()).setGerritAuthKeyFile(sshKey.getPrivateKey());
        gerritServer.start();

        gerritServer.triggerEvent(Setup.createPatchsetCreated());

        TestUtils.waitForBuilds(project, 1);
        //wait until command is registered
        // CS IGNORE MagicNumber FOR NEXT 2 LINES. REASON: ConstantsNotNeeded
        Thread.sleep(TimeUnit.SECONDS.toMillis(10));
        assertEquals(2, server.getNrCommandsHistory("gerrit review.*"));

        FreeStyleBuild buildOne = project.getLastCompletedBuild();
        assertSame(Result.SUCCESS, buildOne.getResult());
        assertEquals(1, project.getLastCompletedBuild().getNumber());
        assertSame(PluginImpl.DEFAULT_SERVER_NAME,
            buildOne.getCause(GerritCause.class).getEvent().getProvider().getName());

    }
}
