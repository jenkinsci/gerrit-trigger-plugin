package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.manual;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import hudson.model.Hudson;
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
        WebClient wc = new WebClient();
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
     * {@link ManualTriggerAction#doGerritSearch(String, org.kohsuke.stapler.StaplerRequest, org.kohsuke.stapler.StaplerResponse)}.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testDoGerritSearch() throws Exception {
        //TODO: this test fails with code 500 and a SSH connection error.

        //add a server so that the manual trigger action URL can be accessed by users with proper access rights.
        PluginImpl.getInstance().addServer(new GerritServer("testServer"));
        ManualTriggerAction action = getManualTriggerAction();
        WebClient wc = new WebClient().login("bobby", "bobby");
        try {
            HtmlPage page = wc.goTo(action.getUrlName());
            HtmlForm form = page.getFormByName("theSearch");
            form.getInputByName("queryString").setValueAttribute("2000");
            Page result = form.submit(null);
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
     * {@link ManualTriggerAction#doGerritSearch(String, org.kohsuke.stapler.StaplerRequest, org.kohsuke.stapler.StaplerResponse)}.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testDoGerritSearchNotPermitted() throws Exception {
        //add a server so that the manual trigger action URL can be accessed by users with proper access rights.
        PluginImpl.getInstance().addServer(new GerritServer("testServer"));
        ManualTriggerAction action = getManualTriggerAction();
        WebClient wc = new WebClient();
        try {
            HtmlPage page = wc.goTo(action.getUrlName());
            HtmlForm form = page.getFormByName("theSearch");
            form.getInputByName("queryString").setValueAttribute("2000");
            form.submit(null);
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
        return Hudson.getInstance().getExtensionList(ManualTriggerAction.class).get(0);
    }
}
