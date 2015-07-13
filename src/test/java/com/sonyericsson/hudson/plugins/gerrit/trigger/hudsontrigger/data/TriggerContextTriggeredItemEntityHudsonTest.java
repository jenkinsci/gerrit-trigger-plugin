/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2013 Sony Mobile Communications AB. All rights reserved.
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
import hudson.model.Cause.UserCause;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * Tests the TriggerContext.Wrap class in a Hudson context.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class TriggerContextTriggeredItemEntityHudsonTest extends HudsonTestCase {


    /**
     * Tests that {@link TriggeredItemEntity#getProject()} can find a project from its name.
     * @throws IOException if so.
     */
    public void testGetProject() throws IOException {
        AbstractProject project = createFreeStyleProject("myProject");
        TriggeredItemEntity wrap = new TriggeredItemEntity(null, "myProject");
        assertNotNull(wrap.getProject());
        assertSame(project, wrap.getProject());
    }

    /**
     * Tests that {@link TriggeredItemEntity#getBuild()} can find a build from plain info.
     * @throws IOException if so.
     * @throws InterruptedException if so.
     * @throws ExecutionException if so.
     */
    public void testGetBuild() throws IOException, InterruptedException, ExecutionException {
        AbstractProject project = createFreeStyleProject("myProject");
        AbstractBuild build = (AbstractBuild)project.scheduleBuild2(0, new UserCause()).get();
        TriggeredItemEntity wrap = new TriggeredItemEntity(build.getNumber(), "myProject");
        assertNotNull(wrap.getBuild());
        assertEquals("myProject", wrap.getBuild().getParent().getFullName());
    }

    /**
     * Thests that the serializable data is correctly set in the Constructor.
     * {@link TriggeredItemEntity#TriggeredItemEntity(hudson.model.Job, hudson.model.Run)}.
     * @throws InterruptedException if so.
     * @throws ExecutionException if so.
     * @throws IOException if so.
     */
    public void testInitProjectBuild() throws InterruptedException, ExecutionException, IOException {
        AbstractProject project = createFreeStyleProject("myProject");
        AbstractBuild build = (AbstractBuild)project.scheduleBuild2(0, new UserCause()).get();
        TriggeredItemEntity wrap = new TriggeredItemEntity(project, build);
        assertEquals(project.getFullName(), wrap.getProjectId());
        assertEquals(build.getNumber(), wrap.getBuildNumber().intValue());
    }

    /**
     * Tests that the serializable data is correctly set in the Constructor.
     * {@link TriggeredItemEntity#TriggeredItemEntity(hudson.model.Run)}.
     * @throws InterruptedException if so.
     * @throws ExecutionException if so.
     * @throws IOException if so.
     */
    public void testInitBuild() throws InterruptedException, ExecutionException, IOException {
        AbstractProject project = createFreeStyleProject("myProject");
        AbstractBuild build = (AbstractBuild)project.scheduleBuild2(0, new UserCause()).get();
        TriggeredItemEntity wrap = new TriggeredItemEntity(build);
        assertEquals(project.getFullName(), wrap.getProjectId());
        assertEquals(build.getNumber(), wrap.getBuildNumber().intValue());
    }
}
