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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import hudson.model.FreeStyleProject;
import hudson.util.IOUtils;
import net.sf.json.JSONObject;
import org.jvnet.hudson.test.HudsonTestCase;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;

/**
 * Tests {@link BuildCompletedRestCommandJob}.
 *
 * @author <a href="mailto:robert.sandell@sonymobile.com">Robert Sandell</a>
 */
public class BuildCompletedRestCommandJobHudsonTest extends HudsonTestCase {

    private String lastPath;
    private String lastContent;

    /**
     * The test.
     *
     * @throws IOException          if so
     * @throws InterruptedException if so.
     */
    public void testIt() throws IOException, InterruptedException {
        jenkins.setCrumbIssuer(null);
        GerritServer server1 = new GerritServer(PluginImpl.DEFAULT_SERVER_NAME);
        PluginImpl.getInstance().addServer(server1);
        Config config = (Config)server1.getConfig();
        config.setGerritFrontEndURL(this.getURL().toString() + "gerrit/");
        config.setUseRestApi(true);
        config.setGerritHttpUserName("user");
        config.setGerritHttpPassword("passwd");

        server1.start();

        PatchsetCreated event = Setup.createPatchsetCreated(server1.getName());

        FreeStyleProject project = createFreeStyleProject();
        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        trigger.setGerritProjects(Collections.singletonList(
                new GerritProject(CompareType.PLAIN, event.getChange().getProject(),
                        Collections.singletonList(new Branch(CompareType.PLAIN, event.getChange().getBranch())),
                        null, null, null)
        ));
        trigger.setSilentMode(false);
        trigger.setGerritBuildSuccessfulCodeReviewValue(1);
        trigger.setGerritBuildSuccessfulVerifiedValue(1);

        PluginImpl.getInstance().getHandler().post(event);

        //CS IGNORE MagicNumber FOR NEXT 9 LINES. REASON: TestData
        long startedWait = System.currentTimeMillis();
        while (project.getLastCompletedBuild() == null) {
            if (System.currentTimeMillis() - startedWait > 10000) {
                throw new RuntimeException("Timeout!");
            }
            System.out.println("waiting for build to complete...");
            Thread.sleep(1000);
        }

        System.out.println("Path: " + lastPath);
        System.out.println("Content: " + lastContent);

        assertNotNull(lastPath);
        assertNotNull(lastContent);
        assertEquals("/a/changes/project~branch~Iddaaddaa123456789/revisions/9999/review", lastPath);
        JSONObject json = JSONObject.fromObject(lastContent);


        assertStringContains(json.getString("message"), "Build Successful");
        JSONObject labels = json.getJSONObject("labels");
        assertEquals(1, labels.getInt("Code-Review"));
        assertEquals(1, labels.getInt("Verified"));

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
