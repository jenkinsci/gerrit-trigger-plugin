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

package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.manual;

import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.manual.TriggerMonitor}.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(AbstractProject.class)
public class TriggerMonitorTest {

    //CS IGNORE LineLength FOR NEXT 4 LINES. REASON: Javadoc
    /**
     * Tests {@link TriggerMonitor#add(com.sonyericsson.hudson.plugins.gerrit.trigger.events.lifecycle.GerritEventLifecycle)}.
     * @throws Exception if so.
     */
    @Test
    public void testAdd() throws Exception {
        TriggerMonitor monitor = new TriggerMonitor();
        ManualPatchsetCreated patch = Setup.createManualPatchsetCreated();
        monitor.add(patch);
        assertEquals(1, monitor.getEvents().size());
        assertSame(patch, monitor.getEvents().get(0).getEvent());
    }

    //CS IGNORE LineLength FOR NEXT 4 LINES. REASON: Javadoc

    /**
     * Tests {@link TriggerMonitor#contains(com.sonyericsson.hudson.plugins.gerrit.trigger.events.lifecycle.GerritEventLifecycle)}
     * when it does.
     * @throws Exception if so.
     */
    @Test
    public void testContains() throws Exception {
        TriggerMonitor monitor = new TriggerMonitor();
        ManualPatchsetCreated patch = Setup.createManualPatchsetCreated();
        monitor.add(patch);
        assertTrue(monitor.contains(patch));
    }

    //CS IGNORE LineLength FOR NEXT 4 LINES. REASON: Javadoc

    /**
     * Tests {@link TriggerMonitor#contains(com.sonyericsson.hudson.plugins.gerrit.trigger.events.lifecycle.GerritEventLifecycle)}
     * when nothing is.
     * @throws Exception if so.
     */
    @SuppressWarnings({"deprecation" })
    @Test
    public void testContainsFalse() throws Exception {
        TriggerMonitor monitor = new TriggerMonitor();
        ManualPatchsetCreated patch = Setup.createManualPatchsetCreated();
        monitor.add(patch);
        ManualPatchsetCreated patch2 = Setup.createManualPatchsetCreated();
        patch2.getChange().setNumber("2");
        assertFalse(monitor.contains(patch2));
    }

    /**
     * Tests {@link TriggerMonitor#triggerScanStarting(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)}
     * when it is.
     * @throws Exception if so.
     */
    @Test
    public void testTriggerScanStarting() throws Exception {
        TriggerMonitor monitor = new TriggerMonitor();
        ManualPatchsetCreated patch = Setup.createManualPatchsetCreated();
        monitor.add(patch);
        monitor.triggerScanStarting(patch);

        TriggerMonitor.EventState state = monitor.getEvents().get(0);

        assertTrue(state.isTriggerScanStarted());
    }

    /**
     * Tests {@link TriggerMonitor#triggerScanStarting(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)}
     * when it is not.
     * @throws Exception if so.
     */
    @Test
    public void testTriggerScanStartingFalse() throws Exception {
        TriggerMonitor monitor = new TriggerMonitor();
        ManualPatchsetCreated patch = Setup.createManualPatchsetCreated();
        monitor.add(patch);

        TriggerMonitor.EventState state = monitor.getEvents().get(0);

        assertFalse(state.isTriggerScanStarted());
    }


    /**
     * Tests {@link TriggerMonitor#triggerScanDone(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)}
     * when it is.
     * @throws Exception if so.
     */
    @Test
    public void testTriggerScanDone() throws Exception {
        TriggerMonitor monitor = new TriggerMonitor();
        ManualPatchsetCreated patch = Setup.createManualPatchsetCreated();
        monitor.add(patch);
        monitor.triggerScanStarting(patch);
        monitor.triggerScanDone(patch);

        TriggerMonitor.EventState state = monitor.getEvents().get(0);

        assertTrue(state.isTriggerScanDone());
    }

    /**
     * Tests {@link TriggerMonitor#triggerScanDone(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)}
     * when it is not.
     * @throws Exception if so.
     */
    @Test
    public void testTriggerScanDoneFalse() throws Exception {
        TriggerMonitor monitor = new TriggerMonitor();
        ManualPatchsetCreated patch = Setup.createManualPatchsetCreated();
        monitor.add(patch);
        monitor.triggerScanStarting(patch);

        TriggerMonitor.EventState state = monitor.getEvents().get(0);

        assertFalse(state.isTriggerScanDone());
    }

    //CS IGNORE LineLength FOR NEXT 4 LINES. REASON: Javadoc

    /**
     * Tests {@link TriggerMonitor#projectTriggered(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent, hudson.model.Job)}.
     * @throws Exception if so.
     */
    @Test
    public void testProjectTriggered() throws Exception {
        TriggerMonitor monitor = new TriggerMonitor();
        ManualPatchsetCreated patch = Setup.createManualPatchsetCreated();
        monitor.add(patch);
        monitor.triggerScanStarting(patch);
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        doReturn("projectX").when(project).getFullName();
        monitor.projectTriggered(patch, project);

        TriggerMonitor.EventState state = monitor.getEvents().get(0);

        assertEquals(1, state.getBuilds().size());
        assertSame(project, state.getBuilds().get(0).getProject());
    }

    /**
     * Tests
     * {@link TriggerMonitor#buildStarted(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent, hudson.model.Run)}.
     * @throws Exception if so.
     */
    @Test
    public void testBuildStarted() throws Exception {
        TriggerMonitor monitor = new TriggerMonitor();
        ManualPatchsetCreated patch = Setup.createManualPatchsetCreated();
        monitor.add(patch);
        monitor.triggerScanStarting(patch);
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        doReturn("projectX").when(project).getFullName();
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
        monitor.projectTriggered(patch, project);
        monitor.buildStarted(patch, build);

        TriggerMonitor.EventState state = monitor.getEvents().get(0);

        assertEquals(1, state.getBuilds().size());
        assertSame(build, state.getBuilds().get(0).getBuild());
        assertSame(project, state.getBuilds().get(0).getProject());
    }

    /**
     * Tests
     * {@link TriggerMonitor#buildCompleted(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent, hudson.model.Run)}.
     * @throws Exception if so.
     */
    @Test
    public void testBuildCompleted() throws Exception {
        TriggerMonitor monitor = new TriggerMonitor();
        ManualPatchsetCreated patch = Setup.createManualPatchsetCreated();
        monitor.add(patch);
        monitor.triggerScanStarting(patch);
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        doReturn("projectX").when(project).getFullName();
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
        monitor.projectTriggered(patch, project);
        monitor.buildStarted(patch, build);
        monitor.buildCompleted(patch, build);

        TriggerMonitor.EventState state = monitor.getEvents().get(0);

        assertEquals(1, state.getBuilds().size());
        assertSame(build, state.getBuilds().get(0).getBuild());
    }

    /**
     * Tests {@link TriggerMonitor#allBuildsCompleted(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)}.
     * @throws Exception if so.
     */
    @Test
    public void testAllBuildsCompleted() throws Exception {
        TriggerMonitor monitor = new TriggerMonitor();
        ManualPatchsetCreated patch = Setup.createManualPatchsetCreated();
        monitor.add(patch);
        monitor.triggerScanStarting(patch);
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        doReturn("projectX").when(project).getFullName();
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
        monitor.projectTriggered(patch, project);
        monitor.triggerScanDone(patch);
        monitor.buildStarted(patch, build);
        monitor.buildCompleted(patch, build);
        monitor.allBuildsCompleted(patch);

        TriggerMonitor.EventState state = monitor.getEvents().get(0);

        assertEquals(1, state.getBuilds().size());
        assertTrue(state.isAllBuildsCompleted());
    }
}
