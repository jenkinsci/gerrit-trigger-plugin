/*
 * The MIT License
 *
 * Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 * Copyright 2012 Sony Mobile Communications AB. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier;

import com.sonymobile.tools.gerrit.gerritevents.GerritCmdRunner;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritManualCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.verifyZeroInteractions;
import static org.powermock.api.mockito.PowerMockito.when;


/**
 * Tests for {@link ToGerritRunListener}.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({
        Jenkins.class,
        AbstractProject.class,
        NotificationFactory.class,
        PluginImpl.class,
        ToGerritRunListener.class,

})
public class ToGerritRunListenerTest {

    private GerritNotifier mockNotifier;
    private NotificationFactory mockNotificationFactory;
    private PluginImpl plugin;
    private GerritServer server;
    private Jenkins jenkins;

    /**
     * Creates a new static mock of GerritNotifier before each test.
     *
     * @throws Exception if so.
     */
    @Before
    public void setup() throws Exception {
        jenkins = mock(Jenkins.class);
        mockStatic(Jenkins.class);
        when(Jenkins.getInstance()).thenReturn(jenkins);

        mockStatic(NotificationFactory.class);
        mockStatic(PluginImpl.class);
        mockNotificationFactory = mock(NotificationFactory.class);
        plugin = mock(PluginImpl.class);
        mockNotifier = mock(GerritNotifier.class);
        server = mock(GerritServer.class);
        doReturn(mockNotifier).when(mockNotificationFactory)
                .createGerritNotifier(any(GerritCmdRunner.class), any(String.class));
        when(NotificationFactory.class, "getInstance").thenReturn(mockNotificationFactory);
        when(PluginImpl.class, "getInstance").thenReturn(plugin);
        when(plugin.getServer(PluginImpl.DEFAULT_SERVER_NAME)).thenReturn(server);
        when(server.getName()).thenReturn(PluginImpl.DEFAULT_SERVER_NAME);
    }

    /**
     * Returns a mocked version of an AbstractProject, where getFullName() returns the provided name.
     *
     * @param fullName - the name of the project.
     * @throws Exception if so.
     * @return a mock.
     */
    private AbstractProject mockProject(String fullName) throws Exception {
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        doReturn(fullName).when(project).getFullName();
        when(jenkins.getItemByFullName(eq(fullName), same(AbstractProject.class))).thenReturn(project);
        when(jenkins.getItemByFullName(eq(fullName), same(Job.class))).thenReturn(project);
        return project;
    }

    /**
     * Returns a mocked AbstractBuild. The build will contain a mocked AbstractProject with the provided name and have
     * the provided buildNumber.
     *
     * @param projectFullName the project's name
     * @param buildNumber     the buildNumber.
     * @return a mock.
     * @throws Exception if so.
     */
    private AbstractBuild mockBuild(String projectFullName, int buildNumber) throws Exception {

        AbstractProject project = mockProject(projectFullName);

        String buildId = projectFullName + "#" + buildNumber;
        AbstractBuild build = mock(AbstractBuild.class);
        doReturn(buildId).when(build).getId();
        when(build.getProject()).thenReturn(project);
        when(build.getParent()).thenReturn(project);
        doReturn(build).when(project).getBuild(eq(buildId));
        when(build.getNumber()).thenReturn(buildNumber);
        EnvVars envVars = Setup.createEnvVars();

        doReturn(envVars).when(build).getEnvironment();
        doReturn(envVars).when(build).getEnvironment(any(TaskListener.class));

        Map<String, String> buildVarsMap = new HashMap<String, String>();
        buildVarsMap.put("BUILD_NUM", Integer.toString(buildNumber));
        when(build.getBuildVariables()).thenReturn(buildVarsMap);

        return build;
    }

    /**
     * Tests {@link ToGerritRunListener#onCompleted(hudson.model.Run, hudson.model.TaskListener)}. With a
     * trigger in normal/non-silent mode.
     *
     * @throws Exception if so.
     */
    @Test
    public void testOnCompleted() throws Exception {
        AbstractBuild build = mockBuild("projectX", 2);
        ManualPatchsetCreated event = Setup.createManualPatchsetCreated();
        event = spy(event);
        GerritCause cause = new GerritCause(event, false);
        when(build.getCause(GerritCause.class)).thenReturn(cause);
        CauseAction causeAction = mock(CauseAction.class);
        when(causeAction.getCauses()).thenReturn(Collections.<Cause>singletonList(cause));
        when(build.getAction(CauseAction.class)).thenReturn(causeAction);
        when(build.getResult()).thenReturn(Result.SUCCESS);

        ToGerritRunListener toGerritRunListener = new ToGerritRunListener();
        BuildMemory memory = Whitebox.getInternalState(toGerritRunListener, BuildMemory.class);
        memory.started(event, build);

        toGerritRunListener.onCompleted(build, mock(TaskListener.class));


        verify(event).fireBuildCompleted(same(build));
        verify(event).fireAllBuildsCompleted();
        verify(mockNotificationFactory).queueBuildCompleted(
                any(BuildMemory.MemoryImprint.class), any(TaskListener.class));
    }


    /**
     * Tests {@link ToGerritRunListener#onCompleted(hudson.model.Run, hudson.model.TaskListener)}. With a
     * trigger in silent mode.
     *
     * @throws Exception if so.
     */
    @Test
    public void testOnCompletedSilentMode() throws Exception {
        AbstractBuild build = mockBuild("projectX", 2);
        ManualPatchsetCreated event = Setup.createManualPatchsetCreated();
        event = spy(event);
        GerritCause cause = new GerritCause(event, true);
        when(build.getCause(GerritCause.class)).thenReturn(cause);
        CauseAction causeAction = mock(CauseAction.class);
        when(causeAction.getCauses()).thenReturn(Collections.<Cause>singletonList(cause));
        when(build.getAction(CauseAction.class)).thenReturn(causeAction);
        when(build.getResult()).thenReturn(Result.SUCCESS);

        ToGerritRunListener toGerritRunListener = new ToGerritRunListener();

        toGerritRunListener.onCompleted(build, mock(TaskListener.class));

        verify(event).fireBuildCompleted(same(build));
        verifyZeroInteractions(mockNotifier);
    }

    /**
     * Tests {@link ToGerritRunListener#obtainUnsuccessfulMessage}.
     * File path is not configured.
     *
     * @throws Exception if so.
     */
    @Test
    public void testObtainUnsuccessfulMessageNoFilepathConfigured() throws Exception {
        AbstractBuild build = mockBuild("projectX", 2);
        PatchsetCreated event = spy(Setup.createPatchsetCreated());

        ToGerritRunListener toGerritRunListener = Setup.createFailureMessageRunListener(build, event, null);

        BuildMemory memory = Whitebox.getInternalState(toGerritRunListener, BuildMemory.class);
        memory.started(event, build);
        toGerritRunListener.onCompleted(build, mock(TaskListener.class));

        verify(toGerritRunListener, never()).getMatchingWorkspaceFiles(any(FilePath.class), any(String.class));
        verify(toGerritRunListener, never()).getExpandedContent(any(FilePath.class), any(EnvVars.class));
    }

    /**
     * Tests {@link ToGerritRunListener#obtainUnsuccessfulMessage}.
     * File path is configured, but not files match the glob.
     *
     * @throws Exception if so.
     */
    @Test
    public void testObtainUnsuccessfulMessageNoMatchingFiles() throws Exception {
        AbstractBuild build = mockBuild("projectX", 2);
        FilePath[] fileList = {};
        String filepath = "error-file*.txt";
        PatchsetCreated event = spy(Setup.createPatchsetCreated());

        ToGerritRunListener toGerritRunListener = Setup.createFailureMessageRunListener(build, event, filepath);

        doReturn(fileList).when(toGerritRunListener).getMatchingWorkspaceFiles(any(FilePath.class), eq(filepath));

        BuildMemory memory = Whitebox.getInternalState(toGerritRunListener, BuildMemory.class);
        memory.started(event, build);
        toGerritRunListener.onCompleted(build, mock(TaskListener.class));

        verify(toGerritRunListener, times(1)).getMatchingWorkspaceFiles(any(FilePath.class), eq(filepath));
        verify(toGerritRunListener, never()).getExpandedContent(any(FilePath.class), any(EnvVars.class));
    }

    /**
     * Tests {@link ToGerritRunListener#obtainUnsuccessfulMessage}. Results in a message being retrieved.
     *
     * @throws Exception if so.
     */
    @Test
    public void testObtainUnsuccessfulMessageWithMatchingFiles() throws Exception {
        AbstractBuild build = mockBuild("projectX", 2);
        String filepath = "error-file*.txt";
        String message = "This is the failure";

        FilePath[] fileList = {new FilePath(File.createTempFile("error-file", ".txt"))};

        PatchsetCreated event = spy(Setup.createPatchsetCreated());

        ToGerritRunListener toGerritRunListener = Setup.createFailureMessageRunListener(build, event, filepath);

        doReturn(fileList).when(toGerritRunListener).getMatchingWorkspaceFiles(any(FilePath.class), eq(filepath));
        doReturn(message).when(toGerritRunListener).getExpandedContent(eq(fileList[0]), any(EnvVars.class));

        BuildMemory memory = Whitebox.getInternalState(toGerritRunListener, BuildMemory.class);
        memory.started(event, build);
        toGerritRunListener.onCompleted(build, mock(TaskListener.class));

        verify(toGerritRunListener, times(1)).getMatchingWorkspaceFiles(any(FilePath.class), eq(filepath));
        verify(toGerritRunListener, times(1)).getExpandedContent(any(FilePath.class), any(EnvVars.class));
    }

    /**
     * Tests {@link ToGerritRunListener#onStarted(hudson.model.Run, hudson.model.TaskListener)}. With a
     * trigger in normal/non-silent mode.
     *
     * @throws Exception if so.
     */
    @Test
    public void testOnStarted() throws Exception {
        AbstractBuild build = mockBuild("projectX", 2);
        ManualPatchsetCreated event = Setup.createManualPatchsetCreated();
        event = spy(event);
        GerritCause cause = new GerritCause(event, false);
        when(build.getCause(GerritCause.class)).thenReturn(cause);
        CauseAction causeAction = mock(CauseAction.class);
        when(causeAction.getCauses()).thenReturn(Collections.<Cause>singletonList(cause));
        when(build.getAction(CauseAction.class)).thenReturn(causeAction);

        ToGerritRunListener toGerritRunListener = new ToGerritRunListener();

        toGerritRunListener.onStarted(build, mock(TaskListener.class));

        verify(event).fireBuildStarted(same(build));
        verify(mockNotificationFactory).queueBuildStarted(same(build),
                any(TaskListener.class),
                same(event),
                any(BuildsStartedStats.class));
    }

    /**
     * Tests {@link ToGerritRunListener#onStarted(hudson.model.Run, hudson.model.TaskListener)}. With a
     * trigger in silent mode.
     *
     * @throws Exception if so.
     */
    @Test
    public void testOnStartedSilentMode() throws Exception {
        AbstractBuild build = mockBuild("projectX", 2);
        ManualPatchsetCreated event = Setup.createManualPatchsetCreated();
        event = spy(event);
        GerritCause cause = new GerritCause(event, true);
        when(build.getCause(GerritCause.class)).thenReturn(cause);
        CauseAction causeAction = mock(CauseAction.class);
        when(causeAction.getCauses()).thenReturn(Collections.<Cause>singletonList(cause));
        when(build.getAction(CauseAction.class)).thenReturn(causeAction);

        ToGerritRunListener toGerritRunListener = new ToGerritRunListener();

        toGerritRunListener.onStarted(build, mock(TaskListener.class));

        verify(event).fireBuildStarted(same(build));
        verifyZeroInteractions(mockNotifier);
    }

    /**
     * Tests {@link ToGerritRunListener#onTriggered(hudson.model.Job,
     * com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent)}.
     *
     * @throws Exception if so.
     */
    @Test
    public void testOnTriggered() throws Exception {
        AbstractProject project = mockProject("projectX");
        ManualPatchsetCreated event = Setup.createManualPatchsetCreated();
        event = spy(event);

        ToGerritRunListener toGerritRunListener = new ToGerritRunListener();

        toGerritRunListener.onTriggered(project, event);

        verify(event).fireProjectTriggered(same(project));
    }

    /**
     * Tests {@link ToGerritRunListener#onRetriggered(hudson.model.Job,
     * com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent, java.util.List)}.
     *
     * @throws Exception if so.
     */
    @Test
    public void testOnRetriggered() throws Exception {
        AbstractProject project = mockProject("projectX");
        ManualPatchsetCreated event = Setup.createManualPatchsetCreated();
        event = spy(event);

        ToGerritRunListener toGerritRunListener = new ToGerritRunListener();

        toGerritRunListener.onRetriggered(project, event, null);

        verify(event).fireProjectTriggered(same(project));
    }

    /**
     * Tests {@link ToGerritRunListener#cleanUpGerritCauses(GerritCause, hudson.model.Run)}. With only one
     * cause in the list.
     *
     * @throws Exception if so.
     */
    @Test
    public void testCleanUpGerritCausesOne() throws Exception {
        AbstractBuild build = mockBuild("projectX", 2);
        PatchsetCreated event = Setup.createPatchsetCreated();
        GerritCause cause = new GerritCause(event, true);
        when(build.getCause(GerritCause.class)).thenReturn(cause);
        CauseAction causeAction = mock(CauseAction.class);
        List<Cause> causes = new LinkedList<Cause>();
        causes.add(cause);
        when(causeAction.getCauses()).thenReturn(causes);
        when(build.getAction(CauseAction.class)).thenReturn(causeAction);

        ToGerritRunListener toGerritRunListener = new ToGerritRunListener();

        toGerritRunListener.cleanUpGerritCauses(cause, build);

        assertEquals(1, causes.size());
    }

    /**
     * Tests {@link ToGerritRunListener#cleanUpGerritCauses(GerritCause, hudson.model.Run)}. With three
     * duplicated causes in the list.
     *
     * @throws Exception if so.
     */
    @Test
    public void testCleanUpGerritCausesThree() throws Exception {
        AbstractBuild build = mockBuild("projectX", 2);
        PatchsetCreated event = Setup.createPatchsetCreated();
        GerritCause cause = new GerritCause(event, true);
        when(build.getCause(GerritCause.class)).thenReturn(cause);
        CauseAction causeAction = mock(CauseAction.class);
        List<Cause> causes = new LinkedList<Cause>();
        causes.add(cause);
        causes.add(cause);
        causes.add(cause);
        when(causeAction.getCauses()).thenReturn(causes);
        when(build.getAction(CauseAction.class)).thenReturn(causeAction);

        ToGerritRunListener toGerritRunListener = new ToGerritRunListener();

        toGerritRunListener.cleanUpGerritCauses(cause, build);

        assertEquals(1, causes.size());
    }

    /**
     * Tests {@link ToGerritRunListener#cleanUpGerritCauses(GerritCause, hudson.model.Run)}. With three
     * duplicated causes of different instances in the list.
     *
     * @throws Exception if so.
     */
    @Test
    public void testCleanUpGerritCausesThreeInstances() throws Exception {
        AbstractBuild build = mockBuild("projectX", 2);
        PatchsetCreated event = Setup.createPatchsetCreated();
        GerritCause cause = new GerritCause(event, true);
        when(build.getCause(GerritCause.class)).thenReturn(cause);
        CauseAction causeAction = mock(CauseAction.class);
        List<Cause> causes = new LinkedList<Cause>();
        causes.add(cause);
        causes.add(new GerritCause(event, true));
        causes.add(new GerritCause(event, true));
        when(causeAction.getCauses()).thenReturn(causes);
        when(build.getAction(CauseAction.class)).thenReturn(causeAction);

        ToGerritRunListener toGerritRunListener = new ToGerritRunListener();

        toGerritRunListener.cleanUpGerritCauses(cause, build);

        assertEquals(1, causes.size());
    }

    /**
     * Tests {@link ToGerritRunListener#cleanUpGerritCauses(GerritCause, hudson.model.Run)}. With two
     * duplicated causes and one manual cause in the list.
     *
     * @throws Exception if so.
     */
    @Test
    public void testCleanUpGerritCausesOneManual() throws Exception {
        AbstractBuild build = mockBuild("projectX", 2);
        PatchsetCreated event = Setup.createPatchsetCreated();
        GerritCause cause = new GerritCause(event, true);
        when(build.getCause(GerritCause.class)).thenReturn(cause);
        GerritManualCause manualCause = new GerritManualCause();
        manualCause.setEvent(event);
        manualCause.setSilentMode(true);
        CauseAction causeAction = mock(CauseAction.class);
        List<Cause> causes = new LinkedList<Cause>();
        causes.add(cause);
        causes.add(manualCause);
        causes.add(cause);
        when(causeAction.getCauses()).thenReturn(causes);
        when(build.getAction(CauseAction.class)).thenReturn(causeAction);

        ToGerritRunListener toGerritRunListener = new ToGerritRunListener();

        toGerritRunListener.cleanUpGerritCauses(cause, build);

        assertEquals(2, causes.size());
    }
}
