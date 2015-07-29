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
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.Collections;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class WorkflowTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void test_trigger_workflow() throws Exception {
        jenkinsRule.jenkins.setCrumbIssuer(null);
        GerritServer gerritServer = createGerritServer();

        gerritServer.start();
        try {
            PatchsetCreated event = Setup.createPatchsetCreated(gerritServer.getName());

            WorkflowJob job = jenkinsRule.jenkins.createProject(WorkflowJob.class, "WFJob");

            job.setDefinition(new CpsFlowDefinition("" +
                    "node {\n" +
                    "   stage 'Build'\n " +
                    "   sh \"echo Gerrit trigger: ${GERRIT_EVENT_TYPE}\"\n " +
                    "}\n"));

            GerritTrigger trigger = Setup.createDefaultTrigger(job);
            trigger.setGerritProjects(Collections.singletonList(
                    new GerritProject(CompareType.PLAIN, event.getChange().getProject(),
                            Collections.singletonList(new Branch(CompareType.PLAIN, event.getChange().getBranch())),
                            null, null, null)
            ));
            trigger.setSilentMode(false);
            trigger.setGerritBuildSuccessfulCodeReviewValue(1);
            trigger.setGerritBuildSuccessfulVerifiedValue(1);

            PluginImpl.getInstance().getHandler().post(event);

            TestUtils.waitForBuilds(job, 1);
            WorkflowRun run = job.getBuilds().iterator().next();
            
            jenkinsRule.assertLogContains("Gerrit trigger: patchset-created", run);            
        } finally {
            gerritServer.stop();
        }
    }

    private GerritServer createGerritServer() throws IOException {
        GerritServer server1 = new GerritServer(PluginImpl.DEFAULT_SERVER_NAME);

        PluginImpl.getInstance().addServer(server1);
        Config config = (Config)server1.getConfig();
        config.setGerritFrontEndURL(jenkinsRule.getURL().toString() + "gerrit/");
        config.setUseRestApi(true);
        config.setGerritHttpUserName("user");
        config.setGerritHttpPassword("passwd");
        config.setRestCodeReview(true);
        config.setRestVerified(true);

        return server1;
    }
}
