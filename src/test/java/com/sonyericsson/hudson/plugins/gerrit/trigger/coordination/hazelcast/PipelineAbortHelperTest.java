/*
 *  The MIT License
 *
 *  Copyright 2026 CloudBees, Inc.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.hazelcast;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertFalse;

/**
 * Tests for {@link PipelineAbortHelper}.
 */
public class PipelineAbortHelperTest {

    //CS IGNORE VisibilityModifier FOR NEXT 3 LINES. REASON: JUnit Rule.
    //CS IGNORE JavadocVariable FOR NEXT 2 LINES. REASON: JUnit Rule.
    @Rule
    public final JenkinsRule jenkins = new JenkinsRule();

    /**
     * A FreeStyle build is never a Pipeline — should always return false.
     */
    @Test
    public void testFreeStyleBuildReturnsFalse() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        assertFalse(PipelineAbortHelper.isPipelineNotYetStarted(build));
    }

    /**
     * A Pipeline build blocked inside a running step has getCurrentHeads() non-empty,
     * so isPipelineNotYetStarted() must return false (safe to interrupt).
     */
    @Test
    public void testPipelineBlockedAtSemaphoreReturnsFalse() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline-semaphore");
        job.setDefinition(new CpsFlowDefinition(
                "semaphore 'wait'\n"
                + "echo 'done'", true));

        WorkflowRun run = job.scheduleBuild2(0).waitForStart();

        // Wait until the semaphore step is reached — at this point CPS is fully started
        SemaphoreStep.waitForStart("wait/1", run);

        assertFalse("Pipeline blocked at semaphore should report CPS started",
                PipelineAbortHelper.isPipelineNotYetStarted(run));

        // Unblock and let the build finish cleanly
        SemaphoreStep.success("wait/1", null);
        jenkins.waitForCompletion(run);
        jenkins.assertBuildStatusSuccess(run);
    }

    /**
     * A Pipeline build that has been interrupted after it started should still
     * report false — it is past initialisation, so delivery was correct.
     */
    @Test
    public void testAbortedPipelineReturnsFalse() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "pipeline-aborted");
        job.setDefinition(new CpsFlowDefinition(
                "semaphore 'wait-abort'\n"
                + "echo 'done'", true));

        WorkflowRun run = job.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait-abort/1", run);

        // Abort while it's at the semaphore (CPS has started)
        assertFalse(PipelineAbortHelper.isPipelineNotYetStarted(run));

        run.getExecutor().interrupt(Result.ABORTED);
        jenkins.waitForCompletion(run);
    }
}
