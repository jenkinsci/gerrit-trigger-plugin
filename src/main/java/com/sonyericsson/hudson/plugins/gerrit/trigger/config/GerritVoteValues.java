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
 * Holds the 12 default vote values (verified and code-review) for each build outcome.
 * Extracted from {@link Config} to reduce class size and improve maintainability.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritVoteValues {

    /**
     * Default verified vote to Gerrit when a build is started.
     */
    public static final int DEFAULT_GERRIT_BUILD_STARTED_VERIFIED_VALUE = 0;
    /**
     * Default verified vote to Gerrit when a build is unstable.
     */
    public static final int DEFAULT_GERRIT_BUILD_UNSTABLE_VERIFIED_VALUE = 0;
    /**
     * Default verified vote to Gerrit when a build is failed.
     */
    public static final int DEFAULT_GERRIT_BUILD_FAILURE_VERIFIED_VALUE = -1;
    /**
     * Default verified vote to Gerrit when a build is successful.
     */
    public static final int DEFAULT_GERRIT_BUILD_SUCCESSFUL_VERIFIED_VALUE = 1;
    /**
     * Default verified vote to Gerrit when a build is not built.
     */
    public static final int DEFAULT_GERRIT_BUILD_NOT_BUILT_VERIFIED_VALUE = 0;
    /**
     * Default verified vote to Gerrit when a build is aborted.
     */
    public static final int DEFAULT_GERRIT_BUILD_ABORTED_VERIFIED_VALUE = 0;
    /**
     * Default code review vote to Gerrit when a build is started.
     */
    public static final int DEFAULT_GERRIT_BUILD_STARTED_CODE_REVIEW_VALUE = 0;
    /**
     * Default code review vote to Gerrit when a build is unstable.
     */
    public static final int DEFAULT_GERRIT_BUILD_UNSTABLE_CODE_REVIEW_VALUE = -1;
    /**
     * Default code review vote to Gerrit when a build is failed.
     */
    public static final int DEFAULT_GERRIT_BUILD_FAILURE_CODE_REVIEW_VALUE = 0;
    /**
     * Default code review vote to Gerrit when a build is successful.
     */
    public static final int DEFAULT_GERRIT_BUILD_SUCCESSFUL_CODE_REVIEW_VALUE = 0;
    /**
     * Default code review vote to Gerrit when a build is not built.
     */
    public static final int DEFAULT_GERRIT_BUILD_NOT_BUILT_CODE_REVIEW_VALUE = 0;
    /**
     * Default code review vote to Gerrit when a build is aborted.
     */
    public static final int DEFAULT_GERRIT_BUILD_ABORTED_CODE_REVIEW_VALUE = 0;

    private Integer buildStartedVerifiedValue;
    private Integer buildSuccessfulVerifiedValue;
    private Integer buildFailedVerifiedValue;
    private Integer buildUnstableVerifiedValue;
    private Integer buildNotBuiltVerifiedValue;
    private Integer buildAbortedVerifiedValue;
    private Integer buildStartedCodeReviewValue;
    private Integer buildSuccessfulCodeReviewValue;
    private Integer buildFailedCodeReviewValue;
    private Integer buildUnstableCodeReviewValue;
    private Integer buildNotBuiltCodeReviewValue;
    private Integer buildAbortedCodeReviewValue;

    /**
     * Default constructor. Sets all vote values to their defaults.
     */
    public GerritVoteValues() {
        setDefaults();
    }

    /**
     * Constructs vote values from a JSON object.
     *
     * @param formData the JSON object with form data.
     */
    public GerritVoteValues(JSONObject formData) {
        setValues(formData);
    }

    /**
     * Copy constructor.
     *
     * @param source the GerritVoteValues to copy from.
     */
    public GerritVoteValues(GerritVoteValues source) {
        this.buildStartedVerifiedValue = source.buildStartedVerifiedValue;
        this.buildSuccessfulVerifiedValue = source.buildSuccessfulVerifiedValue;
        this.buildFailedVerifiedValue = source.buildFailedVerifiedValue;
        this.buildUnstableVerifiedValue = source.buildUnstableVerifiedValue;
        this.buildNotBuiltVerifiedValue = source.buildNotBuiltVerifiedValue;
        this.buildAbortedVerifiedValue = source.buildAbortedVerifiedValue;
        this.buildStartedCodeReviewValue = source.buildStartedCodeReviewValue;
        this.buildSuccessfulCodeReviewValue = source.buildSuccessfulCodeReviewValue;
        this.buildFailedCodeReviewValue = source.buildFailedCodeReviewValue;
        this.buildUnstableCodeReviewValue = source.buildUnstableCodeReviewValue;
        this.buildNotBuiltCodeReviewValue = source.buildNotBuiltCodeReviewValue;
        this.buildAbortedCodeReviewValue = source.buildAbortedCodeReviewValue;
    }

    /**
     * Factory method to create a GerritVoteValues from an {@link IGerritHudsonTriggerConfig}.
     *
     * @param config the config to copy vote values from.
     * @return a new GerritVoteValues instance.
     */
    public static GerritVoteValues fromConfig(IGerritHudsonTriggerConfig config) {
        GerritVoteValues v = new GerritVoteValues();
        v.buildStartedVerifiedValue = config.getGerritBuildStartedVerifiedValue();
        v.buildStartedCodeReviewValue = config.getGerritBuildStartedCodeReviewValue();
        v.buildSuccessfulVerifiedValue = config.getGerritBuildSuccessfulVerifiedValue();
        v.buildSuccessfulCodeReviewValue = config.getGerritBuildSuccessfulCodeReviewValue();
        v.buildFailedVerifiedValue = config.getGerritBuildFailedVerifiedValue();
        v.buildFailedCodeReviewValue = config.getGerritBuildFailedCodeReviewValue();
        v.buildUnstableVerifiedValue = config.getGerritBuildUnstableVerifiedValue();
        v.buildUnstableCodeReviewValue = config.getGerritBuildUnstableCodeReviewValue();
        v.buildNotBuiltVerifiedValue = config.getGerritBuildNotBuiltVerifiedValue();
        v.buildNotBuiltCodeReviewValue = config.getGerritBuildNotBuiltCodeReviewValue();
        v.buildAbortedVerifiedValue = config.getGerritBuildAbortedVerifiedValue();
        v.buildAbortedCodeReviewValue = config.getGerritBuildAbortedCodeReviewValue();
        return v;
    }

    /**
     * Sets all vote values from the provided JSONObject.
     * When formData is empty, all defaults are applied.
     *
     * @param formData the JSON object with form data.
     */
    public void setValues(JSONObject formData) {
        if (formData.isEmpty()) {
            setDefaults();
        } else {
            buildStartedVerifiedValue = getValueFromFormData(formData, "gerritBuildStartedVerifiedValue");
            buildSuccessfulVerifiedValue = getValueFromFormData(formData, "gerritBuildSuccessfulVerifiedValue");
            buildFailedVerifiedValue = getValueFromFormData(formData, "gerritBuildFailedVerifiedValue");
            buildUnstableVerifiedValue = getValueFromFormData(formData, "gerritBuildUnstableVerifiedValue");
            buildNotBuiltVerifiedValue = getValueFromFormData(formData, "gerritBuildNotBuiltVerifiedValue");
            buildAbortedVerifiedValue = getValueFromFormData(formData, "gerritBuildAbortedVerifiedValue");
            buildStartedCodeReviewValue = getValueFromFormData(formData, "gerritBuildStartedCodeReviewValue");
            buildSuccessfulCodeReviewValue = getValueFromFormData(formData,
                    "gerritBuildSuccessfulCodeReviewValue");
            buildFailedCodeReviewValue = getValueFromFormData(formData, "gerritBuildFailedCodeReviewValue");
            buildUnstableCodeReviewValue = getValueFromFormData(formData, "gerritBuildUnstableCodeReviewValue");
            buildNotBuiltCodeReviewValue = getValueFromFormData(formData, "gerritBuildNotBuiltCodeReviewValue");
            buildAbortedCodeReviewValue = getValueFromFormData(formData, "gerritBuildAbortedCodeReviewValue");
        }
    }

    /**
     * Obtain value from a key in formdata.
     *
     * @param formData JSONObject.
     * @param key      key to extract value for.
     * @return value.
     */
    private Integer getValueFromFormData(JSONObject formData, String key) {
        if (formData.has(key)) {
            String testData = formData.optString(key);
            if (testData == null || testData.isEmpty()) {
                return null;
            } else {
                try {
                    return Integer.parseInt(testData);
                } catch (NumberFormatException nfe) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Sets all 12 vote values to their defaults.
     */
    private void setDefaults() {
        buildStartedVerifiedValue = DEFAULT_GERRIT_BUILD_STARTED_VERIFIED_VALUE;
        buildSuccessfulVerifiedValue = DEFAULT_GERRIT_BUILD_SUCCESSFUL_VERIFIED_VALUE;
        buildFailedVerifiedValue = DEFAULT_GERRIT_BUILD_FAILURE_VERIFIED_VALUE;
        buildUnstableVerifiedValue = DEFAULT_GERRIT_BUILD_UNSTABLE_VERIFIED_VALUE;
        buildNotBuiltVerifiedValue = DEFAULT_GERRIT_BUILD_NOT_BUILT_VERIFIED_VALUE;
        buildAbortedVerifiedValue = DEFAULT_GERRIT_BUILD_ABORTED_VERIFIED_VALUE;
        buildStartedCodeReviewValue = DEFAULT_GERRIT_BUILD_STARTED_CODE_REVIEW_VALUE;
        buildSuccessfulCodeReviewValue = DEFAULT_GERRIT_BUILD_SUCCESSFUL_CODE_REVIEW_VALUE;
        buildFailedCodeReviewValue = DEFAULT_GERRIT_BUILD_FAILURE_CODE_REVIEW_VALUE;
        buildUnstableCodeReviewValue = DEFAULT_GERRIT_BUILD_UNSTABLE_CODE_REVIEW_VALUE;
        buildNotBuiltCodeReviewValue = DEFAULT_GERRIT_BUILD_NOT_BUILT_CODE_REVIEW_VALUE;
        buildAbortedCodeReviewValue = DEFAULT_GERRIT_BUILD_ABORTED_CODE_REVIEW_VALUE;
    }

    // ---- Getters ----

    /**
     * Get build started verified value.
     * @return the value.
     */
    public Integer getBuildStartedVerifiedValue() {
        return buildStartedVerifiedValue;
    }

    /**
     * Get build started code review value.
     * @return the value.
     */
    public Integer getBuildStartedCodeReviewValue() {
        return buildStartedCodeReviewValue;
    }

    /**
     * Get build successful verified value.
     * @return the value.
     */
    public Integer getBuildSuccessfulVerifiedValue() {
        return buildSuccessfulVerifiedValue;
    }

    /**
     * Get build successful code review value.
     * @return the value.
     */
    public Integer getBuildSuccessfulCodeReviewValue() {
        return buildSuccessfulCodeReviewValue;
    }

    /**
     * Get build failed verified value.
     * @return the value.
     */
    public Integer getBuildFailedVerifiedValue() {
        return buildFailedVerifiedValue;
    }

    /**
     * Get build failed code review value.
     * @return the value.
     */
    public Integer getBuildFailedCodeReviewValue() {
        return buildFailedCodeReviewValue;
    }

    /**
     * Get build unstable verified value.
     * @return the value.
     */
    public Integer getBuildUnstableVerifiedValue() {
        return buildUnstableVerifiedValue;
    }

    /**
     * Get build unstable code review value.
     * @return the value.
     */
    public Integer getBuildUnstableCodeReviewValue() {
        return buildUnstableCodeReviewValue;
    }

    /**
     * Get build not built verified value.
     * @return the value.
     */
    public Integer getBuildNotBuiltVerifiedValue() {
        return buildNotBuiltVerifiedValue;
    }

    /**
     * Get build not built code review value.
     * @return the value.
     */
    public Integer getBuildNotBuiltCodeReviewValue() {
        return buildNotBuiltCodeReviewValue;
    }

    /**
     * Get build aborted verified value.
     * @return the value.
     */
    public Integer getBuildAbortedVerifiedValue() {
        return buildAbortedVerifiedValue;
    }

    /**
     * Get build aborted code review value.
     * @return the value.
     */
    public Integer getBuildAbortedCodeReviewValue() {
        return buildAbortedCodeReviewValue;
    }

    // ---- Setters (needed for JCasC / configuration-as-code binding) ----

    /**
     * Set build started verified value.
     * @param value the value.
     */
    public void setBuildStartedVerifiedValue(Integer value) {
        this.buildStartedVerifiedValue = value;
    }

    /**
     * Set build started code review value.
     * @param value the value.
     */
    public void setBuildStartedCodeReviewValue(Integer value) {
        this.buildStartedCodeReviewValue = value;
    }

    /**
     * Set build successful verified value.
     * @param value the value.
     */
    public void setBuildSuccessfulVerifiedValue(Integer value) {
        this.buildSuccessfulVerifiedValue = value;
    }

    /**
     * Set build successful code review value.
     * @param value the value.
     */
    public void setBuildSuccessfulCodeReviewValue(Integer value) {
        this.buildSuccessfulCodeReviewValue = value;
    }

    /**
     * Set build failed verified value.
     * @param value the value.
     */
    public void setBuildFailedVerifiedValue(Integer value) {
        this.buildFailedVerifiedValue = value;
    }

    /**
     * Set build failed code review value.
     * @param value the value.
     */
    public void setBuildFailedCodeReviewValue(Integer value) {
        this.buildFailedCodeReviewValue = value;
    }

    /**
     * Set build unstable verified value.
     * @param value the value.
     */
    public void setBuildUnstableVerifiedValue(Integer value) {
        this.buildUnstableVerifiedValue = value;
    }

    /**
     * Set build unstable code review value.
     * @param value the value.
     */
    public void setBuildUnstableCodeReviewValue(Integer value) {
        this.buildUnstableCodeReviewValue = value;
    }

    /**
     * Set build not built verified value.
     * @param value the value.
     */
    public void setBuildNotBuiltVerifiedValue(Integer value) {
        this.buildNotBuiltVerifiedValue = value;
    }

    /**
     * Set build not built code review value.
     * @param value the value.
     */
    public void setBuildNotBuiltCodeReviewValue(Integer value) {
        this.buildNotBuiltCodeReviewValue = value;
    }

    /**
     * Set build aborted verified value.
     * @param value the value.
     */
    public void setBuildAbortedVerifiedValue(Integer value) {
        this.buildAbortedVerifiedValue = value;
    }

    /**
     * Set build aborted code review value.
     * @param value the value.
     */
    public void setBuildAbortedCodeReviewValue(Integer value) {
        this.buildAbortedCodeReviewValue = value;
    }
}
