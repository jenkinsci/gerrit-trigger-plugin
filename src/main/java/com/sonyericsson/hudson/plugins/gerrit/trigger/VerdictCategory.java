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

/**
 * A verdict category for setting comments in Gerrit, i.e. code-review, verify
 * @author Tomas Westling &lt;tomas.westling@sonymobile.com&gt;
 */
public class VerdictCategory extends AbstractDescribableImpl<VerdictCategory> {

    private String verdictValue;
    private String verdictDescription;
    private int defaultBuildStartedReportingValue;
    private int defaultBuildSuccessfulReportingValue;
    private int defaultBuildFailedReportingValue;
    private int defaultBuildUnstableReportingValue;
    private int defaultBuildNotBuiltReportingValue;

    /**
     * Standard constructor.
     * @param value the value in Gerrit for the verdict category.
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

    public VerdictCategory(String verdictValue, String verdictDescription, int defaultBuildStartedReportingValue, int defaultBuildSuccessfulReportingValue, int defaultBuildFailedReportingValue, int defaultBuildUnstableReportingValue, int defaultBuildNotBuiltReportingValue) {
        this.verdictValue = verdictValue;
        this.verdictDescription = verdictDescription;
        this.defaultBuildStartedReportingValue = defaultBuildStartedReportingValue;
        this.defaultBuildSuccessfulReportingValue = defaultBuildSuccessfulReportingValue;
        this.defaultBuildFailedReportingValue = defaultBuildFailedReportingValue;
        this.defaultBuildUnstableReportingValue = defaultBuildUnstableReportingValue;
        this.defaultBuildNotBuiltReportingValue = defaultBuildNotBuiltReportingValue;
    }

    /**
     * Standard getter for the value.
     * @return the value.
     */
    public String getVerdictValue() {
        return verdictValue;
    }

    /**
     * Standard getter for the description.
     * @return the description.
     */
    public String getVerdictDescription() {
        return verdictDescription;
    }

    /**
     * Creates a VerdictCategory from a JSONObject.
     * @param obj the JSONObject.
     * @return a VerdictCategory.
     */
    public static VerdictCategory createVerdictCategoryFromJSON(JSONObject obj, JSONObject topLevelJSON) {
        String value = obj.getString("verdictValue");
        String description = obj.getString("verdictDescription");
        int defaultBuildStartedReportingValue = topLevelJSON.containsKey(value + "Started") ? topLevelJSON.getInt(value + "Started") : 0;
        int defaultBuildSuccessfulReportingValue = topLevelJSON.containsKey(value + "Successful") ? topLevelJSON.getInt(value + "Successful") : 0;
        int defaultBuildFailedReportingValue = topLevelJSON.containsKey(value + "Failed") ? topLevelJSON.getInt(value + "Failed") : 0;
        int defaultBuildUnstableReportingValue = topLevelJSON.containsKey(value + "Unstable") ? topLevelJSON.getInt(value + "Unstable") : 0;
        int defaultBuildNotBuiltReportingValue = topLevelJSON.containsKey(value + "Not Built") ? topLevelJSON.getInt(value + "Not Built") : 0;

        return new VerdictCategory(value, description, defaultBuildStartedReportingValue,
            defaultBuildSuccessfulReportingValue, defaultBuildFailedReportingValue,
            defaultBuildUnstableReportingValue, defaultBuildNotBuiltReportingValue);
    }
    
    /**
     * Standard getter for the build started reporting value.
     *
     * @return the description.
     */
    public int getDefaultBuildStartedReportingValue() {
        return defaultBuildStartedReportingValue;
    }

    /**
     * Standard setter for the build started reporting value.
     */
    public void setDefaultBuildStartedReportingValue(int defaultBuildStartedReportingValue) {
        this.defaultBuildStartedReportingValue = defaultBuildStartedReportingValue;
    }

    /**
     * Standard getter for the build successful reporting value.
     *
     * @return the description.
     */
    public int getDefaultBuildSuccessfulReportingValue() {
        return defaultBuildSuccessfulReportingValue;
    }

    /**
     * Standard setter for the build successful reporting value.
     */
    public void setDefaultBuildSuccessfulReportingValue(int defaultBuildSuccessfulReportingValue) {
        this.defaultBuildSuccessfulReportingValue = defaultBuildSuccessfulReportingValue;
    }

    /**
     * Standard getter for the build failed reporting value.
     *
     * @return the description.
     */
    public int getDefaultBuildFailedReportingValue() {
        return defaultBuildFailedReportingValue;
    }

    /**
     * Standard setter for the build failed reporting value.
     */
    public void setDefaultBuildFailedReportingValue(int defaultBuildFailedReportingValue) {
        this.defaultBuildFailedReportingValue = defaultBuildFailedReportingValue;
    }

    /**
     * Standard getter for the build unstable reporting value.
     *
     * @return the description.
     */
    public int getDefaultBuildUnstableReportingValue() {
        return defaultBuildUnstableReportingValue;
    }

    /**
     * Standard setter for the build unstable reporting value.
     */
    public void setDefaultBuildUnstableReportingValue(int defaultBuildUnstableReportingValue) {
        this.defaultBuildUnstableReportingValue = defaultBuildUnstableReportingValue;
    }

    /**
     * Standard getter for the build not built reporting value.
     *
     * @return the description.
     */
    public int getDefaultBuildNotBuiltReportingValue() {
        return defaultBuildNotBuiltReportingValue;
    }

    /**
     * Standard setter for the build not built reporting value.
     */
    public void setDefaultBuildNotBuiltReportingValue(int defaultBuildNotBuiltReportingValue) {
        this.defaultBuildNotBuiltReportingValue = defaultBuildNotBuiltReportingValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

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
