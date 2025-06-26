/*
 *  The MIT License
 *
 *  Copyright (c) 2022 Christoph Kreisl. All rights reserved.
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

import com.sonymobile.tools.gerrit.gerritevents.dto.GerritChangeStatus;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Topic Association Option.
 * If this option is enabled the job matching the topic configuration is triggered.
 * Since changes assigned to a topic could be in an inappropriate state we check if changes
 * in state NEW, MERGED or ABANDONED should be ignored.
 *
 * In general this shouldn't happen since changes can only be merged together.
 * However, if someone adds a change to an already merged topic the jobs for the merged changes
 * shouldn't be triggered again if not required.
 */
public class TopicAssociation extends AbstractDescribableImpl<TopicAssociation> {

   private boolean ignoreNewChangeStatus;
   private boolean ignoreMergedChangeStatus;
   private boolean ignoreAbandonedChangeStatus;

    /**
     * Default data bound constructor.
     *
     * @param ignoreNewChangeStatus If changes with status NEW should be ignored
     * @param ignoreMergedChangeStatus If changes with status MERGED should be ignored
     * @param ignoreAbandonedChangeStatus If changes with status ABANDONED should be ignored
     */
    @DataBoundConstructor
    public TopicAssociation(final boolean ignoreNewChangeStatus,
                            final boolean ignoreMergedChangeStatus,
                            final boolean ignoreAbandonedChangeStatus) {
        this.ignoreNewChangeStatus = ignoreNewChangeStatus;
        this.ignoreMergedChangeStatus = ignoreMergedChangeStatus;
        this.ignoreAbandonedChangeStatus = ignoreAbandonedChangeStatus;
    }

    /**
     * Default constructor.
     */
    public TopicAssociation() {
        this.ignoreNewChangeStatus = false;
        this.ignoreMergedChangeStatus = false;
        this.ignoreAbandonedChangeStatus = false;
    }

    /**
     * Returns true if a change in state NEW should be ignored otherwise false.
     *
     * @return true if it should be ignored otherwise false
     */
    public boolean isIgnoreNewChangeStatus() {
        return ignoreNewChangeStatus;
    }

    /**
     * Enable or disable ignoring changes with status NEW.
     *
     * @param ignoreNewChangeStatus true or false.
     */
    public void setIgnoreNewChangeStatus(boolean ignoreNewChangeStatus) {
        this.ignoreNewChangeStatus = ignoreNewChangeStatus;
    }

    /**
     * Returns true if a change in state NEW should be ignored otherwise false.
     * Used for jelly file.
     *
     * @return true if it should be ignored otherwise false
     */
    public boolean isIgnoreMergedChangeStatus() {
        return ignoreMergedChangeStatus;
    }

    /**
     * Enable or disable ignoring changes with status MERGED.
     *
     * @param ignoreMergedChangeStatus true or false.
     */
    public void setIgnoreMergedChangeStatus(boolean ignoreMergedChangeStatus) {
        this.ignoreMergedChangeStatus = ignoreMergedChangeStatus;
    }

    /**
     * Returns true if a change in state NEW should be ignored otherwise false.
     * Used for jelly file.
     *
     * @return true if it should be ignored otherwise false
     */
    public boolean isIgnoreAbandonedChangeStatus() {
        return ignoreAbandonedChangeStatus;
    }

    /**
     * Enable or disable ignoring changes with status ABANDONED.
     *
     * @param ignoreAbandonedChangeStatus true or false.
     */
    public void setIgnoreAbandonedChangeStatus(boolean ignoreAbandonedChangeStatus) {
        this.ignoreAbandonedChangeStatus = ignoreAbandonedChangeStatus;
    }

    /**
     * Checks if the change state is interesting.
     *
     * @param c the change.
     * @return true if the change is interesting otherwise false.
     */
    public boolean isInterestingChangeStatus(final Change c) {
        boolean isNewChange = c.getStatus().equals(GerritChangeStatus.NEW);
        boolean isMergedChange = c.getStatus().equals(GerritChangeStatus.MERGED);
        boolean isAbandonedChange = c.getStatus().equals(GerritChangeStatus.ABANDONED);

        if (isNewChange && ignoreNewChangeStatus) {
            return false;
        }

        if (isMergedChange && ignoreMergedChangeStatus) {
            return false;
        }

        return !isAbandonedChange || !ignoreAbandonedChangeStatus;
    }

    /**
     * The Descriptor for the Topic Association.
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<TopicAssociation> {
        @Override
        public String getDisplayName() {
            return "Topic Association";
        }
    }
}
