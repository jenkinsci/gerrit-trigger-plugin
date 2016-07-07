/*
 * The MIT License
 *
 * Copyright 2012, 2014 Sony Mobile Communications AB. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * Tests to see if jobs from Gerrit-Trigger v. 2.10.1 can be loaded.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class BackCompat2101HudsonTest extends HudsonTestCase {

    /**
     * Tests that a dynamic trigger config in 2.10.1 carries over correctly.
     * @see https://issues.jenkins-ci.org/browse/JENKINS-22155
     */
    @LocalData
    public void testDynamicTriggerLoading() {
        FreeStyleProject project = (FreeStyleProject)jenkins.getItem("DynamicTrigger");
        assertNotNull(project);
        GerritTrigger trigger = GerritTrigger.getTrigger(project);
        assertNotNull(trigger);
        assertTrue(trigger.isDynamicTriggerConfiguration());
        assertEquals("file://tmp/some.txt", trigger.getTriggerConfigURL());
    }
}
