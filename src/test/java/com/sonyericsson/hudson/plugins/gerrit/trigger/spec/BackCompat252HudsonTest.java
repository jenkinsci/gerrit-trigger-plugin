/*
 * The MIT License
 *
 * Copyright 2012, 2013 Sony Mobile Communications AB. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.spec;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.BadgeAction;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritManualCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.RetriggerAction;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginGerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.TestUtils;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventType;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.DraftPublished;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.TopLevelItem;
import hudson.util.RunList;
import org.apache.sshd.server.SshServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests to see if jobs from Gerrit-Trigger v. 2.5.2 can be loaded.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@WithJenkins
class BackCompat252HudsonTest {

    /**
     * The stream-events command.
     */
    protected static final String GERRIT_STREAM_EVENTS = "gerrit stream-events";
    /**
     * An instance of Jenkins Rule.
     */
    private JenkinsRule j;
    private SshServer sshd;
    private SshdServerMock server;

    /**
     * Starts up the sshd server.
     *
     * @param rule the jenkins rule
     *
     * @throws Exception if so
     */
    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        SshdServerMock.generateKeyPair();

        server = new SshdServerMock();
        sshd = SshdServerMock.startServer(server);
        GerritServer gerritServer = PluginImpl.getFirstServer_();
        assertNotNull(gerritServer);
        gerritServer.stopConnection();
        SshdServerMock.configureFor(sshd, gerritServer);
        gerritServer.startConnection();
        server.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor(GERRIT_STREAM_EVENTS, SshdServerMock.CommandMock.class);
        server.returnCommandFor("gerrit review.*", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor("gerrit version", SshdServerMock.EofCommandMock.class);
        SshdServerMock.configureFor(sshd, PluginImpl.getFirstServer_());
    }

    /**
     * Shuts down the sshd server.
     *
     * @throws Exception if so
     */
    @AfterEach
    void tearDown() throws Exception {
        server.stopServer(sshd);
        sshd = null;
        server = null;
    }

    /**
     * Tests that the FreeStyleProject can be loaded.
     */
    @Test
    @LocalData
    void testFreeStyleJob() {
        TopLevelItem freestyleJobItem = j.jenkins.getItem("freestyleJob");
        assertThat(freestyleJobItem, instanceOf(AbstractProject.class));
        AbstractProject freestyleJob = (AbstractProject)freestyleJobItem;
        assertNotNull(freestyleJob);
        GerritTrigger trigger = GerritTrigger.getTrigger(freestyleJob);
        assertNotNull(trigger);
        List<GerritProject> gerritProjects = trigger.getGerritProjects();
        assertNotNull(gerritProjects);
        assertThat(gerritProjects.size(), equalTo(1));
        GerritProject gerritProject = gerritProjects.get(0);
        assertThat(gerritProject.getPattern(), equalTo("project"));
        List<Branch> branches = gerritProject.getBranches();
        assertThat(branches.size(), equalTo(1));
        Branch branch = branches.get(0);
        assertThat(branch.getPattern(), equalTo("bra.*"));
        List<PluginGerritEvent> triggerOnEvents = trigger.getTriggerOnEvents();
        assertThat(triggerOnEvents.size(), equalTo(2));
        assertSame(PatchsetCreated.class, triggerOnEvents.get(0).getCorrespondingEventClass());
        assertSame(DraftPublished.class, triggerOnEvents.get(1).getCorrespondingEventClass());
    }

    /**
     * Tests that the MatrixProject can be loaded.
     */
    @Test
    @LocalData
    void testMatrixJob() {
        TopLevelItem matrixJobItem = j.jenkins.getItem("matrixJob");
        assertThat(matrixJobItem, instanceOf(AbstractProject.class));
        AbstractProject matrixJob = (AbstractProject)matrixJobItem;
        assertNotNull(matrixJob);
        GerritTrigger trigger = GerritTrigger.getTrigger(matrixJob);
        assertNotNull(trigger);
        List<GerritProject> gerritProjects = trigger.getGerritProjects();
        assertNotNull(gerritProjects);
        assertThat(gerritProjects.size(), equalTo(1));
        GerritProject gerritProject = gerritProjects.get(0);
        assertThat(gerritProject.getPattern(), equalTo("project"));
        List<Branch> branches = gerritProject.getBranches();
        assertThat(branches.size(), equalTo(1));
        Branch branch = branches.get(0);
        assertThat(branch.getPattern(), equalTo(".*er"));
        List<PluginGerritEvent> triggerOnEvents = trigger.getTriggerOnEvents();
        assertThat(triggerOnEvents.size(), equalTo(2));
        assertSame(PatchsetCreated.class, triggerOnEvents.get(0).getCorrespondingEventClass());
        assertSame(DraftPublished.class, triggerOnEvents.get(1).getCorrespondingEventClass());
    }

    /**
     * Tests that the builds from the FreeStyleProject can be loaded.
     */
    @Test
    @LocalData
    void testFreeStyleBuild() {
        Item item = j.jenkins.getItem("freestyleJob");
        assertThat("Item is not a FreeStyleProject", item, instanceOf(FreeStyleProject.class));
        FreeStyleProject proj = (FreeStyleProject)item;
        RunList<FreeStyleBuild> builds = proj.getBuilds();
        assertNotNull(builds);
        assertFalse(builds.isEmpty(), "The build list should not be empty");
        FreeStyleBuild freeStyleBuild = proj.getFirstBuild();
        assertNotNull(freeStyleBuild.getAction(RetriggerAction.class));
        GerritManualCause cause = freeStyleBuild.getCause(GerritManualCause.class);
        assertNotNull(cause);
        BadgeAction action = freeStyleBuild.getAction(BadgeAction.class);
        assertNotNull(action);
        GerritTriggeredEvent event = action.getEvent();
        assertNotNull(event);
        GerritEventType eventType = event.getEventType();
        assertSame(PatchsetCreated.class, eventType.getEventRepresentative());
    }

    /**
     * Tests that the builds from the MatrixProject can be loaded.
     */
    @Test
    @LocalData
    void testMatrixBuild() {
        Item item = j.jenkins.getItem("matrixJob");
        assertThat("Item is not a MatrixProject", item, instanceOf(MatrixProject.class));
        MatrixProject proj = (MatrixProject)item;
        RunList<MatrixBuild> builds = proj.getBuilds();
        assertNotNull(builds);
        assertFalse(builds.isEmpty(), "The build list should not be empty");
        MatrixBuild matrixBuild = proj.getFirstBuild();
        assertNotNull(matrixBuild.getAction(RetriggerAction.class));
        GerritManualCause cause = matrixBuild.getCause(GerritManualCause.class);
        assertNotNull(cause);
        BadgeAction action = matrixBuild.getAction(BadgeAction.class);
        assertNotNull(action);
    }

    //CS IGNORE MagicNumber FOR NEXT 20 LINES. REASON: Testdata

    /**
     * Tests that a new build can be triggered for the old FreeStyleProject.
     */
    @Test
    @LocalData
    void testNewTriggeredBuild() {
        server.waitForCommand(GERRIT_STREAM_EVENTS, 10000);
        Item item = j.jenkins.getItem("freestyleJob");
        assertThat("Item is not a FreeStyleProject", item, instanceOf(FreeStyleProject.class));
        FreeStyleProject project = (FreeStyleProject)item;
        int number = project.getLastBuild().getNumber() + 1;
        PluginImpl.getServer_(PluginImpl.DEFAULT_SERVER_NAME).triggerEvent(Setup.createPatchsetCreated());
        TestUtils.waitForBuilds(project, number, 20000);
        //3 old builds + the new one.
        assertEquals(number, project.getLastCompletedBuild().getNumber());
        assertSame(Result.SUCCESS, project.getLastCompletedBuild().getResult());
    }

}
