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
package com.sonyericsson.hudson.plugins.gerrit.trigger.version;

import hudson.util.VersionNumber;

/**
 * Subclass of VersionNumber which can account for gerrit snapshot versions.
 *
 * @author Tomas Westling &lt;thomas.westling@sonyericsson.com&gt;
 */
public class GerritVersionNumber extends VersionNumber {
    private boolean snapshot = false;

    /**
     * Parses a string like "1.0.2" into the version number.
     * @param num the version string.
     */
    public GerritVersionNumber(String num) {
        super(num);
    }

    /**
     * Returns a new GerritVersionNumber from a String.
     * Sets the snapshot field to true if it finds a -g in the version String.
     * @param num the version String.
     * @return the GerritVersionNumber.
     */
    public static GerritVersionNumber getGerritVersionNumber(String num) {
        GerritVersionNumber versionNumber;
        String[] split = num.split("-");
        versionNumber = new GerritVersionNumber(split[0]);
        versionNumber.snapshot = num.contains("-g");
        return versionNumber;
    }

    /**
     * Getter for if the version number is a snapshot.
     * @return if it is a snapshot.
     */
    public boolean isSnapshot() {
        return snapshot;
    }
}
