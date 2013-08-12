/*
 *  The MIT License
 *
 *  Copyright 2011 Sony Ericsson Mobile Communications. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.mock;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginCommentAddedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginGerritEvent;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.util.RunList;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
                Collections.singletonList(new Branch(CompareType.ANT, "**")), null));
        p.addTrigger(new GerritTrigger(projects, null,
                null, null, null, null, null, null, null, null, null, null,
                false, true, false, null, null, null, null, null, null, null, null, false, false, null));
        base.submit(base.createWebClient().getPage(p, "configure").getFormByName("config"));
        return p;
    }

    /**
     * Creates a {@link FreeStyleProject} with a gerrit-trigger dynamically configured.
     *
     * @param base the test case that is doing the current testing.
     * @param name the name of the new job.
     * @return the project.
     *
     * @throws Exception if so.
     */
    public static FreeStyleProject createGerritDynamicTriggeredJob(HudsonTestCase base, String name) throws Exception {
        FreeStyleProject p = base.hudson.createProject(FreeStyleProject.class, name);
        List<GerritProject> projects = new LinkedList<GerritProject>();

        File file = File.createTempFile("dynamic", "txt");
        FileWriter fw = new FileWriter(file);
        fw.write("p=project\n");
        fw.write("b=branch");
        fw.close();
        List<PluginGerritEvent> list = new LinkedList<PluginGerritEvent>();
        URI uri = file.toURI();
        String filepath = uri.toURL().toString();
        GerritTrigger trigger = new GerritTrigger(projects, null,
                null, null, null, null, null, null, null, null, null, null, false, true,
                false, null, null, null, null, null, null, null, list, true, false, filepath);
        p.addTrigger(trigger);
        base.submit(base.createWebClient().getPage(p, "configure").getFormByName("config"));
        return p;
    }

    /**
     * Creates a {@link FreeStyleProject} with a gerrit-trigger configured for Code Review +1.
     *
     * @param base the test case that is doing the current testing.
     * @param name the name of the new job.
     * @return the project.
     *
     * @throws Exception if so.
     */
    public static FreeStyleProject createGerritTriggeredJobForCommentAdded(HudsonTestCase base, String name)
            throws Exception {
        FreeStyleProject p = base.hudson.createProject(FreeStyleProject.class, name);
        List<GerritProject> projects = new LinkedList<GerritProject>();
        projects.add(new GerritProject(CompareType.ANT, "**",
                Collections.singletonList(new Branch(CompareType.ANT, "**")), null));
        PluginCommentAddedEvent event = new PluginCommentAddedEvent("CRVW", "1");
        List<PluginGerritEvent> list = new LinkedList<PluginGerritEvent>();
        list.add(event);
        p.addTrigger(new GerritTrigger(projects, null,
                null, null, null, null, null, null, null, null, null, null,
                false, true, false, null, null, null, null, null, null, null, list, false, false, null));
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

    //CS IGNORE MagicNumber FOR NEXT 50 LINES. REASON: Testdata.
    /**
     * Waits for a build to start for the specified event.
     *
     * @param event     the event to monitor.
     * @param timeoutMs the maximum time in ms to wait for the build to start.
     * @return the build that started.
     */
    public static AbstractBuild waitForBuildToStart(PatchsetCreated event, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        final AtomicReference<AbstractBuild> ref = new AtomicReference<AbstractBuild>();
        event.addListener(new GerritEventLifeCycleAdaptor() {
            @Override
            public void buildStarted(PatchsetCreated event, AbstractBuild build) {
                ref.getAndSet(build);
            }
        });
        while (ref.get() == null) {
            if (System.currentTimeMillis() - startTime >= timeoutMs) {
                throw new RuntimeException("Timeout!");
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                System.err.println("Interrupted while waiting!");
            }
        }
        return ref.get();
    }

    //CS IGNORE MagicNumber FOR NEXT 50 LINES. REASON: Testdata.
    /**
     * Utility method that returns when the expected number of builds are done, or the timeout has expired.
     *
     * @param project   the project to check
     * @param number    the number of builds to wait for.
     * @param timeoutMs the timeout in ms.
     * @return the builds.
     */
    public static RunList<FreeStyleBuild> waitForBuilds(FreeStyleProject project, int number, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        while (project.getBuilds().size() < number) {
            if (System.currentTimeMillis() - startTime >= timeoutMs) {
                throw new RuntimeException("Timeout!");
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                System.err.println("Interrupted while waiting!");
            }
        }
        boolean allDone = false;
        do {
            boolean thisTime = true;
            for (AbstractBuild b : project.getBuilds()) {
                if (b.isBuilding()) {
                    thisTime = false;
                }
            }
            if (thisTime) {
                allDone = true;
            } else {
                if (System.currentTimeMillis() - startTime >= timeoutMs) {
                    throw new RuntimeException("Timeout!");
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    System.err.println("Interrupted while waiting!");
                }
            }
        } while (!allDone);
        return project.getBuilds();
    }
}
