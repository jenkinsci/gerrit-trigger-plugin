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

import static com.sonyericsson.hudson.plugins.gerrit.trigger.test.SshdServerMock.GERRIT_STREAM_EVENTS;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.util.RunList;

import org.apache.sshd.SshServer;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.Handler;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.api.exception.GerritTriggerException;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.test.SshdServerMock;

/**
 * Unit test for API which is contributed to external.
 *
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public class GerritTriggerApiTest extends HudsonTestCase {
    private final String gerritServerName = "testServer";
    private final String projectName = "testProject";
    private final int timeToBuild = 5000;
    private final int port = 29418;

    private SshdServerMock server;
    private SshServer sshd;

    @Override
    protected void setUp() throws Exception {
        SshdServerMock.generateKeyPair();
        server = new SshdServerMock();
        sshd = SshdServerMock.startServer(port, server);
        server.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor(GERRIT_STREAM_EVENTS, SshdServerMock.CommandMock.class);
        server.returnCommandFor("gerrit review.*", SshdServerMock.EofCommandMock.class);
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
     * Test triggering job with events using API.
     * @throws Exception Error creating job.
     */
    @Test
    public void testApiTriggerBuild() throws Exception {
        GerritServer gerritServer = new GerritServer(gerritServerName);
        PluginImpl.getInstance().addServer(gerritServer);
        gerritServer.start();
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(this, projectName, gerritServerName);

        GerritTriggerApi api = new GerritTriggerApi();
        Handler handler = null;
        try {
            handler = api.getHandler();
        } catch (GerritTriggerException ex) {
            fail(ex.getMessage());
        }
        assertNotNull(handler);
        handler.post(Setup.createPatchsetCreated(gerritServerName));
        RunList<FreeStyleBuild> builds = DuplicatesUtil.waitForBuilds(project, 1, timeToBuild);

        FreeStyleBuild buildOne = builds.get(0);
        assertSame(Result.SUCCESS, buildOne.getResult());
        assertEquals(1, project.getBuilds().size());
        assertSame(gerritServerName, buildOne.getCause(GerritCause.class).getEvent().getProvider().getName());
    }
}
