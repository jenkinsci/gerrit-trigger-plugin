/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

/**
 * Configuration bean for the global configuration.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class Config implements IGerritHudsonTriggerConfig {

    /**
     * Default verified vote to Gerrit when a build is started.
     */
    public static final int DEFAULT_GERRIT_BUILD_STARTED_VERIFIED_VALUE = 0;
    /**
     *  Default code review vote to Gerrit when a build is started.
     */
    public static final int DEFAULT_GERRIT_BUILD_STARTED_CODE_REVIEW_VALUE = 0;
    /**
     *  Default verified vote to Gerrit when a build is unstable.
     */
    public static final int DEFAULT_GERRIT_BUILD_UNSTABLE_VERIFIED_VALUE = 0;
    /**
     *  Default code review vote to Gerrit when a build is unstable.
     */
    public static final int DEFAULT_GERRIT_BUILD_UNSTABLE_CODE_REVIEW_VALUE = -1;
    /**
     *  Default verified vote to Gerrit when a build is failed.
     */
    public static final int DEFAULT_GERRIT_BUILD_FAILURE_VERIFIED_VALUE = -1;
    /**
     *  Default code review vote to Gerrit when a build is failed.
     */
    public static final int DEFAULT_GERRIT_BUILD_FAILURE_CODE_REVIEW_VALUE = 0;
    /**
     *  Default code review vote to Gerrit when a build is successful.
     */
    public static final int DEFAULT_GERRIT_BUILD_SUCCESSFUL_CODE_REVIEW_VALUE = 0;
    /**
     *  Default verified vote to Gerrit when a build is successful.
     */
    public static final int DEFAULT_GERRIT_BUILD_SUCCESSFUL_VERIFIED_VALUE = 1;

    private String gerritHostName;
    private int gerritSshPort;
    private String gerritUserName;
    private File gerritAuthKeyFile;
    private String gerritAuthKeyFilePassword;
    private int numberOfWorkerThreads;
    private String gerritVerifiedCmdBuildSuccessful;
    private String gerritVerifiedCmdBuildUnstable;
    private String gerritVerifiedCmdBuildFailed;
    private String gerritVerifiedCmdBuildStarted;
    private String gerritFrontEndUrl;
    private static Config singleton;
    private transient List<String> gerritProjects;
    private int gerritBuildStartedVerifiedValue;
    private int gerritBuildStartedCodeReviewValue;
    private int gerritBuildSuccessfulVerifiedValue;
    private int gerritBuildSuccessfulCodeReviewValue;
    private int gerritBuildFailedVerifiedValue;
    private int gerritBuildFailedCodeReviewValue;
    private int gerritBuildUnstableVerifiedValue;
    private int gerritBuildUnstableCodeReviewValue;

    /**
     * Constructor.
     * @param formData the data.
     */
    public Config(JSONObject formData) {
        setValues(formData);
    }

    @Override
    public void setValues(JSONObject formData) {
        gerritHostName = formData.optString("gerritHostName", GerritDefaultValues.DEFAULT_GERRIT_HOSTNAME);
        gerritSshPort = formData.optInt("gerritSshPort", GerritDefaultValues.DEFAULT_GERRIT_SSH_PORT);
        gerritUserName = formData.optString("gerritUserName", GerritDefaultValues.DEFAULT_GERRIT_USERNAME);
        String file = formData.optString("gerritAuthKeyFile", null);
        if (file != null) {
            gerritAuthKeyFile = new File(file);
        } else {
            gerritAuthKeyFile = GerritDefaultValues.DEFAULT_GERRIT_AUTH_KEY_FILE;
        }
        gerritAuthKeyFilePassword = formData.optString(
                "gerritAuthKeyFilePassword",
                GerritDefaultValues.DEFAULT_GERRIT_AUTH_KEY_FILE_PASSWORD);

        if (gerritAuthKeyFilePassword != null && gerritAuthKeyFilePassword.length() <= 0) {
            gerritAuthKeyFilePassword = null;
        }

        numberOfWorkerThreads = formData.optInt(
                "numberOfWorkerThreads",
                GerritDefaultValues.DEFAULT_NR_OF_WORKER_THREADS);

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
                "GerritVerifiedCmdBuildStarted",
                "gerrit approve <CHANGE>,<PATCHSET> --message 'Build Started <BUILDURL> <STARTED_STATS>' "
                + "--verified <VERIFIED> --code-review <CODE_REVIEW>");
        gerritVerifiedCmdBuildFailed = formData.optString(
                "GerritVerifiedCmdBuildFailed",
                "gerrit approve <CHANGE>,<PATCHSET> --message 'Build Failed <BUILDS_STATS>' "
                + "--verified <VERIFIED> --code-review <CODE_REVIEW>");
        gerritVerifiedCmdBuildSuccessful = formData.optString(
                "GerritVerifiedCmdBuildSuccessful",
                "gerrit approve <CHANGE>,<PATCHSET> --message 'Build Successful <BUILDS_STATS>' "
                + "--verified <VERIFIED> --code-review <CODE_REVIEW>");
        gerritVerifiedCmdBuildUnstable = formData.optString(
                "GerritVerifiedCmdBuildUnstable",
                "gerrit approve <CHANGE>,<PATCHSET> --message 'Build Unstable <BUILDS_STATS>' "
                + "--verified <VERIFIED> --code-review <CODE_REVIEW>");
        gerritFrontEndUrl = formData.optString(
                "gerritFrontEndUrl",
                "http://" + GerritDefaultValues.DEFAULT_GERRIT_HOSTNAME);
    }

    /**
     * Constructs a config with default data.
     */
    public Config() {
        this(new JSONObject(false));
    }

    /**
     * Unused Constructor?
     * @param formData the data
     * @param req a path.
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
     * @param gerritAuthKeyFilePassword the password
     * @see #getGerritAuthKeyFilePassword()
     */
    public void setGerritAuthKeyFilePassword(String gerritAuthKeyFilePassword) {
        this.gerritAuthKeyFilePassword = gerritAuthKeyFilePassword;
    }

    @Override
    public String getGerritFrontEndUrl() {
        String url = gerritFrontEndUrl;
        if (!url.endsWith("/")) {
            url += '/';
        }
        return url;
    }

    /**
     * GerritFrontEndURL.
     * @param gerritFrontEndURL the URL
     * @see #getGerritFrontEndURL()
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
     * @param gerritSshPort the port
     * @see #getGerritSshPort()
     */
    public void setGerritSshPort(int gerritSshPort) {
        this.gerritSshPort = gerritSshPort;
    }

    @Override
    public String getGerritUserName() {
        return gerritUserName;
    }

    /**
     * GerritUserName.
     * @param gerritUserName the username
     * @see #getGerritUserName()
     */
    public void setGerritUserName(String gerritUserName) {
        this.gerritUserName = gerritUserName;
    }

    @Override
    public int getNumberOfWorkerThreads() {
        return numberOfWorkerThreads;
    }

    /**
     * NumberOfWorkerThreads.
     * @param numberOfWorkerThreads nr of threads.
     * @see #getNumberOfWorkerThreads()
     */
    public void setNumberOfWorkerThreads(int numberOfWorkerThreads) {
        this.numberOfWorkerThreads = numberOfWorkerThreads;
    }

    @Override
    public String getGerritCmdBuildSuccessful() {
        return gerritVerifiedCmdBuildSuccessful;
    }

    /**
     * GerritVerifiedCmdBuildSuccessful.
     * @param cmd the command
     * @see #getGerritVerifiedCmdBuildSuccessful()
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
     * @param cmd the command
     * @see #getGerritVerifiedCmdBuildUnstable()
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
     * @param cmd the command
     * @see #getGerritVerifiedCmdBuildStarted()
     */
    public void setGerritVerifiedCmdBuildStarted(String cmd) {
        gerritVerifiedCmdBuildStarted = cmd;
    }

    /**
     * Flag this as loaded.
     */
    public void loaded() {
        singleton = this;
    }

    /**
     * Returns the singleton instance.
     * @return the singelton.
     */
    public static IGerritHudsonTriggerConfig get() {
        return singleton;
    }

    @Override
    public void setGerritProjects(List<String> projects) {
        gerritProjects = projects;
    }

    @Override
    public List<String> getGerritProjects() {
        if (gerritProjects == null) {
            gerritProjects = new ArrayList<String>();
        }
        return gerritProjects;
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
    public Authentication getGerritAuthentication() {
        return new Authentication(gerritAuthKeyFile, gerritUserName, gerritAuthKeyFilePassword);
    }
}
