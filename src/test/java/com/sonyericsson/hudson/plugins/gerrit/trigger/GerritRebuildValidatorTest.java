package com.sonyericsson.hudson.plugins.gerrit.trigger;

import static org.junit.Assert.assertNotNull;

import hudson.ExtensionList;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests for {@link GerritRebuildValidator}.
 */
public class GerritRebuildValidatorTest {

    /**
     * An instance of {@link JenkinsRule}.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule j = new JenkinsRule();

    @Issue("JENKINS-72409")
    @Test
    public void testRebuildValidatorStackOverflow() throws Exception {
        assertNotNull(ExtensionList.lookupSingleton(GerritRebuildValidator.class));
        FreeStyleProject p = j.createFreeStyleProject();
        j.assertBuildStatusSuccess(p.scheduleBuild2(0, null, new Action[0]));
    }
}
