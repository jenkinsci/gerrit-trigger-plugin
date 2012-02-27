/*
 *  The MIT License
 *
 *  Copyright 2011 Sony Ericsson Mobile Communications. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.mock;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public abstract class DuplicatesUtil {

    /**
     * Utility constructor.
     */
    private DuplicatesUtil() {

    }

    /**
     * Creates a {@link FreeStyleProject} with a gerrit-trigger configured.
     *
     * @param base the test case that is doing the current testing.
     * @param name the name of the new job.
     * @return the project.
     *
     * @throws Exception if so.
     */
    public static FreeStyleProject createGerritTriggeredJob(HudsonTestCase base, String name) throws Exception {
        FreeStyleProject p = base.hudson.createProject(FreeStyleProject.class, name);
        List<GerritProject> projects = new LinkedList<GerritProject>();
        projects.add(new GerritProject(CompareType.ANT, "**",
                Collections.singletonList(new Branch(CompareType.ANT, "**"))));
        p.addTrigger(new GerritTrigger(projects,
                null, null, null, null, null, null, null, null, false, true, true, false, false, false,
                null, null, null, null, null, null, null));
        base.submit(base.createWebClient().getPage(p, "configure").getFormByName("config"));
        return p;
    }

    /**
     * Finds the form in the html document that performs the provided action.
     *
     * @param action the action to search for.
     * @param forms  the html forms in the document.
     * @return the form, or null of there is none.
     */
    public static HtmlForm getFormWithAction(String action, List<HtmlForm> forms) {
        for (HtmlForm f : forms) {
            if (f.getActionAttribute().equalsIgnoreCase(action)) {
                return f;
            }
        }
        return null;
    }
}
