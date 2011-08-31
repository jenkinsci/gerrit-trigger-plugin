/*
 * The MIT License
 *
 * Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritCmdRunner;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritManualCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.TaskListener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.*;

/**
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(fullyQualifiedNames = {
        "com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.NotificationFactory"
}, value = AbstractProject.class)
public class ToGerritRunListenerTest {

    private GerritNotifier mockNotifier;
    private NotificationFactory mockNotificationFactory;

    /**
     * Creates a new static mock of GerritNotifier before each test.
     *
     * @throws Exception if so.
     */
    @Before
    public void setup() throws Exception {
        PowerMockito.mockStatic(NotificationFactory.class);
        mockNotificationFactory = mock(NotificationFactory.class);
        mockNotifier = mock(GerritNotifier.class);
        doReturn(mockNotifier).when(mockNotificationFactory).createGerritNotifier(any(GerritCmdRunner.class));
        PowerMockito.when(NotificationFactory.class, "getInstance").thenReturn(mockNotificationFactory);
    }

    /**
     * Returns a mocked version of an AbstractProject, where getFullName() returns the provided name.
     *
     * @param fullName - the name of the project.
     * @return a mock.
     */
    private AbstractProject mockProject(String fullName) {
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        doReturn(fullName).when(project).getFullName();
        return project;
    }

    /**
     * Returns a mocked AbstractBuild. The build will contain a mocked AbstractProject with the provided name and have
     * the provided buildNumber.
     *
     * @param projectFullName the project's name
     * @param buildNumber     the buildNumber.
     * @return a mock.
     */
    private AbstractBuild mockBuild(String projectFullName, int buildNumber) {
        System.out.println("mockBuild");
        AbstractProject project = mockProject(projectFullName);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
        when(build.getNumber()).thenReturn(buildNumber);
        return build;
    }

    /**
     * Tests {@link ToGerritRunListener#onCompleted(hudson.model.AbstractBuild, hudson.model.TaskListener)}. With a
     * trigger in normal/non-silent mode.
     *
     * @throws Exception if so.
     */
    @Test
    public void testOnCompleted() throws Exception {
        AbstractBuild build = mockBuild("projectX", 2);
        PatchsetCreated event = Setup.createPatchsetCreated();
        event = spy(event);
        GerritCause cause = new GerritCause(event, false);
        when(build.getCause(GerritCause.class)).thenReturn(cause);
        CauseAction causeAction = mock(CauseAction.class);
        when(causeAction.getCauses()).thenReturn(Collections.<Cause>singletonList(cause));
        when(build.getAction(CauseAction.class)).thenReturn(causeAction);

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
     * Tests {@link ToGerritRunListener#onCompleted(hudson.model.AbstractBuild, hudson.model.TaskListener)}. With a
     * trigger in silent mode.
     *
     * @throws Exception if so.
     */
    @Test
    public void testOnCompletedSilentMode() throws Exception {
        AbstractBuild build = mockBuild("projectX", 2);
        PatchsetCreated event = Setup.createPatchsetCreated();
        event = spy(event);
        GerritCause cause = new GerritCause(event, true);
        when(build.getCause(GerritCause.class)).thenReturn(cause);
        CauseAction causeAction = mock(CauseAction.class);
        when(causeAction.getCauses()).thenReturn(Collections.<Cause>singletonList(cause));
        when(build.getAction(CauseAction.class)).thenReturn(causeAction);

        ToGerritRunListener toGerritRunListener = new ToGerritRunListener();

        toGerritRunListener.onCompleted(build, mock(TaskListener.class));

        verify(event).fireBuildCompleted(same(build));
        verifyZeroInteractions(mockNotifier);
    }

    /**
     * Tests {@link ToGerritRunListener#onStarted(hudson.model.AbstractBuild, hudson.model.TaskListener)}. With a
     * trigger in normal/non-silent mode.
     *
     * @throws Exception if so.
     */
    @Test
    public void testOnStarted() throws Exception {
        AbstractBuild build = mockBuild("projectX", 2);
        PatchsetCreated event = Setup.createPatchsetCreated();
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
     * Tests {@link ToGerritRunListener#onStarted(hudson.model.AbstractBuild, hudson.model.TaskListener)}. With a
     * trigger in silent mode.
     *
     * @throws Exception if so.
     */
    @Test
    public void testOnStartedSilentMode() throws Exception {
        AbstractBuild build = mockBuild("projectX", 2);
        PatchsetCreated event = Setup.createPatchsetCreated();
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
     * Tests {@link ToGerritRunListener#onTriggered(hudson.model.AbstractProject, PatchsetCreated)}.
     *
     * @throws Exception if so.
     */
    @Test
    public void testOnTriggered() throws Exception {
        AbstractProject project = mockProject("projectX");
        PatchsetCreated event = Setup.createPatchsetCreated();
        event = spy(event);

        ToGerritRunListener toGerritRunListener = new ToGerritRunListener();

        toGerritRunListener.onTriggered(project, event);

        verify(event).fireProjectTriggered(same(project));
    }

    /**
     * Tests {@link ToGerritRunListener#onRetriggered(hudson.model.AbstractProject, PatchsetCreated, java.util.List)}.
     *
     * @throws Exception if so.
     */
    @Test
    public void testOnRetriggered() throws Exception {
        AbstractProject project = mockProject("projectX");
        PatchsetCreated event = Setup.createPatchsetCreated();
        event = spy(event);

        ToGerritRunListener toGerritRunListener = new ToGerritRunListener();

        toGerritRunListener.onRetriggered(project, event, null);

        verify(event).fireProjectTriggered(same(project));
    }

    /**
     * Tests {@link ToGerritRunListener#cleanUpGerritCauses(GerritCause, hudson.model.AbstractBuild)}. With only one
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
     * Tests {@link ToGerritRunListener#cleanUpGerritCauses(GerritCause, hudson.model.AbstractBuild)}. With three
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
     * Tests {@link ToGerritRunListener#cleanUpGerritCauses(GerritCause, hudson.model.AbstractBuild)}. With three
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
     * Tests {@link ToGerritRunListener#cleanUpGerritCauses(GerritCause, hudson.model.AbstractBuild)}. With two
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
