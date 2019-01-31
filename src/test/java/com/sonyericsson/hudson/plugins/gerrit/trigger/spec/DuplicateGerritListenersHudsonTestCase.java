/*
 * The MIT License
 *
 * Copyright 2011 Sony Mobile Communications Inc. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.spec;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.sonymobile.tools.gerrit.gerritevents.GerritConnection;
import com.sonymobile.tools.gerrit.gerritevents.GerritEventListener;
import com.sonymobile.tools.gerrit.gerritevents.GerritHandler;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;

import hudson.model.Item;
import hudson.model.FreeStyleProject;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.powermock.reflect.Whitebox;

import java.util.Collection;
import java.util.List;

import static com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil.createGerritTriggeredJob;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;


//CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: Test data.

/**
 * This tests different scenarios of adding listeners to the
 * {@link com.sonymobile.tools.gerrit.gerritevents.GerritHandler}
 * to make sure that no duplicates are created.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class DuplicateGerritListenersHudsonTestCase {
    /**
     * An instance of Jenkins Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule j = new JenkinsRule();

    private SshdServerMock.KeyPairFiles keyFile;

    /**
     * Runs before test method.
     *
     * @throws Exception throw if so.
     */
    @Before
    public void setUp() throws Exception {
        keyFile = SshdServerMock.generateKeyPair();
    }

    /**
     * Tests creating a new project.
     *
     * @throws Exception if so.
     */
    @LocalData
    @Test
    public void testNewProjectCreation() throws Exception {
        createGerritTriggeredJob(j, "testJob1");
        assertNbrOfGerritEventListeners(
                PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME));
    }

    /**
     * Tests resaving a project.
     *
     * @throws Exception if so.
     */
    @LocalData
    @Test
    public void testNewProjectCreationWithReSave() throws Exception {
        FreeStyleProject p = createGerritTriggeredJob(j, "testJob2");
        j.configRoundtrip((Item)p);
        assertNbrOfGerritEventListeners(
                PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME));
    }

    /**
     * Tests correct addListener behaviour when a project is renamed.
     *
     * @throws Exception id so.
     */
    @LocalData
    @Test
    public void testNewProjectCreationWithReName() throws Exception {
        FreeStyleProject p = createGerritTriggeredJob(j, "testJob3");

        HtmlForm form = j.createWebClient().getPage(p, "confirm-rename").getFormByName("config");
        form.getInputByName("newName").setValueAttribute("testJob33");
        HtmlPage confirmPage = j.submit(form);
        assertEquals("testJob33", p.getName());
        assertNbrOfGerritEventListeners(
                PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME));
    }

    /**
     * Tests that the listeners are added correctly to the handler when a connection is established for the first time.
     *
     * @throws Exception if so.
     */
    @Test
    public void testNewProjectCreationFirstNoConnection() throws Exception {
        @SuppressWarnings("unused")
        List<GerritServer> servers = PluginImpl.getInstance().getServers();

        //create a server for testing
        GerritServer server = new GerritServer(PluginImpl.DEFAULT_SERVER_NAME);
        servers.add(server);
        server.start();

        createGerritTriggeredJob(j, "testJob4");
        GerritConnection connection = Whitebox.getInternalState(server, GerritConnection.class);
        assertNull(connection);
        assertNbrOfGerritEventListeners(server);
        Config config = (Config)server.getConfig();
        config.setGerritAuthKeyFile(keyFile.getPublicKey());
        config.setGerritHostName("localhost");
        config.setGerritFrontEndURL("http://localhost");
        config.setGerritSshPort(29418);
        config.setGerritProxy("");
        server.startConnection();

        assertNbrOfGerritEventListeners(server);
        connection = Whitebox.getInternalState(server, GerritConnection.class);
        assertNotNull(connection);
    }

    /**
     * Asserts that the number of GerritEventListeners for server is as expected.
     * @param server the Gerrit server to check.
     */
    private void assertNbrOfGerritEventListeners(GerritServer server) {
        GerritHandler handler = Whitebox.getInternalState(server, GerritHandler.class);
        assertNotNull(handler);
        Collection<GerritEventListener> gerritEventListeners =
                Whitebox.getInternalState(handler, "gerritEventListeners");
        int nbrOfListeners = 0;
        nbrOfListeners++; // EventListener adds 1 listener
        nbrOfListeners++; // DependencyQueueTaskDispatcher adds 1 listener
        nbrOfListeners++; // ReplicationQueueTaskDispatcher adds 1 listener
        if (server.isConnected() && server.getConfig().isEnableProjectAutoCompletion()
                && server.isProjectCreatedEventsSupported()) {
            nbrOfListeners++; // GerritProjectListUpdater adds 1 listener
        }
        nbrOfListeners++; // GerritMissedEventsPlaybackManager adds 1 listeners
        assertEquals(nbrOfListeners, gerritEventListeners.size());
    }
}
