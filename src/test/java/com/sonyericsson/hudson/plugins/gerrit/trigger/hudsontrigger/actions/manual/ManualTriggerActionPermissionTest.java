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
import org.htmlunit.html.HtmlButton;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.*;

//CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: Test-data.

/**
 * Tests {@link ManualTriggerAction} with regards to security concerns.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@WithJenkins
class ManualTriggerActionPermissionTest {

    /**
     * Running test in Jenkins.
     */
    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
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
    void testGetGetUrlNameNotPermitted() throws Exception {
        //add a server so that the manual trigger action URL can be accessed by users with proper access rights.
        PluginImpl.getInstance().addServer(new GerritServer("testServer"));
        ManualTriggerAction action = getManualTriggerAction();
        JenkinsRule.WebClient wc = j.createWebClient().withJavaScriptEnabled(true);
        HtmlPage page = wc.goTo("");
        HtmlButton button = (HtmlButton)page.getElementById("header-more-actions");
        assertNull(button, "Anonymous should not see the RootAction");
    }

    /**
     * Tests if the html-link to {@link ManualTriggerAction#getUrlName()} is visible from the main-page.
     * It should be visible to Administrators as configured in the test-configuration.
     *
     * @throws Exception if so.
     */
    @Test
    void testGetUrlName() throws Exception {
        //add a server so that the manual trigger action URL can be accessed by users with proper access rights.
        PluginImpl.getInstance().addServer(new GerritServer("testServer"));
        ManualTriggerAction action = getManualTriggerAction();
        JenkinsRule.WebClient wc = j.createWebClient().withJavaScriptEnabled(true);
        wc.login("admin", "admin");
        HtmlPage page = wc.goTo("");
        page.getElementById("header-more-actions").click();
        wc.waitForBackgroundJavaScript(5000);
        HtmlAnchor a = assertDoesNotThrow(() ->
            page.getAnchorByHref(Functions.joinPath(j.contextPath, action.getUrlName())),
                "Admin should see the RootAction");
        assertNotNull(a);
    }

    /**
     * Tests if the html-link to {@link ManualTriggerAction#getUrlName()} is visible from the main-page.
     * It should be visible to Privileged Users as configured in the test-configuration.
     *
     * @throws Exception if so.
     */
    @Test
    void testGetUrlNamePrivileged() throws Exception {
        //add a server so that the manual trigger action URL can be accessed by users with proper access rights.
        PluginImpl.getInstance().addServer(new GerritServer("testServer"));
        ManualTriggerAction action = getManualTriggerAction();
        JenkinsRule.WebClient wc = j.createWebClient().login("bobby", "bobby");
        HtmlPage page = wc.goTo("");
        page.getElementById("header-more-actions").click();
        wc.waitForBackgroundJavaScript(5000);
        assertDoesNotThrow(() -> {
            HtmlAnchor a = page.getAnchorByHref(Functions.joinPath(j.contextPath, action.getUrlName()));
            assertNotNull(a);
        }, "Bobby should see the RootAction");
    }

    /**
     * A simple check so that the action's config.jelly gets the correct Permission to check.
     *
     */
    @Test
    void testGetRequiredPermission() {
        ManualTriggerAction action = getManualTriggerAction();
        assertSame(PluginImpl.MANUAL_TRIGGER, action.getRequiredPermission());
    }

    //CS IGNORE LineLength FOR NEXT 4 LINES. REASON: Javadoc link.

    /**
     * Tests that a privileged user can perform a search via
     * {@link ManualTriggerAction#doGerritSearch(String, String, boolean, org.kohsuke.stapler.StaplerRequest2, org.kohsuke.stapler.StaplerResponse2)}.
     *
     * @throws Exception if so.
     */
    @Test
    void testDoGerritSearch() throws Exception {
        //add a server so that the manual trigger action URL can be accessed by users with proper access rights.
        PluginImpl.getInstance().addServer(new GerritServer("testServer"));
        ManualTriggerAction action = getManualTriggerAction();
        JenkinsRule.WebClient wc = j.createWebClient().login("bobby", "bobby");

        HtmlPage page = wc.goTo(action.getUrlName());
        HtmlForm form = page.getFormByName("theSearch");
        form.getInputByName("queryString").setValue("2000");
        Page result = assertDoesNotThrow(() -> j.submit(form),
                "Bobby should have been able to access the search page");
        j.assertGoodStatus(result);
    }

    //CS IGNORE LineLength FOR NEXT 4 LINES. REASON: Javadoc link.

    /**
     * Tests that an anonymous user can not perform a search via
     * {@link ManualTriggerAction#doGerritSearch(String, String, boolean, org.kohsuke.stapler.StaplerRequest2, org.kohsuke.stapler.StaplerResponse2)}.
     *
     */
    @Test
    void testDoGerritSearchNotPermitted() {
        //add a server so that the manual trigger action URL can be accessed by users with proper access rights.
        PluginImpl.getInstance().addServer(new GerritServer("testServer"));
        ManualTriggerAction action = getManualTriggerAction();
        JenkinsRule.WebClient wc = j.createWebClient();

        FailingHttpStatusCodeException e = assertThrows(FailingHttpStatusCodeException.class, () -> {
                    HtmlPage page = wc.goTo(action.getUrlName());
                    HtmlForm form = page.getFormByName("theSearch");
                    form.getInputByName("queryString").setValue("2000");
                    j.submit(form);
                }, "Anonymous should not have been able to access the search page");
        assertEquals(403, e.getStatusCode());
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
