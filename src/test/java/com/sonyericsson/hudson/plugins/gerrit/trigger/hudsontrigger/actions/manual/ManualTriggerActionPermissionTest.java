/*
 *  The MIT License
 *
 *  Copyright (c) 2011 Sony Mobile Communications Inc. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.manual;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import jenkins.model.Jenkins;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;

//CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: Test-data.

/**
 * Tests {@link ManualTriggerAction} with regards to security concerns.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class ManualTriggerActionPermissionTest extends HudsonTestCase {
    //TODO One test fails with a 404 on gerrit-trigger.js when executed from Parent Pom, but not from this module.

    /**
     * Tests if the html-link to {@link ManualTriggerAction#getUrlName()} is visible from the main-page.
     * It should be hidden from an Anonymous user as configured in the test-configuration.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testGetGetUrlNameNotPermitted() throws Exception {
        //add a server so that the manual trigger action URL can be accessed by users with proper access rights.
        PluginImpl.getInstance().addServer(new GerritServer("testServer"));
        ManualTriggerAction action = getManualTriggerAction();
        WebClient wc = createWebClient();
        HtmlPage page = wc.goTo("/");
        try {
            HtmlAnchor a = page.getAnchorByHref(action.getUrlName());
            assertNull(a);
        } catch (ElementNotFoundException e) {
            return;
        }
        fail("Anonymous should not see the RootAction");
    }

    /**
     * Tests if the html-link to {@link ManualTriggerAction#getUrlName()} is visible from the main-page.
     * It should be visible to Administrators as configured in the test-configuration.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testGetUrlName() throws Exception {
        //add a server so that the manual trigger action URL can be accessed by users with proper access rights.
        PluginImpl.getInstance().addServer(new GerritServer("testServer"));
        ManualTriggerAction action = getManualTriggerAction();
        WebClient wc = new WebClient().login("admin", "admin");
        HtmlPage page = wc.goTo("/");
        try {
            HtmlAnchor a = page.getAnchorByHref(action.getUrlName());
            assertNotNull(a);
        } catch (ElementNotFoundException e) {
            fail("Admin should see the RootAction");
        }
    }

    /**
     * Tests if the html-link to {@link ManualTriggerAction#getUrlName()} is visible from the main-page.
     * It should be visible to Privileged Users as configured in the test-configuration.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testGetUrlNamePrivileged() throws Exception {
        //add a server so that the manual trigger action URL can be accessed by users with proper access rights.
        PluginImpl.getInstance().addServer(new GerritServer("testServer"));
        ManualTriggerAction action = getManualTriggerAction();
        WebClient wc = new WebClient().login("bobby", "bobby");
        HtmlPage page = wc.goTo("/");
        try {
            HtmlAnchor a = page.getAnchorByHref(action.getUrlName());
            assertNotNull(a);
        } catch (ElementNotFoundException e) {
            fail("Bobby should see the RootAction");
        }
    }

    /**
     * Tests that {@link ManualTriggerAction#getDisplayName()} returns null for Anonymous users.
     * As configured in the test-configuration.
     * Something has changed in the security handling between Hudson 1.362 and Jenkins 1.400
     * so that executed code is running as the SYSTEM user, and so has access.
     * So now this test tests nothing since it is also mostly covered by {@link #testGetGetUrlNameNotPermitted()}.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testGetDisplayNameNotPermitted() throws Exception {
        //ManualTriggerAction action = getManualTriggerAction();
        //assertNull(action.getDisplayName());
    }

    /**
     * A simple check so that the action's config.jelly gets the correct Permission to check.
     *
     * @throws Exception if so.
     */
    public void testGetRequiredPermission() throws Exception {
        ManualTriggerAction action = getManualTriggerAction();
        assertSame(PluginImpl.MANUAL_TRIGGER, action.getRequiredPermission());
    }

    //CS IGNORE LineLength FOR NEXT 4 LINES. REASON: Javadoc link.

    /**
     * Tests that a privileged user can perform a search via
     * {@link ManualTriggerAction#doGerritSearch(String, String, boolean, org.kohsuke.stapler.StaplerRequest, org.kohsuke.stapler.StaplerResponse)}.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testDoGerritSearch() throws Exception {
        //add a server so that the manual trigger action URL can be accessed by users with proper access rights.
        PluginImpl.getInstance().addServer(new GerritServer("testServer"));
        ManualTriggerAction action = getManualTriggerAction();
        WebClient wc = new WebClient().login("bobby", "bobby");
        try {
            HtmlPage page = wc.goTo(action.getUrlName());
            HtmlForm form = page.getFormByName("theSearch");
            form.getInputByName("queryString").setValueAttribute("2000");
            Page result = submit(form);
            assertGoodStatus(result);
        } catch (FailingHttpStatusCodeException e) {
            if (e.getStatusCode() == 403) {
                fail("Bobby should have been able to access the search page");
            } else {
                throw e;
            }
        }
    }

    //CS IGNORE LineLength FOR NEXT 4 LINES. REASON: Javadoc link.

    /**
     * Tests that an anonymous user can not perform a search via
     * {@link ManualTriggerAction#doGerritSearch(String, String, boolean, org.kohsuke.stapler.StaplerRequest, org.kohsuke.stapler.StaplerResponse)}.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testDoGerritSearchNotPermitted() throws Exception {
        //add a server so that the manual trigger action URL can be accessed by users with proper access rights.
        PluginImpl.getInstance().addServer(new GerritServer("testServer"));
        ManualTriggerAction action = getManualTriggerAction();
        WebClient wc = createWebClient();
        try {
            HtmlPage page = wc.goTo(action.getUrlName());
            HtmlForm form = page.getFormByName("theSearch");
            form.getInputByName("queryString").setValueAttribute("2000");
            submit(form);
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(403, e.getStatusCode());
            return;
        }
        fail("Anonymous should not have been able to access the search page");
    }

    /**
     * Utility method.
     * Finds and returns the Action from the Extension register.
     *
     * @return the Action
     */
    private ManualTriggerAction getManualTriggerAction() {
        return Jenkins.getInstance().getExtensionList(ManualTriggerAction.class).get(0);
    }
}
