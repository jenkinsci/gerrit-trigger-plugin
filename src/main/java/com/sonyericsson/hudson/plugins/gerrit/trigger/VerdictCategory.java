/*
 *  The MIT License
 *
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
package com.sonyericsson.hudson.plugins.gerrit.trigger;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import net.sf.json.JSONObject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;

/**
 * A verdict category for setting comments in Gerrit, i.e. code-review, verify
 *
 * @author Tomas Westling &lt;tomas.westling@sonymobile.com&gt;
 */
public class VerdictCategory extends AbstractDescribableImpl<VerdictCategory> {

    private String verdictValue;
    private String verdictDescription;
    private Integer defaultBuildStartedReportingValue;
    private Integer defaultBuildSuccessfulReportingValue;
    private Integer defaultBuildFailedReportingValue;
    private Integer defaultBuildUnstableReportingValue;
    private Integer defaultBuildNotBuiltReportingValue;

    /**
     * Standard constructor.
     *
     * @param value       the value in Gerrit for the verdict category.
     * @param description the text describing the verdict category.
     */
    public VerdictCategory(String value, String description) {
        verdictValue = value;
        verdictDescription = description;
        defaultBuildStartedReportingValue = 0;
        defaultBuildSuccessfulReportingValue = 0;
        defaultBuildFailedReportingValue = 0;
        defaultBuildUnstableReportingValue = 0;
        defaultBuildNotBuiltReportingValue = 0;
    }

    public VerdictCategory(String verdictValue, String verdictDescription,
        Integer defaultBuildStartedReportingValue,
        Integer defaultBuildSuccessfulReportingValue,
        Integer defaultBuildFailedReportingValue,
        Integer defaultBuildUnstableReportingValue,
        Integer defaultBuildNotBuiltReportingValue) {
        this.verdictValue = verdictValue;
        this.verdictDescription = verdictDescription;
        this.defaultBuildStartedReportingValue = defaultBuildStartedReportingValue;
        this.defaultBuildSuccessfulReportingValue = defaultBuildSuccessfulReportingValue;
        this.defaultBuildFailedReportingValue = defaultBuildFailedReportingValue;
        this.defaultBuildUnstableReportingValue = defaultBuildUnstableReportingValue;
        this.defaultBuildNotBuiltReportingValue = defaultBuildNotBuiltReportingValue;
    }

    /**
     * Creates a VerdictCategory from a JSONObject.
     *
     * @param obj the JSONObject.
     * @return a VerdictCategory.
     */
    public static VerdictCategory fromJSON(JSONObject obj, JSONObject topLevelObj) {
        String value = obj.getString("verdictValue");
        String description = obj.getString("verdictDescription");
        Integer defaultBuildStartedReportingValue = topLevelObj.containsKey(value + "Started") ? Config.getIntegerFromString(topLevelObj.getString(value + "Started")) : null;
        Integer defaultBuildSuccessfulReportingValue = topLevelObj.containsKey(value + "Successful") ? Config.getIntegerFromString(topLevelObj.getString(value + "Successful")) : null;
        Integer defaultBuildFailedReportingValue = topLevelObj.containsKey(value + "Failed") ? Config.getIntegerFromString(topLevelObj.getString(value + "Failed")) : null;
        Integer defaultBuildUnstableReportingValue = topLevelObj.containsKey(value + "Unstable") ? Config.getIntegerFromString(topLevelObj.getString(value + "Unstable")) : null;
        Integer defaultBuildNotBuiltReportingValue = topLevelObj.containsKey(value + "Not Built") ? Config.getIntegerFromString(topLevelObj.getString(value + "Not Built")) : null;

        return new VerdictCategory(value, description, defaultBuildStartedReportingValue,
            defaultBuildSuccessfulReportingValue, defaultBuildFailedReportingValue,
            defaultBuildUnstableReportingValue, defaultBuildNotBuiltReportingValue);
    }

    /**
     * Standard getter for the value.
     *
     * @return the value.
     */
    public String getVerdictValue() {
        return verdictValue;
    }

    /**
     * Standard getter for the description.
     *
     * @return the description.
     */
    public String getVerdictDescription() {
        return verdictDescription;
    }

    /**
     * Standard getter for the build started reporting value.
     *
     * @return the description.
     */
    public Integer getDefaultBuildStartedReportingValue() {
        return defaultBuildStartedReportingValue;
    }

    /**
     * Standard setter for the build started reporting value.
     */
    public void setDefaultBuildStartedReportingValue(Integer defaultBuildStartedReportingValue) {
        this.defaultBuildStartedReportingValue = defaultBuildStartedReportingValue;
    }

    /**
     * Standard getter for the build successful reporting value.
     *
     * @return the description.
     */
    public Integer getDefaultBuildSuccessfulReportingValue() {
        return defaultBuildSuccessfulReportingValue;
    }

    /**
     * Standard setter for the build successful reporting value.
     */
    public void setDefaultBuildSuccessfulReportingValue(Integer defaultBuildSuccessfulReportingValue) {
        this.defaultBuildSuccessfulReportingValue = defaultBuildSuccessfulReportingValue;
    }

    /**
     * Standard getter for the build failed reporting value.
     *
     * @return the description.
     */
    public Integer getDefaultBuildFailedReportingValue() {
        return defaultBuildFailedReportingValue;
    }

    /**
     * Standard setter for the build failed reporting value.
     */
    public void setDefaultBuildFailedReportingValue(Integer defaultBuildFailedReportingValue) {
        this.defaultBuildFailedReportingValue = defaultBuildFailedReportingValue;
    }

    /**
     * Standard getter for the build unstable reporting value.
     *
     * @return the description.
     */
    public Integer getDefaultBuildUnstableReportingValue() {
        return defaultBuildUnstableReportingValue;
    }

    /**
     * Standard setter for the build unstable reporting value.
     */
    public void setDefaultBuildUnstableReportingValue(Integer defaultBuildUnstableReportingValue) {
        this.defaultBuildUnstableReportingValue = defaultBuildUnstableReportingValue;
    }

    /**
     * Standard getter for the build not built reporting value.
     *
     * @return the description.
     */
    public Integer getDefaultBuildNotBuiltReportingValue() {
        return defaultBuildNotBuiltReportingValue;
    }

    /**
     * Standard setter for the build not built reporting value.
     */
    public void setDefaultBuildNotBuiltReportingValue(Integer defaultBuildNotBuiltReportingValue) {
        this.defaultBuildNotBuiltReportingValue = defaultBuildNotBuiltReportingValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        VerdictCategory category = (VerdictCategory) o;

        return verdictValue != null ? verdictValue.equals(category.verdictValue) : category.verdictValue == null;
    }

    /**
     * The Descriptor for a VerdictCategory.
     */
    @Extension
    public static class VerdictCategoryDescriptor extends Descriptor<VerdictCategory> {
        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
