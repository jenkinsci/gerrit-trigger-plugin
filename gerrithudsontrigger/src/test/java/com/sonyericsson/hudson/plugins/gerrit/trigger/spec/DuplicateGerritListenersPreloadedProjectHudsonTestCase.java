/*
 * The MIT License
 *
 * Copyright 2011 Sony Ericsson Mobile Communications. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.spec;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritHandler;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;

import hudson.model.Item;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;
import org.powermock.reflect.Whitebox;

import java.util.Map;

import static com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil.createGerritTriggeredJob;

/**
 * This tests different scenarios of adding listeners to the
 * {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritHandler}
 * with a pre-loaded project configured, to make sure that no duplicates are created.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class DuplicateGerritListenersPreloadedProjectHudsonTestCase extends HudsonTestCase {

    /**
     * Tests that the trigger is added as a listener during startup of the server.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testProject() throws Exception {
        GerritHandler handler = Whitebox.getInternalState(PluginImpl.getInstance(), GerritHandler.class);
        Map<Integer, GerritEventListener> gerritEventListeners =
                Whitebox.getInternalState(handler, "gerritEventListeners");
        assertEquals(1, gerritEventListeners.size());
    }

    /**
     * Tests that the re-loaded trigger is still there when a new triggered project is added.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testCreateNewProject() throws Exception {
        FreeStyleProject p = createGerritTriggeredJob(this, "testing1");
        GerritHandler handler = Whitebox.getInternalState(PluginImpl.getInstance(), GerritHandler.class);
        Map<Integer, GerritEventListener> gerritEventListeners =
                Whitebox.getInternalState(handler, "gerritEventListeners");
        assertEquals(2, gerritEventListeners.size());
    }

    /**
     * Tests that the re-loaded trigger is still there when a new triggered project is added and reconfigured.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testReconfigureNewProject() throws Exception {
        FreeStyleProject p = createGerritTriggeredJob(this, "testing1");
        GerritHandler handler = Whitebox.getInternalState(PluginImpl.getInstance(), GerritHandler.class);
        Map<Integer, GerritEventListener> gerritEventListeners =
                Whitebox.getInternalState(handler, "gerritEventListeners");
        assertEquals(2, gerritEventListeners.size());
        configRoundtrip((Item)p);
        assertEquals(2, gerritEventListeners.size());
    }
}
