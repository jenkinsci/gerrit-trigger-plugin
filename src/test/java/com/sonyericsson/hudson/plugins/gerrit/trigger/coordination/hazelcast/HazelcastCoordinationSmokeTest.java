/*
 *  The MIT License
 *
 *  Copyright 2026 CloudBees, Inc.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.hazelcast;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.CoordinationModeFactory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.TestUtils;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;

import java.util.Collections;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import org.apache.sshd.server.SshServer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock.GERRIT_STREAM_EVENTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Smoke test that exercises Hazelcast coordination mode during the <strong>default</strong>
 * {@code mvn test} run, without requiring the {@code -Ptest-hazelcast} profile.
 * <p>
 * {@link BuildCancellationHazelcastIntegrationTest} and its {@link HazelcastTestRule} only run
 * under that profile, because the coordination mode system property has to be set before the JVM
 * starts (see {@link HazelcastTestRule} for why setting it from an instance {@code @Rule} is too
 * late). This test avoids that requirement by starting its own embedded Hazelcast server and
 * setting the coordination properties from a {@code @ClassRule}, which is guaranteed by JUnit4 to
 * run before any instance {@code @Rule} - including {@link JenkinsRule} - regardless of field
 * declaration order.
 * <p>
 * The point of this test is narrow: give normal CI a fast, self-contained signal that Hazelcast
 * coordination mode still wires up and can trigger and complete a build end-to-end. It is not a
 * replacement for the broader cancellation-race coverage in
 * {@link BuildCancellationHazelcastIntegrationTest}, which remains opt-in due to its cost and
 * flakiness surface.
 */
public class HazelcastCoordinationSmokeTest {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastCoordinationSmokeTest.class);

    private static final String COORDINATION_MODE_PROPERTY = "gerrit.trigger.coordination.mode";
    private static final String HAZELCAST_MODE = "hazelcast";
    // Generous: the in-JVM Hazelcast client's CPU contention slows the SSH handshake on busy agents.
    private static final int SERVER_WAIT = 20000;
    private static final int BUILD_TIMEOUT = 30000;

    /**
     * Starts an embedded Hazelcast server and points the coordination system properties at it
     * before {@link JenkinsRule} boots Jenkins - see the class Javadoc for why a {@code @ClassRule}
     * is required here rather than {@code @Before}/{@code @Rule}.
     */
    //CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JUnit ClassRule.
    @ClassRule
    public static final ExternalResource HAZELCAST_SERVER = new ExternalResource() {

        private String originalMode;
        private String originalAddresses;

        @Override
        protected void before() {
            originalMode = System.getProperty(COORDINATION_MODE_PROPERTY);
            originalAddresses = System.getProperty(HazelcastConfig.CLIENT_ADDRESSES_PROPERTY);

            EmbeddedHazelcastTestServer.start();
            System.setProperty(COORDINATION_MODE_PROPERTY, HAZELCAST_MODE);
            System.setProperty(HazelcastConfig.CLIENT_ADDRESSES_PROPERTY,
                    "localhost:" + EmbeddedHazelcastTestServer.getPort());
            logger.info("Smoke test: embedded Hazelcast server ready on port {}",
                    EmbeddedHazelcastTestServer.getPort());
        }

        @Override
        protected void after() {
            // Jenkins (via JenkinsRule teardown, which nests inside this ClassRule and has
            // already completed by the time this runs) shuts down every CoordinationModeProvider
            // - including HazelcastCoordinationProvider, which calls HazelcastManager.shutdown()
            // itself. Calling it again here would race the plugin's own shutdown and intermittently
            // throw HazelcastClientNotActiveException, so only the server this class started is
            // stopped here.
            EmbeddedHazelcastTestServer.stop();
            restoreProperty(COORDINATION_MODE_PROPERTY, originalMode);
            restoreProperty(HazelcastConfig.CLIENT_ADDRESSES_PROPERTY, originalAddresses);
        }

        private void restoreProperty(String key, String value) {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        }
    };

    /**
     * An instance of Jenkins Rule.
     */
    //CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule jenkins = new JenkinsRule();

    /**
     * Outputs build logs to std out.
     */
    //CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final BuildWatcher watcher = new BuildWatcher();

    private SshServer sshd;
    private SshdServerMock serverMock;
    private GerritServer gerritServer;

    /**
     * Sets up the SSH server mock before each test.
     *
     * @throws Exception if setup fails
     */
    @Before
    public void setUp() throws Exception {
        SshdServerMock.generateKeyPair();
        serverMock = new SshdServerMock();
        sshd = SshdServerMock.startServer(serverMock);
        serverMock.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        serverMock.returnCommandFor(GERRIT_STREAM_EVENTS, SshdServerMock.CommandMock.class);
        serverMock.returnCommandFor("gerrit review.*", SshdServerMock.EofCommandMock.class);
        serverMock.returnCommandFor("gerrit version", SshdServerMock.SendVersionCommand.class);
        gerritServer = PluginImpl.getFirstServer_();
        if (gerritServer != null) {
            SshdServerMock.configureFor(sshd, gerritServer, true);
        }
    }

    /**
     * Tears down the SSH server and clears Hazelcast state.
     *
     * @throws Exception if teardown fails
     */
    @After
    public void tearDown() throws Exception {
        HazelcastTestHelper.clearAllMaps();
        if (sshd != null) {
            sshd.stop(true);
            sshd = null;
        }
    }

    /**
     * Verifies that Hazelcast coordination mode is actually active, and that a Gerrit event
     * triggers and completes a build end-to-end through Hazelcast-backed build memory, event
     * claiming, and notification claiming - not just the mode selection.
     *
     * @throws Exception if unexpected errors appear.
     */
    @Test
    @LocalData("common")
    public void testHazelcastCoordinationModeTriggersAndCompletesBuild() throws Exception {
        CoordinationModeFactory factory = CoordinationModeFactory.get();
        String storageClass = factory.getStorage().getClass().getSimpleName();
        assertEquals("Expected Hazelcast storage - falling back to local mode would defeat "
                + "the point of this smoke test", "HazelcastBuildMemoryStorage", storageClass);
        assertNotNull("Expected a selected coordination mode", factory.getSelectedMode());
        assertEquals("Expected Hazelcast mode", "Hazelcast (Distributed)",
                factory.getSelectedMode().getModeName());

        FreeStyleProject project = jenkins.createFreeStyleProject();
        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        trigger.setGerritProjects(Collections.singletonList(
                new GerritProject(CompareType.ANT, "**",
                        Collections.singletonList(new Branch(CompareType.ANT, "**")),
                        null, null, null, false)));
        project.addTrigger(trigger);
        trigger.start(project, false);

        serverMock.waitForCommand(GERRIT_STREAM_EVENTS, SERVER_WAIT);

        PatchsetCreated patchset = Setup.createPatchsetCreated();
        gerritServer.triggerEvent(patchset);

        TestUtils.waitForBuilds(project, 1, BUILD_TIMEOUT);
        FreeStyleBuild build = project.getLastBuild();
        assertNotNull("Build should have been triggered via Hazelcast-backed coordination", build);
        jenkins.assertBuildStatusSuccess(build);
    }
}
