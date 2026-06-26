package com.sonyericsson.hudson.plugins.gerrit.trigger;

import hudson.ExtensionList;
import hudson.model.Action;
import hudson.model.FreeStyleProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link GerritRebuildValidator}.
 */
@WithJenkins
class GerritRebuildValidatorTest {

    /**
     * An instance of {@link JenkinsRule}.
     */
    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("JENKINS-72409")
    @Test
    void testRebuildValidatorStackOverflow() throws Exception {
        assertNotNull(ExtensionList.lookupSingleton(GerritRebuildValidator.class));
        FreeStyleProject p = j.createFreeStyleProject();
        j.assertBuildStatusSuccess(p.scheduleBuild2(0, null, new Action[0]));
    }
}
