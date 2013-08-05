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

import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tests for the project setup for a Gerrit triggered project.
 * @author Tomas Westling &lt;tomas.westling@sonymobile.com&gt;
 */
public class GerritTriggerProjectHudsonTest extends HudsonTestCase {

    /**
     * Tests that the dropdown list for comment added is populated with the correct values.
     * @throws Exception if so.
     */
    public void testPopulateDropDown() throws Exception {
        @SuppressWarnings("unused")
        ArrayList<GerritServer> servers = PluginImpl.getInstance().getServers();

        //create a server for testing
        GerritServer server = new GerritServer(PluginImpl.DEFAULT_SERVER_NAME);
        servers.add(server);
        server.start();

        FreeStyleProject project = DuplicatesUtil.createGerritTriggeredJobForCommentAdded(this, "myGerritProject");
        WebClient wc = createWebClient();
        HtmlPage page = wc.goTo("/job/myGerritProject/configure");
        List<HtmlElement> elements = page.getDocumentElement().getElementsByAttribute("td", "class", "setting-name");
        HtmlElement tr = null;
        for (HtmlElement element : elements) {
            if ("Verdict Category".equals(element.getTextContent())) {
                tr = element.getEnclosingElement("tr");
                break;
            }
        }
        assertNotNull(tr);
        HtmlElement settingsMainElement = tr.getOneHtmlElementByAttribute("td", "class", "setting-main");
        HtmlSelect select = (HtmlSelect)settingsMainElement.getChildElements().iterator().next();
        Iterator<HtmlElement> iterator = select.getChildElements().iterator();
        HtmlElement option = iterator.next();
        String value = option.getAttribute("value");
        //This will test that the default values are correct.
        assertEquals("First value should be CRVW", "CRVW", value);
        option = iterator.next();
        value = option.getAttribute("value");
        assertEquals("Second value should be VRIF", "VRIF", value);
    }
}
