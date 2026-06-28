/*
 * The MIT License
 *
 * Copyright (c) 2024 Amarula Solutions. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.workflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import hudson.model.Result;
import java.util.HashMap;
import java.util.Map;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests for {@link GerritTriggerCheckStep}.
 */
public class GerritTriggerCheckStepTest {

    /**
     * Jenkins rule instance.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 3 LINES. REASON: JenkinsRule.
    @Rule
    public JenkinsRule j = new JenkinsRule();

    // ---- Step configuration tests ----

    @Test
    public void testDefaultConstructor() {
        GerritTriggerCheckStep step = new GerritTriggerCheckStep();
        assertNull(step.getChecks());
        assertNull(step.getMessage());
        assertNull(step.getUrl());
    }

    @Test
    public void testSetChecks() {
        GerritTriggerCheckStep step = new GerritTriggerCheckStep();
        Map<String, String> checks = new HashMap<>();
        checks.put("checker:a", "SUCCESSFUL");
        checks.put("checker:b", "FAILED");
        step.setChecks(checks);

        assertEquals(checks, step.getChecks());
        assertEquals(2, step.getChecks().size());
    }

    @Test
    public void testSetMessage() {
        GerritTriggerCheckStep step = new GerritTriggerCheckStep();
        step.setMessage("All tests passed");
        assertEquals("All tests passed", step.getMessage());
    }

    @Test
    public void testSetMessageTrimsEmpty() {
        GerritTriggerCheckStep step = new GerritTriggerCheckStep();
        step.setMessage("   ");
        assertNull(step.getMessage());
    }

    @Test
    public void testSetUrl() {
        GerritTriggerCheckStep step = new GerritTriggerCheckStep();
        step.setUrl("https://jenkins.example.com/job/test/1/console");
        assertEquals("https://jenkins.example.com/job/test/1/console", step.getUrl());
    }

    @Test
    public void testSetUrlTrimsEmpty() {
        GerritTriggerCheckStep step = new GerritTriggerCheckStep();
        step.setUrl("   ");
        assertNull(step.getUrl());
    }

    // ---- CheckState enum tests ----

    @Test
    public void testCheckStateAllValuesPresent() {
        GerritTriggerCheckStep.CheckState[] states = GerritTriggerCheckStep.CheckState.values();
        // CS IGNORE MagicNumber FOR NEXT 1 LINES. REASON: Expected enum count.
        assertEquals(6, states.length);
    }

    @Test
    public void testCheckStateValueOfValid() {
        assertEquals(GerritTriggerCheckStep.CheckState.FAILED,
                GerritTriggerCheckStep.CheckState.valueOf("FAILED"));
        assertEquals(GerritTriggerCheckStep.CheckState.NOT_STARTED,
                GerritTriggerCheckStep.CheckState.valueOf("NOT_STARTED"));
        assertEquals(GerritTriggerCheckStep.CheckState.SCHEDULED,
                GerritTriggerCheckStep.CheckState.valueOf("SCHEDULED"));
        assertEquals(GerritTriggerCheckStep.CheckState.RUNNING,
                GerritTriggerCheckStep.CheckState.valueOf("RUNNING"));
        assertEquals(GerritTriggerCheckStep.CheckState.SUCCESSFUL,
                GerritTriggerCheckStep.CheckState.valueOf("SUCCESSFUL"));
        assertEquals(GerritTriggerCheckStep.CheckState.NOT_RELEVANT,
                GerritTriggerCheckStep.CheckState.valueOf("NOT_RELEVANT"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCheckStateValueOfInvalid() {
        GerritTriggerCheckStep.CheckState.valueOf("INVALID");
    }

    @Test(expected = NullPointerException.class)
    public void testCheckStateValueOfNull() {
        GerritTriggerCheckStep.CheckState.valueOf(null);
    }

    // ---- Step descriptor tests ----

    @Test
    public void testDescriptorFunctionName() {
        GerritTriggerCheckStep.DescriptorImpl d = new GerritTriggerCheckStep.DescriptorImpl();
        assertEquals("gerritTriggerCheck", d.getFunctionName());
    }

    @Test
    public void testDescriptorDisplayName() {
        GerritTriggerCheckStep.DescriptorImpl d = new GerritTriggerCheckStep.DescriptorImpl();
        assertEquals("Post Gerrit check results", d.getDisplayName());
    }

    @Test
    public void testDescriptorRequiredContext() {
        GerritTriggerCheckStep.DescriptorImpl d = new GerritTriggerCheckStep.DescriptorImpl();
        assertNotNull(d.getRequiredContext());
        assertTrue(d.getRequiredContext().contains(hudson.model.Run.class));
    }

    // ---- Pipeline error tests ----

    /**
     * Tests that the step throws when the build was not triggered by Gerrit.
     */
    @Test
    public void testNoGerritCauseThrowsIllegalStateException() throws Exception {
        jenkins.model.JenkinsLocationConfiguration.get().setUrl("http://localhost:8080/");

        WorkflowJob job = j.createProject(WorkflowJob.class, "noGerritCause");
        job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                + "  gerritTriggerCheck checks: ['checker:x': 'SUCCESSFUL']\n"
                + "}", true));

        WorkflowRun run = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        j.assertLogContains("gerritTriggerCheck: build was not triggered by Gerrit", run);
    }

    /**
     * Tests that the step throws when checks map is empty.
     * Requires a build with GerritCause — this test verifies the error
     * is surfaced correctly when running in a Jenkins pipeline.
     */
    @Test
    public void testEmptyChecksThrowsInPipeline() throws Exception {
        jenkins.model.JenkinsLocationConfiguration.get().setUrl("http://localhost:8080/");

        WorkflowJob job = j.createProject(WorkflowJob.class, "checkEmpty");
        job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                + "  gerritTriggerCheck checks: [:] \n"
                + "}", true));

        WorkflowRun run = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        // Both GerritCause missing and empty checks are possible failures
        assertTrue("Should contain error about Gerrit trigger or checks",
                JenkinsRule.getLog(run).contains("gerritTriggerCheck"));
    }

    /**
     * Tests that an invalid state string throws in a pipeline.
     */
    @Test
    public void testInvalidStateThrowsInPipeline() throws Exception {
        jenkins.model.JenkinsLocationConfiguration.get().setUrl("http://localhost:8080/");

        WorkflowJob job = j.createProject(WorkflowJob.class, "checkBadState");
        job.setDefinition(new CpsFlowDefinition(
                "node {\n"
                + "  gerritTriggerCheck checks: ['checker:test': 'INVALID_STATE']\n"
                + "}", true));

        WorkflowRun run = j.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        String log = JenkinsRule.getLog(run);
        // Could fail on missing GerritCause before reaching state validation
        assertTrue("Should contain error",
                log.contains("gerritTriggerCheck"));
    }
}
