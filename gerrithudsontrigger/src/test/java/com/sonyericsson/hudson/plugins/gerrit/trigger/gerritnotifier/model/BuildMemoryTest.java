/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
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

import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

//CS IGNORE MagicNumber FOR NEXT 700 LINES. REASON: test-data.

/**
 * JUnit 4 tests of {@link BuildMemory}.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class BuildMemoryTest {

    /**
     * test.
     */
    @Test
    public void testGetMemoryImprint() {
        System.out.println("getMemoryImprint");

        BuildMemory instance = new BuildMemory();
        PatchsetCreated event = Setup.createPatchsetCreated();
        final AbstractProject project = mock(AbstractProject.class);
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

        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
        instance.completed(event, build);

        project = mock(AbstractProject.class);
        build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
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

        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
        instance.started(event, build);

        project = mock(AbstractProject.class);
        build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
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

        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
        instance.completed(event, build);

        project = mock(AbstractProject.class);
        build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
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

        AbstractProject project = mock(AbstractProject.class);
        instance.triggered(event, project);

        project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
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

        instance.triggered(event, mock(AbstractProject.class));

        AbstractBuild mock = mock(AbstractBuild.class);
        AbstractProject project = mock(AbstractProject.class);
        when(mock.getProject()).thenReturn(project);
        instance.started(event, mock);

        instance.triggered(event, mock(AbstractProject.class));

        mock = mock(AbstractBuild.class);
        project = mock(AbstractProject.class);
        when(mock.getProject()).thenReturn(project);
        instance.started(event, mock);

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

        AbstractBuild mock = mock(AbstractBuild.class);
        AbstractProject project = mock(AbstractProject.class);
        when(mock.getProject()).thenReturn(project);
        instance.started(event, mock);

        mock = mock(AbstractBuild.class);
        project = mock(AbstractProject.class);
        when(mock.getProject()).thenReturn(project);
        instance.started(event, mock);

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

        AbstractBuild mock = mock(AbstractBuild.class);
        AbstractProject project = mock(AbstractProject.class);
        when(mock.getProject()).thenReturn(project);
        instance.started(event, mock);

        instance.triggered(event, mock(AbstractProject.class));

        mock = mock(AbstractBuild.class);
        project = mock(AbstractProject.class);
        when(mock.getProject()).thenReturn(project);
        instance.started(event, mock);

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

        AbstractBuild mock = mock(AbstractBuild.class);
        AbstractProject project = mock(AbstractProject.class);
        when(mock.getProject()).thenReturn(project);
        instance.started(event, mock);

        mock = mock(AbstractBuild.class);
        project = mock(AbstractProject.class);
        when(mock.getProject()).thenReturn(project);
        instance.started(event, mock);

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

        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);

        BuildMemory instance = new BuildMemory();
        instance.completed(event, build);
        assertTrue(instance.isAllBuildsCompleted(event));
    }

    /**
     * test.
     */
    @Test
    public void testStarted() {
        System.out.println("started");
        PatchsetCreated event = Setup.createPatchsetCreated();

        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);

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

        AbstractProject project = mock(AbstractProject.class);

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

        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);

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

        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);

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
        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
        instance.started(event, build);

        PatchsetCreated event2 = Setup.createPatchsetCreated();
        event2.getChange().setNumber(event.getChange().getNumber() + 34);
        project = mock(AbstractProject.class);
        build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
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

        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
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

        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
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

        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);

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

        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);

        BuildMemory instance = new BuildMemory();
        instance.started(event, build);

        PatchsetCreated event2 = Setup.createPatchsetCreated();
        event2.getChange().setNumber(event.getChange().getNumber() + 34);
        AbstractProject project2 = mock(AbstractProject.class);
        build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project2);
        instance.started(event2, build);

        AbstractProject project3 = mock(AbstractProject.class);
        build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project3);
        instance.started(event2, build);

        assertTrue(instance.isBuilding(event, project));
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
        AbstractProject project = mock(AbstractProject.class);
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
        AbstractProject project = mock(AbstractProject.class);
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
        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
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
        AbstractProject project = mock(AbstractProject.class);
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
        AbstractProject project = mock(AbstractProject.class);
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

        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
        AbstractProject project2 = mock(AbstractProject.class);
        AbstractBuild build2 = mock(AbstractBuild.class);
        when(build2.getProject()).thenReturn(project2);
        AbstractProject project3 = mock(AbstractProject.class);
        AbstractBuild build3 = mock(AbstractBuild.class);
        when(build3.getProject()).thenReturn(project3);

        instance.started(event, build);
        instance.completed(event, build2);
        instance.started(event, build3);

        instance.retriggered(event, project2, null);
        MemoryImprint memory = instance.getMemoryImprint(event);
        assertNotNull(memory);
        assertEquals(3, memory.getEntries().length);

        MemoryImprint.Entry entry = null;
        for (MemoryImprint.Entry e : memory.getEntries()) {
            if (e.getProject().equals(project2)) {
                entry = e;
                break;
            }
        }
        assertNotNull(entry);
        assertFalse(entry.isBuildCompleted());
    }
}
