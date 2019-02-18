/*
 * The MIT License
 *
 * Copyright 2012 Sony Mobile Communications AB. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.sonyericsson.hudson.plugins.gerrit.trigger.utils;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.SkipVote;
import hudson.model.Result;

/**
 * Common static logic utility methods.
 *
 * @author Robert Sandell &lt;robert.sandell@sonymobile.com&gt;
 */
public abstract class Logic {

    /**
     * Utility Constructor.
     */
    private Logic() {
    }

    /**
     * If the given result should be skipped according to the configuration.
     *
     * @param skipVote the skip configuration.
     * @param result the build result.
     * @return true if skipped, false if not or skipVote is null.
     */
    public static boolean shouldSkip(SkipVote skipVote, Result result) {
        if (skipVote == null) {
            return false;
        }
        if (result == Result.SUCCESS) {
            return skipVote.isOnSuccessful();
        } else if (result == Result.FAILURE) {
            return skipVote.isOnFailed();
        } else if (result == Result.UNSTABLE) {
            return skipVote.isOnUnstable();
        } else if (result == Result.NOT_BUILT) {
            return skipVote.isOnNotBuilt();
        } else if (result == Result.ABORTED) {
            return skipVote.isOnAborted();
        } else {
            return false;
        }
    }
}
