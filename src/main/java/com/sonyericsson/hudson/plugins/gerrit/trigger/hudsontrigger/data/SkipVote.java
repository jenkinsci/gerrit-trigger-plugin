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

package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

/**
 * Information about what votes to "skip" for a
 * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger}.
 *
 * @author Robert Sandell &lt;robert.sandell@sonymobile.com&gt;
 */
public class SkipVote implements Serializable {
    private static final long serialVersionUID = -372913758160165355L;
    private boolean onSuccessful;
    private boolean onFailed;
    private boolean onUnstable;
    private boolean onNotBuilt;
    private boolean onAborted;

    /**
     * Standard DataBound Constructor.
     * @param onSuccessful if the vote should be skipped (not counted) for {@link hudson.model.Result#SUCCESS} builds.
     * @param onFailed if the vote should be skipped (not counted) for {@link hudson.model.Result#FAILURE} builds.
     * @param onUnstable if the vote should be skipped (not counted) for {@link hudson.model.Result#UNSTABLE} builds.
     * @param onNotBuilt if the vote should be skipped (not counted) for {@link hudson.model.Result#NOT_BUILT} builds.
     * @param onAborted if the vote should be skipped (not counted) for {@link hudson.model.Result#ABORTED} builds.
     */
    @DataBoundConstructor
    public SkipVote(boolean onSuccessful, boolean onFailed, boolean onUnstable, boolean onNotBuilt, boolean onAborted) {
        this.onSuccessful = onSuccessful;
        this.onFailed = onFailed;
        this.onUnstable = onUnstable;
        this.onNotBuilt = onNotBuilt;
        this.onAborted = onAborted;
    }

    /**
     * Old Standard DataBound Constructor left here for backwards compatiblity.
     * @param onSuccessful if the vote should be skipped (not counted) for {@link hudson.model.Result#SUCCESS} builds.
     * @param onFailed if the vote should be skipped (not counted) for {@link hudson.model.Result#FAILURE} builds.
     * @param onUnstable if the vote should be skipped (not counted) for {@link hudson.model.Result#UNSTABLE} builds.
     * @param onNotBuilt if the vote should be skipped (not counted) for {@link hudson.model.Result#NOT_BUILT} builds.
     * @deprecated replaced with {@link #SkipVote(boolean, boolean, boolean, boolean, boolean) }
     */
    @Deprecated
    public SkipVote(boolean onSuccessful, boolean onFailed, boolean onUnstable, boolean onNotBuilt) {
        /* by default and for backward compatibility onAborted is set to the value of onFailed */
        this(onSuccessful, onFailed, onUnstable, onNotBuilt, onFailed);
    }

    /**
     * Default Constructor.
     */
    public SkipVote() {
    }

    /**
     * If the vote should be skipped (not counted) for {@link hudson.model.Result#SUCCESS} builds.
     *
     * @return true if it should be skipped.
     */
    public boolean isOnSuccessful() {
        return onSuccessful;
    }

    /**
     * If the vote should be skipped (not counted) for {@link hudson.model.Result#SUCCESS} builds.
     *
     * @param onSuccessful true if it should be skipped.
     */
    public void setOnSuccessful(boolean onSuccessful) {
        this.onSuccessful = onSuccessful;
    }

    /**
     * If the vote should be skipped (not counted) for {@link hudson.model.Result#FAILURE} builds.
     *
     * @return true if it should be skipped.
     */
    public boolean isOnFailed() {
        return onFailed;
    }

    /**
     * If the vote should be skipped (not counted) for {@link hudson.model.Result#FAILURE} builds.
     * @param onFailed true if it should be skipped.
     */
    public void setOnFailed(boolean onFailed) {
        this.onFailed = onFailed;
    }

    /**
     * If the vote should be skipped (not counted) for {@link hudson.model.Result#UNSTABLE} builds.
     *
     * @return true if it should be skipped.
     */
    public boolean isOnUnstable() {
        return onUnstable;
    }

    /**
     * If the vote should be skipped (not counted) for {@link hudson.model.Result#UNSTABLE} builds.
     *
     * @param onUnstable true if it should be skipped.
     */
    public void setOnUnstable(boolean onUnstable) {
        this.onUnstable = onUnstable;
    }

    /**
     * If the vote should be skipped (not counted) for {@link hudson.model.Result#NOT_BUILT} builds.
     *
     * @return true if it should be skipped.
     */
    public boolean isOnNotBuilt() {
        return onNotBuilt;
    }

    /**
     * If the vote should be skipped (not counted) for {@link hudson.model.Result#NOT_BUILT} builds.
     *
     * @param onNotBuilt true if it should be skipped.
     */
    public void setOnNotBuilt(boolean onNotBuilt) {
        this.onNotBuilt = onNotBuilt;
    }

    /**
     * If the vote should be skipped (not counted) for {@link hudson.model.Result#ABORTED} builds.
     * @return true if it should be skipped.
     */
    public boolean isOnAborted() {
        return onAborted;
    }

    /**
     * If the vote should be skipped (not counted) for {@link hudson.model.Result#ABORTED} builds.
     * @param onAborted true if it should be skipped.
     */
    public void setOnAborted(boolean onAborted) {
        this.onAborted = onAborted;
    }
}
