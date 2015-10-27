/*
 * The MIT License
 *
 * Copyright 2013 Sony Mobile Communications AB. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.job.rest;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.TestUtils;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;

import hudson.model.FreeStyleProject;
import hudson.model.UnprotectedRootAction;
import hudson.util.IOUtils;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests {@link BuildCompletedRestCommandJob}.
 *
 * @author <a href="mailto:robert.sandell@sonymobile.com">Robert Sandell</a>
 */
public class BuildCompletedRestCommandJobHudsonTest {

    /**
     * An instance of Jenkins Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Unlock the instance if secured.
     * @throws Exception if it occurs.
     */
    @Before
    public void unlockInstance() throws Exception {
        Setup.unLock(j);
    }

    /**
     * Guts of the test.
     * @throws Exception if it occurs.
     */
    private void runTest() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        GerritServer server1 = new GerritServer(PluginImpl.DEFAULT_SERVER_NAME);
        PluginImpl.getInstance().addServer(server1);
        Config config = (Config)server1.getConfig();
        config.setGerritFrontEndURL(j.getURL().toString() + "gerrit/");
        config.setUseRestApi(true);
        config.setGerritHttpUserName("user");
        config.setGerritHttpPassword("passwd");
        config.setRestCodeReview(true);
        config.setRestVerified(true);

        server1.start();

        PatchsetCreated event = Setup.createPatchsetCreated(server1.getName());

        FreeStyleProject project = j.createFreeStyleProject();
        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        trigger.setGerritProjects(Collections.singletonList(
                new GerritProject(CompareType.PLAIN, event.getChange().getProject(),
                        Collections.singletonList(new Branch(CompareType.PLAIN, event.getChange().getBranch())),
                        null, null, null, false)
        ));
        trigger.setSilentMode(false);
        trigger.setGerritBuildSuccessfulCodeReviewValue(1);
        trigger.setGerritBuildSuccessfulVerifiedValue(1);

        PluginImpl.getInstance().getHandler().post(event);

        TestUtils.waitForBuilds(project, 1);
        //CS IGNORE MagicNumber FOR NEXT 3 LINES. REASON: TestData
        System.out.println("waiting for post build notification...");
        Thread.sleep(3000);

        FakeHttpGerrit g = getGerrit();
        System.out.println("Path: " + g.lastPath);
        System.out.println("Content: " + g.lastContent);

        assertNotNull(g.lastPath);
        assertNotNull(g.lastContent);
        assertEquals("/a/changes/project~branch~Iddaaddaa123456789/revisions/9999/review", g.lastPath);
        JSONObject json = JSONObject.fromObject(g.lastContent);


        j.assertStringContains(json.getString("message"), "Build Successful");
        JSONObject labels = json.getJSONObject("labels");
        assertEquals(1, labels.getInt("Code-Review"));
        assertEquals(1, labels.getInt("Verified"));
    }
    /**
     * The test with a locked down instance.
     *
     * @throws Exception          if so
     */
    @Test
    public void testItWithSecurity() throws Exception {
        Setup.lockDown(j);
        runTest();
    }

    /**
     * The test.
     *
     * @throws Exception          if so
     */
    @Test
    public void testIt() throws Exception {
        runTest();
    }
    /**
     * Finds the registered {@link FakeHttpGerrit}.
     *
     * @return the root action
     */
    private FakeHttpGerrit getGerrit() {
        return Jenkins.getInstance().getExtensionList(FakeHttpGerrit.class).get(0);
    }

    /**
     * Acts as a fake REST endpoint to receive the REST commands from the command job.
     */
    @TestExtension
    public static class FakeHttpGerrit implements UnprotectedRootAction {

        String lastPath;
        String lastContent;

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
            lastPath = request.getRestOfPath();
            lastContent = IOUtils.toString(request.getReader());

            response.setContentType("application/json");
            PrintWriter writer = response.getWriter();
            writer.write(new JSONObject(true).toString());
            writer.flush();
        }
    }

}
