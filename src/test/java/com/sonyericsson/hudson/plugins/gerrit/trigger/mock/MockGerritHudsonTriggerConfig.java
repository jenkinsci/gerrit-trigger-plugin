/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData;
import com.sonyericsson.hudson.plugins.gerrit.trigger.VerdictCategory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.ReplicationConfig;

import net.sf.json.JSONObject;

import org.apache.http.auth.Credentials;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

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
                + " REFSPEC=<REFSPEC> MSG=I started a build."
                + " BUILDURL=<BUILDURL>"
                + " STARTED_STATS=<STARTED_STATS>"
                + " ENV_BRANCH=$BRANCH"
                + " ENV_CHANGE=$CHANGE"
                + " ENV_PATCHSET=$PATCHSET"
                + " ENV_REFSPEC=$REFSPEC"
                + " ENV_CHANGEURL=$CHANGE_URL";
    }

    @Override
    public String getGerritCmdBuildSuccessful() {
        return "CHANGE=<CHANGE>"
                + " CHANGE_ID=<CHANGE_ID>"
                + " PATCHSET=<PATCHSET>"
                + " VERIFIED=<VERIFIED>"
                + " CODEREVIEW=<CODE_REVIEW>"
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
        return "CHANGE=<CHANGE> PATCHSET=<PATCHSET> VERIFIED=-1 MSG=A disappointed butler says not OK.<BUILDURL>";
    }

    @Override
    public String getGerritCmdBuildUnstable() {
        // TODO Auto-generated method stub
        return "CHANGE=<CHANGE> PATCHSET=<PATCHSET> VERIFIED=0 MSG=The build is Unstable";
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
    @Override
    public int getGerritBuildStartedVerifiedValue() {
        return 1;
    }

    @Override
    public int getGerritBuildStartedCodeReviewValue() {
        return 2;
    }

    @Override
    public int getGerritBuildSuccessfulVerifiedValue() {
        return 3;
    }

    @Override
    public int getGerritBuildSuccessfulCodeReviewValue() {
        return 4;
    }

    @Override
    public int getGerritBuildFailedVerifiedValue() {
        return -1;
    }

    @Override
    public int getGerritBuildFailedCodeReviewValue() {
        return -2;
    }

    @Override
    public int getGerritBuildUnstableVerifiedValue() {
        return -3;
    }

    @Override
    public int getGerritBuildUnstableCodeReviewValue() {
        return -4;
    }

    @Override
    public int getGerritBuildNotBuiltVerifiedValue() {
        return -5;
    }

    @Override
    public int getGerritBuildNotBuiltCodeReviewValue() {
        return -6;
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
    public boolean hasDefaultValues() {
        return false;
    }

    @Override
    public boolean isGerritBuildCurrentPatchesOnly() {
        return true;
    }

    @Override
    public boolean isEnablePluginMessages() {
        return true;
    }

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
}
