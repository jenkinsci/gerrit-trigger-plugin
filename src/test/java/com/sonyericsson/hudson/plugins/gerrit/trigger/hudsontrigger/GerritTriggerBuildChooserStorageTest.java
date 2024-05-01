package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlOption;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import com.gargoylesoftware.htmlunit.html.HtmlSelect;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import hudson.plugins.git.util.BuildChooser;
import hudson.scm.SCM;

/**
 * Tests for configuration and storage of {@link GerritTriggerBuildChooser} and
 * {@link GerritTriggerBuildChooserWithMergeCommitSupport}.
 *
 * @author Eric Isakson
 */
public class GerritTriggerBuildChooserStorageTest {

    /**
     * XPath selector to identify the Git radio button in the SCM config.
     */
    private static final String GIT_RADIO_BUTTON_XPATH =
        "//input[@type = 'radio' and @name = 'scm' and normalize-space(..) = 'Git']";

    /**
     * XPath selector to identify the Git add additional behaviours button in the page.
     */
    private static final String GIT_ADD_ADDITIONAL_BEHAVIORS_BUTTON_XPATH = "//button[@suffix='extensions']";

    /**
     * XPath selector to identify the Git choosing strategy menu item after the add button is clicked.
     */
    private static final String GIT_CHOOSING_STRATEGY_MENUITEM_XPATH =
        "//a[text()='Strategy for choosing what to build']";

    /**
     * XPath selector to identify the select option list for the choosing strategy in the page.
     */
    private static final String GIT_CHOOSING_STRATEGY_SELECT_XPATH =
        "//self::node()[@descriptorid='" + BuildChooserSetting.class.getCanonicalName() + "']//select";

    /**
     * Max number of times to loop while waiting on page update.
     */
    private static final int MAX_RETRIES_WAITING_FOR_PAGE_UPDATE = 10;

    /**
     * Time to sleep between condition checks while waiting on page update.
     */
    private static final long SLEEP_MILLIS_WAITING_FOR_PAGE_UPDATE = 1000L;

    /**
     * Jenkins rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule();

    /**
     * Verify the project has the expected settings for the given chooser.
     *
     * @param p The project to verify.
     * @param expected The expected chooser.
     */
    private void assertBuildChooser(FreeStyleProject p, BuildChooser expected) {
        assertNotNull(p);
        assertNotNull(expected);
        SCM scm = p.getScm();
        assertTrue(scm instanceof GitSCM);
        GitSCM gitSCM = (GitSCM)scm;
        List<BuildChooserSetting> buildChooserSettings = gitSCM.getExtensions().getAll(BuildChooserSetting.class);
        assertEquals(1, buildChooserSettings.size());
        assertEquals(expected.getClass(), buildChooserSettings.get(0).getBuildChooser().getClass());
    }

    /**
     * Confirm configuration state is restored properly after reload and restart.
     *
     * @param chooser The chooser to test.
     * @throws Exception If anything unexpected happens.
     */
    private void testStorageForChooser(BuildChooser chooser) throws Exception {
        rr.then(r -> {
            FreeStyleProject p = r.createFreeStyleProject("testproject");

            // Setup the chooser using the web page form
            HtmlPage page = r.createWebClient().getPage(p, "configure");
            HtmlRadioButtonInput scmGit = (HtmlRadioButtonInput)page.getFirstByXPath(GIT_RADIO_BUTTON_XPATH);
            scmGit.setChecked(true);
            page = scmGit.click();
            HtmlButton addAdditionalBehaviorButton =
                (HtmlButton)page.getFirstByXPath(GIT_ADD_ADDITIONAL_BEHAVIORS_BUTTON_XPATH);
            page = addAdditionalBehaviorButton.click();
            HtmlAnchor gitChoosingStrategyLink = (HtmlAnchor)page.getFirstByXPath(GIT_CHOOSING_STRATEGY_MENUITEM_XPATH);
            page = gitChoosingStrategyLink.click();
            // Retry with wait in between while the page updates and fail if we do not find the select
            // element within a fixed number of retries...
            // CS IGNORE LineLength FOR NEXT 1 LINES. REASON: Long URL.
            // See https://stackoverflow.com/questions/17843521/get-the-changed-html-content-after-its-updated-by-javascript-htmlunit
            int amountOfTries = MAX_RETRIES_WAITING_FOR_PAGE_UPDATE;
            HtmlSelect select = (HtmlSelect)page.getFirstByXPath(GIT_CHOOSING_STRATEGY_SELECT_XPATH);
            while (amountOfTries > 0 && select == null) {
                amountOfTries--;
                synchronized (page) {
                    page.wait(SLEEP_MILLIS_WAITING_FOR_PAGE_UPDATE);
                }
                select = (HtmlSelect)page.getFirstByXPath(GIT_CHOOSING_STRATEGY_SELECT_XPATH);
            }
            assertNotNull(select);
            HtmlOption option = select.getOptionByText(chooser.getDisplayName());
            page = select.setSelectedAttribute(option, true);
            page = option.click();
            HtmlForm form = page.getFormByName("config");
            r.submit(form, "Submit"); // This is the "Save" button

            // Round trip the configuration
            p = r.configRoundtrip(p);

            // Confirm the chooser is set correctly after the config round trip
            assertBuildChooser(p, chooser);
        });
        // rr.then(r -> {
        //     // Confirm the chooser is still set correctly after the restart
        //     FreeStyleProject p = r.jenkins.getItemByFullName("testproject", FreeStyleProject.class);
        //     assertBuildChooser(p, chooser);
        // });
    }

    /**
     * Test for {@link GerritTriggerBuildChooser}.
     *
     * @throws Exception If anything unexpected happens.
     */
    @Test
    public void testStorageForGerritTriggerBuildChooser() throws Exception {
        testStorageForChooser(new GerritTriggerBuildChooser());
    }

    /**
     * Test for {@link GerritTriggerBuildChooserWithMergeCommitSupport}.
     *
     * @throws Exception If anything unexpected happens.
     */
    @Test
    public void testStorageForGerritTriggerBuildChooserWithMergeCommitSupport() throws Exception {
        testStorageForChooser(new GerritTriggerBuildChooserWithMergeCommitSupport());
    }
}
