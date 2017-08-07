package com.sonyericsson.hudson.plugins.gerrit.trigger.dependency;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginGerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventType;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Provider;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import hudson.model.FreeStyleProject;
import hudson.tasks.Shell;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The set of integration tests for dependency feature of Gerrit Trigger Plugin.
 * Can be moved or renamed in future to be more generic.
 */
public class IntegrationTest {
    /**
     * An instance of Jenkins Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule jenkinsRule = new JenkinsRule();

    private static final int TIMEOUT = 10;
    private static final int POLLING_INTERVAL = 1000;

    /**
     * Test that child build see the env variables that contains information about "parent" build.
     * @throws Exception throw if so
     */
    @Test
    public void testChildBuildSeeParametersOfParentJob() throws Exception {
        String gerritServerName = "";
        GerritServer gerritServer = createMockGerritServer(gerritServerName);
        PluginImpl.getInstance().addServer(gerritServer);

        ChangeBasedEvent gerritEvent = createMockChangeBasedEvent(gerritServerName);
        List<GerritProject> projectList = createMockGerritProjectList();
        List<PluginGerritEvent> triggerOnEvents = createMockPluginGerritEventList();

        GerritTrigger parentTrigger = new GerritTrigger(projectList);
        FreeStyleProject parent = createJobWithGerritTrigger("parent", parentTrigger, triggerOnEvents);

        GerritTrigger childTrigger = new GerritTrigger(projectList);
        childTrigger.setDependencyJobsNames(parent.getName());
        FreeStyleProject child = createJobWithGerritTrigger("child", childTrigger, triggerOnEvents);
        child.getBuildersList().add(new Shell("test \"$DEP_parent_BUILD_NAME\" == \"parent\""));


        PluginImpl.getInstance().getHandler().notifyListeners(gerritEvent);

        waitCompletedBuild(parent, TIMEOUT);
        waitCompletedBuild(child, TIMEOUT);

        jenkinsRule.assertBuildStatusSuccess(parent.getLastCompletedBuild());
        jenkinsRule.assertBuildStatusSuccess(child.getLastCompletedBuild());
    }

    /**
     * Wait for the first completed build of specified project.
     * @param project the instance of project
     * @param timeout timeout in seconds
     * @throws InterruptedException throw if so
     */
    private void waitCompletedBuild(FreeStyleProject project, int timeout) throws InterruptedException {
        long timeStarted = System.currentTimeMillis();
        long timeoutMs = TimeUnit.SECONDS.toMillis(timeout);

        while (project.getLastCompletedBuild() == null && System.currentTimeMillis() - timeStarted < timeoutMs) {
            Thread.sleep(POLLING_INTERVAL);
        }
    }

    /**
     * Creates FreeStyleProject with specified Gerrit Trigger
     * and configure it to be triggered on provided list of events.
     *
     * @param name the project name
     * @param trigger the trigger to attach
     * @param triggerOnEvents the list of event to be triggered on
     * @return configured project
     * @throws IOException in case of failure during project creation
     */
    private FreeStyleProject createJobWithGerritTrigger(String name,
                                                        GerritTrigger trigger,
                                                        List<PluginGerritEvent> triggerOnEvents) throws IOException {
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(name);
        project.addTrigger(trigger);
        trigger.setTriggerOnEvents(triggerOnEvents);
        trigger.start(project, true);
        return project;
    }

    /**
     * Creates basic mock for PluginGerritEvent and returns the list that contains exactly one object.
     * @return list with one mocked instance of PluginGerritEvent class
     */
    private List<PluginGerritEvent> createMockPluginGerritEventList() {
        PluginGerritEvent triggeredEvent = mock(PluginGerritEvent.class);
        when(triggeredEvent.shouldTriggerOn(any(GerritTriggeredEvent.class))).thenReturn(true);

        List<PluginGerritEvent> triggerOnEvents = new ArrayList<PluginGerritEvent>(1);
        triggerOnEvents.add(triggeredEvent);
        return triggerOnEvents;
    }

    /**
     * Creates basic mock for GerritProject and returns the list that contains exactly one object.
     * @return list with one mocked instance of GerritProject class
     */
    private List<GerritProject> createMockGerritProjectList() {
        List<GerritProject> projectList = new ArrayList<GerritProject>();

        GerritProject project = mock(GerritProject.class);

        when(project.getCompareType()).thenReturn(CompareType.PLAIN);
        when(project.isInteresting(anyString(), anyString(), anyString())).thenReturn(true);
        projectList.add(project);
        return projectList;
    }

    /**
     * Creates basic mock for Change Based event.
     * @param gerritServerName gerrit server name
     * @return mocked instance of ChangeBasedEvent class
     */
    private ChangeBasedEvent createMockChangeBasedEvent(String gerritServerName) {
        ChangeBasedEvent gerritEvent = mock(ChangeBasedEvent.class);
        Change change = mock(Change.class);
        when(change.getNumber()).thenReturn("1");
        when(gerritEvent.getChange()).thenReturn(change);
        PatchSet patchSet = mock(PatchSet.class);
        when(gerritEvent.getPatchSet()).thenReturn(patchSet);
        when(gerritEvent.getEventType()).thenReturn(GerritEventType.TOPIC_CHANGED);
        when(gerritEvent.getEventCreatedOn()).thenReturn(new Date());
        Provider provider = mock(Provider.class);
        when(provider.getName()).thenReturn(gerritServerName);
        when(gerritEvent.getProvider()).thenReturn(provider);

        when(change.getProject()).thenReturn("mockProject");
        when(change.getId()).thenReturn("mockChange");
        when(change.getBranch()).thenReturn("mockBranch");
        when(patchSet.getNumber()).thenReturn("mockPatchSetNumber");
        when(patchSet.getRevision()).thenReturn("mockRevision");
        when(patchSet.getRef()).thenReturn("mockRefSpec");
        return gerritEvent;
    }

    /**
     * Creates basic mock for Gerrit Server with provided name.
     * @param gerritServerName gerrit server name
     * @return mocked instance of GerritServer class
     */
    private GerritServer createMockGerritServer(String gerritServerName) {
        GerritServer gerritServer = mock(GerritServer.class);
        when(gerritServer.getName()).thenReturn(gerritServerName);
        return gerritServer;
    }
}
