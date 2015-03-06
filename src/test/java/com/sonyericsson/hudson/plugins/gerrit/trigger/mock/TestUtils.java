/*
 *  The MIT License
 *
 *  Copyright 2011 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
 *  Copyright 2014 rinrinne All rights reserved.
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

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.lifecycle.GerritEventLifecycle;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;

/**
 * A utility class for test.
 *
 * @author rinrinne (rinrin.ne@gmail.com)
 */
public final class TestUtils {

    /**
     * Default build wait time in ms.
     */
    public static final int DEFAULT_WAIT_BUILD_MS = 30000;

    private static final int SLEEP_DURATION = 1000;

    /**
     * Utility constructor.
     */
    private TestUtils() {

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

    /**
     * Get the future build to start as reference.
     *
     * @param event the event to monitor.
     * @return the reference of future build to start.
     */
    public static AtomicReference<AbstractBuild> getFutureBuildToStart(GerritEventLifecycle event) {
        final AtomicReference<AbstractBuild> reference = new AtomicReference<>();
        event.addListener(new GerritEventLifeCycleAdaptor() {
            @Override
            public void buildStarted(GerritEvent event, AbstractBuild build) {
                reference.getAndSet(build);
            }
        });
        return reference;
    }

    /**
     * Waits until the build is started, or the default timeout has expired.
     *
     * @param reference the reference of future build to start.
     * @return the build that started.
     */
    public static AbstractBuild waitForBuildToStart(AtomicReference<AbstractBuild> reference) {
        return waitForBuildToStart(reference, DEFAULT_WAIT_BUILD_MS);
    }


    /**
     * Waits until the build is started, or the timeout has expired.
     *
     * @param reference the reference of future build to start.
     * @param timeoutMs the maximum time in ms to wait for the build to start.
     * @return the build that started.
     */
    public static AbstractBuild waitForBuildToStart(AtomicReference<AbstractBuild> reference, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        while (reference.get() == null) {
            if (System.currentTimeMillis() - startTime >= timeoutMs) {
                throw new RuntimeException("Timeout!");
            }
            try {
                Thread.sleep(SLEEP_DURATION);
            } catch (InterruptedException e) {
                System.err.println("Interrupted while waiting!");
            }
        }
        return reference.get();
    }

    /**
     * Waits until the expected number of build are done, or the default timeout has expired.
     *
     * @param project   the project to check
     * @param number    the build number to wait for.
     */
    public static void waitForBuilds(AbstractProject project, int number) {
        waitForBuilds(project, number, DEFAULT_WAIT_BUILD_MS);
    }

    /**
     * Waits until the expected number of build are done, or the timeout has expired.
     *
     * @param project   the project to check
     * @param number    the build number to wait for.
     * @param timeoutMs the timeout in ms.
     */
    public static void waitForBuilds(AbstractProject project, int number, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        while (project.getLastCompletedBuild() == null || project.getLastCompletedBuild().getNumber() != number) {
            if (System.currentTimeMillis() - startTime >= timeoutMs) {
                throw new RuntimeException("Timeout!");
            }
            try {
                Thread.sleep(SLEEP_DURATION);
            } catch (InterruptedException e) {
                System.err.println("Interrupted while waiting!");
            }
        }
    }
}
