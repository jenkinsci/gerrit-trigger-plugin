package com.sonyericsson.hudson.plugins.gerrit.trigger.mock;

import java.io.File;

import hudson.util.Secret;

/**
 * Mock class of a Config for ProjectListTest.
 */
public class MockConfigForProjectListTest extends MockGerritHudsonTriggerConfig {

    //CS IGNORE MagicNumber FOR NEXT 100 LINES. REASON: Mock object.
    private File authKey;
    private static final int SSHPORT = 29418;

    @Override
    public File getGerritAuthKeyFile() {
        return authKey;
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
        return 1;
    }

    @Override
    public int getProjectListRefreshInterval() {
        return 1;
    }

    /**
     * Set auth key.
     * @param key auth file.
     */
    public void setGerritAuthKeyFile(File key) {
        this.authKey = key;
    }

    @Override
    public String getGerritAuthKeyFilePassword() {
        return "";
    }

    @Override
    public Secret getGerritAuthKeyFileSecretPassword() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getGerritFrontEndUrl() {
        return "http://localhost:8089/";
    }

    @Override
    public String getGerritHostName() {
        return "localhost";
    }

    @Override
    public int getGerritSshPort() {
        return SSHPORT;
    }

    @Override
    public String getGerritProxy() {
        return "";
    }

    @Override
    public String getGerritUserName() {
        return "";
    }

    @Override
    public String getGerritEMail() {
        return "";
    }

}
