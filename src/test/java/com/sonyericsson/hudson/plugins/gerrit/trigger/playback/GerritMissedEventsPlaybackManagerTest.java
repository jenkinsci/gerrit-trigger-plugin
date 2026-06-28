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
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
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
import hudson.security.ACL;
import jenkins.model.Jenkins;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 *
 * missed events tests for events-log plugin interaction.
 */
class GerritMissedEventsPlaybackManagerTest {

    /**
     * regexp for change events plugin.
     */
    public static final String EVENTS_LOG_CHANGE_EVENTS_URL_REGEXP = ".+plugins/events-log/events/.+";
    /**
     * instance of WireMockExtension.
     */
    @RegisterExtension
    private static final WireMockExtension WIRE_MOCK = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();
    private XmlFile xmlFile;
    private static final int SLEEPTIME = 500;
    private static final int HTTPOK = 200;
    private MockedStatic<Jenkins> jenkinsMockedStatic;
    private MockedStatic<PluginImpl> pluginMockedStatic;
    private MockedStatic<GerritMissedEventsPlaybackManager> playbackManagerMockedStatic;
    private MockedStatic<GerritPluginChecker> pluginCheckerMockedStatic;

    /**
     * Create ReplicationQueueTaskDispatcher with a mocked GerritHandler.
     */
    @BeforeEach
    void setUp() throws Exception {
        Jenkins jenkinsMock = mock(Jenkins.class);
        jenkinsMockedStatic = mockStatic(Jenkins.class);
        jenkinsMockedStatic.when(Jenkins::get).thenReturn(jenkinsMock);
        jenkinsMockedStatic.when(Jenkins::getAuthentication).thenReturn(ACL.SYSTEM);
        jenkinsMockedStatic.when(Jenkins::getAuthentication2).thenReturn(ACL.SYSTEM2);

        PluginImpl plugin = mock(PluginImpl.class);
        GerritServer server = mock(GerritServer.class);

        MockPluginCheckerConfig config = new MockPluginCheckerConfig();
        config.setGerritFrontEndURL("http://localhost:" + WIRE_MOCK.getPort());
        config.setUseRestApi(true);
        config.setGerritHttpUserName("user");
        config.setGerritHttpPassword("passwd");

        when(plugin.getServer(any(String.class))).thenReturn(server);
        GerritHandler handler = mock(GerritHandler.class);
        when(plugin.getHandler()).thenReturn(handler);
        when(server.getConfig()).thenReturn(config);
        pluginMockedStatic = mockStatic(PluginImpl.class);
        pluginMockedStatic.when(PluginImpl::getInstance).thenReturn(plugin);
        pluginMockedStatic.when(() -> PluginImpl.getServer_(any(String.class))).thenReturn(server);

        playbackManagerMockedStatic = mockStatic(GerritMissedEventsPlaybackManager.class);

        File tmpFile = File.createTempFile("gerrit-server-timestamps", ".xml");
        tmpFile.deleteOnExit();
        PrintWriter out = new PrintWriter(tmpFile);
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
        playbackManagerMockedStatic
                .when(() -> GerritMissedEventsPlaybackManager.getConfigXml("defaultServer"))
                .thenReturn(xmlFile);

        pluginCheckerMockedStatic = mockStatic(GerritPluginChecker.class);
        pluginCheckerMockedStatic.when(() -> GerritPluginChecker.isPluginEnabled(any(IGerritHudsonTriggerConfig.class)
                , anyString(), anyBoolean())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        jenkinsMockedStatic.close();
        pluginMockedStatic.close();
        playbackManagerMockedStatic.close();
        pluginCheckerMockedStatic.close();
    }

    /**
     * returns a GerritMissedEventsPlaybackManager.
     * @return GerritMissedEventsPlaybackManager.
     */
    private GerritMissedEventsPlaybackManager setupManager() {
        GerritMissedEventsPlaybackManager missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        assertDoesNotThrow(missingEventsPlaybackManager::load);

        assertNotNull(missingEventsPlaybackManager.serverTimestamp);

        assertTrue(missingEventsPlaybackManager.isSupported(), "should be true");

        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
                "refs/heads/master");
        patchsetCreated.setReceivedOn(System.currentTimeMillis());

        missingEventsPlaybackManager.gerritEvent(patchsetCreated);
        patchsetCreated.setReceivedOn(System.currentTimeMillis());
        missingEventsPlaybackManager.gerritEvent(patchsetCreated);
        assertDoesNotThrow(() -> Thread.sleep(SLEEPTIME));

        missingEventsPlaybackManager.connectionDown();
        missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        assertDoesNotThrow(missingEventsPlaybackManager::load);
        return missingEventsPlaybackManager;
    }

    /**
     * Given a Gerrit Server with Events-Log plugin installed
     * When we request the events from a time range
     * Then we receive the response in JSON
     * And we can convert to events.
     */
    @Test
    void testConvertJSONToEvents() {
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


        WIRE_MOCK.stubFor(get(urlMatching(EVENTS_LOG_CHANGE_EVENTS_URL_REGEXP))
                .willReturn(aResponse()
                        .withStatus(HTTPOK)
                        .withHeader("Content-Type", "text/html")
                        .withBody(json)));


        List<GerritTriggeredEvent> events = assertDoesNotThrow(() -> missingEventsPlaybackManager.getEventsFromDateRange(
                    missingEventsPlaybackManager.getDateFromTimestamp()));
        assertEquals(1, events.size(), "Should have 1 event");

    }

    /**
     * Given a Gerrit Server with Events-log plugin installed
     * When we request the events from a time range
     * And we receive a malformed response
     * Then we log an error
     * And we return an empty set of events.
     */
    @Test
    void testHandleMalformedConnection() {
        GerritMissedEventsPlaybackManager missingEventsPlaybackManager =
                setupManager();

        WIRE_MOCK.stubFor(get(urlMatching(EVENTS_LOG_CHANGE_EVENTS_URL_REGEXP))
                .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

        List<GerritTriggeredEvent> events = assertDoesNotThrow(() -> missingEventsPlaybackManager.getEventsFromDateRange(
                    missingEventsPlaybackManager.getDateFromTimestamp()));
        assertEquals(0, events.size(), "Should have 0 event");

    }

    /**
     * Given a Gerrit Server with Events-log plugin installed
     * When we request the events from a time range
     * And we receive a malformed response
     * Then we log an error
     * And we return an empty set of events.
     */
    @Test
    void testHandleEmptyResponse() {
        GerritMissedEventsPlaybackManager missingEventsPlaybackManager =
                setupManager();

        WIRE_MOCK.stubFor(get(urlMatching(EVENTS_LOG_CHANGE_EVENTS_URL_REGEXP))
                .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));

        List<GerritTriggeredEvent> events = assertDoesNotThrow(() -> missingEventsPlaybackManager.getEventsFromDateRange(
                    missingEventsPlaybackManager.getDateFromTimestamp()));
        assertEquals(0, events.size(), "Should have 0 event");

    }

    /**
     * Given a Gerrit Server with Events-log plugin installed
     * When we request the events from a time range
     * And we receive garbage as a response
     * Then we log an error
     * And we return an empty set of events.
     */
    @Test
    void testHandleGarbageResponse() {
        GerritMissedEventsPlaybackManager missingEventsPlaybackManager =
                setupManager();

        WIRE_MOCK.stubFor(get(urlMatching(EVENTS_LOG_CHANGE_EVENTS_URL_REGEXP))
                .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE)));

        List<GerritTriggeredEvent> events = assertDoesNotThrow(() -> missingEventsPlaybackManager.getEventsFromDateRange(
                    missingEventsPlaybackManager.getDateFromTimestamp()));
        assertEquals(0, events.size(), "Should have 0 event");

    }

    /**
     * This tests that the initial `isSupported` state is false.
     */
    @Test
    void testInitialSupportedState() {
       // Option 1a: not supported
       pluginCheckerMockedStatic.when(() -> GerritPluginChecker.isPluginEnabled(any(IGerritHudsonTriggerConfig.class)
               , anyString(), anyBoolean())).thenReturn(false);

        GerritMissedEventsPlaybackManager missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        assertFalse(missingEventsPlaybackManager.isSupported(), "isSupported should be false");

       // Option 1b: not supported
       pluginCheckerMockedStatic.when(() -> GerritPluginChecker.isPluginEnabled(any(IGerritHudsonTriggerConfig.class)
               , anyString(), anyBoolean())).thenReturn(null);

        missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        assertFalse(missingEventsPlaybackManager.isSupported(), "isSupported should be false");

       // Option 2: supported
       pluginCheckerMockedStatic.when(() -> GerritPluginChecker.isPluginEnabled(any(IGerritHudsonTriggerConfig.class)
               , anyString(), anyBoolean())).thenReturn(true);

        missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        assertTrue(missingEventsPlaybackManager.isSupported(), "isSupported should be true");
    }

    /**
     * This tests that the supported state does not change if GerritPluginChecker
     * cannot determine the state successfully.
     */
    @Test
    void testStateOnlyChangesWhenValid() {
       pluginCheckerMockedStatic.when(() -> GerritPluginChecker.isPluginEnabled(any(IGerritHudsonTriggerConfig.class)
               , anyString(), anyBoolean())).thenReturn(false);

        GerritMissedEventsPlaybackManager missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        assertFalse(missingEventsPlaybackManager.isSupported(), "isSupported should be false");

        pluginCheckerMockedStatic.when(() -> GerritPluginChecker.isPluginEnabled(any(IGerritHudsonTriggerConfig.class)
                , anyString(), anyBoolean())).thenReturn(true);

        missingEventsPlaybackManager.checkIfEventsLogPluginSupported();
        assertTrue(missingEventsPlaybackManager.isSupported(), "isSupported should be true");

        pluginCheckerMockedStatic.when(() -> GerritPluginChecker.isPluginEnabled(any(IGerritHudsonTriggerConfig.class)
                , anyString(), anyBoolean())).thenReturn(null);

        missingEventsPlaybackManager.checkIfEventsLogPluginSupported();
        assertTrue(missingEventsPlaybackManager.isSupported(), "isSupported should be true");
    }
}
