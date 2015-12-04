/*
 * The MIT License
 *
 * Copyright 2015 Jiri Engelthaler. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.manual;

import static org.mockito.Mockito.any;

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;

import net.sf.json.JSONObject;

import org.apache.sshd.SshServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.List;

import javax.servlet.http.HttpSession;

import hudson.model.FreeStyleProject;

//CS IGNORE LineLength FOR NEXT 1 LINES. REASON: static import
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//CS IGNORE LineLength FOR NEXT 1 LINES. REASON: static java import.

/**
 * @author Jiri Engelthaler &lt;EngyCZ@gmail.com&gt;
 */

public class ManualTriggerActionApprovalTest {

    /**
     * An instance of Jenkins Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule j = new JenkinsRule();

    private final String gerritServerName = "testServer";
    private final String projectName = "testProject";
    private final int port = 29418;

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
        sshd = SshdServerMock.startServer(port, server);
        server.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor("gerrit version", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor("gerrit query --format=JSON --current-patch-set \"status:open\"",
            SshdServerMock.SendQueryLastPatchSet.class);
        server.returnCommandFor("gerrit query --format=JSON --patch-sets --current-patch-set \"status:open\"",
            SshdServerMock.SendQueryAllPatchSets.class);
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
     * Tests {@link ManualTriggerAction#getApprovals(net.sf.json.JSONObject, int)}.
     * With an last patchset
     * @throws Exception if so.
     */
    @Test
    public void testDoGerritSearchLastPatchSet() throws Exception {
        GerritServer gerritServer = new GerritServer(gerritServerName);
        PluginImpl.getInstance().addServer(gerritServer);
        gerritServer.start();

        Config config = (Config)gerritServer.getConfig();
        config.setGerritAuthKeyFile(sshKey.getPrivateKey());
        config.setGerritHostName("localhost");
        config.setGerritSshPort(port);
        gerritServer.startConnection();

        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(j, projectName, gerritServerName);

        final ManualTriggerAction action = new ManualTriggerAction();
        HttpSession session = mock(HttpSession.class);

        StaplerRequest request = mock(StaplerRequest.class);
        StaplerResponse response = mock(StaplerResponse.class);

        when(request.getSession(true)).thenReturn(session);

        doAnswer(new Answer() {
            /**
             * @see org.mockito.stubbing.Answer#answer(org.mockito.invocation.InvocationOnMock)
             */
            @Override
            public Object answer(InvocationOnMock aInvocation) throws Throwable {
                List<JSONObject> json = (List<JSONObject>)aInvocation.getArguments()[1];

                ManualTriggerAction.HighLow highLow = action.getCodeReview(json.get(0), 2);
                assertEquals(2, highLow.getHigh());
                assertEquals(-1, highLow.getLow());

                highLow = action.getVerified(json.get(0), 2);
                assertEquals(2, highLow.getHigh());
                assertEquals(-1, highLow.getLow());

                return null;
            }

        }).when(session).setAttribute(eq("result"), any());

        action.doGerritSearch("status:open", gerritServerName, false, request, response);
    }

    /**
     * Tests {@link ManualTriggerAction#getApprovals(net.sf.json.JSONObject, int)}.
     * With an all patchsets
     * @throws Exception if so.
     */
    @Test
    public void testDoGerritSearchAllPatchSets() throws Exception {
        GerritServer gerritServer = new GerritServer(gerritServerName);
        PluginImpl.getInstance().addServer(gerritServer);
        gerritServer.start();

        Config config = (Config)gerritServer.getConfig();
        config.setGerritAuthKeyFile(sshKey.getPrivateKey());
        config.setGerritHostName("localhost");
        config.setGerritSshPort(port);
        gerritServer.startConnection();

        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(j, projectName, gerritServerName);

        final ManualTriggerAction action = new ManualTriggerAction();
        HttpSession session = mock(HttpSession.class);

        StaplerRequest request = mock(StaplerRequest.class);
        StaplerResponse response = mock(StaplerResponse.class);

        when(request.getSession(true)).thenReturn(session);

        doAnswer(new Answer() {
            /**
             * @see org.mockito.stubbing.Answer#answer(org.mockito.invocation.InvocationOnMock)
             */
            @Override
            public Object answer(InvocationOnMock aInvocation) throws Throwable {
                List<JSONObject> json = (List<JSONObject>)aInvocation.getArguments()[1];

                ManualTriggerAction.HighLow highLow = action.getCodeReview(json.get(0), 1);
                assertEquals(0, highLow.getHigh());
                assertEquals(0, highLow.getLow());

                highLow = action.getCodeReview(json.get(0), 2);
                assertEquals(2, highLow.getHigh());
                assertEquals(-1, highLow.getLow());

                highLow = action.getVerified(json.get(0), 1);
                assertEquals(0, highLow.getHigh());
                assertEquals(0, highLow.getLow());

                highLow = action.getVerified(json.get(0), 2);
                assertEquals(2, highLow.getHigh());
                assertEquals(-1, highLow.getLow());

                return null;
            }

        }).when(session).setAttribute(eq("result"), any());

        action.doGerritSearch("status:open", gerritServerName, true, request, response);
    }
}
