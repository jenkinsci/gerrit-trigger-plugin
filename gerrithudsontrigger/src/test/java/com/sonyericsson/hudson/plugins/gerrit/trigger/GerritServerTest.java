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

import static com.sonyericsson.hudson.plugins.gerrit.trigger.test.SshdServerMock.GERRIT_STREAM_EVENTS;

import org.apache.sshd.SshServer;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.test.SshdServerMock;

import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.util.RunList;

import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * Provide Html unit tests for server management and triggering from UI.
 * @author Anthony Wei-Wing Chin &lt;anthony.a.chin@ericsson.com&gt;
 * @author Mathieu Wang &lt;mathieu.wang@ericsson.com&gt;
 */
public class GerritServerTest extends HudsonTestCase {

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

    private final int timeToBuild = 5000;
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

    @Override
    protected void setUp() throws Exception {
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
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
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
        FreeStyleProject projectOne = DuplicatesUtil.createGerritTriggeredJob(this, projectOneName, gerritServerOneName);
        FreeStyleProject projectTwo = DuplicatesUtil.createGerritTriggeredJob(this, projectTwoName, gerritServerTwoName);
        gerritServerOne.triggerEvent(Setup.createPatchsetCreated(gerritServerOneName));
        gerritServerTwo.triggerEvent(Setup.createPatchsetCreated(gerritServerTwoName));
        RunList<FreeStyleBuild> buildsOne = DuplicatesUtil.waitForBuilds(projectOne, 1, timeToBuild);
        RunList<FreeStyleBuild> buildsTwo = DuplicatesUtil.waitForBuilds(projectTwo, 1, timeToBuild);

        FreeStyleBuild buildOne = buildsOne.get(0);
        assertSame(Result.SUCCESS, buildOne.getResult());
        assertEquals(1, projectOne.getBuilds().size());
        assertSame(gerritServerOneName, buildOne.getCause(GerritCause.class).getEvent().getProvider().getName());

        FreeStyleBuild buildTwo = buildsTwo.get(0);
        assertSame(Result.SUCCESS, buildTwo.getResult());
        assertEquals(1, projectTwo.getBuilds().size());
        assertSame(gerritServerTwoName, buildTwo.getCause(GerritCause.class).getEvent().getProvider().getName());
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

        assertEquals(1, PluginImpl.getInstance().getServers().size());
        assertTrue(wrongMessageWarning, textContent.contains("Remove server"));
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
        DuplicatesUtil.createGerritTriggeredJob(this, projectOneName, gerritServerOneName);

        GerritServer server2 = new GerritServer(gerritServerTwoName);
        PluginImpl.getInstance().addServer(server2);

        removeServer(gerritServerOneName);

        assertEquals(1, PluginImpl.getInstance().getServers().size());
        assertTrue(wrongMessageWarning,
                textContent.contains("Disable Gerrit Trigger in the following jobs and remove server \""
                                    + gerritServerOneName + "\"?"));
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

        DuplicatesUtil.createGerritTriggeredJob(this, projectOneName, gerritServerOneName);

        removeServer(gerritServerOneName);

        assertEquals(false, buttonFound);
        assertEquals(1, PluginImpl.getInstance().getServers().size());
        assertEquals(removeLastServerWarning, textContent);
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

        assertEquals(false, buttonFound);
        assertEquals(1, PluginImpl.getInstance().getServers().size());
        assertEquals(removeLastServerWarning, textContent);
    }

    /**
     * Remove Server from UI.
     * @param serverName the name of the Gerrit server you want to access.
     * @throws IOException if error removing server.
     */
    private void removeServer(String serverName) throws IOException {
        URL url = new URL(getURL(), Functions.joinPath(serverURL, "server", serverName, "remove"));
        HtmlPage removalPage = createWebClient().getPage(url);

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
        assertEquals(1, PluginImpl.getInstance().getServers().size());

        addNewServerByCopyingConfig(gerritServerTwoName, gerritServerOneName);
        assertEquals(2, PluginImpl.getInstance().getServers().size());

        //try add server with same name:
        try {
            addNewServerWithDefaultConfigs(gerritServerOneName);
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(badRequestErrorCode, e.getStatusCode());
        }
        //make sure the server has not been added.
        assertEquals(2, PluginImpl.getInstance().getServers().size());
    }

    /**
     * Add a GerritServer with default configs from the UI.
     *
     * @param serverName the name
     * @throws IOException if error getting URL or getting page from URL.
     */
    private void addNewServerWithDefaultConfigs(String serverName) throws IOException {
        URL url = new URL(getURL(), newServerURL);
        HtmlPage page = createWebClient().getPage(url);
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
        assertTrue("Failed to choose 'GerritServer with Default Configurations'", radioButtonDefaultConfig.isChecked());

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
        URL url = new URL(getURL(), newServerURL);
        HtmlPage page = createWebClient().getPage(url);
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
        assertTrue("Failed to choose 'Copy from Existing Server Configurations'", radioButtonCopy.isChecked());

        form.getInputByName(fromInputFormName).setValueAttribute(fromServerName);

        form.submit(null);
    }

    /**
     * Test triggering job with events from a Gerrit server with Pseudo mode.
     * @throws Exception Error creating job.
     */
    @Test
    public void testTriggeringFromGerritServersWithPseudoMode() throws Exception {
        GerritServer gerritServerOne = new GerritServer(gerritServerOneName, true);
        PluginImpl.getInstance().addServer(gerritServerOne);
        gerritServerOne.start();
        FreeStyleProject projectOne = DuplicatesUtil.createGerritTriggeredJob(this, projectOneName, gerritServerOneName);
        PluginImpl.getInstance().getHandler().post(Setup.createPatchsetCreated(gerritServerOneName));
        RunList<FreeStyleBuild> buildsOne = DuplicatesUtil.waitForBuilds(projectOne, 1, timeToBuild);

        FreeStyleBuild buildOne = buildsOne.get(0);
        assertSame(Result.SUCCESS, buildOne.getResult());
        assertEquals(1, projectOne.getBuilds().size());
        assertSame(gerritServerOneName, buildOne.getCause(GerritCause.class).getEvent().getProvider().getName());
    }

}
