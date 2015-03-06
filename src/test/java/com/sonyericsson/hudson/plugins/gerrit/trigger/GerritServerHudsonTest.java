/*
 * MIT License
 * Copyright (c) 2013, Ericsson
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS
 * OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger;

import static com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock.GERRIT_STREAM_EVENTS;

import org.apache.sshd.SshServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.TestUtils;
import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;

import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;


/**
 * Provide Html unit tests for server management and triggering from UI.
 *
 * @author Anthony Wei-Wing Chin &lt;anthony.a.chin@ericsson.com&gt;
 * @author Mathieu Wang &lt;mathieu.wang@ericsson.com&gt;
 */
public class GerritServerHudsonTest {
    /**
     * An instance of Jenkins Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule j = new JenkinsRule();

    private final String gerritServerOneName = "testServer1";
    private final String gerritServerTwoName = "testServer2";
    private final String projectOneName = "testProject1";
    private final String projectTwoName = "testProject2";
    private final String gerritManagementURL = "gerrit-trigger";
    private final String newServerURL = gerritManagementURL + "/newServer";
    private final String serverURL = gerritManagementURL;

    private final String removalFormName = "removal";
    private final String newServerFormName = "createItem";
    private final String inputFormName = "name";
    private final String fromInputFormName = "from";
    private final String radioButtonName = "mode";
    private final String radioButtonDefaultConfigValue = "com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer";
    private final String radioButtonCopyValue = "copy";

    private final int badRequestErrorCode = 400;
    private final int portOne = 29418;
    private final int portTwo = 29419;

    private final String removeLastServerWarning = "Cannot remove the last server!";
    private final String wrongMessageWarning = "Wrong message when trying to remove GerritServer! ";

    private boolean buttonFound = true;
    private String textContent = "";
    private SshdServerMock serverOne;
    private SshdServerMock serverTwo;
    private SshServer sshdOne;
    private SshServer sshdTwo;


    /**
     * Runs before test method.
     *
     * @throws Exception throw if so.
     */
    @Before
    public void setUp() throws Exception {
        SshdServerMock.generateKeyPair();
        serverOne = new SshdServerMock();
        serverTwo = new SshdServerMock();
        sshdOne = SshdServerMock.startServer(portOne, serverOne);
        sshdTwo = SshdServerMock.startServer(portTwo, serverTwo);
        serverOne.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        serverOne.returnCommandFor(GERRIT_STREAM_EVENTS, SshdServerMock.CommandMock.class);
        serverOne.returnCommandFor("gerrit review.*", SshdServerMock.EofCommandMock.class);
        serverOne.returnCommandFor("gerrit version", SshdServerMock.EofCommandMock.class);
        serverTwo.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        serverTwo.returnCommandFor(GERRIT_STREAM_EVENTS, SshdServerMock.CommandMock.class);
        serverTwo.returnCommandFor("gerrit review.*", SshdServerMock.EofCommandMock.class);
        serverTwo.returnCommandFor("gerrit version", SshdServerMock.EofCommandMock.class);
    }

    /**
     * Runs after test method.
     *
     * @throws Exception throw if so.
     */
    @After
    public void tearDown() throws Exception {
        sshdOne.stop(true);
        sshdTwo.stop(true);
        sshdOne = null;
        sshdTwo = null;
    }

    /**
     * Test triggering two jobs with events from two different Gerrit servers.
     * @throws Exception Error creating job.
     */
    @Test
    public void testTriggeringFromMultipleGerritServers() throws Exception {
        GerritServer gerritServerOne = new GerritServer(gerritServerOneName);
        GerritServer gerritServerTwo = new GerritServer(gerritServerTwoName);
        PluginImpl.getInstance().addServer(gerritServerOne);
        PluginImpl.getInstance().addServer(gerritServerTwo);
        gerritServerOne.start();
        gerritServerTwo.start();
        FreeStyleProject projectOne = DuplicatesUtil.createGerritTriggeredJob(j, projectOneName, gerritServerOneName);
        FreeStyleProject projectTwo = DuplicatesUtil.createGerritTriggeredJob(j, projectTwoName, gerritServerTwoName);
        gerritServerOne.triggerEvent(Setup.createPatchsetCreated(gerritServerOneName));
        gerritServerTwo.triggerEvent(Setup.createPatchsetCreated(gerritServerTwoName));
        TestUtils.waitForBuilds(projectOne, 1);
        TestUtils.waitForBuilds(projectTwo, 1);

        FreeStyleBuild buildOne = projectOne.getLastCompletedBuild();
        assertThat(buildOne.getResult()).isEqualTo(Result.SUCCESS);
        assertThat(projectOne.getLastCompletedBuild().getNumber()).is(1);
        assertThat(buildOne.getCause(GerritCause.class).getEvent()
            .getProvider().getName()).isEqualTo(gerritServerOneName);

        FreeStyleBuild buildTwo = projectTwo.getLastCompletedBuild();
        assertThat(buildTwo.getResult()).isEqualTo(Result.SUCCESS);
        assertThat(projectTwo.getLastCompletedBuild().getNumber()).is(1);
        assertThat(buildTwo.getCause(GerritCause.class).getEvent()
            .getProvider().getName()).isEqualTo(gerritServerTwoName);
    }

    /**
     * Test removing one server from two Gerrit servers without configured jobs.
     * @throws Exception if error getting page from URL.
     */
    @Test
    public void testRemoveOneServerFromTwoWithoutConfiguredJobs() throws Exception {
        GerritServer server = new GerritServer(gerritServerOneName);
        PluginImpl.getInstance().addServer(server);
        server.start();
        GerritServer server2 = new GerritServer(gerritServerTwoName);
        PluginImpl.getInstance().addServer(server2);

        removeServer(gerritServerOneName);

        assertThat(PluginImpl.getInstance().getServers()).hasSize(1);
        assert_().withFailureMessage(wrongMessageWarning).that(
            textContent).contains("Remove server");
    }

    /**
     * Test removing one server when two Gerrit servers exist and when at least one job is configured with this server.
     * @throws Exception if error getting URL or creating Gerrit triggered job.
     */
    @Test
    public void testRemoveServerWithConfiguredJob() throws Exception {
        GerritServer server = new GerritServer(gerritServerOneName);
        PluginImpl.getInstance().addServer(server);
        server.start();
        DuplicatesUtil.createGerritTriggeredJob(j, projectOneName, gerritServerOneName);

        GerritServer server2 = new GerritServer(gerritServerTwoName);
        PluginImpl.getInstance().addServer(server2);

        removeServer(gerritServerOneName);

        assertThat(PluginImpl.getInstance().getServers()).hasSize(1);
        assert_().withFailureMessage(wrongMessageWarning).that(textContent).contains(
            "Disable Gerrit Trigger in the following jobs and remove server \""
            + gerritServerOneName + "\"?");
    }

    /**
     * Test removing one server with configured job from UI.
     * @throws Exception if error getting URL or creating Gerrit triggered job.
     */
    @Test
    public void testRemoveLastServerWithConfiguredJob() throws Exception {
        GerritServer server = new GerritServer(gerritServerOneName);
        PluginImpl.getInstance().addServer(server);
        server.start();

        DuplicatesUtil.createGerritTriggeredJob(j, projectOneName, gerritServerOneName);

        removeServer(gerritServerOneName);

        assertThat(buttonFound).isFalse();
        assertThat(PluginImpl.getInstance().getServers()).hasSize(1);
        assertThat(textContent).isEqualTo(removeLastServerWarning);
    }

    /**
     * Test removing one server without configured job from UI.
     * @throws IOException if error getting URL or getting page from URL.
     */
    @Test
    public void testRemoveOneServerWithoutConfiguredJob() throws IOException {
        GerritServer server = new GerritServer(gerritServerOneName);
        PluginImpl.getInstance().addServer(server);

        removeServer(gerritServerOneName);

        assertThat(buttonFound).isFalse();
        assertThat(PluginImpl.getInstance().getServers()).hasSize(1);
        assertThat(textContent).isEqualTo(removeLastServerWarning);
    }

    /**
     * Remove Server from UI.
     * @param serverName the name of the Gerrit server you want to access.
     * @throws IOException if error removing server.
     */
    private void removeServer(String serverName) throws IOException {
        URL url = new URL(j.getURL(), Functions.joinPath(serverURL, "server", serverName, "remove"));
        HtmlPage removalPage = j.createWebClient().getPage(url);

        int serverSize = PluginImpl.getInstance().getServers().size();

        HtmlForm form = removalPage.getFormByName(removalFormName);
        textContent = form.getTextContent();

        if (serverSize == 1) {
            try { form.submit();
            } catch (Exception e) {
                buttonFound = false;
            }
        } else {
            form.submit(null);
        }
    }

    /**
     * Test adding server from UI.
     * @throws IOException if error adding server.
     */


    @Test
    public void testAddServer() throws IOException {
        addNewServerWithDefaultConfigs(gerritServerOneName);
        assertThat(PluginImpl.getInstance().getServers()).hasSize(1);

        addNewServerByCopyingConfig(gerritServerTwoName, gerritServerOneName);
        assertThat(PluginImpl.getInstance().getServers()).hasSize(2);

        //try add server with same name:
        try {
            addNewServerWithDefaultConfigs(gerritServerOneName);
        } catch (FailingHttpStatusCodeException e) {
            assertThat(e.getStatusCode()).isEqualTo(badRequestErrorCode);
        }
        //make sure the server has not been added.
        assertThat(PluginImpl.getInstance().getServers()).hasSize(2);
    }

    /**
     * Add a GerritServer with default configs from the UI.
     *
     * @param serverName the name
     * @throws IOException if error getting URL or getting page from URL.
     */
    private void addNewServerWithDefaultConfigs(String serverName) throws IOException {
        URL url = new URL(j.getURL(), newServerURL);
        HtmlPage page = j.createWebClient().getPage(url);
        HtmlForm form = page.getFormByName(newServerFormName);

        form.getInputByName(inputFormName).setValueAttribute(serverName);

        List<HtmlRadioButtonInput> radioButtons = form.getRadioButtonsByName(radioButtonName);
        HtmlRadioButtonInput radioButtonDefaultConfig = null;
        for (HtmlRadioButtonInput button : radioButtons) {
            if (radioButtonDefaultConfigValue.equals(button.getValueAttribute())) {
                radioButtonDefaultConfig = button;
                radioButtonDefaultConfig.setChecked(true);
            }
        }
        assert_().withFailureMessage(
            "Failed to choose 'GerritServer with Default Configurations'")
            .that(radioButtonDefaultConfig.isChecked()).isTrue();

        form.submit(null);
    }

    /**
     * Add a GerritServer by copying existing configs from the UI.
     *
     * @param newServerName the name of the new server
     * @param fromServerName the name of the server from which the config is copied.
     * @throws IOException if error getting URL or getting page from URL.
     */
    private void addNewServerByCopyingConfig(String newServerName, String fromServerName) throws IOException {
        URL url = new URL(j.getURL(), newServerURL);
        HtmlPage page = j.createWebClient().getPage(url);
        HtmlForm form = page.getFormByName(newServerFormName);

        form.getInputByName(inputFormName).setValueAttribute(newServerName);

        List<HtmlRadioButtonInput> radioButtons = form.getRadioButtonsByName(radioButtonName);
        HtmlRadioButtonInput radioButtonCopy = null;
        for (HtmlRadioButtonInput button : radioButtons) {
            if (radioButtonCopyValue.equals(button.getValueAttribute())) {
                radioButtonCopy = button;
                radioButtonCopy.setChecked(true);
            }
        }
        assert_().withFailureMessage(
            "Failed to choose 'Copy from Existing Server Configurations'")
            .that(radioButtonCopy.isChecked()).isTrue();

        form.getInputByName(fromInputFormName).setValueAttribute(fromServerName);

        form.submit(null);
    }

    /**
     * Test not connect to Gerrit on startup.
     * @throws Exception Error creating job.
     */
    @Test
    public void testConnectOnStartup() throws Exception {
        GerritServer gerritServerOne = new GerritServer(gerritServerOneName, true);
        PluginImpl.getInstance().addServer(gerritServerOne);
        gerritServerOne.start();
        assertThat(gerritServerOne.isNoConnectionOnStartup()).isTrue();
    }
}
