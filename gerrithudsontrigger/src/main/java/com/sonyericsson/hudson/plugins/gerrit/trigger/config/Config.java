/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.config;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.*;

/**
 * Configuration bean for the global configuration.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class Config implements IGerritHudsonTriggerConfig {

    /**
     * Default verified vote to Gerrit when a build is started.
     */
    public static final int DEFAULT_GERRIT_BUILD_STARTED_VERIFIED_VALUE = 0;
    /**
     * Default code review vote to Gerrit when a build is started.
     */
    public static final int DEFAULT_GERRIT_BUILD_STARTED_CODE_REVIEW_VALUE = 0;
    /**
     * Default verified vote to Gerrit when a build is unstable.
     */
    public static final int DEFAULT_GERRIT_BUILD_UNSTABLE_VERIFIED_VALUE = 0;
    /**
     * Default code review vote to Gerrit when a build is unstable.
     */
    public static final int DEFAULT_GERRIT_BUILD_UNSTABLE_CODE_REVIEW_VALUE = -1;
    /**
     * Default verified vote to Gerrit when a build is failed.
     */
    public static final int DEFAULT_GERRIT_BUILD_FAILURE_VERIFIED_VALUE = -1;
    /**
     * Default code review vote to Gerrit when a build is failed.
     */
    public static final int DEFAULT_GERRIT_BUILD_FAILURE_CODE_REVIEW_VALUE = 0;
    /**
     * Default code review vote to Gerrit when a build is successful.
     */
    public static final int DEFAULT_GERRIT_BUILD_SUCCESSFUL_CODE_REVIEW_VALUE = 0;
    /**
     * Default verified vote to Gerrit when a build is successful.
     */
    public static final int DEFAULT_GERRIT_BUILD_SUCCESSFUL_VERIFIED_VALUE = 1;
    /**
     * Default verified vote to Gerrit when a build is successful.
     */
    public static final boolean DEFAULT_ENABLE_MANUAL_TRIGGER = true;
    public static final boolean DEFAULT_BUILD_CURRENT_PATCHES_ONLY = true;
    private String gerritHostName;
    private int gerritSshPort;
    private String gerritUserName;
    private File gerritAuthKeyFile;
    private String gerritAuthKeyFilePassword;
    private boolean gerritBuildCurrentPatchesOnly;
    private int numberOfWorkerThreads;
    private String gerritVerifiedCmdBuildSuccessful;
    private String gerritVerifiedCmdBuildUnstable;
    private String gerritVerifiedCmdBuildFailed;
    private String gerritVerifiedCmdBuildStarted;
    private String gerritFrontEndUrl;
    private int gerritBuildStartedVerifiedValue;
    private int gerritBuildStartedCodeReviewValue;
    private int gerritBuildSuccessfulVerifiedValue;
    private int gerritBuildSuccessfulCodeReviewValue;
    private int gerritBuildFailedVerifiedValue;
    private int gerritBuildFailedCodeReviewValue;
    private int gerritBuildUnstableVerifiedValue;
    private int gerritBuildUnstableCodeReviewValue;
    private boolean enableManualTrigger;
    private int numberOfSendingWorkerThreads;
    private int buildScheduleDelay;

    /**
     * Constructor.
     *
     * @param formData the data.
     */
    public Config(JSONObject formData) {
        setValues(formData);
    }

    @Override
    public void setValues(JSONObject formData) {
        gerritHostName = formData.optString("gerritHostName", DEFAULT_GERRIT_HOSTNAME);
        gerritSshPort = formData.optInt("gerritSshPort", DEFAULT_GERRIT_SSH_PORT);
        gerritUserName = formData.optString("gerritUserName", DEFAULT_GERRIT_USERNAME);
        String file = formData.optString("gerritAuthKeyFile", null);
        if (file != null) {
            gerritAuthKeyFile = new File(file);
        } else {
            gerritAuthKeyFile = DEFAULT_GERRIT_AUTH_KEY_FILE;
        }
        gerritAuthKeyFilePassword = formData.optString(
                "gerritAuthKeyFilePassword",
                DEFAULT_GERRIT_AUTH_KEY_FILE_PASSWORD);

        if (gerritAuthKeyFilePassword != null && gerritAuthKeyFilePassword.length() <= 0) {
            gerritAuthKeyFilePassword = null;
        }
        gerritBuildCurrentPatchesOnly = formData.optBoolean(
                "gerritBuildCurrentPatchesOnly",
                DEFAULT_BUILD_CURRENT_PATCHES_ONLY);

        numberOfWorkerThreads = formData.optInt(
                "numberOfReceivingWorkerThreads",
                DEFAULT_NR_OF_RECEIVING_WORKER_THREADS);
        if (numberOfWorkerThreads <= 0) {
            numberOfWorkerThreads = DEFAULT_NR_OF_RECEIVING_WORKER_THREADS;
        }
        numberOfSendingWorkerThreads = formData.optInt(
                "numberOfSendingWorkerThreads",
                DEFAULT_NR_OF_SENDING_WORKER_THREADS);
        if (numberOfSendingWorkerThreads <= 0) {
            numberOfSendingWorkerThreads = DEFAULT_NR_OF_SENDING_WORKER_THREADS;
        }

        gerritBuildStartedVerifiedValue = formData.optInt(
                "gerritBuildStartedVerifiedValue",
                DEFAULT_GERRIT_BUILD_STARTED_VERIFIED_VALUE);
        gerritBuildStartedCodeReviewValue = formData.optInt(
                "gerritBuildStartedCodeReviewValue",
                DEFAULT_GERRIT_BUILD_STARTED_CODE_REVIEW_VALUE);
        gerritBuildSuccessfulVerifiedValue = formData.optInt(
                "gerritBuildSuccessfulVerifiedValue",
                DEFAULT_GERRIT_BUILD_SUCCESSFUL_VERIFIED_VALUE);
        gerritBuildSuccessfulCodeReviewValue = formData.optInt(
                "gerritBuildSuccessfulCodeReviewValue",
                DEFAULT_GERRIT_BUILD_SUCCESSFUL_CODE_REVIEW_VALUE);
        gerritBuildFailedVerifiedValue = formData.optInt(
                "gerritBuildFailedVerifiedValue",
                DEFAULT_GERRIT_BUILD_FAILURE_VERIFIED_VALUE);
        gerritBuildFailedCodeReviewValue = formData.optInt(
                "gerritBuildFailedCodeReviewValue",
                DEFAULT_GERRIT_BUILD_FAILURE_CODE_REVIEW_VALUE);
        gerritBuildUnstableVerifiedValue = formData.optInt(
                "gerritBuildUnstableVerifiedValue",
                DEFAULT_GERRIT_BUILD_UNSTABLE_VERIFIED_VALUE);
        gerritBuildUnstableCodeReviewValue = formData.optInt(
                "gerritBuildUnstableCodeReviewValue",
                DEFAULT_GERRIT_BUILD_UNSTABLE_CODE_REVIEW_VALUE);

        gerritVerifiedCmdBuildStarted = formData.optString(
                "gerritVerifiedCmdBuildStarted",
                "gerrit approve <CHANGE>,<PATCHSET> --message 'Build Started <BUILDURL> <STARTED_STATS>' "
                        + "--verified <VERIFIED> --code-review <CODE_REVIEW>");
        gerritVerifiedCmdBuildFailed = formData.optString(
                "gerritVerifiedCmdBuildFailed",
                "gerrit approve <CHANGE>,<PATCHSET> --message 'Build Failed <BUILDS_STATS>' "
                        + "--verified <VERIFIED> --code-review <CODE_REVIEW>");
        gerritVerifiedCmdBuildSuccessful = formData.optString(
                "gerritVerifiedCmdBuildSuccessful",
                "gerrit approve <CHANGE>,<PATCHSET> --message 'Build Successful <BUILDS_STATS>' "
                        + "--verified <VERIFIED> --code-review <CODE_REVIEW>");
        gerritVerifiedCmdBuildUnstable = formData.optString(
                "gerritVerifiedCmdBuildUnstable",
                "gerrit approve <CHANGE>,<PATCHSET> --message 'Build Unstable <BUILDS_STATS>' "
                        + "--verified <VERIFIED> --code-review <CODE_REVIEW>");
        gerritFrontEndUrl = formData.optString(
                "gerritFrontEndUrl",
                DEFAULT_GERRIT_HOSTNAME);
        enableManualTrigger = formData.optBoolean(
                "enableManualTrigger",
                DEFAULT_ENABLE_MANUAL_TRIGGER);
        buildScheduleDelay = formData.optInt(
                "buildScheduleDelay",
                DEFAULT_BUILD_SCHEDULE_DELAY);
        if (buildScheduleDelay <= DEFAULT_BUILD_SCHEDULE_DELAY) {
            buildScheduleDelay = DEFAULT_BUILD_SCHEDULE_DELAY;
        }
    }

    /**
     * Constructs a config with default data.
     */
    public Config() {
        this(new JSONObject(false));
    }

    /**
     * Unused Constructor?
     *
     * @param formData the data
     * @param req      a path.
     */
    public Config(JSONObject formData, StaplerRequest req) {
        this(formData);
    }

    @Override
    public File getGerritAuthKeyFile() {
        return gerritAuthKeyFile;
    }

    /**
     * GerritAuthKeyFile.
     *
     * @param gerritAuthKeyFile the file
     * @see #getGerritAuthKeyFile()
     */
    public void setGerritAuthKeyFile(File gerritAuthKeyFile) {
        this.gerritAuthKeyFile = gerritAuthKeyFile;
    }

    @Override
    public String getGerritAuthKeyFilePassword() {
        return gerritAuthKeyFilePassword;
    }

    /**
     * GerritAuthKeyFilePassword.
     *
     * @param gerritAuthKeyFilePassword the password
     * @see #getGerritAuthKeyFilePassword()
     */
    public void setGerritAuthKeyFilePassword(String gerritAuthKeyFilePassword) {
        this.gerritAuthKeyFilePassword = gerritAuthKeyFilePassword;
    }

    /**
     * GerritBuildCurrentPatchesOnly
     *
     * @param gerritBuildCurrentPatchesOnly whether to only build the current patch set
     * @see #isGerritBuildCurrentPatchesOnly()
     */
    public void setGerritBuildCurrentPatchesOnly(boolean gerritBuildCurrentPatchesOnly) {
        this.gerritBuildCurrentPatchesOnly = gerritBuildCurrentPatchesOnly;
    }

    @Override
    public String getGerritFrontEndUrl() {
        String url = gerritFrontEndUrl;
        if (url != null && !url.equals("") && !url.endsWith("/")) {
            url += '/';
        }
        return url;
    }

    /**
     * GerritFrontEndURL.
     *
     * @param gerritFrontEndURL the URL
     * @see #getGerritFrontEndUrl()
     */
    public void setGerritFrontEndURL(String gerritFrontEndURL) {
        this.gerritFrontEndUrl = gerritFrontEndURL;
    }

    @Override
    public String getGerritHostName() {
        return gerritHostName;
    }

    /**
     * GerritHostName.
     *
     * @param gerritHostName the hostname
     * @see #getGerritHostName()
     */
    public void setGerritHostName(String gerritHostName) {
        this.gerritHostName = gerritHostName;
    }

    @Override
    public int getGerritSshPort() {
        return gerritSshPort;
    }

    /**
     * GerritSshPort.
     *
     * @param gerritSshPort the port
     * @see #getGerritSshPort()
     */
    public void setGerritSshPort(int gerritSshPort) {
        this.gerritSshPort = gerritSshPort;
    }

    @Override
    public int getBuildScheduleDelay() {
        return buildScheduleDelay;
    }

    /**
     * Setting buildScheduleDelay.
     *
     * @param buildScheduleDelay the delay time
     * @see #getBuildScheduleDelay()
     */
    public void setBuildScheduleDelay(int buildScheduleDelay) {
        this.buildScheduleDelay = buildScheduleDelay;
    }

    @Override
    public String getGerritUserName() {
        return gerritUserName;
    }

    /**
     * GerritUserName.
     *
     * @param gerritUserName the username
     * @see #getGerritUserName()
     */
    public void setGerritUserName(String gerritUserName) {
        this.gerritUserName = gerritUserName;
    }

    @Override
    public int getNumberOfReceivingWorkerThreads() {
        if (numberOfWorkerThreads <= 0) {
            numberOfWorkerThreads = DEFAULT_NR_OF_RECEIVING_WORKER_THREADS;
        }
        return numberOfWorkerThreads;
    }

    @Override
    public int getNumberOfSendingWorkerThreads() {
        if (numberOfSendingWorkerThreads <= 0) {
            numberOfSendingWorkerThreads = DEFAULT_NR_OF_SENDING_WORKER_THREADS;
        }
        return numberOfSendingWorkerThreads;
    }

    /**
     * NumberOfWorkerThreads.
     *
     * @param numberOfReceivingWorkerThreads nr of threads.
     * @see #getNumberOfReceivingWorkerThreads()
     */
    public void setNumberOfReceivingWorkerThreads(int numberOfReceivingWorkerThreads) {
        this.numberOfWorkerThreads = numberOfReceivingWorkerThreads;
    }

    @Override
    public boolean isGerritBuildCurrentPatchesOnly() {
        return gerritBuildCurrentPatchesOnly;
    }

    @Override
    public String getGerritCmdBuildSuccessful() {
        return gerritVerifiedCmdBuildSuccessful;
    }

    /**
     * GerritVerifiedCmdBuildSuccessful.
     *
     * @param cmd the command
     * @see #getGerritCmdBuildSuccessful()
     */
    public void setGerritVerifiedCmdBuildSuccessful(String cmd) {
        gerritVerifiedCmdBuildSuccessful = cmd;
    }

    @Override
    public String getGerritCmdBuildUnstable() {
        return gerritVerifiedCmdBuildUnstable;
    }

    /**
     * GerritVerifiedCmdBuildUnstable.
     *
     * @param cmd the command
     * @see #getGerritCmdBuildUnstable()
     */
    public void setGerritVerifiedCmdBuildUnstable(String cmd) {
        gerritVerifiedCmdBuildUnstable = cmd;
    }

    @Override
    public String getGerritCmdBuildFailed() {
        return gerritVerifiedCmdBuildFailed;
    }

    /**
     * GerritVerifiedCmdBuildFailed.
     *
     * @param cmd the command
     * @see #setGerritVerifiedCmdBuildFailed(java.lang.String)
     */
    public void setGerritVerifiedCmdBuildFailed(String cmd) {
        gerritVerifiedCmdBuildFailed = cmd;
    }

    @Override
    public String getGerritCmdBuildStarted() {
        return gerritVerifiedCmdBuildStarted;
    }

    /**
     * GerritVerifiedCmdBuildStarted.
     *
     * @param cmd the command
     * @see #getGerritCmdBuildStarted()
     */
    public void setGerritVerifiedCmdBuildStarted(String cmd) {
        gerritVerifiedCmdBuildStarted = cmd;
    }

    @Override
    public int getGerritBuildStartedVerifiedValue() {
        return gerritBuildStartedVerifiedValue;
    }

    @Override
    public int getGerritBuildStartedCodeReviewValue() {
        return gerritBuildStartedCodeReviewValue;
    }

    @Override
    public int getGerritBuildSuccessfulVerifiedValue() {
        return gerritBuildSuccessfulVerifiedValue;
    }

    @Override
    public int getGerritBuildSuccessfulCodeReviewValue() {
        return gerritBuildSuccessfulCodeReviewValue;
    }

    @Override
    public int getGerritBuildFailedVerifiedValue() {
        return gerritBuildFailedVerifiedValue;
    }

    @Override
    public int getGerritBuildFailedCodeReviewValue() {
        return gerritBuildFailedCodeReviewValue;
    }

    @Override
    public int getGerritBuildUnstableVerifiedValue() {
        return gerritBuildUnstableVerifiedValue;
    }

    @Override
    public int getGerritBuildUnstableCodeReviewValue() {
        return gerritBuildUnstableCodeReviewValue;
    }

    @Override
    public String getGerritFrontEndUrlFor(String changeSetNumber, String revision) {
        StringBuilder str = new StringBuilder(getGerritFrontEndUrl());
        str.append(changeSetNumber);
        return str.toString();
    }

    @Override
    public boolean isEnableManualTrigger() {
        return enableManualTrigger;
    }

    /**
     * Sets if the manual trigger should be enabled or not.
     *
     * @param enableManualTrigger true if it should be enabled/shown.
     * @see #isEnableManualTrigger()
     */
    public void setEnableManualTrigger(boolean enableManualTrigger) {
        this.enableManualTrigger = enableManualTrigger;
    }

    @Override
    public Authentication getGerritAuthentication() {
        return new Authentication(gerritAuthKeyFile, gerritUserName, gerritAuthKeyFilePassword);
    }

    @Override
    public boolean hasDefaultValues() {
        //both hostname and frontendurl should be null or "" for this to be true
        return (gerritHostName == null
                || (DEFAULT_GERRIT_HOSTNAME).equals(gerritHostName))

                && (gerritFrontEndUrl == null
                || (DEFAULT_GERRIT_HOSTNAME).equals(gerritFrontEndUrl));
    }
}
