/*
 *  The MIT License
 *
 *  Copyright (c) 2015 Sony Mobile Communications Inc. All rights reserved.
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
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Rules regarding cancellation of builds for when patchsets of the same change comes in.
 *
 * @author Tomas Westling &lt;tomas.westling@sonymobile.com&gt;
 */
public class BuildCancellationPolicy extends AbstractDescribableImpl<BuildCancellationPolicy> {
    private boolean enabled = false;
    private boolean abortNewPatchsets = false;
    private boolean abortManualPatchsets = false;
    private boolean abortSameTopic = false;

    /**
     * Default databound constructor.
     *
     * @param abortNewPatchsets abort new patch sets
     * @param abortManualPatchsets abort manual patch sets
     * @param abortSameTopic abort same topic
     */
    @DataBoundConstructor
    public BuildCancellationPolicy(final boolean abortNewPatchsets,
                                   final boolean abortManualPatchsets,
                                   final boolean abortSameTopic) {
        this.enabled = true;
        this.abortNewPatchsets = abortNewPatchsets;
        this.abortManualPatchsets = abortManualPatchsets;
        this.abortSameTopic = abortSameTopic;
    }

    /**
     * Default constructor.
     */
    public BuildCancellationPolicy() {
    }

    /**
     * Getter for if build cancellation is turned off or on.
     *
     * @return whether build cancellation is turned off or on.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Turns on/off the build cancellation.
     *
     * @param enabled whether build cancellation should be turned off or on.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Standard getter for the abortNewPatchsets value.
     *
     * @return the abortNewPatchsets value.
     */
    public boolean isAbortNewPatchsets() {
        return abortNewPatchsets;
    }

    /**
     * Standard setter for the abortNewPatchsets value.
     *
     * @param abortNewPatchsets true if new patchsets should be cancelled by older.
     */
    public void setAbortNewPatchsets(boolean abortNewPatchsets) {
        this.abortNewPatchsets = abortNewPatchsets;
    }

    /**
     * Standard getter for the abortManualPatchsets value.
     *
     * @return the abortManualPatchsets value.
     */
    public boolean isAbortManualPatchsets() {
        return abortManualPatchsets;
    }

    /**
     * Standard setter for the abortManualPatchsets value.
     *
     * @param abortManualPatchsets true if manual patchsets should be cancelled.
     */
    public void setAbortManualPatchsets(boolean abortManualPatchsets) {
        this.abortManualPatchsets = abortManualPatchsets;
    }

    /**
     * Standard getter for the abortSameTopic value.
     *
     * @return the abortSameTopic value.
     */
    public boolean isAbortSameTopic() {
        return abortSameTopic;
    }

    /**
     * Standard setter for the abortSameTopic value.
     *
     * @param abortSameTopic true if patchsets with same topic should be cancelled.
     */
    public void setAbortSameTopic(boolean abortSameTopic) {
        this.abortSameTopic = abortSameTopic;
    }

    /**
     * Creates a new BuildCancellationPolicy object from JSON.
     *
     * @param obj the JSONObject.
     * @return a new BuildCancellationPolicy object.
     */
    public static BuildCancellationPolicy createPolicyFromJSON(JSONObject obj) {
        boolean newPatchsets = obj.getBoolean("abortNewPatchsets");
        boolean manualPatchsets = obj.getBoolean("abortManualPatchsets");
        boolean abortSameTopic = obj.optBoolean("abortSameTopic");
        BuildCancellationPolicy policy = new BuildCancellationPolicy();
        policy.setEnabled(true);
        policy.setAbortNewPatchsets(newPatchsets);
        policy.setAbortManualPatchsets(manualPatchsets);
        policy.setAbortSameTopic(abortSameTopic);
        return policy;
    }

    /**
     * The descriptor.
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<BuildCancellationPolicy> {
        @Override
        public BuildCancellationPolicy newInstance(final StaplerRequest req, final JSONObject formData)
                throws FormException {
            if (formData.has("buildCancellationPolicy")) {
                return super.newInstance(req, formData.getJSONObject("buildCancellationPolicy"));
            } else {
                return null;
            }
        }
    }
}
