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
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.AfterEach;

import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 *
 * Missed events load and persist tests.
 *
 */
class GerritMissedEventsLoadPersistTest {

    private static final int MAXRANDOMNUMBER = 100;
    private static final int SLEEPTIME = 500;
    private static final long FIXTURE_TIME_SLICE = 1430244884000L;
    private static final long REGRESSION_EVENT_SECONDS_A = 2000000000L;
    private static final long REGRESSION_EVENT_SECONDS_B = 1000000000L;

    private MockedStatic<Jenkins> jenkinsMockedStatic;
    private MockedStatic<PluginImpl> pluginMockedStatic;
    private MockedStatic<GerritPluginChecker> pluginCheckerMockedStatic;

    /**
     * Create mocks.
     */
    @BeforeEach
    void setUp() throws Exception {
        Jenkins jenkinsMock = mock(Jenkins.class);
        jenkinsMockedStatic = mockStatic(Jenkins.class);
        jenkinsMockedStatic.when(Jenkins::get).thenReturn(jenkinsMock);
        jenkinsMockedStatic.when(Jenkins::getInstanceOrNull).thenReturn(jenkinsMock);
        jenkinsMockedStatic.when(Jenkins::getAuthentication).thenReturn(ACL.SYSTEM);
        jenkinsMockedStatic.when(Jenkins::getAuthentication2).thenReturn(ACL.SYSTEM2);

        File jenkinsRootDir = Files.createTempDirectory("jenkins-root").toFile();
        jenkinsRootDir.deleteOnExit();
        when(jenkinsMock.getRootDir()).thenReturn(jenkinsRootDir);

        PluginImpl plugin = mock(PluginImpl.class);
        GerritServer server = mock(GerritServer.class);
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        config = spy(config);
        when(plugin.getServer(any(String.class))).thenReturn(server);
        GerritHandler handler = mock(GerritHandler.class);
        when(plugin.getHandler()).thenReturn(handler);
        when(server.getConfig()).thenReturn(config);
        pluginMockedStatic = mockStatic(PluginImpl.class);
        pluginMockedStatic.when(PluginImpl::getInstance).thenReturn(plugin);

        pluginCheckerMockedStatic = mockStatic(GerritPluginChecker.class);
        pluginCheckerMockedStatic.when(
                () -> GerritPluginChecker.isPluginEnabled(
                        any(IGerritHudsonTriggerConfig.class),
                        anyString(),
                        anyBoolean()
                )
        ).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        jenkinsMockedStatic.close();
        pluginMockedStatic.close();
        pluginCheckerMockedStatic.close();
    }

    /**
     * Writes a fixture instance timestamp file for the given server, as if this JVM instance had
     * previously persisted it, so that {@code load()} finds it.
     * @param serverName the Gerrit server name.
     * @param timeSliceMillis the timestamp to write.
     * @throws IOException if it occurs.
     */
    private void writeInstanceTimestampFixture(String serverName, long timeSliceMillis) throws IOException {
        XmlFile xml = new InstanceTimestampStore(serverName).getInstanceConfigXml();
        String text = "<?xml version='1.0' encoding='UTF-8'?>\n"
                + "<com.sonyericsson.hudson.plugins.gerrit.trigger.playback.EventTimeSlice "
                + "plugin='gerrit-trigger@2.14.0-SNAPSHOT'>"
                + "<timeSlice>" + timeSliceMillis + "</timeSlice>"
                + "<events>"
                + "</events>"
                + "</com.sonyericsson.hudson.plugins.gerrit.trigger.playback.EventTimeSlice>";
        try (PrintWriter out = new PrintWriter(xml.getFile())) {
            out.println(text);
        }
    }

    /**
     * Test if Gerrit returns a null eventCreated attribute.
     * @throws IOException if occurs.
     */
    @Test
    void testNullEventCreatedOn() throws IOException {
        InputStream stream = getClass().getResourceAsStream("DeserializeEventCreatedOnTest.json");
        String json = IOUtils.toString(stream);
        JSONObject jsonObject = JSONObject.fromObject(json);
        GerritEvent evt = GerritJsonEventFactory.getEvent(jsonObject);
        GerritTriggeredEvent gEvt = (GerritTriggeredEvent)evt;
        assertNull(gEvt.getEventCreatedOn());

        GerritMissedEventsPlaybackManager missingEventsPlaybackManager
        = new GerritMissedEventsPlaybackManager("defaultServer");

        assertFalse(missingEventsPlaybackManager.saveTimestamp(gEvt));

    }

    /**
     * Given a non-existing timestamp file
     * When we attempt to load it
     * Then we retrieve a null map.
     * @throws IOException if it occurs.
     */
    @Test
    void testLoadTimeStampFromNonExistentFile() throws IOException {

        GerritMissedEventsPlaybackManager missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        assertDoesNotThrow(missingEventsPlaybackManager::load);

        assertNull(missingEventsPlaybackManager.serverTimestamp);

    }

    /**
     * Given an existing timestamp file
     * And it contains at least one entry with a valid timestamp
     * When we attempt to load it
     * Then we retrieve a non-null map.
     * @throws IOException if it occurs.
     */
    @Test
    void testLoadTimeStampFromFile() throws IOException {
        writeInstanceTimestampFixture("defaultServer", FIXTURE_TIME_SLICE);

        GerritMissedEventsPlaybackManager missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager("defaultServer");
        assertDoesNotThrow(missingEventsPlaybackManager::load);

        assertNotNull(missingEventsPlaybackManager.serverTimestamp);
    }

    /**
     * Given an existing timestamp file
     * And it contains at least one entry with a valid timestamp
     * When a new event is received for the server connection
     * Then the timestamp is persisted.
     */
    @Test
    void testPersistTimeStampToFile() {

        Random randomGenerator = new Random();
        int randomInt = randomGenerator.nextInt(MAXRANDOMNUMBER);
        String serverName = Integer.valueOf(randomInt).toString() + "-server";
        GerritMissedEventsPlaybackManager missingEventsPlaybackManager
                = new GerritMissedEventsPlaybackManager(serverName);
        assertDoesNotThrow(missingEventsPlaybackManager::load);

        // Provider name must match serverName, or gerritEvent() filters the event out as
        // belonging to a different server and never calls saveTimestamp().
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated(serverName, "someProject",
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
        try {
            writeInstanceTimestampFixture("defaultServer", FIXTURE_TIME_SLICE);
        } catch (IOException e) {
            fail(e.getMessage());
        }

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
     * Given an existing timestamp file
     * When a connection is restarted
     * Then the diff between last timestamp and current time
     * should be greater than 0.
     */
    @Test
    void testGetTimeStampDiff() {
        GerritMissedEventsPlaybackManager missingEventsPlaybackManager =
                setupManager();

        assertNotNull(missingEventsPlaybackManager.serverTimestamp);
        assertTrue(new Date().getTime() - missingEventsPlaybackManager.getDateFromTimestamp().getTime() > 0,
                "Diff should be greater than 0");

        missingEventsPlaybackManager.shutdown();
    }

    /**
     * Regression test for the fixed cross-server bug: {@code previousTimeSlice} used to be a
     * {@code static} field shared by every {@link GerritMissedEventsPlaybackManager} instance,
     * even though one instance is created per configured Gerrit server. A server with a larger
     * event time slice could suppress persistence for another server with a smaller one, since
     * the shared field would already be ahead of the second server's own time slice.
     *
     * <p>The persistence tick normally runs on a background scheduled thread, but Mockito's
     * static mocks (Jenkins, in particular) are only visible on the thread that registered them.
     * So the tick is invoked synchronously here, via the same {@code persistenceCheck} Runnable
     * the real scheduler would use, rather than waiting on the actual background thread.
     *
     * <p>Given two managers for two different servers
     * When the first receives an event with a much later time slice than the second
     * And the first's persistence tick has already run
     * Then the second still persists its own (smaller) time slice independently.
     * @throws Exception if reflection or IO fails.
     */
    @Test
    public void testCrossServerPersistenceIsIndependent() throws Exception {
        String serverA = "regression-server-a";
        String serverB = "regression-server-b";

        GerritMissedEventsPlaybackManager managerA = new GerritMissedEventsPlaybackManager(serverA);
        GerritMissedEventsPlaybackManager managerB = new GerritMissedEventsPlaybackManager(serverB);

        // Epoch seconds: A is far later than B, so a shared "previousTimeSlice" set by A's tick
        // would (before the fix) permanently block B's tick from ever persisting.
        PatchsetCreated eventForA = Setup.createPatchsetCreated(serverA, "project", "ref",
                Long.toString(REGRESSION_EVENT_SECONDS_A));
        PatchsetCreated eventForB = Setup.createPatchsetCreated(serverB, "project", "ref",
                Long.toString(REGRESSION_EVENT_SECONDS_B));

        managerA.saveTimestamp(eventForA);
        managerB.saveTimestamp(eventForB);

        runPersistenceCheck(managerA);
        runPersistenceCheck(managerB);

        EventTimeSlice persistedA = new InstanceTimestampStore(serverA).readTimestamp();
        EventTimeSlice persistedB = new InstanceTimestampStore(serverB).readTimestamp();

        assertNotNull(persistedA, "server A should have persisted its own timestamp");
        assertNotNull(persistedB, "server B should have persisted its own timestamp, independently of server A's"
                + " later time slice");
        assertEquals(TimeUnit.SECONDS.toMillis(REGRESSION_EVENT_SECONDS_A), persistedA.getTimeSlice());
        assertEquals(TimeUnit.SECONDS.toMillis(REGRESSION_EVENT_SECONDS_B), persistedB.getTimeSlice());
    }

    /**
     * Runs the manager's persistence-check tick synchronously, on the calling thread, by
     * invoking the same Runnable the real scheduled executor would run.
     * @param manager the manager whose tick to run.
     * @throws ReflectiveOperationException if the private field cannot be accessed.
     */
    private void runPersistenceCheck(GerritMissedEventsPlaybackManager manager) throws ReflectiveOperationException {
        Field field = GerritMissedEventsPlaybackManager.class.getDeclaredField("persistenceCheck");
        field.setAccessible(true);
        ((Runnable)field.get(manager)).run();
    }

}
