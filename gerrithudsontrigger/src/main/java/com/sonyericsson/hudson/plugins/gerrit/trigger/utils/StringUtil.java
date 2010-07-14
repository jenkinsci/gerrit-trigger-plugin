/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.utils;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;

/**
 * Various string making utility methods.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public final class StringUtil {

    /**
     * What comes before the change and patch numbers in a refspec.
     */
    public static final String REFSPEC_PREFIX = "refs/changes/";

    /**
     * The base URL of this plugin.
     */
    public static final String PLUGIN_URL = "/plugin/gerrit-trigger/";

    /**
     * The base URL of the plugin images.
     */
    public static final String PLUGIN_IMAGES_URL = PLUGIN_URL + "images/";

    /**
     * Provate Constructor for Utility Class.
     */
    private StringUtil() {
    }

    /**
     * Creates a refspec string from the data in the event.
     * For a change with number 3456 and patchset 1 the refspec would be refs/changes/56/3456/1
     * @param event the event.
     * @return the refspec.
     */
    public static String makeRefSpec(PatchsetCreated event) {
        StringBuilder str = new StringBuilder(REFSPEC_PREFIX);
        String number = event.getChange().getNumber();
        if (number.length() < 2) {
            str.append("0").append(number);
        } else if (number.length() == 2) {
            str.append(number);
        } else {
            str.append(number.substring(number.length() - 2));
        }
        str.append("/").append(number);
        str.append("/").append(event.getPatchSet().getNumber());
        return str.toString();
    }
}
