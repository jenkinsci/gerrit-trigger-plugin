package com.sonyericsson.hudson.plugins.gerrit.trigger.utils;

import com.sonyericsson.hudson.plugins.gerrit.trigger.VerdictCategory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.ReplicationConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.BuildCancellationPolicy;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.Notify;
import com.sonymobile.tools.gerrit.gerritevents.ssh.Authentication;
import com.sonymobile.tools.gerrit.gerritevents.watchdog.WatchTimeExceptionData;

import hudson.util.Secret;
import net.sf.json.JSONObject;

import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;

import java.io.File;
import java.util.List;

/**
 * Created by escoheb on 11/14/14.
 */
public class MockPluginCheckerConfig implements IGerritHudsonTriggerConfig {

    private String frontEndUrl;
    private String gerritHttpUser;
    private String gerritHttpPassword;
    private boolean isUseRestApi;

    /**
     * Sets gerritHttpPassword.
     *
     * @param gerritHttpPassword the password
     * @see #getGerritHttpPassword()
     */
    public void setGerritHttpPassword(String gerritHttpPassword) {
        this.gerritHttpPassword = gerritHttpPassword;
    }

    /**
     * Sets gerritHttpUserName.
     *
     * @param gerritHttpUserName the username
     * @see #getGerritHttpUserName()
     */
    public void setGerritHttpUserName(String gerritHttpUserName) {
        this.gerritHttpUser = gerritHttpUserName;
    }

    /**
     * GerritFrontEndURL.
     *
     * @param gerritFrontEndURL the URL
     * @see #getGerritFrontEndUrl()
     */
    public void setGerritFrontEndURL(String gerritFrontEndURL) {
        this.frontEndUrl = gerritFrontEndURL;
    }

    @Override
    public boolean isGerritBuildCurrentPatchesOnly() {
        return false;
    }

    @Override
    public String getGerritEMail() {
        return null;
    }

    @Override
    public String getGerritFrontEndUrl() {
        return frontEndUrl;
    }

    @Override
    public Credentials getHttpCredentials() {
        return new UsernamePasswordCredentials(gerritHttpUser, gerritHttpPassword);
    }

    @Override
    public String getGerritCmdBuildStarted() {
        return null;
    }

    @Override
    public String getGerritCmdBuildSuccessful() {
        return null;
    }

    @Override
    public String getGerritCmdBuildFailed() {
        return null;
    }

    @Override
    public String getGerritCmdBuildUnstable() {
        return null;
    }

    @Override
    public String getGerritCmdBuildNotBuilt() {
        return null;
    }

    @Override
    public Integer getGerritBuildStartedVerifiedValue() {
        return 0;
    }

    @Override
    public Integer getGerritBuildStartedCodeReviewValue() {
        return 0;
    }

    @Override
    public Integer getGerritBuildSuccessfulVerifiedValue() {
        return 0;
    }

    @Override
    public Integer getGerritBuildSuccessfulCodeReviewValue() {
        return 0;
    }

    @Override
    public Integer getGerritBuildFailedVerifiedValue() {
        return 0;
    }

    @Override
    public Integer getGerritBuildFailedCodeReviewValue() {
        return 0;
    }

    @Override
    public Integer getGerritBuildUnstableVerifiedValue() {
        return 0;
    }

    @Override
    public Integer getGerritBuildUnstableCodeReviewValue() {
        return 0;
    }

    @Override
    public Integer getGerritBuildNotBuiltVerifiedValue() {
        return 0;
    }

    @Override
    public Integer getGerritBuildNotBuiltCodeReviewValue() {
        return 0;
    }

    @Override
    public void setValues(JSONObject form) {

    }

    @Override
    public String getGerritFrontEndUrlFor(String number, String revision) {
        return null;
    }

    @Override
    public String getGerritFrontEndUrlFor(GerritTriggeredEvent event) {
        return null;
    }

    @Override
    public List<VerdictCategory> getCategories() {
        return null;
    }

    @Override
    public void setCategories(List<VerdictCategory> categories) {

    }

    @Override
    public boolean isEnableManualTrigger() {
        return false;
    }

    @Override
    public int getBuildScheduleDelay() {
        return 0;
    }

    @Override
    public int getDynamicConfigRefreshInterval() {
        return 0;
    }

    @Override
    public boolean hasDefaultValues() {
        return false;
    }

    @Override
    public boolean isEnablePluginMessages() {
        return false;
    }

    @Override
    public boolean isTriggerOnAllComments() { return true; }

    @Override
    public boolean isUseRestApi() {
        return isUseRestApi;
    }

    @Override
    public Secret getGerritHttpSecretPassword() {
        return null;
    }

    @Override
    public String getGerritHttpPassword() {
        return gerritHttpPassword;
    }

    @Override
    public boolean isRestCodeReview() {
        return false;
    }

    @Override
    public boolean isRestVerified() {
        return false;
    }

    @Override
    public String getGerritHttpUserName() {
        return gerritHttpUser;
    }

    @Override
    public ReplicationConfig getReplicationConfig() {
        return null;
    }

    @Override
    public void setNumberOfSendingWorkerThreads(int numberOfSendingWorkerThreads) {

    }

    @Override
    public int getNumberOfReceivingWorkerThreads() {
        return 0;
    }

    @Override
    public int getNumberOfSendingWorkerThreads() {
        return 0;
    }

    @Override
    public Notify getNotificationLevel() {
        return null;
    }

    @Override
    public Secret getGerritAuthKeyFileSecretPassword() {
        return null;
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
        return null;
    }

    @Override
    public File getGerritAuthKeyFile() {
        return null;
    }

    @Override
    public String getGerritAuthKeyFilePassword() {
        return null;
    }

    @Override
    public String getGerritHostName() {
        return null;
    }

    @Override
    public int getGerritSshPort() {
        return 0;
    }

    @Override
    public String getGerritUserName() {
        return null;
    }

    @Override
    public Authentication getGerritAuthentication() {
        return null;
    }

    @Override
    public String getGerritProxy() {
        return null;
    }

    /**
     * Set the REST API flag.
     * @param b use rest API.
     */
    public void setUseRestApi(boolean b) {
        this.isUseRestApi = b;
    }

    @Override
    public BuildCancellationPolicy getBuildCurrentPatchesOnly() {
        return null;
    }

    @Override
    public int getProjectListRefreshInterval() {
        return 0;
    }

    @Override
    public boolean isEnableProjectAutoCompletion() {
        return false;
    }

    @Override
    public int getProjectListFetchDelay() {
        return 0;
    }
}
