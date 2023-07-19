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

import org.htmlunit.ElementNotFoundException;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import hudson.Functions;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.View;
import hudson.scm.SCM;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

//CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: Test-data.

/**
 * Tests {@link ManualTriggerAction} with regards to security concerns.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class ManualTriggerActionPermissionTest {

    /**
     * Running test in Jenkins.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(
                new MockAuthorizationStrategy()
                        .grant(View.DELETE,
                                Item.BUILD,
                                Run.UPDATE,
                                Hudson.READ,
                                Item.CONFIGURE,
                                Item.DELETE,
                                SCM.TAG,
                                Run.DELETE,
                                Item.READ,
                                Item.CREATE,
                                PluginImpl.MANUAL_TRIGGER,
                                Item.WORKSPACE,
                                View.CREATE,
                                View.CONFIGURE)
                        .everywhere().to("bobby")
                        .grant(Hudson.READ).everywhere().toEveryone()
                        .grant(Hudson.ADMINISTER).everywhere().to("admin")
        );
    }

    /**
     * Tests if the html-link to {@link ManualTriggerAction#getUrlName()} is visible from the main-page.
     * It should be hidden from an Anonymous user as configured in the test-configuration.
     *
     * @throws Exception if so.
     */
    @Test
    public void testGetGetUrlNameNotPermitted() throws Exception {
        //add a server so that the manual trigger action URL can be accessed by users with proper access rights.
        PluginImpl.getInstance().addServer(new GerritServer("testServer"));
        ManualTriggerAction action = getManualTriggerAction();
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("");
        try {
            HtmlAnchor a = page.getAnchorByHref(Functions.joinPath(j.contextPath, action.getUrlName()));
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
    @Test
    public void testGetUrlName() throws Exception {
        //add a server so that the manual trigger action URL can be accessed by users with proper access rights.
        PluginImpl.getInstance().addServer(new GerritServer("testServer"));
        ManualTriggerAction action = getManualTriggerAction();
        JenkinsRule.WebClient wc = j.createWebClient().login("admin", "admin");
        HtmlPage page = wc.goTo("");
        try {
            HtmlAnchor a = page.getAnchorByHref(Functions.joinPath(j.contextPath, action.getUrlName()));
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
    @Test
    public void testGetUrlNamePrivileged() throws Exception {
        //add a server so that the manual trigger action URL can be accessed by users with proper access rights.
        PluginImpl.getInstance().addServer(new GerritServer("testServer"));
        ManualTriggerAction action = getManualTriggerAction();
        JenkinsRule.WebClient wc = j.createWebClient().login("bobby", "bobby");
        HtmlPage page = wc.goTo("");
        try {
            HtmlAnchor a = page.getAnchorByHref(Functions.joinPath(j.contextPath, action.getUrlName()));
            assertNotNull(a);
        } catch (ElementNotFoundException e) {
            fail("Bobby should see the RootAction");
        }
    }

    /**
     * A simple check so that the action's config.jelly gets the correct Permission to check.
     *
     * @throws Exception if so.
     */
    @Test
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
    @Test
    public void testDoGerritSearch() throws Exception {
        //add a server so that the manual trigger action URL can be accessed by users with proper access rights.
        PluginImpl.getInstance().addServer(new GerritServer("testServer"));
        ManualTriggerAction action = getManualTriggerAction();
        JenkinsRule.WebClient wc = j.createWebClient().login("bobby", "bobby");
        try {
            HtmlPage page = wc.goTo(action.getUrlName());
            HtmlForm form = page.getFormByName("theSearch");
            form.getInputByName("queryString").setValue("2000");
            Page result = j.submit(form);
            j.assertGoodStatus(result);
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
    @Test
    public void testDoGerritSearchNotPermitted() throws Exception {
        //add a server so that the manual trigger action URL can be accessed by users with proper access rights.
        PluginImpl.getInstance().addServer(new GerritServer("testServer"));
        ManualTriggerAction action = getManualTriggerAction();
        JenkinsRule.WebClient wc = j.createWebClient();
        try {
            HtmlPage page = wc.goTo(action.getUrlName());
            HtmlForm form = page.getFormByName("theSearch");
            form.getInputByName("queryString").setValue("2000");
            j.submit(form);
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
        return j.jenkins.getExtensionList(ManualTriggerAction.class).get(0);
    }
}
