/*
 * The MIT License
 *
 * Copyright 2015 Ericsson.
 * Copyright (c) 2018, CloudBees, Inc.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger;

import static com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock.GERRIT_STREAM_EVENTS;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.UrlUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Result;

import jenkins.model.Jenkins;
import org.apache.sshd.SshServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.TestUtils;
import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

/**
 * Unit test to ensure build can be triggered even if
 * security is enabled and only authenticated users can login.
 *
 * @author Scott Hebert &lt;scott.hebert@ericsson.com&gt;
 */

public class LockedDownGerritEventTest {

    /**
     * An instance of Jenkins Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule j = new JenkinsRule();

    //private final String gerritServerName = "testServer";
    private final String projectName = "testProject";
    private final int port = 29418;
    private static final int NUMBEROFSENDERTHREADS = 1;

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
     * Test that a build can still be triggered if only authenticated
     * users can login.
     *
     * Given a secured Jenkins instance
     * And a permission scheme that does not provide any permissions to Anonymous
     * And a configured Gerrit Server
     * And a Gerrit Triggered Job
     * When an event triggers a build
     * Then the build completes successfully.
     *
     * @throws Exception throw if so.
     */
    @Test
    public void testTriggerWithLockedDownInstance() throws Exception {
        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJob(j, projectName);

        Setup.lockDown(j);

        GerritTrigger trigger = project.getTrigger(GerritTrigger.class);
        trigger.setSilentStartMode(false);

        GerritServer gerritServer = new GerritServer(PluginImpl.DEFAULT_SERVER_NAME);
        SshdServerMock.configureFor(sshd, gerritServer);
        PluginImpl.getInstance().addServer(gerritServer);
        gerritServer.getConfig().setNumberOfSendingWorkerThreads(NUMBEROFSENDERTHREADS);
        ((Config)gerritServer.getConfig()).setGerritAuthKeyFile(sshKey.getPrivateKey());
        gerritServer.start();

        gerritServer.triggerEvent(Setup.createPatchsetCreated());

        TestUtils.waitForBuilds(project, 1);
        //wait until command is registered
        // CS IGNORE MagicNumber FOR NEXT 2 LINES. REASON: ConstantsNotNeeded
        Thread.sleep(TimeUnit.SECONDS.toMillis(10));
        assertEquals(2, server.getNrCommandsHistory("gerrit review.*"));

        FreeStyleBuild buildOne = project.getLastCompletedBuild();
        assertSame(Result.SUCCESS, buildOne.getResult());
        assertEquals(1, project.getLastCompletedBuild().getNumber());
        assertSame(PluginImpl.DEFAULT_SERVER_NAME,
            buildOne.getCause(GerritCause.class).getEvent().getProvider().getName());

    }

    /**
     * Just verify that {@link PluginImpl#getServer(String)} et. al. hasn't been locked down too tightly.
     *
     * @throws Exception if so
     */
    @Test
    @Issue({"SECURITY-402", "SECURITY-403" })
    public void testUserCanConfigureAJob() throws Exception {

        GerritServer gerritServer = new GerritServer(PluginImpl.DEFAULT_SERVER_NAME);
        SshdServerMock.configureFor(sshd, gerritServer);
        PluginImpl.getInstance().addServer(gerritServer);
        gerritServer.getConfig().setNumberOfSendingWorkerThreads(NUMBEROFSENDERTHREADS);
        ((Config)gerritServer.getConfig()).setGerritAuthKeyFile(sshKey.getPrivateKey());
        gerritServer.start();

        GerritServer otherServer = new GerritServer("theOtherServer");
        SshdServerMock.configureFor(sshd, otherServer);
        PluginImpl.getInstance().addServer(otherServer);
        otherServer.getConfig().setNumberOfSendingWorkerThreads(NUMBEROFSENDERTHREADS);
        ((Config)otherServer.getConfig()).setGerritAuthKeyFile(sshKey.getPrivateKey());
        otherServer.start();

        FreeStyleProject project = new TestUtils.JobBuilder(j)
                .name(projectName)
                .silentStartMode(false)
                .serverName("theOtherServer").build();


        Setup.lockDown(j);
        j.getInstance().setAuthorizationStrategy(
                new MockAuthorizationStrategy().grant(Item.READ, Item.DISCOVER).everywhere().toAuthenticated()
                        .grant(Jenkins.READ, Item.DISCOVER).everywhere().toEveryone()
                        .grant(Item.CONFIGURE).everywhere().to("bob"));

        final JenkinsRule.WebClient webClient = j.createWebClient().login("bob", "bob");
        HtmlPage page = webClient.getPage(project, "configure");
        j.submit(page.getFormByName("config"));

        final FreeStyleProject freshJob = j.jenkins.getItem(projectName, j.jenkins, FreeStyleProject.class);
        final String serverName = GerritTrigger.getTrigger(freshJob).getServerName();
        assertEquals("theOtherServer", serverName);
    }

    // CS IGNORE MagicNumber FOR NEXT 32 LINES. REASON: test data.

    /**
     * Tests that only an admin can read server configuration and manipulate server state.
     * @throws Exception if so
     */
    @Test
    @Issue({"SECURITY-402", "SECURITY-403" })
    public void testOnlyAdminCanPerformServerConfigurationActions() throws Exception {
        GerritServer gerritServer = new GerritServer(PluginImpl.DEFAULT_SERVER_NAME);
        SshdServerMock.configureFor(sshd, gerritServer);
        PluginImpl.getInstance().addServer(gerritServer);
        gerritServer.getConfig().setNumberOfSendingWorkerThreads(NUMBEROFSENDERTHREADS);
        ((Config)gerritServer.getConfig()).setGerritAuthKeyFile(sshKey.getPrivateKey());
        gerritServer.start();

        Setup.lockDown(j);
        j.getInstance().setAuthorizationStrategy(
                new MockAuthorizationStrategy().grant(Item.READ, Item.DISCOVER).everywhere().toAuthenticated()
                        .grant(Jenkins.READ, Item.DISCOVER).everywhere().toEveryone()
                        .grant(Item.CONFIGURE).everywhere().to("bob")
                        .grant(Jenkins.ADMINISTER).everywhere().to("alice"));
        j.jenkins.setCrumbIssuer(null); //Not really testing csrf right now
        JenkinsRule.WebClient webClient = j.createWebClient().login("alice", "alice");
        HtmlPage page = webClient.goTo("plugin/gerrit-trigger/servers/0/");
        HtmlForm config = page.getFormByName("config");
        assertNotNull(config);
        post(webClient, "plugin/gerrit-trigger/servers/0/sleep", "application/json", null);

        webClient = j.createWebClient().login("bob", "bob");
        webClient.assertFails("plugin/gerrit-trigger/servers/0/", 403);
        post(webClient, "plugin/gerrit-trigger/servers/0/wakeup", null, 403);
    }

    /**
     * Performs an HTTP POST request to the relative url.
     *
     * @param webClient the client
     * @param relative the url relative to the context path
     * @param expectedContentType if expecting specific content type or null if not
     * @param expectedStatus if expecting a failing http status code or null if not
     * @throws IOException if so
     */
    private static void post(JenkinsRule.WebClient webClient, String relative,
                             String expectedContentType, Integer expectedStatus) throws IOException {
        WebRequest request = new WebRequest(
                UrlUtils.toUrlUnsafe(webClient.getContextPath() + relative),
                HttpMethod.POST);
        try {
            Page p = webClient.getPage(request);
            if (expectedContentType != null) {
                assertThat(p.getWebResponse().getContentType(), is(expectedContentType));
            }
        } catch (FailingHttpStatusCodeException e) {
            if (expectedStatus != null) {
                assertEquals(expectedStatus.intValue(), e.getStatusCode());
            } else {
                throw e;
            }
        }
    }
}
