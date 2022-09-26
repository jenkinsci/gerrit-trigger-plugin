/*
 *  The MIT License
 *
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.project;

import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.VerdictCategory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil;

import hudson.model.FreeStyleProject;

import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for the project setup for a Gerrit triggered project.
 * @author Tomas Westling &lt;tomas.westling@sonymobile.com&gt;
 */
public class GerritTriggerProjectHudsonTest {
    /**
     * An instance of Jenkins Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule j = new JenkinsRule();

    /**
     * Tests that the dropdown list for comment added is populated with the correct values.
     * @throws Exception if so.
     */
    @Test
    public void testPopulateDropDown() throws Exception {
        @SuppressWarnings("unused")
        List<GerritServer> servers = PluginImpl.getInstance().getServers();

        //create a server for testing
        GerritServer server = new GerritServer(PluginImpl.DEFAULT_SERVER_NAME);
        servers.add(server);
        server.start();

        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJobForCommentAdded(j, "myGerritProject");

        try (WebClient wc = j.createWebClient()) {
            HtmlPage page = wc.getPage(project, "configure");
            final List<HtmlElement> elements = page.getDocumentElement()
                    .getElementsByAttribute("select", "name", "_.verdictCategory");
            assertThat(elements, hasSize(1));
            List<String> expected = Arrays.asList("Verified", "Code-Review");
            verifyOptions((HtmlSelect)elements.get(0), expected);
        }
    }

    /**
     * Tests that the dropdown list for comment added is populated with the correct values when we choose "Any Server".
     * @throws Exception if so.
     */
    @Test
    public void testPopulateDropDownFromTwoServers() throws Exception {
        @SuppressWarnings("unused")
        List<GerritServer> servers = PluginImpl.getInstance().getServers();

        //create a server with default Verdict Categories
        GerritServer server1 = new GerritServer("testServer1");
        servers.add(server1);
        server1.start();

        GerritServer server2 = new GerritServer("testServer2");
        servers.add(server2);
        server2.start();
        final List<VerdictCategory> categories = server2.getConfig().getCategories();
        categories.add(new VerdictCategory("Code-Review2", "Code Review For Gerrit 2.6+"));
        categories.add(new VerdictCategory("Verified2", "Verified For Gerrit 2.6+"));

        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJobForCommentAdded(j, "myGerritProject",
                GerritServer.ANY_SERVER);
        try (WebClient wc = j.createWebClient()) {
            HtmlPage page = wc.getPage(project, "configure");
            final List<HtmlElement> elements = page.getDocumentElement()
                    .getElementsByAttribute("select", "name", "_.verdictCategory");
            assertThat(elements, hasSize(1));
            List<String> expected = Arrays.asList("Verified", "Code-Review", "Code-Review2", "Verified2");
            verifyOptions((HtmlSelect)elements.get(0), expected);
        }
    }

    /**
     * Verifies that the provided HtmlSelect contains options with values as in the expected list.
     * The option values and the expected list will be sorted inline for comparison.
     * @param select the html element to check
     * @param expected the list of expected values
     */
    private void verifyOptions(HtmlSelect select, List<String> expected) {
        Iterator<DomElement> iterator = select.getChildElements().iterator();
        Collections.sort(expected);
        List<String> actual = new ArrayList<String>(expected.size());
        while (iterator.hasNext()) {
            DomElement option = iterator.next();
            actual.add(option.getAttribute("value"));
        }
        Collections.sort(actual);
        assertThat(actual, CoreMatchers.equalTo(expected));
    }
}
