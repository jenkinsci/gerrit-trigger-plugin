/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.PatchSet;
import net.sf.json.JSONObject;

/**
 * Represents a Patchset manually selected to be built by a user.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class ManualPatchsetCreated extends PatchsetCreated {

    private String userName;

    /**
     * Default Constructor.
     */
    public ManualPatchsetCreated() {
    }

    /**
     * Standard Constructor.
     * @param change JSONObject containing the change information.
     * @param patch JSONObject containing the patchSet information.
     * @param userName the user that manually fired the Gerrit event.
     */
    public ManualPatchsetCreated(JSONObject change, JSONObject patch, String userName) {
        fromJson(change, patch);
        this.userName = userName;
    }

    /**
     * Sets the relevant values from the JSONObjects.
     * @param change the change info.
     * @param patch the patchSet info.
     */
    public void fromJson(JSONObject change, JSONObject patch) {
        setChange(new Change(change));
        setPatchset(new PatchSet(patch));
    }

    /**
     * The name of the user who "created" the event.
     * @return the userName.
     */
    public String getUserName() {
        return userName;
    }

    /**
     * The name of the user who "created" the event.
     * @param userName the userName.
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("[");
        str.append(getClass().getSimpleName());
        str.append(" Change: ").append(getChange());
        str.append(" PatchSet: ").append(getPatchSet());
        str.append("]");
        return str.toString();
    }
}
