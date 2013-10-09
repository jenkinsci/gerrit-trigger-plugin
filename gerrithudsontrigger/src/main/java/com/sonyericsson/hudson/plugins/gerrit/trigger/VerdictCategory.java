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

    /**
     * The value for Code-Review.
     */
    public static final String CODEREVIEW_VALUE = "Code-Review";
    /**
     * The description for Code-Review.
     */
    public static final String CODEREVIEW_DESCRIPTION = "Code Review";
    /**
     * The value for Verified.
     */
    public static final String VERIFIED_VALUE = "Verified";
    /**
     * The description for Verified.
     */
    public static final String VERIFIED_DESCRIPTION = "Verified";

    private static final String ALTERNATIVE_CODEREVIEW_VALUE = "CRVW";
    private static final String ALTERNATIVE_VERIFIED_VALUE = "VRIF";

    private String verdictValue;
    private String verdictDescription;

    /**
     * Standard constructor.
     * @param value the value in Gerrit for the verdict category.
     * @param description the text describing the verdict category.
     */
    public VerdictCategory(String value, String description) {
        verdictValue = normalizeValue(value);
        verdictDescription = description;
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
     * Normalize value.
     *
     * @param value the value in Gerrit for the verdict category.
     * @return the normarized value.
     */
    public static String normalizeValue(String value) {
        if (ALTERNATIVE_CODEREVIEW_VALUE.equals(value)) {
            return CODEREVIEW_VALUE;
        }
        if (ALTERNATIVE_VERIFIED_VALUE.equals(value)) {
            return VERIFIED_VALUE;
        }
        return value;
    }
    /**
     * Creates a VerdictCategory from a JSONObject.
     * @param obj the JSONObject.
     * @return a VerdictCategory.
     */
    public static VerdictCategory createVerdictCategoryFromJSON(JSONObject obj) {
        String value = obj.getString("verdictValue");
        String description = obj.getString("verdictDescription");
        return new VerdictCategory(value, description);
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
