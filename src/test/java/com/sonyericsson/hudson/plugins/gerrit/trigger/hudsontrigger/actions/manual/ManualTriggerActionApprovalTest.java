/*
 * The MIT License
 *
 * Copyright 2015 Jiri Engelthaler. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.manual;

import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlImage;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTable;
import com.gargoylesoftware.htmlunit.html.HtmlTableCell;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;
import org.apache.sshd.SshServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author Jiri Engelthaler &lt;EngyCZ@gmail.com&gt;
 */
public class ManualTriggerActionApprovalTest {

    private static final int VERIFIED_COLUMN = 10;
    private static final int CODE_REVIEW_COLUMN = 11;
    private static final int FIRST_RESULT_ROW = 1;
    private static final int SECOND_RESULT_ROW = 3;
    /**
     * An instance of Jenkins Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule j = new JenkinsRule();

    private final String gerritServerName = "testServer";
    private final String projectName = "testProject";

    private SshdServerMock server;
    private SshServer sshd;

    /**
     * Runs before test method.
     *
     * @throws Exception throw if so.
     */
    @Before
    public void setUp() throws Exception {
        final SshdServerMock.KeyPairFiles sshKey = SshdServerMock.generateKeyPair();

        server = new SshdServerMock();
        sshd = SshdServerMock.startServer(server);
        server.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor("gerrit version", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor("gerrit query --format=JSON --current-patch-set \"status:open\"",
                SshdServerMock.SendQueryLastPatchSet.class);
        server.returnCommandFor("gerrit query --format=JSON --patch-sets --current-patch-set \"status:open\"",
                SshdServerMock.SendQueryAllPatchSets.class);

        GerritServer gerritServer = new GerritServer(gerritServerName);
        SshdServerMock.configureFor(sshd, gerritServer);
        PluginImpl.getInstance().addServer(gerritServer);
        Config config = (Config)gerritServer.getConfig();
        config.setGerritAuthKeyFile(sshKey.getPrivateKey());
        gerritServer.start();
        gerritServer.startConnection();
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
     * Tests {@link ManualTriggerAction.Approval#getApprovals(net.sf.json.JSONObject, int)}.
     * With an last patchset
     *
     * @throws Exception if so.
     */
    @Test
    public void testDoGerritSearchLastPatchSet() throws Exception {
        JenkinsRule.WebClient client = j.createWebClient();
        HtmlPage page = client.goTo("gerrit_manual_trigger");
        HtmlForm theSearch = page.getFormByName("theSearch");
        page = j.submit(theSearch);
        HtmlTable table = page.getElementByName("searchResultTable");
        HtmlTableCell verifiedCell = table.getCellAt(FIRST_RESULT_ROW, VERIFIED_COLUMN);
        DomNode child = verifiedCell.getFirstChild();
        assertThat(child, instanceOf(HtmlImage.class));
        assertEquals("-1", ((HtmlImage)child).getAltAttribute());

        HtmlTableCell codeReviewCell = table.getCellAt(FIRST_RESULT_ROW, CODE_REVIEW_COLUMN);
        child = codeReviewCell.getFirstChild();
        assertThat(child, instanceOf(HtmlImage.class));
        assertEquals("2", ((HtmlImage)child).getAltAttribute());
    }

    /**
     * Tests {@link ManualTriggerAction.Approval#getApprovals(net.sf.json.JSONObject, int)}.
     * With an all patchsets
     *
     * @throws Exception if so.
     */
    @Test
    public void testDoGerritSearchAllPatchSets() throws Exception {
        JenkinsRule.WebClient client = j.createWebClient();
        HtmlPage page = client.goTo("gerrit_manual_trigger");
        HtmlForm theSearch = page.getFormByName("theSearch");
        page = theSearch.getInputByName("allPatchSets").click();
        theSearch = page.getFormByName("theSearch");
        page = j.submit(theSearch);
        HtmlTable table = page.getElementByName("searchResultTable");
        HtmlTableCell verifiedCell = table.getCellAt(SECOND_RESULT_ROW, VERIFIED_COLUMN);
        DomNode child = verifiedCell.getFirstChild();
        assertThat(child, instanceOf(HtmlImage.class));
        assertEquals("-1", ((HtmlImage)child).getAltAttribute());

        HtmlTableCell codeReviewCell = table.getCellAt(SECOND_RESULT_ROW, CODE_REVIEW_COLUMN);
        child = codeReviewCell.getFirstChild();
        assertThat(child, instanceOf(HtmlImage.class));
        assertEquals("2", ((HtmlImage)child).getAltAttribute());
    }
}
