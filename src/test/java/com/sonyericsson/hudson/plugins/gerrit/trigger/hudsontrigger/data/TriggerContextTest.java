/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved..
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//CS IGNORE MagicNumber FOR NEXT 500 LINES. REASON: Testdata.

/**
 * Tests the TriggerContext's public methods.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(AbstractProject.class)
public class TriggerContextTest {

    /**
     * Returns a mocked version of an AbstractProject, where getFullName() returns the provided name.
     * @param fullName - the name of the project.
     * @return a mock.
     */
    private AbstractProject mockProject(String fullName) {
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullName()).thenReturn(fullName);
        return project;
    }

    /**
     * Returns a mocked AbstractBuild.
     * The build will contain a mocked AbstractProject with the provided name
     * and have the provided buildNumber.
     * @param projectFullName the project's name
     * @param buildNumber the buildNumber.
     * @return a mock.
     */
    private AbstractBuild mockBuild(String projectFullName, int buildNumber) {
        AbstractProject project = mockProject(projectFullName);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getProject()).thenReturn(project);
        when(build.getNumber()).thenReturn(buildNumber);
        return build;
    }

    /**
     * Test of getOthers method, of class TriggerContext.
     */
    @Test
    public void testGetOthers() {
        TriggerContext context = new TriggerContext(mockBuild("projectX", 1), null,
                Arrays.asList(new TriggeredItemEntity(1, "projectY")));
        assertNotNull(context.getOthers());
        assertEquals(1, context.getOthers().size());
    }

    /**
     * Test of setOthers method, of class TriggerContext.
     */
    @Test
    public void testSetOthers() {
        TriggerContext context = new TriggerContext();
        context.setOthers(Arrays.asList(new TriggeredItemEntity(1, "projectY")));
        assertNotNull(context.getOthers());
        assertEquals(1, context.getOthers().size());
    }

    /**
     * Test of getThisBuild method, of class TriggerContext.
     */
    @Test
    public void testGetThisBuild() {
        AbstractBuild build = mockBuild("myProject", 1);
        TriggerContext context = new TriggerContext(build, null, null);
        assertNotNull(context.getThisBuild());
        assertEquals(Integer.valueOf(1), context.getThisBuild().getBuildNumber());
        assertEquals("myProject", context.getThisBuild().getProjectId());
    }

    /**
     * Test of setThisBuild method, of class TriggerContext.
     */
    @Test
    public void testSetThisBuildTriggerContextWrap() {
        TriggerContext context = new TriggerContext();
        TriggeredItemEntity wrap = new TriggeredItemEntity(1, "myProject");
        context.setThisBuild(wrap);
        assertNotNull(context.getThisBuild());
        assertEquals(Integer.valueOf(1), context.getThisBuild().getBuildNumber());
        assertEquals("myProject", context.getThisBuild().getProjectId());
    }

    /**
     * Test of setThisBuild method, of class TriggerContext.
     */
    @Test
    public void testSetThisBuildAbstractBuild() {
        TriggerContext context = new TriggerContext();
        AbstractBuild build = mockBuild("myProject", 1);
        context.setThisBuild(build);
        assertNotNull(context.getThisBuild());
        assertEquals(Integer.valueOf(1), context.getThisBuild().getBuildNumber());
        assertEquals("myProject", context.getThisBuild().getProjectId());
    }

    /**
     * Test of addOtherBuild method, of class TriggerContext.
     */
    @Test
    public void testAddOtherBuild() {
        TriggerContext context = new TriggerContext();
        AbstractBuild build = mockBuild("myProject", 1);
        context.addOtherBuild(build);
        assertNotNull(context.getOthers());
        assertEquals(1, context.getOthers().size());
    }

    /**
     * Test of addOtherBuild method with two builds, of class TriggerContext.
     */
    @Test
    public void testAddTwoOtherBuilds() {
        TriggerContext context = new TriggerContext();
        AbstractBuild build = mockBuild("myProject", 1);
        context.addOtherBuild(build);
        build = mockBuild("myProjectY", 43);
        context.addOtherBuild(build);
        assertNotNull(context.getOthers());
        assertEquals(2, context.getOthers().size());
    }

    /**
     * Test of addOtherBuild method with two builds that are the same, of class TriggerContext.
     */
    @Test
    public void testAddTwoOtherBuildsOfSameType() {
        TriggerContext context = new TriggerContext();
        AbstractBuild build = mockBuild("myProject", 1);
        context.addOtherBuild(build);
        build = mockBuild("myProject", 1);
        context.addOtherBuild(build);
        assertNotNull(context.getOthers());
        assertEquals(1, context.getOthers().size());
    }

    /**
     * Test of addOtherProject method, of class TriggerContext.
     */
    @Test
    public void testAddOtherProject() {
        TriggerContext context = new TriggerContext();
        AbstractProject project = mockProject("myProject");
        context.addOtherProject(project);
        assertNotNull(context.getOthers());
        assertEquals(1, context.getOthers().size());
    }

    /**
     * Test of addOtherProject method, of class TriggerContext.
     */
    @Test
    public void testAddTwoOtherProjects() {
        TriggerContext context = new TriggerContext();
        AbstractProject project = mockProject("myProject");
        context.addOtherProject(project);
        project = mockProject("myProjectY");
        context.addOtherProject(project);
        assertNotNull(context.getOthers());
        assertEquals(2, context.getOthers().size());
    }

    /**
     * Test of addOtherProject method, of class TriggerContext.
     */
    @Test
    public void testAddTwoOtherProjectsWithSameName() {
        TriggerContext context = new TriggerContext();
        AbstractProject project = mockProject("myProject");
        context.addOtherProject(project);
        project = mockProject("myProject");
        context.addOtherProject(project);
        assertNotNull(context.getOthers());
        assertEquals(1, context.getOthers().size());
    }

    /**
     * Test of addOtherProject method, of class TriggerContext.
     */
    @Test
    public void testAddTwoOtherProjectsAndOneBuild() {
        TriggerContext context = new TriggerContext();
        AbstractProject project = mockProject("myProject");
        context.addOtherProject(project);
        project = mockProject("myProjectY");
        context.addOtherProject(project);
        AbstractBuild build = mockBuild("myProjectZ", 2);
        context.addOtherBuild(build);
        assertNotNull(context.getOthers());
        assertEquals(3, context.getOthers().size());
    }

    /**
     * Test of addOtherProject method, of class TriggerContext.
     */
    @Test
    public void testAddTwoOtherProjectsAndOneBuildOfSameProject() {
        TriggerContext context = new TriggerContext();
        AbstractProject project = mockProject("myProject");
        context.addOtherProject(project);
        project = mockProject("myProjectY");
        context.addOtherProject(project);
        AbstractBuild build = mockBuild("myProject", 2);
        context.addOtherBuild(build);
        assertNotNull(context.getOthers());
        assertEquals(2, context.getOthers().size());
    }

    /**
     * Test of hasOthers method with others, of class TriggerContext.
     */
    @Test
    public void testHasOthersTrue() {
        TriggerContext context = new TriggerContext();
        AbstractProject project = mockProject("myProject");
        context.addOtherProject(project);
        assertTrue(context.hasOthers());
    }

    /**
     * Test of hasOthers method with null others, of class TriggerContext.
     */
    @Test
    public void testHasOthersFalseNull() {
        TriggerContext context = new TriggerContext();
        assertFalse(context.hasOthers());
    }

    /**
     * Test of hasOthers method with no others, of class TriggerContext.
     */
    @Test
    public void testHasOthersFalse() {
        TriggerContext context = new TriggerContext(mockBuild("p", 2), null, new LinkedList<TriggeredItemEntity>());
        assertFalse(context.hasOthers());
    }

    /**
     * Test of getOtherBuilds method with no others, of class TriggerContext.
     * With an empty list of "others".
     */
    @Test
    public void testGetOtherBuilds() {
        TriggerContext context = new TriggerContext(mockBuild("p", 2), null, new LinkedList<TriggeredItemEntity>());
        List<Run> others = context.getOtherBuilds();
        assertNotNull(others);
        assertEquals(0, others.size());
    }

    /**
     * Test of getOtherBuilds method with no others, of class TriggerContext.
     * With a null list of "others".
     */
    @Test
    public void testGetOtherBuildsNull() {
        TriggerContext context = new TriggerContext(mockBuild("p", 2), null, null);
        List<Run> others = context.getOtherBuilds();
        assertNotNull(others);
        assertEquals(0, others.size());
    }

    /**
     * Test of getOtherBuilds method with no others, of class TriggerContext.
     * With a list of "others" containing one build.
     */
    @Test
    public void testGetOtherBuildsOne() {
        List<TriggeredItemEntity> bah = new LinkedList<TriggeredItemEntity>();
        bah.add(new TriggeredItemEntity(mockBuild("p2", 3)));
        TriggerContext context = new TriggerContext(mockBuild("p", 2), null, bah);
        List<Run> others = context.getOtherBuilds();
        assertNotNull(others);
        assertEquals(1, others.size());
        assertEquals("p2", others.get(0).getParent().getFullName());
        assertEquals(3, others.get(0).getNumber());
    }

    /**
     * Test of getSortedOthers method with no others, of class TriggerContext.
     * With a list of "others" containing several builds in different stage.
     */
    @Test
    public void testGetSortedOthers() {
        AbstractBuild buildInProgress0 = mockBuild("p0", 1);
        when(buildInProgress0.isBuilding()).thenReturn(true);

        AbstractBuild buildInProgress1 = mockBuild("p1", 1);
        when(buildInProgress1.isBuilding()).thenReturn(true);

        AbstractProject project = mockProject("p7");
        when(project.getBuildByNumber(777)).thenReturn(null);
        TriggeredItemEntity itemWithNullBuild = new TriggeredItemEntity(project);
        itemWithNullBuild.setBuildNumber(777);

        List<TriggeredItemEntity> bah = new LinkedList<TriggeredItemEntity>();
        bah.add(new TriggeredItemEntity(mockBuild("p4", 1)));
        bah.add(new TriggeredItemEntity(mockProject("p5")));
        bah.add(new TriggeredItemEntity(buildInProgress1));
        bah.add(new TriggeredItemEntity(mockBuild("p3", 1)));
        bah.add(new TriggeredItemEntity(mockProject("p6")));
        bah.add(new TriggeredItemEntity(mockBuild("p2", 1)));
        bah.add(itemWithNullBuild);
        bah.add(new TriggeredItemEntity(buildInProgress0));

        TriggerContext context = new TriggerContext(mockBuild("p", 1), null, bah);
        List<TriggeredItemEntity> others = context.getSortedOthers();
        assertNotNull(others);
        assertEquals(8, others.size());
        for (int i = 0; i < others.size(); i++) {
            assertEquals("p" + i, others.get(i).getProjectId());
        }
    }
}
