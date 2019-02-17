/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerDescriptor;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.SkipVote;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Result;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

//CS IGNORE MagicNumber FOR NEXT 700 LINES. REASON: test-data.

/**
 * JUnit 4 tests of {@link BuildMemory}.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({Jenkins.class, AbstractProject.class })
public class BuildMemoryTest {

    private static int nameCount = 0;
    AbstractProject project;
    private AbstractBuild build;
    private Jenkins jenkins;
    private HashMap<TriggerDescriptor, Trigger<?>> triggers;
    private GerritTriggerDescriptor descriptor = new GerritTriggerDescriptor();

    /**
     * Setup the mocks, specifically {@link #jenkins}.
     *
     * @see #setup()
     */
    @Before
    public void setupFull() {
        jenkins = mock(Jenkins.class);
        mockStatic(Jenkins.class);
        when(Jenkins.getInstance()).thenReturn(jenkins);
        setup();
    }

    /**
     * Sets up the {@link #project} and {@link #build} mocks.
     *
     * This is called from {@link #setupFull()} but can also
     * be called several times during a test to create more instances.
     */
    public void setup() {
        String name = "MockProject" + (nameCount++);
        String buildId = "b" + nameCount;
        project = mock(AbstractProject.class);
        doReturn(name).when(project).getFullName();
        build = mock(AbstractBuild.class);
        doReturn(buildId).when(build).getId();
        when(build.getProject()).thenReturn(project);
        when(build.getParent()).thenReturn(project);
        doReturn(build).when(project).getBuild(eq(buildId));
        when(jenkins.getItemByFullName(eq(name), same(AbstractProject.class))).thenReturn(project);
        when(jenkins.getItemByFullName(eq(name), same(Job.class))).thenReturn(project);
        triggers = new HashMap<TriggerDescriptor, Trigger<?>>();
        when(project.getTriggers()).thenReturn(triggers);
    }

    /**
     * test.
     */
    @Test
    public void testGetMemoryImprint() {
        System.out.println("getMemoryImprint");

        BuildMemory instance = new BuildMemory();
        PatchsetCreated event = Setup.createPatchsetCreated();

        instance.triggered(event, project);

        MemoryImprint result = instance.getMemoryImprint(event);
        assertNotNull(result);
        assertEquals(project, result.getEntries()[0].getProject());
        assertEquals(event, result.getEvent());
    }

    /**
     * test.
     */
    @Test
    public void testIsAllBuildsCompletedTrue() {
        System.out.println("isAllBuildsCompleted True");
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();

        instance.completed(event, build);

        setup();

        instance.completed(event, build);

        boolean expResult = true;
        boolean result = instance.isAllBuildsCompleted(event);
        assertEquals(expResult, result);
    }

    /**
     * test.
     */
    @Test
    public void testIsAllBuildsCompletedFalse() {
        System.out.println("isAllBuildsCompleted");
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();

        instance.started(event, build);

        setup();
        //New build instance to be completed, old one still started
        instance.completed(event, build);

        boolean expResult = false;
        boolean result = instance.isAllBuildsCompleted(event);
        assertEquals(expResult, result);
    }

    /**
     * test.
     */
    @Test
    public void testIsAllBuildsCompletedBuildMemoryPatchSetKeyTrue() {
        System.out.println("isAllBuildsCompleted True");
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();


        instance.completed(event, build);

        setup();

        instance.completed(event, build);

        boolean expResult = true;
        boolean result = instance.isAllBuildsCompleted(event);
        assertEquals(expResult, result);
    }

    /**
     * test.
     */
    @Test
    public void testIsAllBuildsCompletedBuildMemoryPatchSetKeyFalse() {
        System.out.println("isAllBuildsCompleted True");
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();


        instance.triggered(event, project);

        setup();

        instance.completed(event, build);

        boolean expResult = false;
        boolean result = instance.isAllBuildsCompleted(event);
        assertEquals(expResult, result);
    }

    /**
     * test.
     */
    @Test
    public void testGetBuildsStartedStats() {
        System.out.println("getBuildsStartedStats");

        BuildMemory instance = new BuildMemory();
        PatchsetCreated event = Setup.createPatchsetCreated();

        instance.triggered(event, project);

        setup();
        instance.started(event, build);

        setup();
        instance.triggered(event, project);

        setup();
        instance.started(event, build);

        BuildsStartedStats result = instance.getBuildsStartedStats(event);
        assertEquals(event, result.getEvent());
        //CS IGNORE MagicNumber FOR NEXT 3 LINES. REASON: mock.
        assertEquals(4, result.getTotalBuildsToStart());
        assertEquals(2, result.getStartedBuilds());
        assertEquals("(2/4)", result.toString());
    }

    /**
     * test.
     */
    @Test
    public void testIsAllBuildsStartedPatchsetCreatedTrue() {
        System.out.println("isAllBuildsStarted");
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();

        instance.started(event, build);

        setup();
        instance.started(event, build);

        boolean expResult = true;
        boolean result = instance.isAllBuildsStarted(event);
        assertEquals(expResult, result);
    }

    /**
     * test.
     */
    @Test
    public void testIsAllBuildsStartedPatchsetCreatedFalse() {
        System.out.println("isAllBuildsStarted");
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();

        instance.started(event, build);

        setup();
        instance.triggered(event, project);

        setup();
        instance.started(event, build);

        boolean expResult = false;
        boolean result = instance.isAllBuildsStarted(event);
        assertEquals(expResult, result);
    }

    /**
     * test.
     */
    @Test
    public void testIsAllBuildsStartedBuildMemoryPatchSetKey() {
        System.out.println("isAllBuildsStarted");
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();


        instance.started(event, build);

        setup();
        instance.started(event, build);

        boolean expResult = true;
        boolean result = instance.isAllBuildsStarted(event);
        assertEquals(expResult, result);
    }

    /**
     * test.
     */
    @Test
    public void testCompleted() {
        System.out.println("completed");
        PatchsetCreated event = Setup.createPatchsetCreated();

        BuildMemory instance = new BuildMemory();
        instance.completed(event, build);
        assertTrue(instance.isAllBuildsCompleted(event));
    }


    /**
     * test.
     */
    @Test
    public void testCancelled() {
        System.out.println("cancelled");
        PatchsetCreated event = Setup.createPatchsetCreated();

        BuildMemory instance = new BuildMemory();
        instance.cancelled(event, project);
        assertTrue(instance.isAllBuildsCompleted(event));
    }

    /**
     * test.
     */
    @Test
    public void testStarted() {
        System.out.println("started");
        PatchsetCreated event = Setup.createPatchsetCreated();

        BuildMemory instance = new BuildMemory();
        instance.started(event, build);
        assertTrue(instance.isAllBuildsStarted(event));
        assertFalse(instance.isAllBuildsCompleted(event));
    }

    /**
     * test.
     */
    @Test
    public void testTriggered() {
        System.out.println("triggered");
        PatchsetCreated event = Setup.createPatchsetCreated();

        BuildMemory instance = new BuildMemory();
        instance.triggered(event, project);
        assertNotNull(instance.getMemoryImprint(event));
        assertFalse(instance.isAllBuildsStarted(event));
        assertFalse(instance.isAllBuildsCompleted(event));
    }

    /**
     * test.
     */
    @Test
    public void testForget() {
        System.out.println("forget");
        PatchsetCreated event = Setup.createPatchsetCreated();

        BuildMemory instance = new BuildMemory();
        instance.completed(event, build);

        instance.forget(event);
        assertNull(instance.getMemoryImprint(event));
    }

    /**
     * Tests the isBuilding method of the class {@link BuildMemory}.
     * With one memories.
     */
    @Test
    public void testIsBuildingTrue() {
        PatchsetCreated event = Setup.createPatchsetCreated();

        BuildMemory instance = new BuildMemory();
        instance.started(event, build);
        assertTrue(instance.isBuilding(event));
    }

    /**
     * Tests the isBuilding method of the class {@link BuildMemory}.
     * With two memories.
     */
    @Test
    public void testIsBuildingTrue2() {
        PatchsetCreated event = Setup.createPatchsetCreated();

        BuildMemory instance = new BuildMemory();

        instance.started(event, build);

        PatchsetCreated event2 = Setup.createPatchsetCreated();
        event2.getChange().setNumber(event.getChange().getNumber() + 34);

        setup();
        instance.started(event2, build);
        assertTrue(instance.isBuilding(event));
        assertTrue(instance.isBuilding(event2));
    }

    /**
     * Tests the isBuilding method of the class {@link BuildMemory}.
     * With no memory.
     */
    @Test
    public void testIsBuildingFalse() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();
        assertFalse(instance.isBuilding(event));
    }

    /**
     * Tests the isBuilding method of the class {@link BuildMemory}.
     * With another event in memory.
     */
    @Test
    public void testIsBuildingFalseSomethingElseIs() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();

        PatchsetCreated event2 = Setup.createPatchsetCreated();
        event2.getChange().setNumber(event.getChange().getNumber() + 34);

        instance.started(event2, build);

        assertFalse(instance.isBuilding(event));
    }

    /**
     * Tests the isBuilding method of the class {@link BuildMemory}.
     * With a forgotten build.
     */
    @Test
    public void testIsBuildingFalseWhenForgotten() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();

        instance.started(event, build);
        instance.forget(event);
        assertFalse(instance.isBuilding(event));
    }

    /**
     * Tests the isBuilding method of the class {@link BuildMemory}.
     * With one started project in memory.
     */
    @Test
    public void testIsBuildingProjectTrue() {
        PatchsetCreated event = Setup.createPatchsetCreated();

        BuildMemory instance = new BuildMemory();
        instance.started(event, build);
        assertTrue(instance.isBuilding(event, project));
    }

    /**
     * Tests the isBuilding method of the class {@link BuildMemory}.
     * With two events with started builds in memory.
     */
    @Test
    public void testIsBuildingProjectTrue2() {
        PatchsetCreated event = Setup.createPatchsetCreated();

        BuildMemory instance = new BuildMemory();
        AbstractProject project1 = project;

        instance.started(event, build);

        PatchsetCreated event2 = Setup.createPatchsetCreated();
        event2.getChange().setNumber(event.getChange().getNumber() + 34);
        setup();
        AbstractProject project2 = project;
        instance.started(event2, build);

        setup();
        AbstractProject project3 = project;
        instance.started(event2, build);

        assertTrue(instance.isBuilding(event, project1));
        assertTrue(instance.isBuilding(event2, project2));
        assertTrue(instance.isBuilding(event2, project3));
    }

    /**
     * Tests the isBuilding method of the class {@link BuildMemory}.
     * With an empty memory.
     */
    @Test
    public void testIsBuildingProjectFalse() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();
        assertFalse(instance.isBuilding(event, project));
    }

    /**
     * Tests the isBuilding method of the class {@link BuildMemory}.
     * With a triggered build in memory.
     */
    @Test
    public void testIsBuildingProjectTriggeredTrue() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();
        instance.triggered(event, project);
        assertTrue(instance.isBuilding(event, project));
    }

    /**
     * Tests the isBuilding method of the class {@link BuildMemory}.
     * With a completed build in memory.
     */
    @Test
    public void testIsBuildingProjectCompletedFalse() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();
        when(build.getResult()).thenReturn(Result.UNSTABLE);
        instance.completed(event, build);
        assertFalse(instance.isBuilding(event, project));
    }

    /**
     * Tests the retriggered method of the class {@link BuildMemory}.
     * With no previous memory and an empty list of "others".
     */
    @Test
    public void testRetriggeredNoMemoryOneProject() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();

        instance.retriggered(event, project, Collections.EMPTY_LIST);
        MemoryImprint memory = instance.getMemoryImprint(event);
        assertNotNull(memory);
        assertEquals(1, memory.getEntries().length);
        assertEquals(project, memory.getEntries()[0].getProject());
        assertFalse(memory.getEntries()[0].isBuildCompleted());
    }

    /**
     * Tests the retriggered method of the class {@link BuildMemory}.
     * With no previous memory and null list of "others".
     */
    @Test
    public void testRetriggeredNoMemoryOneProjectNullOthers() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();

        instance.retriggered(event, project, null);
        MemoryImprint memory = instance.getMemoryImprint(event);
        assertNotNull(memory);
        assertEquals(1, memory.getEntries().length);
        assertEquals(project, memory.getEntries()[0].getProject());
        assertFalse(memory.getEntries()[0].isBuildCompleted());
    }

    /**
     * Tests the retriggered method of the class {@link BuildMemory}.
     * With two started builds and the one to be retriggered as completed already in memory.
     */
    @Test
    public void testRetriggeredExistingMemory() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();

        AbstractProject project1 = project;
        AbstractBuild build1 = build;
        setup();
        AbstractProject project2 = project;
        AbstractBuild build2 = build;
        setup();
        AbstractProject project3 = project;
        AbstractBuild build3 = build;

        instance.started(event, build1);
        instance.completed(event, build2);
        instance.started(event, build3);

        instance.retriggered(event, project2, null);
        MemoryImprint memory = instance.getMemoryImprint(event);
        assertNotNull(memory);
        assertEquals(3, memory.getEntries().length);

        MemoryImprint.Entry entry = null;
        for (MemoryImprint.Entry e : memory.getEntries()) {
            if (e.getProject() == project2) {
                entry = e;
                break;
            }
        }
        assertNotNull(entry);
        assertFalse(entry.isBuildCompleted());
    }

    /**
     * Tests a scenario when two builds are successful and one is unstable, but the unstable build is
     * configured to be skipped.
     * Expected outcome is that
     * {@link BuildMemory.MemoryImprint#wereAllBuildsSuccessful()} will return true.
     */
    @Test
    public void testWereAllBuildsSuccessfulOneUnstableSkipped() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();

        AbstractBuild build1 = build;
        SkipVote skipVote = new SkipVote(false, false, false, false, false);
        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        when(build.getResult()).thenReturn(Result.SUCCESS);
        instance.started(event, build);

        setup();
        AbstractBuild build2 = build;
        when(build.getResult()).thenReturn(Result.SUCCESS);
        skipVote = new SkipVote(false, false, false, false, false);
        trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        instance.started(event, build2);

        setup();
        AbstractBuild build3 = build;
        when(build.getResult()).thenReturn(Result.UNSTABLE);
        skipVote = new SkipVote(false, false, true, false, false);
        trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        instance.started(event, build3);

        instance.completed(event, build1);
        instance.completed(event, build2);
        instance.completed(event, build3);

        assertTrue(instance.getMemoryImprint(event).wereAllBuildsSuccessful());

    }

    /**
     * Tests a scenario when two builds are successful and one is failed, but the failed build is
     * configured to be skipped.
     * Expected outcome is that
     * {@link BuildMemory.MemoryImprint#wereAllBuildsSuccessful()} will return true.
     */
    @Test
    public void testWereAllBuildsSuccessfulOneFailedSkipped() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();

        SkipVote skipVote = new SkipVote(false, false, false, false, false);
        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        AbstractBuild build1 = build;
        when(build.getResult()).thenReturn(Result.SUCCESS);
        instance.started(event, build);

        setup();
        skipVote = new SkipVote(false, false, false, false, false);
        trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        AbstractBuild build2 = build;
        when(build2.getResult()).thenReturn(Result.SUCCESS);
        instance.started(event, build2);

        setup();
        skipVote = new SkipVote(false, true, true, false, false);
        trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        AbstractBuild build3 = build;
        when(build3.getResult()).thenReturn(Result.FAILURE);
        instance.started(event, build3);

        instance.completed(event, build1);
        instance.completed(event, build2);
        instance.completed(event, build3);

        assertTrue(instance.getMemoryImprint(event).wereAllBuildsSuccessful());

    }

    /**
     * Tests a scenario when one build is successful, one is failed, and one is unstable,
     * but the failed and unstable builds are configured to be skipped.
     * Expected outcome is that
     * {@link BuildMemory.MemoryImprint#wereAllBuildsSuccessful()} will return true.
     */
    @Test
    public void testWereAllBuildsSuccessfulOneUnstableOneFailedBothSkippedOneSuccessful() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();

        SkipVote skipVote = new SkipVote(false, false, false, false, false);
        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        AbstractBuild build1 = build;
        when(build.getResult()).thenReturn(Result.SUCCESS);
        instance.started(event, build);

        setup();
        skipVote = new SkipVote(false, false, true, false, false);
        trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        AbstractBuild build2 = build;
        when(build2.getResult()).thenReturn(Result.UNSTABLE);
        instance.started(event, build2);

        setup();
        skipVote = new SkipVote(false, true, true, false, false);
        trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        AbstractBuild build3 = build;
        when(build3.getResult()).thenReturn(Result.FAILURE);
        instance.started(event, build3);

        instance.completed(event, build1);
        instance.completed(event, build2);
        instance.completed(event, build3);

        assertTrue(instance.getMemoryImprint(event).wereAllBuildsSuccessful());

    }

    /**
     * Tests a scenario when two builds are unstable and one is successful, the successful build is
     * configured to be skipped.
     * Expected outcome is that
     * {@link BuildMemory.MemoryImprint#wereAllBuildsSuccessful()} will return false and
     * {@link BuildMemory.MemoryImprint#wereAnyBuildsUnstable()} will return true.
     * As before the skip vote feature was implemented.
     */
    @Test
    public void testWereAllBuildsSuccessfulUnstableOneSuccessfulSkipped() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();

        SkipVote skipVote = new SkipVote(false, false, false, false, false);
        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        AbstractBuild build1 = build;
        when(build.getResult()).thenReturn(Result.UNSTABLE);
        instance.started(event, build);

        setup();
        skipVote = new SkipVote(false, false, false, false, false);
        trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        AbstractBuild build2 = build;
        when(build2.getResult()).thenReturn(Result.UNSTABLE);
        instance.started(event, build2);

        setup();
        skipVote = new SkipVote(true, false, false, false, false);
        trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        AbstractBuild build3 = build;
        when(build3.getResult()).thenReturn(Result.SUCCESS);
        instance.started(event, build3);

        instance.completed(event, build1);
        instance.completed(event, build2);
        instance.completed(event, build3);

        MemoryImprint memoryImprint = instance.getMemoryImprint(event);
        assertFalse(memoryImprint.wereAllBuildsSuccessful());
        assertTrue(memoryImprint.wereAnyBuildsUnstable());

    }

    /**
     * Tests a scenario when two builds are successful and one is unstable, one of the successful builds is
     * configured to be skipped.
     * Expected outcome is that
     * {@link BuildMemory.MemoryImprint#wereAllBuildsSuccessful()} will return false and
     * {@link BuildMemory.MemoryImprint#wereAnyBuildsUnstable()} will return true.
     * As before the skip vote feature was implemented.
     */
    @Test
    public void testWereAllBuildsSuccessfulUnstableTwoSuccessfulOneSkipped() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();

        SkipVote skipVote = new SkipVote(true, false, false, false, false);
        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        AbstractBuild build1 = build;
        when(build.getResult()).thenReturn(Result.SUCCESS);
        instance.started(event, build);

        setup();
        skipVote = new SkipVote(false, false, false, false, false);
        trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        AbstractBuild build2 = build;
        when(build2.getResult()).thenReturn(Result.UNSTABLE);
        instance.started(event, build2);

        setup();
        skipVote = new SkipVote(false, false, false, false, false);
        trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        AbstractBuild build3 = build;
        when(build3.getResult()).thenReturn(Result.SUCCESS);
        instance.started(event, build3);

        instance.completed(event, build1);
        instance.completed(event, build2);
        instance.completed(event, build3);

        MemoryImprint memoryImprint = instance.getMemoryImprint(event);
        assertFalse(memoryImprint.wereAllBuildsSuccessful());
        assertTrue(memoryImprint.wereAnyBuildsUnstable());
    }

    /**
     * Tests a scenario when one build is successful and configured to be skipped.
     * Expected outcome is that
     * {@link BuildMemory.MemoryImprint#wereAllBuildsSuccessful()} will return true
     * As before the skip vote feature was implemented.
     */
    @Test
    public void testWereAllBuildsSuccessfulOneSuccessfulAndSkipped() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();

        SkipVote skipVote = new SkipVote(true, false, false, false, false);
        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        when(build.getResult()).thenReturn(Result.SUCCESS);
        instance.started(event, build);

        instance.completed(event, build);

        MemoryImprint memoryImprint = instance.getMemoryImprint(event);
        assertTrue(memoryImprint.wereAllBuildsSuccessful());
    }

    /**
     * Tests a scenario when one build is unstable and configured to be skipped.
     * Expected outcome is that
     * {@link BuildMemory.MemoryImprint#wereAllBuildsSuccessful()} will return false and
     * {@link BuildMemory.MemoryImprint#wereAnyBuildsUnstable()} will return true.
     * As before the skip vote feature was implemented.
     */
    @Test
    public void testWereAllBuildsSuccessfulOneUnstableAndSkipped() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();

        SkipVote skipVote = new SkipVote(false, false, true, false, false);
        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        when(build.getResult()).thenReturn(Result.UNSTABLE);
        instance.started(event, build);

        instance.completed(event, build);

        MemoryImprint memoryImprint = instance.getMemoryImprint(event);
        assertFalse(memoryImprint.wereAllBuildsSuccessful());
        assertTrue(memoryImprint.wereAnyBuildsUnstable());
    }

    /**
     * Tests a scenario when two builds are unstable and both configured to be skipped.
     * Expected outcome is that
     * {@link BuildMemory.MemoryImprint#wereAllBuildsSuccessful()} will return false and
     * {@link BuildMemory.MemoryImprint#wereAnyBuildsUnstable()} will return true.
     * As before the skip vote feature was implemented.
     */
    @Test
    public void testWereAllBuildsSuccessfulTwoUnstableBothSkipped() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();

        SkipVote skipVote = new SkipVote(true, false, true, false, false);
        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        AbstractBuild build1 = build;
        when(build.getResult()).thenReturn(Result.UNSTABLE);
        instance.started(event, build);

        setup();
        skipVote = new SkipVote(false, false, true, false, false);
        trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        AbstractBuild build2 = build;
        when(build2.getResult()).thenReturn(Result.UNSTABLE);
        instance.started(event, build2);

        instance.completed(event, build1);
        instance.completed(event, build2);

        MemoryImprint memoryImprint = instance.getMemoryImprint(event);
        assertFalse(memoryImprint.wereAllBuildsSuccessful());
        assertTrue(memoryImprint.wereAnyBuildsUnstable());
    }

    /**
     * Tests a scenario when two builds are successful and both configured to be skipped.
     * Expected outcome is that
     * {@link BuildMemory.MemoryImprint#wereAllBuildsSuccessful()} will return true.
     * As before the skip vote feature was implemented.
     */
    @Test
    public void testWereAllBuildsSuccessfulTwoSuccessfulBothSkipped() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory instance = new BuildMemory();

        SkipVote skipVote = new SkipVote(true, false, false, false, false);
        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        AbstractBuild build1 = build;
        when(build.getResult()).thenReturn(Result.SUCCESS);
        instance.started(event, build);

        setup();
        skipVote = new SkipVote(true, false, false, false, false);
        trigger = mock(GerritTrigger.class);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        when(project.getTrigger(eq(GerritTrigger.class))).thenReturn(trigger);
        triggers.put(descriptor, trigger);
        AbstractBuild build2 = build;
        when(build2.getResult()).thenReturn(Result.SUCCESS);
        instance.started(event, build2);

        instance.completed(event, build1);
        instance.completed(event, build2);

        MemoryImprint memoryImprint = instance.getMemoryImprint(event);
        assertTrue(memoryImprint.wereAllBuildsSuccessful());
    }
}
