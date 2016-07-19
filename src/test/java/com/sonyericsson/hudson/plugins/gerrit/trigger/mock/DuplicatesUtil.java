/*
 *  The MIT License
 *
 *  Copyright (c) 2011, 2014 Sony Mobile Communications Inc. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.mock;

import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginCommentAddedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginGerritEvent;

import hudson.model.FreeStyleProject;

import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public abstract class DuplicatesUtil {

    /**
     * Utility constructor.
     */
    private DuplicatesUtil() {

    }

    /**
     * Creates a {@link FreeStyleProject} with a gerrit-trigger configured.
     *
     * @param rule the instance of JenkinsRule.
     * @param projectName the name of the new job.
     * @return the project.
     *
     * @throws Exception if so.
     * @deprecated use {@link TestUtils.JobBuilder} instead.
     */
    @Deprecated
    public static FreeStyleProject createGerritTriggeredJob(JenkinsRule rule, String projectName) throws Exception {
        return createGerritTriggeredJob(rule, projectName, PluginImpl.DEFAULT_SERVER_NAME);
    }

    /**
     * Creates a {@link FreeStyleProject} with a gerrit-trigger configured for a specific server name.
     *
     * @param rule the instance of JenkinsRule.
     * @param projectName the name of the new job.
     * @param serverName of your server
     * @return the project.
     *
     * @throws Exception if so.
     * @deprecated use {@link TestUtils.JobBuilder} instead.
     */
    @Deprecated
    public static FreeStyleProject createGerritTriggeredJob(JenkinsRule rule,
            String projectName, String serverName) throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject(projectName);
        List<GerritProject> projects = new LinkedList<GerritProject>();
        projects.add(new GerritProject(CompareType.ANT, "**",
                Collections.singletonList(new Branch(CompareType.ANT, "**")), null, null, null, false));
        p.addTrigger(new GerritTrigger(projects, null,
                null, null, null, null, null, null, null, null, null, null,
                false, false, true, false, false, null, null, null, null, null, null, null,
                null, serverName, null, null, false, null, null));
        return rule.configRoundtrip(p);
    }

    /**
     * Creates a {@link FreeStyleProject} with a gerrit-trigger dynamically configured.
     *
     * @param rule the instance of JenkinsRule.
     * @param name the name of the new job.
     * @param branchSetting the dynamic branch setting with operator e.g. "^**" or "=branch"
     * @return the project.
     *
     * @throws Exception if so.
     */
    public static FreeStyleProject createGerritDynamicTriggeredJob(JenkinsRule rule, String name,
            String branchSetting) throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject(name);
        List<GerritProject> projects = new LinkedList<GerritProject>();

        File file = File.createTempFile("dynamic", "txt");
        FileWriter fw = new FileWriter(file);
        fw.write("p=project\n");
        fw.write("b" + branchSetting);
        fw.close();
        List<PluginGerritEvent> list = new LinkedList<PluginGerritEvent>();
        URI uri = file.toURI();
        String filepath = uri.toURL().toString();
        GerritTrigger trigger = new GerritTrigger(projects, null,
                null, null, null, null, null, null, null, null, null, null, false, false,
                false, false, false, null, null, null, null, null, null, null, null,
                PluginImpl.DEFAULT_SERVER_NAME, null, list, true, filepath, null);
        p.addTrigger(trigger);
        rule.submit(rule.createWebClient().getPage(p, "configure").getFormByName("config"));
        return p;
    }

    /**
     * Creates a {@link FreeStyleProject} with a gerrit-trigger configured for Code Review +1.
     *
     * @param rule the instance of JenkinsRule.
     * @param name the name of the new job.
     * @return the project.
     *
     * @throws Exception if so.
     */
    public static FreeStyleProject createGerritTriggeredJobForCommentAdded(JenkinsRule rule, String name)
            throws Exception {
        return createGerritTriggeredJobForCommentAdded(rule, name, PluginImpl.DEFAULT_SERVER_NAME);
    }

    /**
     * Creates a {@link FreeStyleProject} with a gerrit-trigger configured with a specific server and Code Review +1.
     *
     * @param rule the instance of JenkinsRule.
     * @param name the name of the new job.
     * @param serverName the name of the GerritServer
     * @return the project.
     *
     * @throws Exception if so.
     */
    public static FreeStyleProject createGerritTriggeredJobForCommentAdded(JenkinsRule rule,
            String name, String serverName)
            throws Exception {
        FreeStyleProject p = rule.createFreeStyleProject(name);
        List<GerritProject> projects = new LinkedList<GerritProject>();
        projects.add(new GerritProject(CompareType.ANT, "**",
                Collections.singletonList(new Branch(CompareType.ANT, "**")), null, null, null, false));
        PluginCommentAddedEvent event = new PluginCommentAddedEvent("Code-Review", "1");
        List<PluginGerritEvent> list = new LinkedList<PluginGerritEvent>();
        list.add(event);
        p.addTrigger(new GerritTrigger(projects, null,
                null, null, null, null, null, null, null, null, null, null,
                false, false, true, false, false, null, null, null, null, null, null, null,
                null, serverName, null, list, false, null, null));
        rule.submit(rule.createWebClient().getPage(p, "configure").getFormByName("config"));
        return p;
    }
}
