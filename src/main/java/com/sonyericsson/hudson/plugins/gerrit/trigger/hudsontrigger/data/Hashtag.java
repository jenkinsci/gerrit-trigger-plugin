/*
 *  The MIT License
 *
 *  Copyright 2013 rinrinne. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a rule for triggering on a hashtag of a GerritProject.
 */
public class Hashtag extends AbstractDescribableImpl<Hashtag> {

    private CompareType compareType;
    private String pattern;

    /**
     * Default empty constructor.
     */
    public Hashtag() {
    }

    /**
     * Default DataBound constructor.
     * @param compareType the CompareType to use when comparing with the pattern.
     * @param pattern the pattern to match on.
     */
    @DataBoundConstructor
    public Hashtag(CompareType compareType, String pattern) {
        this.compareType = compareType;
        this.pattern = pattern;
    }

    /**
     * The CompareType used.
     * @return the CompareType
     */
    public CompareType getCompareType() {
        return compareType;
    }

    /**
     * The CompareType used.
     * @param compareType the compareType.
     */
    public void setCompareType(CompareType compareType) {
        this.compareType = compareType;
    }

    /**
     * The pattern to match on.
     * @return the pattern
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * The pattern to match on.
     * @param pattern the pattern.
     */
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    /**
     * Tells if the given topic are matched by this rule.
     * @param hashtags the hashtags in change.
     * @return true if the hashtags match.
     */
    public boolean isInteresting(List<String> hashtags) {
        if (hashtags == null) {
            hashtags = new ArrayList<>();
        }
        return hashtags.stream().anyMatch(hashtag-> compareType.matches(pattern, hashtag));
    }

    /**
     * The Descriptor for the hashtag.
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<Hashtag> {
        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
