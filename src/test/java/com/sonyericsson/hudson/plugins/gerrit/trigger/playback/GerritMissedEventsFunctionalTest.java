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
package com.sonyericsson.hudson.plugins.gerrit.trigger.playback;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.api.GerritTriggerApi;
import com.sonyericsson.hudson.plugins.gerrit.trigger.api.exception.GerritTriggerException;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.TestUtils;
import com.sonymobile.tools.gerrit.gerritevents.Handler;
import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;

import org.apache.sshd.server.SshServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock.GERRIT_STREAM_EVENTS;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

//CS IGNORE AvoidStarImport FOR NEXT 1 LINES. REASON: UnitTest.

/**
 * Functional Test for Missed Events Playback.
 *
 * @author scott.heber@ericsson.com;
 */
public class GerritMissedEventsFunctionalTest {

    /**
     * An instance of Jenkins Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule j = new JenkinsRule();
    /**
     * An instance of WireMock Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: WireMockRule.
    @Rule
    public final WireMockRule wireMockRule = new WireMockRule(0); // No-args constructor defaults to port 8089

    private static final int HTTPOK = 200;
    private static final int HTTPERROR = 500;
    private static final int SLEEPTIME = 1000;
    // 15 seconds
    private static final long LONGSLEEPTIME = 15000;
    private static final long WAITTIMEFOREVENTLOGDISABLE = 6000;
    private static final long TIMESTAMPDELTA = 1000;

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

        server = new SshdServerMock();
        sshd = SshdServerMock.startServer(server);
        server.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor(GERRIT_STREAM_EVENTS, SshdServerMock.CommandMock.class);
        server.returnCommandFor("gerrit review.*", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor("gerrit version", SshdServerMock.EofCommandMock.class);
        stubFor(get(urlEqualTo("/plugins/" + GerritMissedEventsPlaybackManager.EVENTS_LOG_PLUGIN_NAME + "/"))
                .willReturn(aResponse()
                        .withStatus(HTTPOK)
                        .withHeader("Content-Type", "text/html")
                        .withBody("ok")));

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
     * Test the scenario whereby connection is restarted and events are missed
     * but replayed. This simulates a restart of Jenkins.
     * @throws Exception Error creating job.
     */
    @Test
    public void testRestartWithMissedEvents() throws Exception {
        GerritServer gerritServer = new GerritServer("ZZZZZ");
        PluginImpl.getInstance().addServer(gerritServer);
        gerritServer.start();

        Config config = (Config)gerritServer.getConfig();
        config.setUseRestApi(true);
        config.setGerritHttpUserName("scott");
        config.setGerritHttpPassword("scott");
        config.setGerritFrontEndURL("http://localhost:" + wireMockRule.port());

        config.setGerritProxy("");
        config.setGerritAuthKeyFile(sshKey.getPrivateKey());
        config = SshdServerMock.getConfigFor(sshd, config);
        gerritServer.setConfig(config);


        gerritServer.startConnection();

        while (!gerritServer.isConnected()) {
            Thread.sleep(SLEEPTIME);
        }

        restartWithMissedEvents(gerritServer, "Test ZZZZZ");

    }

    /**
     * Test the scenario whereby events-log plugin becomes not supported during operation,
     * events are received but NOT persisted and Jenkins is restarted.
     * @throws Exception Error creating job.
     */
    @Test
    public void testLosePluginSupportedWithEventsAndRestart() throws Exception {

        int buildNum = 0;

        GerritServer gerritServer = new GerritServer("ABCDEF");
        PluginImpl.getInstance().addServer(gerritServer);
        gerritServer.start();

        Config config = (Config)gerritServer.getConfig();
        config.setGerritFrontEndURL("http://localhost:" + wireMockRule.port());
        config.setGerritProxy("");
        config.setGerritAuthKeyFile(sshKey.getPrivateKey());
        SshdServerMock.configureFor(sshd, gerritServer);
        gerritServer.startConnection();

        while (!gerritServer.isConnected()) {
            Thread.sleep(SLEEPTIME);
        }

        Config config2 = (Config)gerritServer.getConfig();
        config2.setUseRestApi(true);
        config2.setGerritHttpUserName("scott");
        config2.setGerritHttpPassword("scott");

        //simulate a save of config...it calls doConfigSubmit()
        gerritServer.getMissedEventsPlaybackManager().checkIfEventsLogPluginSupported();

        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(j, "Test ABCDEF", gerritServer.getName());
        createAndWaitforPatchset(gerritServer, project, ++buildNum);
        createAndWaitforPatchset(gerritServer, project, ++buildNum);

        EventTimeSlice lastTimeStamp = gerritServer.getMissedEventsPlaybackManager().getServerTimestamp();

        // now we force the plugin is supported to false...
        stubFor(get(urlEqualTo("/plugins/" + GerritMissedEventsPlaybackManager.EVENTS_LOG_PLUGIN_NAME + "/"))
                .willReturn(aResponse()
                        .withStatus(HTTPERROR)
                        .withHeader("Content-Type", "text/html")
                        .withBody("error")));

        // Wait for it to be picked up
        Thread.sleep(WAITTIMEFOREVENTLOGDISABLE);

        assertFalse(gerritServer.getMissedEventsPlaybackManager().isSupported());

        createAndWaitforPatchset(gerritServer, project, ++buildNum);
        createAndWaitforPatchset(gerritServer, project, ++buildNum);

        Long newTimestamp = lastTimeStamp.getTimeSlice() + TIMESTAMPDELTA;

        gerritServer.stopConnection();
        Thread.sleep(SLEEPTIME);

        // now we re-enable feature:
        stubFor(get(urlEqualTo("/plugins/" + GerritMissedEventsPlaybackManager.EVENTS_LOG_PLUGIN_NAME + "/"))
                .willReturn(aResponse()
                        .withStatus(HTTPOK)
                        .withHeader("Content-Type", "text/html")
                        .withBody("ok")));

        String json = "{\"type\":\"patchset-created\",\"change\":{\"project\":\""
                + project.getName()
                + "\",\"branch\":\"develop\","
                + "\"id\":\"Icae2322236e0e521950a0232effda08d6ffcdab7\",\"number\":\"392335\",\"subject\":\""
                + "IPSEC: Small test code fixes due to Sonar warnings\",\"owner\":{\"name\":\"Szymon L\","
                + "\"email\":\"szymon.l@abc.com\",\"username\":\"eszyabc\"},\"url\":"
                + "\"https://abc.aaa.se/392335\","
                + "\"commitMessage\":\"IPSEC: Small test code fixes due to Sonar warnings\\n\\nChange-Id: "
                + "Icae2322236e0e521950a0232effda08d6ffcdab7\\nSigned-off-by:Szymon L \\u003cszymo"
                + "n.l@abc.com\\u003e\\n\",\"status\":\"NEW\"},\"patchSet\":{\"number\":\"2\",\"revision"
                + "\":\"607eea8f472235b3ee47483b630003250764dab2\",\"parents\":"
                + "[\"87c0e57d2497ab334584ec9d1a7953ebcf016e10\"],"
                + "\"ref\":\"refs/changes/35/392335/2\",\"uploader\":{\"name\":\"Szymon L\",\"email\":"
                + "\"szymon"
                + "@abc.com\",\"username\":\"eszyabc\"},\"createdOn\":1413448337,\"author\":{\"name\":\"Szy"
                + "mon L\",\"email\":\"szymon.l@abc.com\",\"username\":\"eszyabc\"},\"isDraft\""
                + ":false,\""
                + "sizeInsertions\":6,\"sizeDeletions\":-7},\"author\":{\"name\":\"Build user for \","
                + "\"email\":\"tnbuilder@"
                + "abc.se\",\"username\":\"tnabc\"},\"approvals\":[{\"type\":\"Verified\""
                + ",\"description\":"
                + "\"Verified\",\"value\":\"-1\"}],\"comment\":\"Patch Set 2: Verified-1\\n\\nBuild Failed \\n\\nhttp:"
                + "//jenkins/tn/job/tn-review/22579/ : FAILURE\",\"eventCreatedOn\":" + newTimestamp.toString() + "}\n";

        stubFor(get(urlMatching(GerritMissedEventsPlaybackManagerTest.EVENTS_LOG_CHANGE_EVENTS_URL_REGEXP))
                .willReturn(aResponse()
                        .withStatus(HTTPOK)
                        .withHeader("Content-Type", "text/html")
                        .withBody(json)));

        gerritServer.restartConnection();

        Thread.sleep(LONGSLEEPTIME);
        assertEquals(buildNum, project.getLastCompletedBuild().getNumber());

    }
    /**
     * Test the scenario whereby connection is restarted and events are missed
     * but replayed. This simulates when REST API is enabled and connection is restarted.
     * @throws Exception Error creating job.
     */
    @Test
    public void testRestartWithRESTApiChangeMissedEvents() throws Exception {
        GerritServer gerritServer = new GerritServer("ABCDEF");
        PluginImpl.getInstance().addServer(gerritServer);
        gerritServer.start();

        Config config = (Config)gerritServer.getConfig();
        config.setGerritFrontEndURL("http://localhost:" + wireMockRule.port());
        config.setGerritProxy("");
        config.setGerritAuthKeyFile(sshKey.getPrivateKey());
        SshdServerMock.configureFor(sshd, gerritServer);
        gerritServer.startConnection();

        while (!gerritServer.isConnected()) {
            Thread.sleep(SLEEPTIME);
        }

        Config config2 = (Config)gerritServer.getConfig();
        config2.setUseRestApi(true);
        config2.setGerritHttpUserName("scott");
        config2.setGerritHttpPassword("scott");

        //simulate a save of config...it calls doConfigSubmit()
        gerritServer.getMissedEventsPlaybackManager().checkIfEventsLogPluginSupported();

        restartWithMissedEvents(gerritServer, "Test ABCDEF");
    }

    /**
     * Helper method to create patchset and wait for associated build.
     * @param gServer configured Gerrit Server.
     * @param project Project.
     * @param buildNumberToWaitFor build number to wait for.
     * @throws Exception Error creating job.
     */
    private void createAndWaitforPatchset(GerritServer gServer,
                                          FreeStyleProject project,
                                          int buildNumberToWaitFor) throws Exception {

        GerritTriggerApi api = new GerritTriggerApi();
        Handler handler = null;
        try {
            handler = api.getHandler();
        } catch (GerritTriggerException ex) {
            fail(ex.getMessage());
        }
        assertNotNull(handler);
        handler.post(Setup.createPatchsetCreated(gServer.getName()));
        TestUtils.waitForBuilds(project, buildNumberToWaitFor);

    }
    /**
     * Helper method to test the scenarios whereby connection is restarted
     * and events are missed but replayed.
     * @param gServer configured Gerrit Server.
     * @param projectName Project name.
     * @throws Exception Error creating job.
     */
    private void restartWithMissedEvents(GerritServer gServer, String projectName) throws Exception {
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(j, projectName, gServer.getName());
        createAndWaitforPatchset(gServer, project, 1);

        assertNotNull(gServer.getMissedEventsPlaybackManager().getServerTimestamp());
        FreeStyleBuild buildOne = project.getLastCompletedBuild();
        assertSame(Result.SUCCESS, buildOne.getResult());
        assertEquals(1, project.getLastCompletedBuild().getNumber());
        assertSame(gServer.getName(), buildOne.getCause(GerritCause.class).getEvent().getProvider().getName());

        gServer.stopConnection();
        Thread.sleep(SLEEPTIME);

        String json = "{\"type\":\"patchset-created\",\"change\":{\"project\":\""
                + projectName
                + "\",\"branch\":\"develop\","
                + "\"id\":\"Icae2322236e0e521950a0232effda08d6ffcdab7\",\"number\":\"392335\",\"subject\":\""
                + "IPSEC: Small test code fixes due to Sonar warnings\",\"owner\":{\"name\":\"Szymon L\","
                + "\"email\":\"szymon.l@abc.com\",\"username\":\"eszyabc\"},\"url\":"
                + "\"https://abc.aaa.se/392335\","
                + "\"commitMessage\":\"IPSEC: Small test code fixes due to Sonar warnings\\n\\nChange-Id: "
                + "Icae2322236e0e521950a0232effda08d6ffcdab7\\nSigned-off-by:Szymon L \\u003cszymo"
                + "n.l@abc.com\\u003e\\n\",\"status\":\"NEW\"},\"patchSet\":{\"number\":\"2\",\"revision"
                + "\":\"607eea8f472235b3ee47483b630003250764dab2\",\"parents\":"
                + "[\"87c0e57d2497ab334584ec9d1a7953ebcf016e10\"],"
                + "\"ref\":\"refs/changes/35/392335/2\",\"uploader\":{\"name\":\"Szymon L\",\"email\":"
                + "\"szymon"
                + "@abc.com\",\"username\":\"eszyabc\"},\"createdOn\":1413448337,\"author\":{\"name\":\"Szy"
                + "mon L\",\"email\":\"szymon.l@abc.com\",\"username\":\"eszyabc\"},\"isDraft\""
                + ":false,\""
                + "sizeInsertions\":6,\"sizeDeletions\":-7},\"author\":{\"name\":\"Build user for \","
                + "\"email\":\"tnbuilder@"
                + "abc.se\",\"username\":\"tnabc\"},\"approvals\":[{\"type\":\"Verified\""
                + ",\"description\":"
                + "\"Verified\",\"value\":\"-1\"}],\"comment\":\"Patch Set 2: Verified-1\\n\\nBuild Failed \\n\\nhttp:"
                + "//jenkins/tn/job/tn-review/22579/ : FAILURE\",\"eventCreatedOn\":1418133772}\n";

        stubFor(get(urlMatching(GerritMissedEventsPlaybackManagerTest.EVENTS_LOG_CHANGE_EVENTS_URL_REGEXP))
                .willReturn(aResponse()
                        .withStatus(HTTPOK)
                        .withHeader("Content-Type", "text/html")
                        .withBody(json)));

        gServer.restartConnection();

        TestUtils.waitForBuilds(project, 2);

    }
}
