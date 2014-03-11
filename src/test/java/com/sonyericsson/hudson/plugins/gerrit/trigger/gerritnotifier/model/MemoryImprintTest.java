/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link BuildMemory.MemoryImprint}.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class MemoryImprintTest {

    /**
     * Tests the reset method of the class {@link BuildMemory.MemoryImprint}.
     * With no previous project.
     */
    @Test
    public void testResetNoPreviousProject() {
        BuildMemory.MemoryImprint imprint = new BuildMemory.MemoryImprint(Setup.createPatchsetCreated());
        AbstractProject project = mock(AbstractProject.class);
        imprint.reset(project);
        assertEquals(1, imprint.getEntries().length);
        assertEquals(project, imprint.getEntries()[0].getProject());
    }

    /**
     * Tests the reset method of the class {@link BuildMemory.MemoryImprint}.
     * With one previous project provided in the constructor.
     */
    @Test
    public void testResetPreviousProject() {
        AbstractProject project = mock(AbstractProject.class);
        BuildMemory.MemoryImprint imprint = new BuildMemory.MemoryImprint(Setup.createPatchsetCreated(), project);
        imprint.reset(project);
        assertEquals(1, imprint.getEntries().length);
        assertEquals(project, imprint.getEntries()[0].getProject());
    }

    /**
     * Tests the reset method of the class {@link BuildMemory.MemoryImprint}.
     * With one previous build provided bu the set method.
     */
    @Test
    public void testResetPreviousBuild() {
        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
        BuildMemory.MemoryImprint imprint = new BuildMemory.MemoryImprint(Setup.createPatchsetCreated());
        imprint.set(project, build);
        assertEquals(1, imprint.getEntries().length);

        imprint.reset(project);
        assertEquals(1, imprint.getEntries().length);
        assertEquals(project, imprint.getEntries()[0].getProject());
        assertNull(imprint.getEntries()[0].getBuild());
        assertFalse(imprint.getEntries()[0].isBuildCompleted());
    }

    /**
     * Tests the reset method of the class {@link BuildMemory.MemoryImprint}.
     * With two previous projects.
     */
    @Test
    public void testResetTwoPreviousProjects() {
        AbstractProject project = mock(AbstractProject.class);
        BuildMemory.MemoryImprint imprint = new BuildMemory.MemoryImprint(Setup.createPatchsetCreated(), project);
        AbstractProject project2 = mock(AbstractProject.class);
        imprint.set(project2);
        assertEquals(2, imprint.getEntries().length);
        imprint.reset(project);
        assertEquals(2, imprint.getEntries().length);
    }

    /**
     * Tests the reset method of the class {@link BuildMemory.MemoryImprint}.
     * With two previous builds.
     */
    @Test
    public void testResetTwoPreviousBuilds() {
        AbstractProject project = mock(AbstractProject.class);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
        AbstractProject project2 = mock(AbstractProject.class);
        AbstractBuild build2 = mock(AbstractBuild.class);
        when(build2.getProject()).thenReturn(project2);
        BuildMemory.MemoryImprint imprint = new BuildMemory.MemoryImprint(Setup.createPatchsetCreated());
        imprint.set(project, build);
        imprint.set(project2, build2);
        assertEquals(2, imprint.getEntries().length);

        imprint.reset(project2);
        assertEquals(2, imprint.getEntries().length);
        assertEquals(project2, imprint.getEntries()[1].getProject());
        assertNull(imprint.getEntries()[1].getBuild());
        assertFalse(imprint.getEntries()[0].isBuildCompleted());
    }
}
