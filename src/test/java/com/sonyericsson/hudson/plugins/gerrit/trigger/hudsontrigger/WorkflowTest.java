/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.TestUtils;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.model.RootAction;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;

import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.Is.isA;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class WorkflowTest {

    /**
     * Jenkins rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule jenkinsRule = new JenkinsRule();

    /**
     * Trigger test.
     * @throws Exception if there is one.
     */
    @Test
    public void testTriggerWorkflow() throws Exception {
        jenkinsRule.jenkins.setCrumbIssuer(null);
        MockGerritServer gerritServer = MockGerritServer.get(jenkinsRule);

        gerritServer.start();
        try {
            PatchsetCreated event = Setup.createPatchsetCreated(gerritServer.getName());

            WorkflowJob job = createWorkflowJob(event);

            PluginImpl.getHandler_().post(event);

            // Now wait for the Gerrit server to trigger the workflow build in Jenkins...
            TestUtils.waitForBuilds(job, 1);
            WorkflowRun run = job.getBuilds().iterator().next();
            jenkinsRule.assertLogContains("Gerrit trigger: patchset-created", run);

            // Workflow build was triggered successfully. Now lets check make sure the
            // gerrit plugin sent a verified notification back to the Gerrit Server...
            JSONObject verifiedMessage = gerritServer.waitForNextVerified();
            // System.out.println(gerritServer.lastContent);
            String message = verifiedMessage.getString("message");
            Assert.assertTrue(message.startsWith("Build Successful"));
            Assert.assertTrue(message.contains("job/WFJob/1/"));
            JSONObject labels = verifiedMessage.getJSONObject("labels");
            assertEquals(1, labels.getInt("Verified"));
        } finally {
            gerritServer.stop();
        }
    }

    /**
     * Tests setting a custom URL from a workflow job.
     * @throws Exception if there is one.
     */
    @Test
    public void testWorkflowStepSetsCustomUrl() throws Exception {
        jenkinsRule.jenkins.setCrumbIssuer(null);
        MockGerritServer gerritServer = MockGerritServer.get(jenkinsRule);

        gerritServer.start();
        try {
            PatchsetCreated event = Setup.createPatchsetCreated(gerritServer.getName());

            WorkflowJob job = createWorkflowJob(event, ""
                + "node {\n"
                + "   stage ('Build') {\n"
                + "   setGerritReview customUrl: 'myCustomUrl'\n"
                + "}}\n");

            PluginImpl.getHandler_().post(event);

            // Now wait for the Gerrit server to trigger the workflow build in Jenkins...
            TestUtils.waitForBuilds(job, 1);
            WorkflowRun run = job.getBuilds().iterator().next();
            jenkinsRule.assertLogContains("setGerritReview", run);

            // Workflow build was triggered successfully. Now lets check make sure the
            // gerrit plugin sent a verified notification back to the Gerrit Server...
            JSONObject verifiedMessage = gerritServer.waitForNextVerified();
            // System.out.println(gerritServer.lastContent);
            String message = verifiedMessage.getString("message");
            Assert.assertTrue(message.startsWith("Build Successful"));
            Assert.assertTrue(message.contains("myCustomUrl"));
            JSONObject labels = verifiedMessage.getJSONObject("labels");
            assertEquals(1, labels.getInt("Verified"));
        } finally {
            gerritServer.stop();
        }
    }

    /**
     * Tests setting an unsuccessful message from a workflow job.
     * @throws Exception if there is one.
     */
    @Test
    public void testWorkflowStepSetsUnsuccessfulMessage() throws Exception {
        jenkinsRule.jenkins.setCrumbIssuer(null);
        MockGerritServer gerritServer = MockGerritServer.get(jenkinsRule);

        gerritServer.start();
        try {
            PatchsetCreated event = Setup.createPatchsetCreated(gerritServer.getName());

            WorkflowJob job = createWorkflowJob(event, ""
                + "node {\n"
                + "   stage ('Build') {\n"
                + "   setGerritReview unsuccessfulMessage: 'myMessage'\n"
                + "   currentBuild.setResult('FAILURE')\n"
                + "}}\n");

            PluginImpl.getHandler_().post(event);

            // Now wait for the Gerrit server to trigger the workflow build in Jenkins...
            TestUtils.waitForBuilds(job, 1);
            WorkflowRun run = job.getBuilds().iterator().next();
            jenkinsRule.assertLogContains("setGerritReview", run);

            // Workflow build was triggered successfully. Now lets check make sure the
            // gerrit plugin sent a verified notification back to the Gerrit Server,
            // and the message contains the custom message...
            JSONObject verifiedMessage = gerritServer.waitForNextVerified();
            // System.out.println(gerritServer.lastContent);
            String message = verifiedMessage.getString("message");
            Assert.assertTrue(message.startsWith("Build Failed"));
            Assert.assertTrue(message.contains("myMessage"));
            JSONObject labels = verifiedMessage.getJSONObject("labels");
            assertEquals(0, labels.getInt("Verified"));
        } finally {
            gerritServer.stop();
        }
    }

    /**
     * Tests setting am unsuccessful message from a successful workflow job.
     * @throws Exception if there is one.
     */
    @Test
    public void testWorkflowStepSetsUnsuccessfulMessageWithSuccessfulBuild() throws Exception {
        jenkinsRule.jenkins.setCrumbIssuer(null);
        MockGerritServer gerritServer = MockGerritServer.get(jenkinsRule);

        gerritServer.start();
        try {
            PatchsetCreated event = Setup.createPatchsetCreated(gerritServer.getName());

            WorkflowJob job = createWorkflowJob(event, ""
                + "node {\n"
                + "   stage ('Build') {\n"
                + "   setGerritReview unsuccessfulMessage: 'myMessage'\n"
                + "}}\n");

            PluginImpl.getHandler_().post(event);

            // Now wait for the Gerrit server to trigger the workflow build in Jenkins...
            TestUtils.waitForBuilds(job, 1);
            WorkflowRun run = job.getBuilds().iterator().next();
            jenkinsRule.assertLogContains("setGerritReview", run);

            // Workflow build was triggered successfully. Now lets check make sure the
            // gerrit plugin sent a verified notification back to the Gerrit Server,
            // and the message does not contain the custom message...
            JSONObject verifiedMessage = gerritServer.waitForNextVerified();
            // System.out.println(gerritServer.lastContent);
            String message = verifiedMessage.getString("message");
            Assert.assertTrue(message.startsWith("Build Successful"));
            Assert.assertFalse(message.contains("myMessage"));
            JSONObject labels = verifiedMessage.getJSONObject("labels");
            assertEquals(1, labels.getInt("Verified"));
        } finally {
            gerritServer.stop();
        }
    }

    /**
     * Tests a {@link JenkinsRule#configRoundtrip(hudson.model.Job)} on the workflow job.
     *
     * @throws Exception if so.
     */
    @Test
    public void testConfigRoundTrip() throws Exception {
        PatchsetCreated event = Setup.createPatchsetCreated(PluginImpl.DEFAULT_SERVER_NAME);
        WorkflowJob job = createWorkflowJob(event);
        jenkinsRule.configRoundtrip(job);
        job = (WorkflowJob)jenkinsRule.jenkins.getItem("WFJob");
        GerritTrigger trigger = GerritTrigger.getTrigger(job);
        assertFalse(trigger.isSilentMode());
        assertEquals(1, trigger.getGerritBuildSuccessfulCodeReviewValue().intValue());
        assertEquals(1, trigger.getGerritBuildSuccessfulVerifiedValue().intValue());
        assertEquals(0, trigger.getGerritBuildFailedCodeReviewValue().intValue());
        assertThat(trigger.getGerritProjects(), hasItem(
                allOf(
                    isA(GerritProject.class),
                    hasProperty("compareType", is(CompareType.PLAIN)),
                    hasProperty("pattern", equalTo(event.getChange().getProject()))
                )
        ));
    }

    /**
     * Creates a {@link WorkflowJob} with a configured {@link GerritTrigger}.
     *
     * @param event the event to trigger on.
     * @return the job
     * @throws IOException if so
     */
    private WorkflowJob createWorkflowJob(PatchsetCreated event) throws IOException {
        return createWorkflowJob(event, ""
                + "node {\n"
                + "   stage ('Build') {\n"
                + "   sh \"echo Gerrit trigger: ${GERRIT_EVENT_TYPE}\"\n"
                + "}}\n");
    }

    /**
     * Creates a {@link WorkflowJob} with a configured {@link GerritTrigger} and given workflow DSL script.
     *
     * @param event  the event to trigger on.
     * @param script the workflow DSL script.
     * @return the job
     * @throws IOException if so
     */
    private WorkflowJob createWorkflowJob(PatchsetCreated event, String script) throws IOException {
        WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "WFJob");
        job.setDefinition(new CpsFlowDefinition(script));

        GerritTrigger trigger = Setup.createDefaultTrigger(job);
        trigger.setGerritProjects(Collections.singletonList(
                new GerritProject(CompareType.PLAIN, event.getChange().getProject(),
                        Collections.singletonList(new Branch(CompareType.PLAIN, event.getChange().getBranch())),
                        null, null, null, false)
        ));
        trigger.setSilentMode(false);
        trigger.setGerritBuildSuccessfulCodeReviewValue(1);
        trigger.setGerritBuildSuccessfulVerifiedValue(1);
        return job;
    }

    /**
     * Mock Gerrit server.
     */
    @TestExtension
    public static class MockGerritServer extends GerritServer implements RootAction {

        private String lastContent;

        /**
         * Create server instance.
         */
        public MockGerritServer() {
            super(PluginImpl.DEFAULT_SERVER_NAME);
        }

        /**
         * Get the server.
         * @param jenkinsRule The Jenkins rule for the test instance.
         * @return The Mock Gerrit server.
         * @throws IOException if so (as the Gerrit boys seem to say).
         */
        private static MockGerritServer get(JenkinsRule jenkinsRule) throws IOException {
            MockGerritServer mockGerritServer = jenkinsRule.jenkins.getExtensionList(MockGerritServer.class).get(0);
            mockGerritServer.configure(jenkinsRule);
            return mockGerritServer;
        }

        /**
         * Config the server.
         * @param jenkinsRule The Jenkins rule for the test instance.
         * @throws IOException if so (as the Gerrit boys seem to say).
         */
        private void configure(JenkinsRule jenkinsRule) throws IOException {
            PluginImpl.getInstance().addServer(this);
            Config config = (Config)getConfig();
            config.setGerritFrontEndURL(jenkinsRule.getURL().toString() + getUrlName() + "/");
            config.setUseRestApi(true);
            config.setGerritHttpUserName("user");
            config.setGerritHttpPassword("passwd");
            config.setRestCodeReview(true);
            config.setRestVerified(true);
        }

        /**
         * Wait for the Verified label/notification.
         * @return The Verified label/notification.
         */
        private JSONObject waitForNextVerified() {
            long start = System.currentTimeMillis();
            while (lastContent == null) {
                // CS IGNORE MagicNumber FOR NEXT 1 LINES. REASON: It's magic.
                if (System.currentTimeMillis() > start + 20000) {
                    Assert.fail("Timed out waiting on Verified message from Gerrit Trigger plugin.");
                }
                try {
                    // CS IGNORE MagicNumber FOR NEXT 1 LINES. REASON: It's magic.
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Assert.fail("Unexpected interrupt: " + e.getMessage());
                }
            }
            return JSONObject.fromObject(lastContent);
        }

        @Override
        public String getIconFileName() {
            return "gear2.png";
        }

        @Override
        public String getDisplayName() {
            return "Gerrit";
        }

        @Override
        public String getUrlName() {
            return "gerrit";
        }

        /**
         * Retrieves the Gerrit command.
         *
         * @param request  the request
         * @param response the response
         * @throws IOException if so.
         */
        public void doDynamic(StaplerRequest request, StaplerResponse response) throws IOException {
            try {
                this.lastContent = IOUtils.toString(request.getReader());
            } finally {
                response.setContentType("application/json");
                PrintWriter writer = response.getWriter();
                writer.write(new JSONObject(true).toString());
                writer.flush();
            }
        }
    }
}
