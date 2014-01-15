/*
 *  The MIT License
 *
 *  Copyright 2014 Smartmatic International Corporation. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;


import hudson.Extension;
import hudson.Util;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.model.AbstractProject;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import java.util.Map;
import hudson.Launcher;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;


/**
 * Triggers a build previously run to send its report to Gerrit, if it hadn't yet.
 *
 * @author Yannick Bréhon &lt;yannick.brehon@smartmatic.com&gt;
 */

public class GerritDelayedApprover extends Notifier {

    //private static final Logger logger = LoggerFactory.getLogger(GerritDelayedApprover.class);
    private String delayedJob;
    private String delayedBuildNumber;


    /**
     * Default DataBound Constructor.
     *
     * @param delayedJob    the name of the job which may need a delayed approval. buildVariables are expanded.
     * @param delayedBuildNumber the number of the build which may need a delayed approval. buildVariables are expanded.
    */
    @DataBoundConstructor
    public GerritDelayedApprover(
            String delayedJob,
            String delayedBuildNumber) {
        this.delayedJob = delayedJob;
        this.delayedBuildNumber = delayedBuildNumber;
    }
    /**
     * The delayedBuildNumber, ie number of the build which may need a delayed approval.
     *
     * @return the delayedBuildNumber
     */
    public String getDelayedBuildNumber() {
        return delayedBuildNumber;
    }

    /**
     * Sets the delayedBuildNumber.
     *
     * @param delayedBuildNumber
     *         the delayedBuildNumber
     */
    public void setDelayedBuildNumber(String delayedBuildNumber) {
        this.delayedBuildNumber = delayedBuildNumber;
    }

    /**
     * The delayedJob, ie the name of the job which may need a delayed approval.
     *
     * @return the delayedJob
     */
    public String getDelayedJob() {
        return delayedJob;
    }

    /**
     * Sets the delayedJob.
     *
     * @param delayedJob
     *         the delayedJob
     */
    public void setDelayedJob(String delayedJob) {
        this.delayedJob = delayedJob;
    }

    /**
     * No concurrency management is required, new style extension.
     * @return BuildStepMonitor.NONE
     *
     */
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * This extension needs to run after the build is completed.
     * @return true
     *
     */
    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    /**
     * The actual performer method which will run in a post-build step and close a Gerrit Triggered event.
     * @param build current build
     * @param launcher launcher
     * @param listener the build listener
     * @throws InterruptedException if interrupted
     * @return boolean indicating whether this perform step succeeded
     */
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener)
                                        throws InterruptedException {
        //logger.info("Running Gerrit Delayed Approval");
        Map<String, String> buildVars = build.getBuildVariables();
        String jobName = Util.replaceMacro(delayedJob, buildVars);
        String numberStr = Util.replaceMacro(delayedBuildNumber, buildVars);
        int number = Integer.parseInt(numberStr);

        //String jobName = (String) build.getBuildVariables().get(jobParameterName);
        //int number = Integer.parseInt((String)build.getBuildVariables().get(buildNumParameterName));
        AbstractProject initiatingJob = Hudson.getInstance().getItemByFullName(jobName, AbstractProject.class);
        if (initiatingJob == null) {
            return false;
        }
        AbstractBuild initiatingBuild = (AbstractBuild)initiatingJob.getBuildByNumber(number);
        GerritCause cause = (GerritCause)initiatingBuild.getCause(GerritCause.class);
        if (cause == null) {
            return false;
        }
        ToGerritRunListener thelistener = ToGerritRunListener.getInstance();
        thelistener.allBuildsCompleted(cause.getEvent(), cause, null);
        return true;
    }

    /*---------------------------------*/
    /* ----- Descriptor stuff -------- */
    /*---------------------------------*/

    /**
     * getter for the Descriptor.
     * @return the associated descriptor
     */
    @Override
    public BuildStepDescriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }

    /**
     * The associated descriptor instance.
     */
    public static final GerritDelayedApproverDescriptor DESCRIPTOR = new GerritDelayedApproverDescriptor();

    /**
     * Descriptor class.
     */
    @Extension
    public static final class GerritDelayedApproverDescriptor extends
            BuildStepDescriptor<Publisher> {

        //private static final Logger logger = LoggerFactory.getLogger(GerritDelayedApproverDescriptor.class);

        /**
         * Overridden constructor, needed to reload saved data.
         */
        public GerritDelayedApproverDescriptor() {
            super(GerritDelayedApprover.class);
            load();
        }

        @Override
        public String getDisplayName() {
            return "Send a delayed Gerrit approval";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        /*@Override
        public String getHelpFile() {
            return "/plugin/build-publisher/help/config/publish.html";
        }*/

    }
}
