/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;


import hudson.Extension;
import hudson.Util;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import net.sf.json.JSONObject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.model.AbstractProject;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import java.io.IOException;
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
    */
    @DataBoundConstructor
    public GerritDelayedApprover(
            String delayedJob,
            String delayedBuildNumber) {
        this.delayedJob = delayedJob;
        this.delayedBuildNumber = delayedBuildNumber;
    }
    /**
     * The delayedBuildNumber, ie number of the build which may need a delayed approval
     *
     * @return the delayedBuildNumber
     */
    public String getDelayedBuildNumber() {
        return delayedBuildNumber;
    }

    /**
     * Set the delayedBuildNumber
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
     * Set the delayedJob
     *
     * @param delayedJob
     *         the delayedJob
     */
    public void setDelayedJob(String delayedJob) {
        this.delayedJob = delayedJob;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean needsToRunAfterFinalized() {
        return true;
    }

    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener)
                                        throws InterruptedException, IOException {
        //logger.info("Running Gerrit Delayed Approval");
        Map<String,String> buildVars = build.getBuildVariables();
        String jobName = Util.replaceMacro(delayedJob,buildVars);
        String numberStr = Util.replaceMacro(delayedBuildNumber,buildVars);
        int number = Integer.parseInt(numberStr);

        //String jobName = (String) build.getBuildVariables().get(jobParameterName);
        //int number = Integer.parseInt((String)build.getBuildVariables().get(buildNumParameterName));
        AbstractProject initiatingJob = Hudson.getInstance().getItemByFullName(jobName, AbstractProject.class);
        if (initiatingJob==null) return false;
        AbstractBuild initiatingBuild = (AbstractBuild)initiatingJob.getBuildByNumber(number);
        GerritCause cause = (GerritCause)initiatingBuild.getCause(GerritCause.class);
        if (cause==null) return false;
        ToGerritRunListener thelistener = ToGerritRunListener.getInstance();
        thelistener.allBuildsCompleted(cause.getEvent(), cause, null);
        return true;
    }

    /*---------------------------------*/
    /* ----- Descriptor stuff -------- */
    /*---------------------------------*/

    @Override
    public BuildStepDescriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final GerritDelayedApproverDescriptor DESCRIPTOR = new GerritDelayedApproverDescriptor();

    @Extension
    public static final class GerritDelayedApproverDescriptor extends
            BuildStepDescriptor<Publisher> {

        //private static final Logger logger = LoggerFactory.getLogger(GerritDelayedApproverDescriptor.class);

        public GerritDelayedApproverDescriptor() {
            super(GerritDelayedApprover.class);
            load();
        }

        @Override
        public String getDisplayName() {
            return "Send a delayed Gerrit approval";
        }

        /*public DescriptorExtensionList<BuildDescriptor,Descriptor<BuildDescriptor>> getBuildSelectorDescriptors(){
            return Jenkins.getInstance().<BuildDescriptor,Descriptor<BuildDescriptor>>getDescriptorList(BuildDescriptor.class);
        }*/

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
