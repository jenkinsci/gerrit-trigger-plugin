/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012, 2013 Sony Mobile Communications AB. All rights reserved.
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

import com.google.common.primitives.Ints;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Provider;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData.TimeSpan;
import com.sonyericsson.hudson.plugins.gerrit.trigger.VerdictCategory;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

//CS IGNORE LineLength FOR NEXT 10 LINES. REASON: static import.
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_BUILD_SCHEDULE_DELAY;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_DYNAMIC_CONFIG_REFRESH_INTERVAL;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_AUTH_KEY_FILE;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_AUTH_KEY_FILE_PASSWORD;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_HOSTNAME;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_SSH_PORT;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_PROXY;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_USERNAME;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_NR_OF_RECEIVING_WORKER_THREADS;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_NR_OF_SENDING_WORKER_THREADS;

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
     * Default verified vote to Gerrit when a build is not built.
     */
    public static final int DEFAULT_GERRIT_BUILD_NOT_BUILT_VERIFIED_VALUE = 0;
    /**
     * Default code review vote to Gerrit when a build is not built.
     */
    public static final int DEFAULT_GERRIT_BUILD_NOT_BUILT_CODE_REVIEW_VALUE = 0;

    /**
     * Default timeout value in minutes for the connection watchdog.
     */
    public static final int DEFAULT_GERRIT_WATCHDOG_TIMEOUT_MINUTES = 0;
    /**
     * Default manual trigger enabled.
     */
    public static final boolean DEFAULT_ENABLE_MANUAL_TRIGGER = true;
    /**
     * Default plug-in messages enabled.
     */
    public static final boolean DEFAULT_ENABLE_PLUGIN_MESSAGES = true;
    /**
     * Default value for {@link #isGerritBuildCurrentPatchesOnly()}.
     */
    public static final boolean DEFAULT_BUILD_CURRENT_PATCHES_ONLY = false;

    private String gerritHostName;
    private int gerritSshPort;
    private String gerritProxy;
    private String gerritUserName;
    private String gerritEMail;
    private File gerritAuthKeyFile;
    private String gerritAuthKeyFilePassword;
    private boolean gerritBuildCurrentPatchesOnly;
    private int numberOfWorkerThreads;
    private String gerritVerifiedCmdBuildSuccessful;
    private String gerritVerifiedCmdBuildUnstable;
    private String gerritVerifiedCmdBuildFailed;
    private String gerritVerifiedCmdBuildStarted;
    private String gerritVerifiedCmdBuildNotBuilt;
    private String gerritFrontEndUrl;
    private int gerritBuildStartedVerifiedValue;
    private int gerritBuildStartedCodeReviewValue;
    private int gerritBuildSuccessfulVerifiedValue;
    private int gerritBuildSuccessfulCodeReviewValue;
    private int gerritBuildFailedVerifiedValue;
    private int gerritBuildFailedCodeReviewValue;
    private int gerritBuildUnstableVerifiedValue;
    private int gerritBuildUnstableCodeReviewValue;
    private int gerritBuildNotBuiltVerifiedValue;
    private int gerritBuildNotBuiltCodeReviewValue;
    private boolean enableManualTrigger;
    private boolean enablePluginMessages;
    private int numberOfSendingWorkerThreads;
    private int buildScheduleDelay;
    private int dynamicConfigRefreshInterval;
    private List<VerdictCategory> categories;
    private int watchdogTimeoutMinutes;
    private WatchTimeExceptionData watchTimeExceptionData;



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
        gerritProxy = formData.optString("gerritProxy", DEFAULT_GERRIT_PROXY);
        gerritUserName = formData.optString("gerritUserName", DEFAULT_GERRIT_USERNAME);
        gerritEMail = formData.optString("gerritEMail", "");
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
        gerritBuildNotBuiltVerifiedValue = formData.optInt(
                "gerritBuildNotBuiltVerifiedValue",
                DEFAULT_GERRIT_BUILD_NOT_BUILT_VERIFIED_VALUE);
        gerritBuildNotBuiltCodeReviewValue = formData.optInt(
                "gerritBuildNotBuiltCodeReviewValue",
                DEFAULT_GERRIT_BUILD_NOT_BUILT_CODE_REVIEW_VALUE);

        gerritVerifiedCmdBuildStarted = formData.optString(
                "gerritVerifiedCmdBuildStarted",
                "gerrit review <CHANGE>,<PATCHSET> --message 'Build Started <BUILDURL> <STARTED_STATS>' "
                        + "--verified <VERIFIED> --code-review <CODE_REVIEW>");
        gerritVerifiedCmdBuildFailed = formData.optString(
                "gerritVerifiedCmdBuildFailed",
                "gerrit review <CHANGE>,<PATCHSET> --message 'Build Failed <BUILDS_STATS>' "
                        + "--verified <VERIFIED> --code-review <CODE_REVIEW>");
        gerritVerifiedCmdBuildSuccessful = formData.optString(
                "gerritVerifiedCmdBuildSuccessful",
                "gerrit review <CHANGE>,<PATCHSET> --message 'Build Successful <BUILDS_STATS>' "
                        + "--verified <VERIFIED> --code-review <CODE_REVIEW>");
        gerritVerifiedCmdBuildUnstable = formData.optString(
                "gerritVerifiedCmdBuildUnstable",
                "gerrit review <CHANGE>,<PATCHSET> --message 'Build Unstable <BUILDS_STATS>' "
                        + "--verified <VERIFIED> --code-review <CODE_REVIEW>");
        gerritVerifiedCmdBuildNotBuilt = formData.optString(
                "gerritVerifiedCmdBuildNotBuilt",
                "gerrit review <CHANGE>,<PATCHSET> --message 'No Builds Executed <BUILDS_STATS>' "
                        + "--verified <VERIFIED> --code-review <CODE_REVIEW>");
        gerritFrontEndUrl = formData.optString(
                "gerritFrontEndUrl",
                DEFAULT_GERRIT_HOSTNAME);
        enableManualTrigger = formData.optBoolean(
                "enableManualTrigger",
                DEFAULT_ENABLE_MANUAL_TRIGGER);
        enablePluginMessages = formData.optBoolean(
                "enablePluginMessages",
                DEFAULT_ENABLE_PLUGIN_MESSAGES);
        buildScheduleDelay = formData.optInt(
                "buildScheduleDelay",
                DEFAULT_BUILD_SCHEDULE_DELAY);
        if (buildScheduleDelay <= DEFAULT_BUILD_SCHEDULE_DELAY) {
            buildScheduleDelay = DEFAULT_BUILD_SCHEDULE_DELAY;
        }
        dynamicConfigRefreshInterval = formData.optInt(
                "dynamicConfigRefreshInterval",
                DEFAULT_DYNAMIC_CONFIG_REFRESH_INTERVAL);
        categories = new LinkedList<VerdictCategory>();
        if (formData.has("verdictCategories")) {
            Object cat = formData.get("verdictCategories");
            if (cat instanceof JSONArray) {
                for (Object jsonObject : (JSONArray)cat) {
                    categories.add(VerdictCategory.createVerdictCategoryFromJSON((JSONObject)jsonObject));
                }
            } else if (cat instanceof JSONObject) {
                categories.add(VerdictCategory.createVerdictCategoryFromJSON((JSONObject)cat));
            }
        }
        watchdogTimeoutMinutes = formData.optInt("watchdogTimeoutMinutes", DEFAULT_GERRIT_WATCHDOG_TIMEOUT_MINUTES);
        watchTimeExceptionData = addWatchTimeExceptionData(formData);
    }

    /**
     * Adds the WatchTimeExceptionData from the form.
     *
     * @param formData the form.
     * @return the WatchTimeExceptionData
     */
    private WatchTimeExceptionData addWatchTimeExceptionData(JSONObject formData) {
        List<Integer> days = new LinkedList<Integer>();
        List<TimeSpan> exceptionTimes = new LinkedList<TimeSpan>();
        int[] daysAsInt = new int[]{};
        if (formData.has("watchdogExceptions")) {
            JSONObject jsonObject = formData.getJSONObject(("watchdogExceptions"));
            if (jsonObject.getBoolean(String.valueOf(Calendar.MONDAY))) {
                days.add(Calendar.MONDAY);
            }
            if (jsonObject.getBoolean(String.valueOf(Calendar.TUESDAY))) {
                days.add(Calendar.TUESDAY);
            }
            if (jsonObject.getBoolean(String.valueOf(Calendar.WEDNESDAY))) {
                days.add(Calendar.WEDNESDAY);
            }
            if (jsonObject.getBoolean(String.valueOf(Calendar.THURSDAY))) {
                days.add(Calendar.THURSDAY);
            }
            if (jsonObject.getBoolean(String.valueOf(Calendar.FRIDAY))) {
                days.add(Calendar.FRIDAY);
            }
            if (jsonObject.getBoolean(String.valueOf(Calendar.SATURDAY))) {
                days.add(Calendar.SATURDAY);
            }
            if (jsonObject.getBoolean(String.valueOf(Calendar.SUNDAY))) {
                days.add(Calendar.SUNDAY);
            }
            daysAsInt = Ints.toArray(days);
            if (jsonObject.has("watchdogExceptionTimes")) {
                Object obj = jsonObject.get("watchdogExceptionTimes");
                if (obj instanceof JSONArray) {
                    for (Object json : (JSONArray)obj) {
                        exceptionTimes.add(TimeSpan.createTimeSpanFromJSONObject((JSONObject)json));
                    }
                } else if (obj instanceof JSONObject) {
                    exceptionTimes.add(TimeSpan.createTimeSpanFromJSONObject((JSONObject)obj));
                }
            }
        }
        return new WatchTimeExceptionData(daysAsInt, exceptionTimes);
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
     * GerritBuildCurrentPatchesOnly.
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
        if (url != null && !url.isEmpty() && !url.endsWith("/")) {
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
    public String getGerritProxy() {
        return gerritProxy;
    }

    /**
     * GerritProxy.
     *
     * @param gerritProxy the proxy url
     * @see #getGerritProxy()
     */
    public void setGerritProxy(String gerritProxy) {
        this.gerritProxy = gerritProxy;
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
    public int getDynamicConfigRefreshInterval() {
        if (dynamicConfigRefreshInterval == 0) {
            dynamicConfigRefreshInterval = DEFAULT_DYNAMIC_CONFIG_REFRESH_INTERVAL;
        } else if (dynamicConfigRefreshInterval < GerritDefaultValues.MINIMUM_DYNAMIC_CONFIG_REFRESH_INTERVAL) {
            dynamicConfigRefreshInterval = GerritDefaultValues.MINIMUM_DYNAMIC_CONFIG_REFRESH_INTERVAL;
        }
        return dynamicConfigRefreshInterval;
    }

    /**
     * Setting dynamicConfigRefreshInterval.
     *
     * @param dynamicConfigRefreshInterval the interval between the fetches.
     * @see #getDynamicConfigRefreshInterval()
     */
    public void setDynamicConfigRefreshInterval(int dynamicConfigRefreshInterval) {
        this.dynamicConfigRefreshInterval = dynamicConfigRefreshInterval;
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
    public String getGerritEMail() {
        return gerritEMail;
    }

    /**
     * The e-mail address for the user in gerrit.
     * Comments added from this e-mail address will be ignored.
     * @param gerritEMail the e-mail address.
     * @see #getGerritEMail()
     */
    public void setGerritEMail(String gerritEMail) {
        this.gerritEMail = gerritEMail;
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
    public String getGerritCmdBuildNotBuilt() {
        return gerritVerifiedCmdBuildNotBuilt;
    }

    /**
     * GerritVerifiedCmdBuildNotBuilt.
     *
     * @param cmd the command
     * @see #getGerritCmdBuildNotBuilt()
     */
    public void setGerritVerifiedCmdBuildNotBuilt(String cmd) {
        gerritVerifiedCmdBuildNotBuilt = cmd;
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
    public int getGerritBuildNotBuiltVerifiedValue() {
        return gerritBuildNotBuiltVerifiedValue;
    }

    @Override
    public int getGerritBuildNotBuiltCodeReviewValue() {
        return gerritBuildNotBuiltCodeReviewValue;
    }

    @Override
    public String getGerritFrontEndUrlFor(String changeSetNumber, String revision) {
        StringBuilder str = new StringBuilder(getGerritFrontEndUrl());
        str.append(changeSetNumber);
        return str.toString();
    }

    @Override
    public String getGerritFrontEndUrlFor(GerritTriggeredEvent event) {
        if (event instanceof ChangeBasedEvent) {
            String changeUrl = ((ChangeBasedEvent)event).getChange().getUrl();
            if (changeUrl != null && !changeUrl.isEmpty()) {
                return changeUrl;
            }
        }
        String url = getGerritFrontEndUrl();
        Provider provider = event.getProvider();
        if (provider != null) {
            String providerUrl = provider.getUrl();
            if (providerUrl != null && !providerUrl.isEmpty()) {
                url = providerUrl;
            }
        }
        StringBuilder str = new StringBuilder(url);
        if (event instanceof ChangeBasedEvent) {
            str.append(((ChangeBasedEvent)event).getChange().getNumber());
        }
        return str.toString();
    }

    @Override
    public List<VerdictCategory> getCategories() {
        return categories;
    }

    /**
     * Setter for the list of VerdictCategories, used to make testing easier.
     * @param categories the list.
     */
    @Override
    public void setCategories(List<VerdictCategory> categories) {
        this.categories = categories;
    }

    /**
     * Getter for the enableManualTrigger value.
     * @return true if manual triggering is enabled.
     */
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

    @Override
    public boolean isEnablePluginMessages() {
        return enablePluginMessages;
    }

    @Override
    public int getWatchdogTimeoutSeconds() {
        return (int)TimeUnit.MINUTES.toSeconds(watchdogTimeoutMinutes);
    }

    /**
     * Convenience getter for the jelly view.
     *
     * @return the watchdogTimeoutMinutes.
     */
    public int getWatchdogTimeoutMinutes() {
        return watchdogTimeoutMinutes;
    }

    @Override
    public WatchTimeExceptionData getExceptionData() {
        return watchTimeExceptionData;
    }
}
