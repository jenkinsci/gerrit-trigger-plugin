/*
 * The MIT License
 *
 * Copyright (c) 2014 Ericsson
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.playback;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.GerritPluginChecker;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.MockPluginCheckerConfig;
import com.sonymobile.tools.gerrit.gerritevents.GerritHandler;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;

import hudson.XmlFile;
import jenkins.model.Jenkins;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

/**
 *
 * missed events tests for events-log plugin interaction.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ Jenkins.class, PluginImpl.class, GerritMissedEventsPlaybackManager.class, GerritPluginChecker.class })
@PowerMockIgnore("javax.net.ssl.*")
public class GerritMissedEventsPlaybackManagerTest {

    /**
     * regexp for change events plugin.
     */
    public static final String EVENTS_LOG_CHANGE_EVENTS_URL_REGEXP = ".+plugins/events-log/events/.+";
    /**
     * instance of WireMockRule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: WireMockRule.
    @Rule
    public final WireMockRule wireMockRule = new WireMockRule(0); // No-args constructor defaults to port 8089
    private XmlFile xmlFile;
    private static final int SLEEPTIME = 500;
    private static final int HTTPOK = 200;

    /**
     * Default constructor.
     */
    public GerritMissedEventsPlaybackManagerTest() {
    }

    /**
     * Create ReplicationQueueTaskDispatcher with a mocked GerritHandler.
     * @throws IOException if it occurs.
     */
    @Before
    public void setUp() throws IOException {
        Jenkins jenkinsMock = mock(Jenkins.class);
        PowerMockito.mockStatic(Jenkins.class);
        when(Jenkins.getInstance()).thenReturn(jenkinsMock);

        PluginImpl plugin = PowerMockito.mock(PluginImpl.class);
        GerritServer server = mock(GerritServer.class);

        MockPluginCheckerConfig config = new MockPluginCheckerConfig();
        config.setGerritFrontEndURL("http://localhost:" + wireMockRule.port());
        config.setUseRestApi(true);
        config.setGerritHttpUserName("user");
        config.setGerritHttpPassword("passwd");

        when(plugin.getServer(any(String.class))).thenReturn(server);
        GerritHandler handler = mock(GerritHandler.class);
        when(plugin.getHandler()).thenReturn(handler);
        when(server.getConfig()).thenReturn(config);
        PowerMockito.mockStatic(PluginImpl.class);
        PowerMockito.when(PluginImpl.getInstance()).thenReturn(plugin);
        PowerMockito.when(PluginImpl.getServer_(any(String.class))).thenReturn(server);

        PowerMockito.mockStatic(GerritMissedEventsPlaybackManager.class);

        File tmpFile = null;
        try {
            tmpFile = File.createTempFile("gerrit-server-timestamps", ".xml");
        } catch (IOException e) {
            fail("Failed to create Temp File");
        }
        tmpFile.deleteOnExit();
        PrintWriter out = null;
        try {
            out = new PrintWriter(tmpFile);
        } catch (FileNotFoundException e) {
            fail("Failed to write to Temp File");
        }
        String text = "<?xml version='1.0' encoding='UTF-8'?>\n"
                + "<com.sonyericsson.hudson.plugins.gerrit.trigger.playback.EventTimeSlice "
                + "plugin='gerrit-trigger@2.14.0-SNAPSHOT'>"
                + "<timeSlice>1430244884000</timeSlice>"
                + "<events>"
                + "</events>"
                + "</com.sonyericsson.hudson.plugins.gerrit.trigger.playback.EventTimeSlice>";
        out.println(text);
        out.close();

        xmlFile = new XmlFile(tmpFile);
        PowerMockito.when(GerritMissedEventsPlaybackManager.getConfigXml("defaultServer")).thenReturn(xmlFile);

        PowerMockito.mockStatic(GerritPluginChecker.class);
        PowerMockito.when(GerritPluginChecker.isPluginEnabled((IGerritHudsonTriggerConfig)anyObject()
                , anyString(), anyBoolean())).thenReturn(true);
    }

    /**
     * returns a GerritMissedEventsPlaybackManager.
     * @return GerritMissedEventsPlaybackManager.
     */
    private GerritMissedEventsPlaybackManager setupManager() {
        GerritMissedEventsPlaybackManager missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        try {
            missingEventsPlaybackManager.load();
        } catch (IOException e) {
            fail(e.getMessage());
        }

        assertNotNull(missingEventsPlaybackManager.serverTimestamp);

        assertTrue("should be true", missingEventsPlaybackManager.isSupported());

        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
                "refs/heads/master");
        patchsetCreated.setReceivedOn(System.currentTimeMillis());

        missingEventsPlaybackManager.gerritEvent(patchsetCreated);
        patchsetCreated.setReceivedOn(System.currentTimeMillis());
        missingEventsPlaybackManager.gerritEvent(patchsetCreated);
        try {
            Thread.currentThread().sleep(SLEEPTIME);
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

        missingEventsPlaybackManager.connectionDown();
        missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        try {
            missingEventsPlaybackManager.load();
        } catch (IOException e) {
            fail(e.getMessage());
        }
        return missingEventsPlaybackManager;
    }

    /**
     * Given a Gerrit Server with Events-Log plugin installed
     * When we request the events from a time range
     * Then we receive the response in JSON
     * And we can convert to events.
     */
    @Test
    public void testConvertJSONToEvents() {
        GerritMissedEventsPlaybackManager missingEventsPlaybackManager =
                setupManager();

        String json = "{\"type\":\"patchset-created\",\"change\":{\"project\":\""
                + "testProject"
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
                + "//jenkins/tn/job/tn-review/22579/ : FAILURE\"}\n";


        stubFor(get(urlMatching(EVENTS_LOG_CHANGE_EVENTS_URL_REGEXP))
                .willReturn(aResponse()
                        .withStatus(HTTPOK)
                        .withHeader("Content-Type", "text/html")
                        .withBody(json)));


        List<GerritTriggeredEvent> events = new ArrayList<GerritTriggeredEvent>();
        try {
            events = missingEventsPlaybackManager.getEventsFromDateRange(
                    missingEventsPlaybackManager.getDateFromTimestamp());
        } catch (IOException e) {
            fail(e.getMessage());
        }

        Assert.assertTrue("Should have 1 event", events.size() == 1);

    }

    /**
     * Given a Gerrit Server with Events-log plugin installed
     * When we request the events from a time range
     * And we receive a malformed response
     * Then we log an error
     * And we return an empty set of events.
     */
    @Test
    public void testHandleMalformedConnection() {
        GerritMissedEventsPlaybackManager missingEventsPlaybackManager =
                setupManager();

        stubFor(get(urlMatching(EVENTS_LOG_CHANGE_EVENTS_URL_REGEXP))
                .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

        List<GerritTriggeredEvent> events = new ArrayList<GerritTriggeredEvent>();
        try {
            events = missingEventsPlaybackManager.getEventsFromDateRange(
                    missingEventsPlaybackManager.getDateFromTimestamp());
        } catch (IOException e) {
            fail(e.getMessage());
        }

        Assert.assertTrue("Should have 0 event", events.size() == 0);

    }

    /**
     * Given a Gerrit Server with Events-log plugin installed
     * When we request the events from a time range
     * And we receive a malformed response
     * Then we log an error
     * And we return an empty set of events.
     */
    @Test
    public void testHandleEmptyResponse() {
        GerritMissedEventsPlaybackManager missingEventsPlaybackManager =
                setupManager();

        stubFor(get(urlMatching(EVENTS_LOG_CHANGE_EVENTS_URL_REGEXP))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        List<GerritTriggeredEvent> events = new ArrayList<GerritTriggeredEvent>();
        try {
            events = missingEventsPlaybackManager.getEventsFromDateRange(
                    missingEventsPlaybackManager.getDateFromTimestamp());
        } catch (IOException e) {
            fail(e.getMessage());
        }

        Assert.assertTrue("Should have 0 event", events.size() == 0);

    }

    /**
     * Given a Gerrit Server with Events-log plugin installed
     * When we request the events from a time range
     * And we receive garbage as a response
     * Then we log an error
     * And we return an empty set of events.
     */
    @Test
    public void testHandleGarbageResponse() {
        GerritMissedEventsPlaybackManager missingEventsPlaybackManager =
                setupManager();

        stubFor(get(urlMatching(EVENTS_LOG_CHANGE_EVENTS_URL_REGEXP))
                .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE)));

        List<GerritTriggeredEvent> events = new ArrayList<GerritTriggeredEvent>();
        try {
            events = missingEventsPlaybackManager.getEventsFromDateRange(
                    missingEventsPlaybackManager.getDateFromTimestamp());
        } catch (IOException e) {
            fail(e.getMessage());
        }

        Assert.assertTrue("Should have 0 event", events.size() == 0);

    }

    /**
     * This tests that the initial `isSupported` state is false.
     */
    @Test
    public void testInitialSupportedState() {
       // Option 1a: not supported
       PowerMockito.when(GerritPluginChecker.isPluginEnabled((IGerritHudsonTriggerConfig)anyObject()
               , anyString(), anyBoolean())).thenReturn(false);

        GerritMissedEventsPlaybackManager missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        Assert.assertFalse("isSupported should be false", missingEventsPlaybackManager.isSupported());

       // Option 1b: not supported
       PowerMockito.when(GerritPluginChecker.isPluginEnabled((IGerritHudsonTriggerConfig)anyObject()
               , anyString(), anyBoolean())).thenReturn(null);

        missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        Assert.assertFalse("isSupported should be false", missingEventsPlaybackManager.isSupported());

       // Option 2: supported
       PowerMockito.when(GerritPluginChecker.isPluginEnabled((IGerritHudsonTriggerConfig)anyObject()
               , anyString(), anyBoolean())).thenReturn(true);

        missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        Assert.assertTrue("isSupported should be true", missingEventsPlaybackManager.isSupported());
    }

    /**
     * This tests that the supported state does not change if GerritPluginChecker
     * cannot determine the state successfully.
     */
    @Test
    public void testStateOnlyChangesWhenValid() {
       PowerMockito.when(GerritPluginChecker.isPluginEnabled((IGerritHudsonTriggerConfig)anyObject()
               , anyString(), anyBoolean())).thenReturn(false);

        GerritMissedEventsPlaybackManager missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        Assert.assertFalse("isSupported should be false", missingEventsPlaybackManager.isSupported());

        PowerMockito.when(GerritPluginChecker.isPluginEnabled((IGerritHudsonTriggerConfig)anyObject()
                , anyString(), anyBoolean())).thenReturn(true);

        missingEventsPlaybackManager.checkIfEventsLogPluginSupported();
        Assert.assertTrue("isSupported should be true", missingEventsPlaybackManager.isSupported());

        PowerMockito.when(GerritPluginChecker.isPluginEnabled((IGerritHudsonTriggerConfig)anyObject()
                , anyString(), anyBoolean())).thenReturn(null);

        missingEventsPlaybackManager.checkIfEventsLogPluginSupported();
        Assert.assertTrue("isSupported should be true", missingEventsPlaybackManager.isSupported());
    }
}
