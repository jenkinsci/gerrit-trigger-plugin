/*
 *  The MIT License
 *
 *  Copyright 2012 Sony Ericsson Mobile Communications. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.version;


import hudson.util.VersionNumber;

/**
 * Subclass of GerritVersionNumber which represents the highest possible version number.
 *
 * @author Tomas Westling &lt;thomas.westling@sonyericsson.com&gt;
 */

public class HighestVersionNumber extends GerritVersionNumber {

    private static final String HIGHEST_VERSION_NUMBER = "9.9.9";

    /**
     * Default constructor, creates a highest version number.
     */
    public HighestVersionNumber() {
        super(HIGHEST_VERSION_NUMBER);
    }

    @Override
    public boolean isOlderThan(VersionNumber rhs) {
        return false;
    }

    @Override
    public boolean isNewerThan(VersionNumber rhs) {
        return true;
    }

    @Override
    public int compareTo(VersionNumber rhs) {
        return 1;
    }

    @Override
    public String toString() {
        return "NaN";
    }
}
