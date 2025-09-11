/*
 *  The MIT License
 *
 *  Copyright 2022 Arm Ltd. All Rights Reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger;

import static com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock.GERRIT_STREAM_EVENTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.yaml.YamlSource;
import java.util.Arrays;
import org.apache.sshd.server.SshServer;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.tools.ant.filters.StringInputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests Jenkins Configuration as Code changes to Gerrit Server connections.
 */
public class JcascServerConnectTest {
    private PluginImpl pluginImpl;
    private SshdServerMock.KeyPairFiles sshKey;
    private SshdServerMock sshdMock1;
    private SshdServerMock sshdMock2;
    private SshServer sshd1;
    private SshServer sshd2;

    private static final long CONNECT_TIMEOUT = 15000;
    private static final long CONNECT_SLEEP = 500;

    /**
     * Ensure Jenkins instance is created.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule j = new JenkinsRule();

    /**
     * Setup environment before each test execution.
     *
     * @throws Exception throw if so.
     */
    @Before
    public void setup() throws Exception {
        pluginImpl = PluginImpl.getInstance();
        sshKey = SshdServerMock.generateKeyPair();

        sshdMock1 = new SshdServerMock();
        sshd1 = initSshServer(sshdMock1);

        sshdMock2 = new SshdServerMock();
        sshd2 = initSshServer(sshdMock2);
    }

    /**
     * Start a mock SSH server instance.
     *
     * @param sshdMock Mock server to start.
     * @return SSH server instance.
     * @throws Exception throw if so.
     */
    private SshServer initSshServer(SshdServerMock sshdMock) throws Exception {
        SshServer sshd = SshdServerMock.startServer(sshdMock);
        sshdMock.returnCommandFor("gerrit version", SshdServerMock.SendVersionCommand.class);
        sshdMock.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        sshdMock.returnCommandFor(GERRIT_STREAM_EVENTS, SshdServerMock.CommandMock.class);
        return sshd;
    }

    /**
     * Tidy up after test run.
     *
     * @throws Exception throw if so.
     */
    @After
    public void cleanup() throws Exception {
        sshd1.stop(true);
        sshd1 = null;
        sshdMock1 = null;
        sshd2.stop(true);
        sshd2 = null;
        sshdMock2 = null;
    }

    /**
     * Test adding a server to the configuration and then remove it.
     *
     * @throws Exception throw if so.
     */
    @Test
    public void testAddThenRemoveServer() throws Exception {
        assertEquals(0, pluginImpl.getServers().size());

        String config1 = generateCascConfig(sshd1);
        ConfigurationAsCode.get().configureWith(YamlSource.of(new StringInputStream(config1)));

        assertEquals(1, pluginImpl.getServers().size());
        GerritServer server1A = pluginImpl.getServers().get(0);
        assertNotNull(server1A);
        assertNotNull(server1A.getProjectListUpdater()); // Proxy for "started == true"
        waitForConnectedState(server1A);

        String config2 = generateCascConfig();
        ConfigurationAsCode.get().configureWith(YamlSource.of(new StringInputStream(config2)));

        assertNull(server1A.getProjectListUpdater()); // Proxy for "started == false"
        assertFalse(server1A.isConnected());

        assertEquals(0, pluginImpl.getServers().size());
    }

    /**
     * Test adding a server and then update configuration without any changes.
     *
     * @throws Exception throw if so.
     */
    @Test
    public void testUpdateWithSameServer() throws Exception {
        assertEquals(0, pluginImpl.getServers().size());

        String config1 = generateCascConfig(sshd1);
        ConfigurationAsCode.get().configureWith(YamlSource.of(new StringInputStream(config1)));

        assertEquals(1, pluginImpl.getServers().size());
        GerritServer server1A = pluginImpl.getServers().get(0);
        assertNotNull(server1A);
        assertNotNull(server1A.getProjectListUpdater()); // Proxy for "started == true"
        waitForConnectedState(server1A);

        ConfigurationAsCode.get().configureWith(YamlSource.of(new StringInputStream(config1)));

        assertNull(server1A.getProjectListUpdater()); // Proxy for "started == false"
        assertFalse(server1A.isConnected());

        assertEquals(1, pluginImpl.getServers().size());
        GerritServer server1B = pluginImpl.getServers().get(0);
        assertNotNull(server1B);
        assertNotNull(server1B.getProjectListUpdater()); // Proxy for "started == true"
        waitForConnectedState(server1B);

        assertEquals(server1A.getSshPort(), server1B.getSshPort());
    }

    /**
     * Test adding a server and then update configuration with a different server.
     *
     * @throws Exception throw if so.
     */
    @Test
    public void testUpdateWithDifferentServer() throws Exception {
        assertEquals(0, pluginImpl.getServers().size());

        String config1 = generateCascConfig(sshd1);
        ConfigurationAsCode.get().configureWith(YamlSource.of(new StringInputStream(config1)));

        assertEquals(1, pluginImpl.getServers().size());
        GerritServer server1 = pluginImpl.getServers().get(0);
        assertNotNull(server1);
        assertNotNull(server1.getProjectListUpdater()); // Proxy for "started == true"
        waitForConnectedState(server1);

        String config2 = generateCascConfig(sshd2);
        ConfigurationAsCode.get().configureWith(YamlSource.of(new StringInputStream(config2)));

        assertNull(server1.getProjectListUpdater()); // Proxy for "started == false"
        assertFalse(server1.isConnected());

        assertEquals(1, pluginImpl.getServers().size());
        GerritServer server2 = pluginImpl.getServers().get(0);
        assertNotNull(server2);
        assertNotNull(server2.getProjectListUpdater()); // Proxy for "started == true"
        waitForConnectedState(server2);

        assertNotEquals(server1.getSshPort(), server2.getSshPort());
    }

    /**
     * Test adding a server and then update configuration by adding a second server.
     *
     * @throws Exception throw if so.
     */
    @Test
    public void testUpdateWithSecondServer() throws Exception {
        assertEquals(0, pluginImpl.getServers().size());

        String config1 = generateCascConfig(sshd1);
        ConfigurationAsCode.get().configureWith(YamlSource.of(new StringInputStream(config1)));

        assertEquals(1, pluginImpl.getServers().size());
        GerritServer server1A = pluginImpl.getServers().get(0);
        assertNotNull(server1A);
        assertNotNull(server1A.getProjectListUpdater()); // Proxy for "started == true"
        waitForConnectedState(server1A);

        String config2 = generateCascConfig(sshd1, sshd2);
        ConfigurationAsCode.get().configureWith(YamlSource.of(new StringInputStream(config2)));

        assertNull(server1A.getProjectListUpdater()); // Proxy for "started == false"
        assertFalse(server1A.isConnected());

        assertEquals(2, pluginImpl.getServers().size());

        GerritServer server1B = pluginImpl.getServers().get(0);
        assertNotNull(server1B);
        assertNotNull(server1B.getProjectListUpdater()); // Proxy for "started == true"
        waitForConnectedState(server1B);

        GerritServer server2 = pluginImpl.getServers().get(1);
        assertNotNull(server2);
        assertNotNull(server2.getProjectListUpdater()); // Proxy for "started == true"
        waitForConnectedState(server2);

        assertEquals(server1A.getSshPort(), server1B.getSshPort());
        assertNotEquals(server1B.getSshPort(), server2.getSshPort());
    }

    /**
     * Wait for Gerrit server state to change to "connected".
     *
     * @param server Server instance to wait for.
     */
    private void waitForConnectedState(GerritServer server) {
        long startTime = System.currentTimeMillis();
        while (!server.isConnected()) {
            assertTrue(System.currentTimeMillis() - startTime < CONNECT_TIMEOUT);
            try {
                Thread.sleep(CONNECT_SLEEP);
                // CS IGNORE EmptyBlock FOR NEXT 2 LINES. REASON: not needed.
            } catch (InterruptedException e) {
            }
        }
    }

    /**
     * Generate configuration document for "Configuration as Code" plugin.
     *
     * @param sshdArgs SSH servers (if any) to include in the configuration.
     * @return Configuration JSON document (as a subset of YAML).
     */
    private String generateCascConfig(SshServer... sshdArgs) {
        JSONArray servers = new JSONArray();
        if (sshdArgs != null) {
            Arrays.stream(sshdArgs)
                    .forEachOrdered(sshd -> servers.add(generateConfigForServer(sshd)));
        }

        JSONObject plugin = new JSONObject();
        plugin.put("servers", servers);

        JSONObject unclass = new JSONObject();
        unclass.put("gerrit-trigger", plugin);

        JSONObject root = new JSONObject();
        root.put("unclassified", unclass);

        return root.toString();
    }

    /**
     * Generate the configuration block for a Gerrit server.
     *
     * @param sshd The SSH server instance to use.
     * @return JSON object for the server configuration block.
     */
    private JSONObject generateConfigForServer(SshServer sshd) {
        String host = sshd.getHost();
        if (host == null || host.isBlank()) {
            host = "localhost";
        }

        JSONObject serverCfg = new JSONObject();
        serverCfg.put("gerritHostName", host);
        serverCfg.put("gerritSshPort", sshd.getPort());
        serverCfg.put("gerritUserName", "nobody");
        serverCfg.put("gerritAuthKeyFile", sshKey.getPrivateKey().getAbsolutePath());
        serverCfg.put("useRestApi", false);
        serverCfg.put("enableProjectAutoCompletion", false);

        JSONObject server = new JSONObject();
        server.put("name", String.format("%s:%d", host, sshd.getPort()));
        server.put("config", serverCfg);

        return server;
    }
}
