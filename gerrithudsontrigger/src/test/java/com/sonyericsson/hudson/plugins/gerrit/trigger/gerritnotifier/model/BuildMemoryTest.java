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

import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.PatchSetKey;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 4 tests of {@link BuildMemory}.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class BuildMemoryTest {

    public BuildMemoryTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Test
    public void testGetMemoryImprint() {
        System.out.println("getMemoryImprint");

        BuildMemory instance = new BuildMemory();
        PatchsetCreated event = Setup.createPatchsetCreated();
        final AbstractProject project = mock(AbstractProject.class);
        instance.triggered(event, project);

        PatchSetKey key = new PatchSetKey(event.getChange().getNumber(), event.getPatchSet().getNumber());

        MemoryImprint result = instance.getMemoryImprint(key);
        assertNotNull(result);
        assertEquals(project, result.getEntries()[0].getProject());
        assertEquals(event, result.getEvent());
    }

    @Test
    public void testIsAllBuildsCompleted_True() {
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

    @Test
    public void testIsAllBuildsCompleted_False() {
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

    @Test
    public void testIsAllBuildsCompleted_BuildMemoryPatchSetKey_True() {
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
        PatchSetKey key = new PatchSetKey(event.getChange().getNumber(), event.getPatchSet().getNumber());
        boolean result = instance.isAllBuildsCompleted(key);
        assertEquals(expResult, result);
    }

    @Test
    public void testIsAllBuildsCompleted_BuildMemoryPatchSetKey_False() {
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
        PatchSetKey key = new PatchSetKey(event.getChange().getNumber(), event.getPatchSet().getNumber());
        boolean result = instance.isAllBuildsCompleted(key);
        assertEquals(expResult, result);
    }

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

        PatchSetKey key = new PatchSetKey(event.getChange().getNumber(), event.getPatchSet().getNumber());
        BuildsStartedStats result = instance.getBuildsStartedStats(key);
        assertEquals(event, result.getEvent());
        assertEquals(4, result.getTotalBuildsToStart());
        assertEquals(2, result.getStartedBuilds());
        assertEquals("(2/4)", result.toString());
    }

    @Test
    public void testIsAllBuildsStarted_PatchsetCreated_True() {
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

    @Test
    public void testIsAllBuildsStarted_PatchsetCreated_False() {
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

    @Test
    public void testIsAllBuildsStarted_BuildMemoryPatchSetKey() {
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
        boolean result = instance.isAllBuildsStarted(
                            new PatchSetKey(event.getChange().getNumber(), event.getPatchSet().getNumber()));
        assertEquals(expResult, result);
    }

    @Test
    public void testCompleted() {
        System.out.println("completed");
        PatchsetCreated event = Setup.createPatchsetCreated();

        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);

        BuildMemory instance = new BuildMemory();
        PatchSetKey expResult = new PatchSetKey(event);
        PatchSetKey result = instance.completed(event, build);
        assertEquals(expResult, result);
        assertTrue(instance.isAllBuildsCompleted(result));
    }

    @Test
    public void testStarted() {
        System.out.println("started");
        PatchsetCreated event = Setup.createPatchsetCreated();

        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);

        BuildMemory instance = new BuildMemory();
        PatchSetKey expResult = new PatchSetKey(event);
        PatchSetKey result = instance.started(event, build);
        assertEquals(expResult, result);
        assertTrue(instance.isAllBuildsStarted(result));
        assertFalse(instance.isAllBuildsCompleted(result));
    }

    @Test
    public void testTriggered() {
        System.out.println("triggered");
        PatchsetCreated event = Setup.createPatchsetCreated();

        AbstractProject project = mock(AbstractProject.class);

        BuildMemory instance = new BuildMemory();
        PatchSetKey expResult = new PatchSetKey(event);
        PatchSetKey result = instance.triggered(event, project);
        assertEquals(expResult, result);
        assertNotNull(instance.getMemoryImprint(result));
        assertFalse(instance.isAllBuildsStarted(result));
        assertFalse(instance.isAllBuildsCompleted(result));
    }

    @Test
    public void testForget() {
        System.out.println("forget");
        PatchsetCreated event = Setup.createPatchsetCreated();

        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);

        BuildMemory instance = new BuildMemory();
        PatchSetKey expResult = new PatchSetKey(event);
        PatchSetKey result = instance.completed(event, build);
        assertEquals(expResult, result);

        instance.forget(result);
        assertNull(instance.getMemoryImprint(result));
    }

}