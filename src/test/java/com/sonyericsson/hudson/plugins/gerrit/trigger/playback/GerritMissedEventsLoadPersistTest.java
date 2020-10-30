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

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.GerritPluginChecker;
import com.sonymobile.tools.gerrit.gerritevents.GerritHandler;
import com.sonymobile.tools.gerrit.gerritevents.GerritJsonEventFactory;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;

import hudson.XmlFile;
import jenkins.model.Jenkins;
import junit.framework.TestCase;
import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Random;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.spy;

/**
 *
 * Missed events load and persist tests.
 *
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ Jenkins.class, PluginImpl.class, GerritMissedEventsPlaybackManager.class, GerritPluginChecker.class })
@PowerMockIgnore("javax.net.ssl.*")
public class GerritMissedEventsLoadPersistTest {

    private static final int MAXRANDOMNUMBER = 100;
    private static final int SLEEPTIME = 500;

    /**
     * Default constructor.
     */
    public GerritMissedEventsLoadPersistTest() {
    }

    /**
     * Create mocks.
     * @throws IOException if it occurs.
     */
    @Before
    public void setUp() throws IOException {
        Jenkins jenkinsMock = mock(Jenkins.class);
        PowerMockito.mockStatic(Jenkins.class);
        when(Jenkins.getInstance()).thenReturn(jenkinsMock);

        PluginImpl plugin = PowerMockito.mock(PluginImpl.class);
        GerritServer server = mock(GerritServer.class);
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        config = spy(config);
        when(plugin.getServer(any(String.class))).thenReturn(server);
        GerritHandler handler = mock(GerritHandler.class);
        when(plugin.getHandler()).thenReturn(handler);
        when(server.getConfig()).thenReturn(config);
        PowerMockito.mockStatic(PluginImpl.class);
        PowerMockito.when(PluginImpl.getInstance()).thenReturn(plugin);

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
        XmlFile xmlFile = new XmlFile(tmpFile);
        PowerMockito.when(GerritMissedEventsPlaybackManager.getConfigXml(anyString())).thenReturn(xmlFile);

        PowerMockito.mockStatic(GerritPluginChecker.class);
        PowerMockito.when(GerritPluginChecker.isPluginEnabled((IGerritHudsonTriggerConfig)anyObject()
                , anyString(), anyBoolean())).thenReturn(true);
    }

    /**
     * Test if Gerrit returns a null eventCreated attribute.
     * @throws IOException if occurs.
     */
    @Test
    public void testNullEventCreatedOn() throws IOException {
        InputStream stream = getClass().getResourceAsStream("DeserializeEventCreatedOnTest.json");
        String json = IOUtils.toString(stream);
        JSONObject jsonObject = JSONObject.fromObject(json);
        GerritEvent evt = GerritJsonEventFactory.getEvent(jsonObject);
        GerritTriggeredEvent gEvt = (GerritTriggeredEvent)evt;
        assertNull(gEvt.getEventCreatedOn());

        GerritMissedEventsPlaybackManager missingEventsPlaybackManager
        = new GerritMissedEventsPlaybackManager("defaultServer");

        assertTrue(!missingEventsPlaybackManager.saveTimestamp(gEvt));

    }
    /**
     * Given a non-existing timestamp file
     * When we attempt to load it
     * Then we retrieve a null map.
     * @throws IOException if it occurs.
     */
    @Test
    public void testLoadTimeStampFromNonExistentFile() throws IOException {

        GerritMissedEventsPlaybackManager.getConfigXml("defaultServer").delete();

        GerritMissedEventsPlaybackManager missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        try {
            missingEventsPlaybackManager.load();
        } catch (IOException e) {
            fail(e.getMessage());
        }

        assertNull(missingEventsPlaybackManager.serverTimestamp);

    }

    /**
     * Given an existing timestamp file
     * And it contains at least one entry with a valid timestamp
     * When we attempt to load it
     * Then we retrieve a non-null map.
     */
    @Test
    public void testLoadTimeStampFromFile() {

        GerritMissedEventsPlaybackManager missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        try {
            missingEventsPlaybackManager.load();
        } catch (IOException e) {
            fail(e.getMessage());
        }

        assertNotNull(missingEventsPlaybackManager.serverTimestamp);
    }

    /**
     * Given an existing timestamp file
     * And it contains at least one entry with a valid timestamp
     * When a new event is received for the server connection
     * Then the timestamp is persisted.
     */
    @Test
    public void testPersistTimeStampToFile() {

        Random randomGenerator = new Random();
        int randomInt = randomGenerator.nextInt(MAXRANDOMNUMBER);
        GerritMissedEventsPlaybackManager missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager(Integer.valueOf(randomInt).toString() + "-server");
        try {
            missingEventsPlaybackManager.load();
        } catch (IOException e) {
            fail(e.getMessage());
        }

        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated("someGerritServer", "someProject",
                "refs/heads/master");
        patchsetCreated.setReceivedOn(System.currentTimeMillis());

        missingEventsPlaybackManager.gerritEvent(patchsetCreated);
        assertNotNull(missingEventsPlaybackManager.serverTimestamp);
    }

    /**
     * Return a missingEventsPlaybackManager.
     * @return missingEventsPlaybackManager.
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
     * Given an existing timestamp file
     * When a connection is restarted
     * Then the diff between last timestamp and current time
     * should be greater than 0.
     */
    @Test
    public void testGetTimeStampDiff() {
        GerritMissedEventsPlaybackManager missingEventsPlaybackManager =
                setupManager();

        assertNotNull(missingEventsPlaybackManager.serverTimestamp);
        TestCase.assertTrue("Diff should be greater than 0",
                new Date().getTime() - missingEventsPlaybackManager.getDateFromTimestamp().getTime() > 0);

        missingEventsPlaybackManager.shutdown();
    }

}
