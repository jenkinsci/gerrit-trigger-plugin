/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Mobile Communications Inc. All rights reserved.
 *  Copyright 2026 Amarula Solutions. All rights reserved.
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

import net.sf.json.JSONObject;

/**
 * Holds the 6 Gerrit review command template strings for each build outcome.
 * Extracted from {@link Config} to reduce class size and improve maintainability.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritCommandTemplates {

    /** Default command for build started. */
    public static final String DEFAULT_CMD_BUILD_STARTED =
            "gerrit review --project <GERRIT_NAME> <CHANGE>,<PATCHSET> "
                    + "--message 'Build Started <BUILDURL> <STARTED_STATS>' "
                    + "--verified <VERIFIED> --code-review <CODE_REVIEW> --tag " + Constants.TAG_VALUE;

    /** Default command for build successful. */
    public static final String DEFAULT_CMD_BUILD_SUCCESSFUL =
            "gerrit review --project <GERRIT_NAME> <CHANGE>,<PATCHSET> "
                    + "--message 'Build Successful <BUILDS_STATS>' "
                    + "--verified <VERIFIED> --code-review <CODE_REVIEW> --tag " + Constants.TAG_VALUE;

    /** Default command for build failed. */
    public static final String DEFAULT_CMD_BUILD_FAILED =
            "gerrit review --project <GERRIT_NAME> <CHANGE>,<PATCHSET> "
                    + "--message 'Build Failed <BUILDS_STATS>' "
                    + "--verified <VERIFIED> --code-review <CODE_REVIEW> --tag " + Constants.TAG_VALUE;

    /** Default command for build unstable. */
    public static final String DEFAULT_CMD_BUILD_UNSTABLE =
            "gerrit review --project <GERRIT_NAME> <CHANGE>,<PATCHSET> "
                    + "--message 'Build Unstable <BUILDS_STATS>' "
                    + "--verified <VERIFIED> --code-review <CODE_REVIEW> --tag " + Constants.TAG_VALUE;

    /** Default command for build not built. */
    public static final String DEFAULT_CMD_BUILD_NOT_BUILT =
            "gerrit review  --project <GERRIT_NAME> <CHANGE>,<PATCHSET> "
                    + "--message 'No Builds Executed <BUILDS_STATS>' "
                    + "--verified <VERIFIED> --code-review <CODE_REVIEW> --tag " + Constants.TAG_VALUE;

    /** Default command for build aborted. */
    public static final String DEFAULT_CMD_BUILD_ABORTED =
            "gerrit review  --project <GERRIT_NAME> <CHANGE>,<PATCHSET> "
                    + "--message 'Build Aborted <BUILDS_STATS>' "
                    + "--verified <VERIFIED> --code-review <CODE_REVIEW> --tag " + Constants.TAG_VALUE;

    private String buildStartedCommand;
    private String buildSuccessfulCommand;
    private String buildFailedCommand;
    private String buildUnstableCommand;
    private String buildNotBuiltCommand;
    private String buildAbortedCommand;

    /**
     * Default constructor. Sets all command templates to their defaults.
     */
    public GerritCommandTemplates() {
        setDefaults();
    }

    /**
     * Constructs command templates from a JSON object.
     *
     * @param formData the JSON object with form data.
     */
    public GerritCommandTemplates(JSONObject formData) {
        setValues(formData);
    }

    /**
     * Copy constructor.
     *
     * @param source the GerritCommandTemplates to copy from.
     */
    public GerritCommandTemplates(GerritCommandTemplates source) {
        this.buildStartedCommand = source.buildStartedCommand;
        this.buildSuccessfulCommand = source.buildSuccessfulCommand;
        this.buildFailedCommand = source.buildFailedCommand;
        this.buildUnstableCommand = source.buildUnstableCommand;
        this.buildNotBuiltCommand = source.buildNotBuiltCommand;
        this.buildAbortedCommand = source.buildAbortedCommand;
    }

    /**
     * Factory method to create a GerritCommandTemplates from an {@link IGerritHudsonTriggerConfig}.
     *
     * @param config the config to copy command templates from.
     * @return a new GerritCommandTemplates instance.
     */
    public static GerritCommandTemplates fromConfig(IGerritHudsonTriggerConfig config) {
        GerritCommandTemplates c = new GerritCommandTemplates();
        c.buildStartedCommand = config.getGerritCmdBuildStarted();
        c.buildSuccessfulCommand = config.getGerritCmdBuildSuccessful();
        c.buildFailedCommand = config.getGerritCmdBuildFailed();
        c.buildUnstableCommand = config.getGerritCmdBuildUnstable();
        c.buildNotBuiltCommand = config.getGerritCmdBuildNotBuilt();
        c.buildAbortedCommand = config.getGerritCmdBuildAborted();
        return c;
    }

    /**
     * Sets all command templates from the provided JSONObject.
     *
     * @param formData the JSON object with form data.
     */
    public void setValues(JSONObject formData) {
        buildStartedCommand = formData.optString(
                "gerritVerifiedCmdBuildStarted",
                DEFAULT_CMD_BUILD_STARTED);
        buildFailedCommand = formData.optString(
                "gerritVerifiedCmdBuildFailed",
                DEFAULT_CMD_BUILD_FAILED);
        buildSuccessfulCommand = formData.optString(
                "gerritVerifiedCmdBuildSuccessful",
                DEFAULT_CMD_BUILD_SUCCESSFUL);
        buildUnstableCommand = formData.optString(
                "gerritVerifiedCmdBuildUnstable",
                DEFAULT_CMD_BUILD_UNSTABLE);
        buildNotBuiltCommand = formData.optString(
                "gerritVerifiedCmdBuildNotBuilt",
                DEFAULT_CMD_BUILD_NOT_BUILT);
        buildAbortedCommand = formData.optString(
                "gerritVerifiedCmdBuildAborted",
                DEFAULT_CMD_BUILD_ABORTED);
    }

    /**
     * Sets all 6 command templates to their default values.
     */
    private void setDefaults() {
        buildStartedCommand = DEFAULT_CMD_BUILD_STARTED;
        buildSuccessfulCommand = DEFAULT_CMD_BUILD_SUCCESSFUL;
        buildFailedCommand = DEFAULT_CMD_BUILD_FAILED;
        buildUnstableCommand = DEFAULT_CMD_BUILD_UNSTABLE;
        buildNotBuiltCommand = DEFAULT_CMD_BUILD_NOT_BUILT;
        buildAbortedCommand = DEFAULT_CMD_BUILD_ABORTED;
    }

    /**
     * Backward compatibility: when upgrading from an older version that didn't have
     * the aborted build command, copy the failed build command as default.
     *
     * @return the resolved instance.
     */
    Object readResolve() {
        if (buildAbortedCommand == null && buildFailedCommand != null) {
            buildAbortedCommand = buildFailedCommand;
        }
        return this;
    }

    // ---- Getters ----

    /**
     * Get build started command template.
     * @return the command template.
     */
    public String getCmdBuildStarted() {
        return buildStartedCommand;
    }

    /**
     * Get build successful command template.
     * @return the command template.
     */
    public String getCmdBuildSuccessful() {
        return buildSuccessfulCommand;
    }

    /**
     * Get build failed command template.
     * @return the command template.
     */
    public String getCmdBuildFailed() {
        return buildFailedCommand;
    }

    /**
     * Get build unstable command template.
     * @return the command template.
     */
    public String getCmdBuildUnstable() {
        return buildUnstableCommand;
    }

    /**
     * Get build not built command template.
     * @return the command template.
     */
    public String getCmdBuildNotBuilt() {
        return buildNotBuiltCommand;
    }

    /**
     * Get build aborted command template.
     * @return the command template.
     */
    public String getCmdBuildAborted() {
        return buildAbortedCommand;
    }

    // ---- Setters (needed for JCasC / configuration-as-code binding) ----

    /**
     * Set build started command template.
     * @param value the command template.
     */
    public void setCmdBuildStarted(String value) {
        this.buildStartedCommand = value;
    }

    /**
     * Set build successful command template.
     * @param value the command template.
     */
    public void setCmdBuildSuccessful(String value) {
        this.buildSuccessfulCommand = value;
    }

    /**
     * Set build failed command template.
     * @param value the command template.
     */
    public void setCmdBuildFailed(String value) {
        this.buildFailedCommand = value;
    }

    /**
     * Set build unstable command template.
     * @param value the command template.
     */
    public void setCmdBuildUnstable(String value) {
        this.buildUnstableCommand = value;
    }

    /**
     * Set build not built command template.
     * @param value the command template.
     */
    public void setCmdBuildNotBuilt(String value) {
        this.buildNotBuiltCommand = value;
    }

    /**
     * Set build aborted command template.
     * @param value the command template.
     */
    public void setCmdBuildAborted(String value) {
        this.buildAbortedCommand = value;
    }
}
