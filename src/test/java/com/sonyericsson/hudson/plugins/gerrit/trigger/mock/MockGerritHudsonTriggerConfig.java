/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Mobile Communications Inc. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.trigger.VerdictCategory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.ReplicationConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.BuildCancellationPolicy;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.Notify;
import com.sonymobile.tools.gerrit.gerritevents.ssh.Authentication;
import com.sonymobile.tools.gerrit.gerritevents.watchdog.WatchTimeExceptionData;

import hudson.util.Secret;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import net.sf.json.JSONObject;

import org.apache.http.auth.Credentials;

/**
 * Mock class of a Config.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class MockGerritHudsonTriggerConfig implements
        IGerritHudsonTriggerConfig {

    @Override
    public String getGerritCmdBuildStarted() {
        return "CHANGE=<CHANGE>"
                + " CHANGE_ID=<CHANGE_ID>"
                + " PATCHSET=<PATCHSET>"
                + " VERIFIED=<VERIFIED>"
                + " CODEREVIEW=<CODE_REVIEW>"
                + " NOTIFICATION_LEVEL=<NOTIFICATION_LEVEL>"
                + " REFSPEC=<REFSPEC> MSG=I started a build."
                + " BUILDURL=<BUILDURL>"
                + " STARTED_STATS=<STARTED_STATS>"
                + " ENV_BRANCH=$BRANCH"
                + " ENV_CHANGE=$CHANGE"
                + " ENV_PATCHSET=$PATCHSET"
                + " ENV_REFSPEC=$REFSPEC"
                + " ENV_CHANGEURL=$CHANGE_URL"
                + " Message\nwith newline";
    }

    @Override
    public String getGerritCmdBuildSuccessful() {
        return "CHANGE=<CHANGE>"
                + " CHANGE_ID=<CHANGE_ID>"
                + " PATCHSET=<PATCHSET>"
                + " VERIFIED=<VERIFIED>"
                + " CODEREVIEW=<CODE_REVIEW>"
                + " NOTIFICATION_LEVEL=<NOTIFICATION_LEVEL>"
                + " REFSPEC=<REFSPEC> MSG='Your friendly butler says OK. BS=<BUILDS_STATS>'"
                + " BUILDURL=<BUILDURL>"
                + " STARTED_STATS=<STARTED_STATS>"
                + " ENV_BRANCH=$BRANCH"
                + " ENV_CHANGE=$CHANGE"
                + " ENV_PATCHSET=$PATCHSET"
                + " ENV_REFSPEC=$REFSPEC"
                + " ENV_CHANGEURL=$CHANGE_URL";
    }

    @Override
    public String getGerritCmdBuildFailed() {
        return "CHANGE=<CHANGE>"
                + " CHANGE_ID=<CHANGE_ID>"
                + " PATCHSET=<PATCHSET>"
                + " VERIFIED=-1"
                + " CODEREVIEW=<CODE_REVIEW>"
                + " NOTIFICATION_LEVEL=<NOTIFICATION_LEVEL>"
                + " REFSPEC=<REFSPEC> MSG='A disappointed butler says not OK. BS=<BUILDS_STATS>'"
                + " BUILDURL=<BUILDURL>"
                + " STARTED_STATS=<STARTED_STATS>"
                + " ENV_BRANCH=$BRANCH"
                + " ENV_CHANGE=$CHANGE"
                + " ENV_PATCHSET=$PATCHSET"
                + " ENV_REFSPEC=$REFSPEC"
                + " ENV_CHANGEURL=$CHANGE_URL";
    }

    @Override
    public String getGerritCmdBuildUnstable() {
        return "CHANGE=<CHANGE>"
                + " CHANGE_ID=<CHANGE_ID>"
                + " PATCHSET=<PATCHSET>"
                + " VERIFIED=<VERIFIED>"
                + " CODEREVIEW=<CODE_REVIEW>"
                + " NOTIFICATION_LEVEL=<NOTIFICATION_LEVEL>"
                + " REFSPEC=<REFSPEC> MSG='The build is Unstable. BS=<BUILDS_STATS>'"
                + " BUILDURL=<BUILDURL>"
                + " STARTED_STATS=<STARTED_STATS>"
                + " ENV_BRANCH=$BRANCH"
                + " ENV_CHANGE=$CHANGE"
                + " ENV_PATCHSET=$PATCHSET"
                + " ENV_REFSPEC=$REFSPEC"
                + " ENV_CHANGEURL=$CHANGE_URL";
    }

    @Override
    public String getGerritCmdBuildNotBuilt() {
        // TODO Copy-pasted from getGerritCmdBuildUnstable.
        return "CHANGE=<CHANGE> PATCHSET=<PATCHSET> VERIFIED=0 MSG=The build is NotBuilt";
    }

    @Override
    public File getGerritAuthKeyFile() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getGerritAuthKeyFilePassword() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Secret getGerritAuthKeyFileSecretPassword() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getGerritFrontEndUrl() {
        return "http://gerrit/";
    }

    @Override
    public String getGerritHostName() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getGerritSshPort() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getGerritProxy() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getGerritUserName() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getGerritEMail() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Notify getNotificationLevel() {
        return Notify.ALL;
    }

    @Override
    public int getNumberOfReceivingWorkerThreads() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getNumberOfSendingWorkerThreads() {
        return 1;
    }

    @Override
    public void setNumberOfSendingWorkerThreads(int numberOfSendingWorkerThreads) {
        return;
    }

    @Override
    public void setValues(JSONObject form) {
        //Empty
    }

    //CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: Mock object.
    private Integer gerritBuildStartedCodeReviewValue = Integer.valueOf(2);
    private Integer gerritBuildSuccessfulCodeReviewValue = Integer.valueOf(4);
    private Integer gerritBuildFailedCodeReviewValue = Integer.valueOf(-2);
    private Integer gerritBuildUnstableCodeReviewValue = Integer.valueOf(-4);
    private Integer gerritBuildNotBuiltCodeReviewValue = Integer.valueOf(-6);

    private Integer gerritBuildStartedVerifiedValue = Integer.valueOf(1);
    private Integer gerritBuildSuccessfulVerifiedValue = Integer.valueOf(3);
    private Integer gerritBuildFailedVerifiedValue = Integer.valueOf(-1);
    private Integer gerritBuildUnstableVerifiedValue = Integer.valueOf(-3);
    private Integer gerritBuildNotBuiltVerifiedValue = Integer.valueOf(-5);

    @Override
    public Integer getGerritBuildStartedVerifiedValue() {
        return gerritBuildStartedVerifiedValue;
    }

    @Override
    public Integer getGerritBuildSuccessfulVerifiedValue() {
        return gerritBuildSuccessfulVerifiedValue;
    }

    @Override
    public Integer getGerritBuildFailedVerifiedValue() {
        return gerritBuildFailedVerifiedValue;
    }

    @Override
    public Integer getGerritBuildUnstableVerifiedValue() {
        return gerritBuildUnstableVerifiedValue;
    }

    @Override
    public Integer getGerritBuildNotBuiltVerifiedValue() {
        return gerritBuildNotBuiltVerifiedValue;
    }

    @Override
    public Integer getGerritBuildStartedCodeReviewValue() {
        return gerritBuildStartedCodeReviewValue;
    }
    @Override
    public Integer getGerritBuildSuccessfulCodeReviewValue() {
        return gerritBuildSuccessfulCodeReviewValue;
    }
    @Override
    public Integer getGerritBuildFailedCodeReviewValue() {
        return gerritBuildFailedCodeReviewValue;
    }
    @Override
    public Integer getGerritBuildUnstableCodeReviewValue() {
        return gerritBuildUnstableCodeReviewValue;
    }
    @Override
    public Integer getGerritBuildNotBuiltCodeReviewValue() {
        return gerritBuildNotBuiltCodeReviewValue;
    }

    /**
     * Set value.
     * @param gerritBuildSuccessfulCodeReviewValue value to set.
     */
    public void setGerritBuildSuccessfulCodeReviewValue(Integer gerritBuildSuccessfulCodeReviewValue) {
        this.gerritBuildSuccessfulCodeReviewValue = gerritBuildSuccessfulCodeReviewValue;
    }

    /**
     * Set value.
     * @param gerritBuildStartedCodeReviewValue value to set.
     */
    public void setGerritBuildStartedCodeReviewValue(Integer gerritBuildStartedCodeReviewValue) {
        this.gerritBuildStartedCodeReviewValue = gerritBuildStartedCodeReviewValue;
    }

    /**
     * Set value.
     * @param gerritBuildFailedCodeReviewValue value to set.
     */
    public void setGerritBuildFailedCodeReviewValue(Integer gerritBuildFailedCodeReviewValue) {
        this.gerritBuildFailedCodeReviewValue = gerritBuildFailedCodeReviewValue;
    }

    /**
     * Set value.
     * @param gerritBuildUnstableCodeReviewValue value to set.
     */
    public void setGerritBuildUnstableCodeReviewValue(Integer gerritBuildUnstableCodeReviewValue) {
        this.gerritBuildUnstableCodeReviewValue = gerritBuildUnstableCodeReviewValue;
    }

    /**
     * Set value.
     * @param gerritBuildNotBuiltCodeReviewValue value to set.
     */
    public void setGerritBuildNotBuiltCodeReviewValue(Integer gerritBuildNotBuiltCodeReviewValue) {
        this.gerritBuildNotBuiltCodeReviewValue = gerritBuildNotBuiltCodeReviewValue;
    }

    /**
     * Set value.
     * @param gerritBuildStartedVerifiedValue value to set.
     */
    public void setGerritBuildStartedVerifiedValue(Integer gerritBuildStartedVerifiedValue) {
        this.gerritBuildStartedVerifiedValue = gerritBuildStartedVerifiedValue;
    }

    /**
     * Set value.
     * @param gerritBuildSuccessfulVerifiedValue value to set.
     */
    public void setGerritBuildSuccessfulVerifiedValue(Integer gerritBuildSuccessfulVerifiedValue) {
        this.gerritBuildSuccessfulVerifiedValue = gerritBuildSuccessfulVerifiedValue;
    }

    /**
     * Set value.
     * @param gerritBuildFailedVerifiedValue value to set.
     */
    public void setGerritBuildFailedVerifiedValue(Integer gerritBuildFailedVerifiedValue) {
        this.gerritBuildFailedVerifiedValue = gerritBuildFailedVerifiedValue;
    }

    /**
     * Set value.
     * @param gerritBuildUnstableVerifiedValue value to set.
     */
    public void setGerritBuildUnstableVerifiedValue(Integer gerritBuildUnstableVerifiedValue) {
        this.gerritBuildUnstableVerifiedValue = gerritBuildUnstableVerifiedValue;
    }

    /**
     * Set value.
     * @param gerritBuildNotBuiltVerifiedValue value to set.
     */
    public void setGerritBuildNotBuiltVerifiedValue(Integer gerritBuildNotBuiltVerifiedValue) {
        this.gerritBuildNotBuiltVerifiedValue = gerritBuildNotBuiltVerifiedValue;
    }

    @Override
    public String getGerritFrontEndUrlFor(String number, String revision) {
        return "http://gerrit/" + number;
    }

    @Override
    public String getGerritFrontEndUrlFor(GerritTriggeredEvent event) {
        return "http://gerrit/1";
    }

    @Override
    public List<VerdictCategory> getCategories() {
        return new LinkedList<VerdictCategory>();
    }

    @Override
    public void setCategories(List<VerdictCategory> categories) {
    }

    @Override
    public boolean isEnableManualTrigger() {
        return true;
    }

    @Override
    public Authentication getGerritAuthentication() {
        return new Authentication(getGerritAuthKeyFile(), getGerritUserName(),
                getGerritAuthKeyFilePassword());
    }

    @Override
    public int getBuildScheduleDelay() {
        return 3;
    }

    @Override
    public int getDynamicConfigRefreshInterval() {
        return 30;
    }

    @Override
    public int getProjectListFetchDelay() {
        return Config.DEFAULT_PROJECT_LIST_FETCH_DELAY;
    }

    @Override
    public int getProjectListRefreshInterval() {
        return Config.DEFAULT_PROJECT_LIST_REFRESH_INTERVAL;
    }

    @Override
    public boolean isEnableProjectAutoCompletion() {
        return Config.DEFAULT_ENABLE_PROJECT_AUTO_COMPLETION;
    }

    @Override
    public boolean hasDefaultValues() {
        return false;
    }

    @Override
    public boolean isGerritBuildCurrentPatchesOnly() {
        return true;
    }

    @Override
    public BuildCancellationPolicy getBuildCurrentPatchesOnly() {
        return new BuildCancellationPolicy();
    }

    @Override
    public boolean isEnablePluginMessages() {
        return true;
    }

    @Override
    public boolean isTriggerOnAllComments() { return true; }

    @Override
    public boolean isUseRestApi() {
        return false;
    }

    @Override
    public int getWatchdogTimeoutMinutes() {
        return 0;
    }

    @Override
    public int getWatchdogTimeoutSeconds() {
        return 0;
    }

    @Override
    public WatchTimeExceptionData getExceptionData() {
        return new WatchTimeExceptionData(new int[]{}, new LinkedList<WatchTimeExceptionData.TimeSpan>());
    }

    @Override
    public Secret getGerritHttpSecretPassword() {
        return null;
    }

    @Override
    public String getGerritHttpPassword() {
        return "";
    }

    @Override
    public String getGerritHttpUserName() {
        return "";
    }

    @Override
    public Credentials getHttpCredentials() {
        return null;
    }

    @Override
    public ReplicationConfig getReplicationConfig() {
        return null;
    }

    @Override
    public boolean isRestCodeReview() {
        return true;
    }

    @Override
    public boolean isRestVerified() {
        return true;
    }
}
