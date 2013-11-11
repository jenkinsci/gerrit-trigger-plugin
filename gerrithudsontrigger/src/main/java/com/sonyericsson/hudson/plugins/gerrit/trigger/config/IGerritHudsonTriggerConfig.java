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
package com.sonyericsson.hudson.plugins.gerrit.trigger.config;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritConnectionConfig2;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.VerdictCategory;
import net.sf.json.JSONObject;

import java.util.List;

/**
 * Interface for the Global configuration.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public interface IGerritHudsonTriggerConfig extends GerritConnectionConfig2 {

    /**
     * If enabled, then old patch revision builds will be canceled.
     * @return true if so.
     */
    boolean isGerritBuildCurrentPatchesOnly();

    /**
     * Base URL for the Gerrit UI.
     * @return the gerrit front end URL. Always ends with '/'
     */
    String getGerritFrontEndUrl();

    /**
     * The command template to use when sending build-started messages to Gerrit.
     * @return the command template.
     */
    String getGerritCmdBuildStarted();

    /**
     * The command template to use when sending build-successful messages to Gerrit.
     * @return the command template.
     */
    String getGerritCmdBuildSuccessful();

    /**
     * The command template to use when sending build-failed messages to Gerrit.
     * @return the command template.
     */
    String getGerritCmdBuildFailed();

    /**
     * The command template to use when sending build-unstable messages to Gerrit.
     * @return the command template.
     */
    String getGerritCmdBuildUnstable();

    /**
     * The command template to use when sending build-not-built messages to Gerrit.
     * @return the command template.
     */
    String getGerritCmdBuildNotBuilt();

    /**
     * The default verified value for build started.
     * @return the value.
     */
    int getGerritBuildStartedVerifiedValue();

    /**
     * The default code review value for build started.
     * @return the value.
     */
    int getGerritBuildStartedCodeReviewValue();

    /**
     * The default verified value for build successful.
     * @return the falue.
     */
    int getGerritBuildSuccessfulVerifiedValue();

    /**
     * The default code review value for build successful.
     * @return the value.
     */
    int getGerritBuildSuccessfulCodeReviewValue();

    /**
     * The default verified value for build failed.
     * @return the value.
     */
    int getGerritBuildFailedVerifiedValue();

    /**
     * The default code review value for build failed.
     * @return the value.
     */
    int getGerritBuildFailedCodeReviewValue();

    /**
     * The default verified value for build unstable.
     * @return the value.
     */
    int getGerritBuildUnstableVerifiedValue();

    /**
     * The default code review value for build unstable.
     * @return the value.
     */
    int getGerritBuildUnstableCodeReviewValue();

    /**
     * The default verified value for build not built.
     * @return the value.
     */
    int getGerritBuildNotBuiltVerifiedValue();

    /**
     * The default code review value for build not built.
     * @return the value.
     */
    int getGerritBuildNotBuiltCodeReviewValue();

    /**
     * Sets all config values from the provided JSONObject.
     * @param form the JSON object with form data.
     */
    void setValues(JSONObject form);

    /**
     * Creates a URL to the provided changeset number.
     * @param number the changeset number
     * @param revision the patch set number (currently not used)
     * @return a URL based on {@link #getGerritFrontEndUrl() } + / + number
     * @see #getGerritFrontEndUrl()
     */
    String getGerritFrontEndUrlFor(String number, String revision);

    /**
     * Creates a URL to the provided changeset number.
     * @param event the gerrit triggered event
     * @return a URL based on frontUrl + / + number
     * @see #getGerritFrontEndUrlFor(String, String)
     */
    String getGerritFrontEndUrlFor(GerritTriggeredEvent event);

    /**
     * Get the list of available VerdictCategories.
     * @return the list.
     */
    List<VerdictCategory> getCategories();

    /**
     * Set the list of available VerdictCategories.
     * @param categories the list.
     */
    void setCategories(List<VerdictCategory> categories);

    /**
     * If the manual trigger is enabled (shown to users) or not.
     * @return true if so.
     */
    boolean isEnableManualTrigger();

    /**
     * Returns the BuildScheduleDelay.
     * @return the value.
     */
   int getBuildScheduleDelay();

    /**
     * Returns the dynamicConfigRefreshInterval.
     * @return the value.
     */
    int getDynamicConfigRefreshInterval();

    /**
     * If the plugin still has default values for hostname and frontendurl.
     * @return true if so.
     */
    boolean hasDefaultValues();

    /**
     * If other plugins are allowed to contribute messages to be forwarded
     * to Gerrit.
     * @return true if so
     */
    boolean isEnablePluginMessages();

    /**
     * If the HTTP REST API should be used for change approval instead of the sh API.
     *
     * @return true if so.
     */
    boolean isUseRestApi();

    /**
     * The password for the HTTP REST API.
     *
     * @return the password
     */
    String getGerritHttpPassword();

    /**
     * The user name for the HTTP REST API.
     *
     * @return username
     */
    String getGerritHttpUserName();
}
