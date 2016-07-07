/*
 *  The MIT License
 *
 *  Copyright 2011, 2015 Sony Mobile Communications Inc. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.lifecycle.GerritEventLifecycle;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import org.apache.commons.lang.NotImplementedException;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jvnet.hudson.test.JenkinsRule;

import static org.apache.commons.lang.StringUtils.isBlank;

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
    public static AtomicReference<Run> getFutureBuildToStart(GerritEventLifecycle event) {
        final AtomicReference<Run> reference = new AtomicReference<Run>();
        event.addListener(new GerritEventLifeCycleAdaptor() {
            @Override
            public void buildStarted(GerritEvent event, Run build) {
                reference.getAndSet(build);
            }
        });
        return reference;
    }

    /**
     * Get the future build to start as reference.
     *
     * @param event the event to monitor.
     * @return the reference of future build to start.
     */
    public static AtomicReference<Run> getFutureBuildToStart2(GerritEventLifecycle event) {
        final AtomicReference<Run> reference = new AtomicReference<Run>();
        event.addListener(new GerritEventLifeCycleAdaptor() {
            @Override
            public void buildStarted(GerritEvent event, Run build) {
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
    public static Run waitForBuildToStart(AtomicReference<Run> reference) {
        return waitForBuildToStart(reference, DEFAULT_WAIT_BUILD_MS);
    }


    /**
     * Waits until the build is started, or the timeout has expired.
     *
     * @param reference the reference of future build to start.
     * @param timeoutMs the maximum time in ms to wait for the build to start.
     * @return the build that started.
     */
    public static Run waitForBuildToStart(AtomicReference<Run> reference, int timeoutMs) {
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
     * Waits until a build has started, which is Gerrit-triggered but not manually triggered.
     *
     * @param project the project which is to be built.
     * @param cbe the event that shall be triggered.
     * @param timeoutMs the timeout in ms for how long to wait.
     * @return The build that has started, or null if it hasn't started yet.
     */
    public static AbstractBuild waitForNonManualBuildToStart(AbstractProject project, ChangeBasedEvent cbe,
                                                             int timeoutMs) {
        AbstractBuild returnBuild = null;
        long startTime = System.currentTimeMillis();
        while (returnBuild == null) {
            if (System.currentTimeMillis() - startTime >= timeoutMs) {
                throw new RuntimeException("Timeout!");
            }
            try {
                Thread.sleep(SLEEP_DURATION);
            } catch (InterruptedException e) {
                System.err.println("Interrupted while waiting!");
            }
            final Iterator<Run> iterator = project._getRuns().iterator();
            while (iterator.hasNext()) {
                final Run next = iterator.next();
                Cause cause = next.getCause(GerritCause.class);
                if (cause != null && cause instanceof GerritCause) {
                    if (cbe.equals(((GerritCause)cause).getEvent())) {
                        if (next.isBuilding()) {
                            returnBuild = (AbstractBuild)next;
                        }
                    }
                }
            }
        }
        return returnBuild;
    }

    /**
     * Waits until the expected number of build are done, or the default timeout has expired.
     *
     * @param project   the project to check
     * @param number    the build number to wait for.
     */
    public static void waitForBuilds(Job project, int number) {
        waitForBuilds(project, number, DEFAULT_WAIT_BUILD_MS);
    }

    /**
     * Waits until the expected number of build are done, or the timeout has expired.
     *
     * @param project   the project to check
     * @param number    the build number to wait for.
     * @param timeoutMs the timeout in ms.
     */
    public static void waitForBuilds(Job project, int number, int timeoutMs) {
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

    /**
     * A builder to create Jobs with a {@link GerritTrigger} configured.
     */
    public static class JobBuilder {
        private static final String DEFAULT_JOB_NAME = "gerritProjectX";
        private JenkinsRule j;
        private String jobName;

        private List<GerritProject> projects = new ArrayList<GerritProject>();
        private String serverName = PluginImpl.DEFAULT_SERVER_NAME;
        private boolean silentMode = false;
        private boolean silentStartMode = false;
        private boolean escapeQuotes = true;
        private boolean dynamicTriggerConfiguration = false;

        /**
         * Creates a builder with the default project name.
         * @param j the rule that rules it all
         */
        public JobBuilder(JenkinsRule j) {
            this.j = j;
            this.jobName = DEFAULT_JOB_NAME;
        }

        /**
         * Sets the job name.
         * @param name the name of the job to create
         * @return this
         */
        public JobBuilder name(String name) {
            this.jobName = name;
            return this;
        }

        /**
         * Adds a {@link GerritProject} configuration to trigger on.
         * @param compareType the compareType
         * @param pattern the pattern
         * @param branches the branch patterns to trigger on.
         *                 If none is provided one with {@link CompareType#ANT} and pattern: '**' is added as default.
         * @return this
         */
        public JobBuilder project(CompareType compareType, String pattern, Branch... branches) {
            List<Branch> bs = new ArrayList<Branch>();
            if (branches == null || branches.length <= 0) {
                bs.add(new Branch(CompareType.ANT, "**"));
            } else {
                Collections.addAll(bs, branches);
            }
            projects.add(new GerritProject(compareType, pattern, bs, null, null, null, false));
            return this;
        }

        /**
         * Sets the name of the {@link com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer} to trigger on.
         * @param name the server name
         * @return this
         */
        public JobBuilder serverName(String name) {
            this.serverName = name;
            return this;
        }

        /**
         * Sets {@link GerritTrigger#setSilentMode(boolean)}.
         * @param mode the silent mode
         * @return this
         */
        public JobBuilder silentMode(boolean mode) {
            this.silentMode = mode;
            return this;
        }

        /**
         * Sets {@link GerritTrigger#setSilentStartMode(boolean)}.
         * @param mode the silent start mode
         * @return this
         */
        public JobBuilder silentStartMode(boolean mode) {
            this.silentStartMode = mode;
            return this;
        }

        /**
         * Sets {@link GerritTrigger#setEscapeQuotes(boolean)}.
         * @param escape escape quotes
         * @return this
         */
        public JobBuilder escapeQuotes(boolean escape) {
            this.escapeQuotes = escape;
            return this;
        }

        /**
         * Sets {@link GerritTrigger#setDynamicTriggerConfiguration(boolean)}.
         * @param dynamicTrigger dynamicTriggerConfiguration
         * @return this
         */
        public JobBuilder dynamicTriggerConfiguration(boolean dynamicTrigger) {
            this.dynamicTriggerConfiguration = dynamicTrigger;
            return this;
        }

        /**
         * Builds a {@link FreeStyleProject}.
         * @return the job
         * @throws Exception if so
         */
        public FreeStyleProject build() throws Exception {
            return build(FreeStyleProject.class);
        }

        /**
         * Builds a Job of the provided type.
         * Due to how {@link FreeStyleProject#addTrigger(hudson.triggers.Trigger)} is defined in all job types
         * it currently only supports {@link FreeStyleProject}, {@link MatrixProject} and {@link WorkflowJob}.
         * Any other job types should be added on an as needed basis.
         *
         * @param jobType the type of job to create
         * @param <T> the type of job to create
         * @return the created job
         * @throws Exception if so
         * @see JenkinsRule#createProject(Class)
         * @see JenkinsRule#configRoundtrip(Job)
         */
        public <T extends Job & TopLevelItem> T build(Class<? extends T> jobType) throws Exception {
            if (isBlank(jobName)) {
                jobName = DEFAULT_JOB_NAME;
            }
            T job = j.createProject(jobType, jobName);
            GerritTrigger trigger = new GerritTrigger(projects);
            if (projects.isEmpty()) {
                project(CompareType.ANT, "**");
            }
            if (isBlank(serverName)) {
                trigger.setServerName(PluginImpl.DEFAULT_SERVER_NAME);
            } else {
                trigger.setServerName(serverName);
            }
            trigger.setSilentMode(silentMode);
            trigger.setSilentStartMode(silentStartMode);
            trigger.setEscapeQuotes(escapeQuotes);
            trigger.setDynamicTriggerConfiguration(dynamicTriggerConfiguration);
            if (job instanceof FreeStyleProject) {
                ((FreeStyleProject)job).addTrigger(trigger);
            } else if (job instanceof MatrixProject) {
                ((MatrixProject)job).addTrigger(trigger);
            } else if (job instanceof WorkflowJob) {
                ((WorkflowJob)job).addTrigger(trigger);
            } else {
                throw new NotImplementedException(job.getClass().getName() + "Is not a supported test class, "
                                                          + "implement here.");
            }

            return j.configRoundtrip(job);
        }
    }
}
