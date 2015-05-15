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

import net.sf.json.JSONObject;

/**
 * Rules regarding cancellation of builds for when patchsets of the same change comes in.
 * @author Tomas Westling &lt;tomas.westling@sonymobile.com&gt;
 */
public class BuildCancellationPolicy {
    private boolean enabled = false;
    private boolean abortNewPatchsets = false;
    private boolean abortManualPatchsets = false;

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
     * Creates a new BuildCancellationPolicy object from JSON.
     *
     * @param obj the JSONObject.
     * @return a new BuildCancellationPolicy object.
     */
    public static BuildCancellationPolicy createPolicyFromJSON(JSONObject obj) {
        boolean newPatchsets = obj.getBoolean("abortNewPatchsets");
        boolean manualPatchsets = obj.getBoolean("abortManualPatchsets");
        BuildCancellationPolicy policy = new BuildCancellationPolicy();
        policy.setEnabled(true);
        policy.setAbortNewPatchsets(newPatchsets);
        policy.setAbortManualPatchsets(manualPatchsets);
        return policy;
    }
}
