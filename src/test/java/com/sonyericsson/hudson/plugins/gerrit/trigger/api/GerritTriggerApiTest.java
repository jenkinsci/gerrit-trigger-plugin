/*
 *  The MIT License
 *
 *  Copyright 2014 rinrinne All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.api;

import static com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock.GERRIT_STREAM_EVENTS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;

import org.apache.sshd.server.SshServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.jvnet.hudson.test.JenkinsRule;

import com.sonymobile.tools.gerrit.gerritevents.Handler;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.TestUtils;
import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;


/**
 * Unit test for API which is contributed to external.
 *
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
@WithJenkins
class GerritTriggerApiTest {
    // CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: Test data.

    /**
     * An instance of Jenkins Rule.
     */
    private JenkinsRule j;

    private SshdServerMock server;
    private SshServer sshd;
    private SshdServerMock.KeyPairFiles sshKey;

    /**
     * Runs before test method.
     *
     * @param rule the jenkins rule
     *
     * @throws Exception throw if so.
     */
    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        sshKey = SshdServerMock.generateKeyPair();

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
    @AfterEach
    void tearDown() throws Exception {
        server.stopServer(sshd);
        sshd = null;
    }

    /**
     * Test triggering job with events using API.
     * @throws Exception Error creating job.
     */
    @Test
    void testApiTriggerBuild() throws Exception {
        String gerritServerName = "testServer";
        GerritServer gerritServer = new GerritServer(gerritServerName);
        gerritServer.setConfig(SshdServerMock.getConfigFor(sshd, sshKey, gerritServer.getConfig()));
        PluginImpl.getInstance().addServer(gerritServer);
        gerritServer.start();
        gerritServer.startConnection();

        FreeStyleProject project = new TestUtils.JobBuilder(j).serverName(gerritServerName).build();
        server.waitForCommand(GERRIT_STREAM_EVENTS, 20000);
        GerritTriggerApi api = new GerritTriggerApi();
        Handler handler = assertDoesNotThrow(api::getHandler);
        assertNotNull(handler);
        handler.post(Setup.createPatchsetCreated(gerritServerName));
        TestUtils.waitForBuilds(project, 1, 20000);

        FreeStyleBuild buildOne = project.getLastCompletedBuild();
        assertSame(Result.SUCCESS, buildOne.getResult());
        assertEquals(1, project.getLastCompletedBuild().getNumber());
        assertSame(gerritServerName, buildOne.getCause(GerritCause.class).getEvent().getProvider().getName());
    }
}
