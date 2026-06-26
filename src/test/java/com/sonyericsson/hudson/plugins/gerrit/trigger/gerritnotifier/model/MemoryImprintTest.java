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
import hudson.model.Job;
import org.junit.jupiter.api.AfterEach;

import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;


/**
 * Tests {@link BuildMemory.MemoryImprint}.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
class MemoryImprintTest {

    private static int nameCount = 0;
    private AbstractProject project;
    private AbstractBuild build;
    private Jenkins jenkins;
    private MockedStatic<Jenkins> jenkinsMockedStatic;

    /**
     * Setup the mocks, specifically {@link #jenkins}.
     *
     * @see #setup()
     */
    @BeforeEach
    void fullSetup() {
        jenkins = mock(Jenkins.class);
        jenkinsMockedStatic = mockStatic(Jenkins.class);
        jenkinsMockedStatic.when(Jenkins::getInstanceOrNull).thenReturn(jenkins);
        setup();
    }

    @AfterEach
    void tearDown() {
        jenkinsMockedStatic.close();
    }

    /**
     * Sets up the {@link #project} and {@link #build} mocks.
     *
     * This is called from {@link #fullSetup()} but can also
     * be called several times during a test to create more instances.
     */
    void setup() {
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
    }

    /**
     * Tests the reset method of the class {@link BuildMemory.MemoryImprint}.
     * With no previous project.
     */
    @Test
    void testResetNoPreviousProject() {
        BuildMemory.MemoryImprint imprint = new BuildMemory.MemoryImprint(Setup.createPatchsetCreated());
        imprint.reset(project);
        assertEquals(1, imprint.getEntries().length);
        assertEquals(project, imprint.getEntries()[0].getProject());
    }

    /**
     * Tests the reset method of the class {@link BuildMemory.MemoryImprint}.
     * With one previous project provided in the constructor.
     */
    @Test
    void testResetPreviousProject() {
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
    void testResetPreviousBuild() {
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
    void testResetTwoPreviousProjects() {
        AbstractProject project1 = project;
        BuildMemory.MemoryImprint imprint = new BuildMemory.MemoryImprint(Setup.createPatchsetCreated(), project);
        setup();
        AbstractProject project2 = project;
        imprint.set(project2);
        assertEquals(2, imprint.getEntries().length);
        imprint.reset(project1);
        assertEquals(2, imprint.getEntries().length);
    }

    /**
     * Tests the reset method of the class {@link BuildMemory.MemoryImprint}.
     * With two previous builds.
     */
    @Test
    void testResetTwoPreviousBuilds() {
        AbstractProject project1 = project;
        AbstractBuild build1 = build;
        setup();
        AbstractProject project2 = project;
        AbstractBuild build2 = build;

        BuildMemory.MemoryImprint imprint = new BuildMemory.MemoryImprint(Setup.createPatchsetCreated());
        imprint.set(project1, build1);
        imprint.set(project2, build2);
        assertEquals(2, imprint.getEntries().length);

        imprint.reset(project2);
        assertEquals(2, imprint.getEntries().length);
        assertEquals(project2, imprint.getEntries()[1].getProject());
        assertNull(imprint.getEntries()[1].getBuild());
        assertFalse(imprint.getEntries()[0].isBuildCompleted());
    }
}
