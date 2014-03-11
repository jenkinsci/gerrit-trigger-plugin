/*
 * The MIT License
 *
 * Copyright 2013 Sony Mobile Communications AB. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger;

import com.sonymobile.tools.gerrit.gerritevents.watchdog.WatchTimeExceptionData;

/**
 * Helper class for easier Jelly usage.
 *
 * @author Tomas Westling &lt;tomas.westling@sonymobile.com&gt;
 */
public class ExceptionDataHelper {
    private String displayName;
    private boolean checked;
    private int id;

    /**
     * Standard constructor.
     *
     * @param displayName the display name.
     * @param id the id for the day.
     * @param data the WatchTimeExceptionData.
     */
    public ExceptionDataHelper(String displayName, int id, WatchTimeExceptionData data) {
        this.displayName = displayName;
        if (data == null) {
            this.checked = false;
        } else {
            this.checked = data.isExceptionDay(id);
        }
        this.id = id;
    }

    /**
     * Standard getter.
     *
     * @return the display name.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Standard getter.
     *
     * @return true if checked.
     */
    public boolean isChecked() {
        return checked;
    }

    /**
     * Standard getter.
     *
     * @return the id.
     */
    public int getId() {
        return id;
    }


}
