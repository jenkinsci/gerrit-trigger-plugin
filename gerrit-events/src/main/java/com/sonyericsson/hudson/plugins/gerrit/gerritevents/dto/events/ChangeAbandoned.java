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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventType;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritJsonEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.PatchSet;
import net.sf.json.JSONObject;

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.ABANDONER;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.CHANGE;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PATCHSET;

/**
 * A DTO representation of the change-abandoned Gerrit Event.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class ChangeAbandoned implements GerritJsonEvent {

    private Change change;
    private PatchSet patchset;
    private Account abandoner;

    /**
     * Default constructor.
     */
    public ChangeAbandoned() {
    }

    /**
     * Constructor that fills data directly.
     * @param json the JSON Object
     * @see #fromJson(String)
     */
    public ChangeAbandoned(JSONObject json) {
        fromJson(json);
    }

    @Override
    public GerritEventType getEventType() {
        return GerritEventType.CHANGE_ABANDONED;
    }

    @Override
    public void fromJson(JSONObject json) {
        if (json.containsKey(CHANGE)) {
            change = new Change(json.getJSONObject(CHANGE));
        }
        if (json.containsKey(PATCHSET)) {
            patchset = new PatchSet(json.getJSONObject(PATCHSET));
        }
        if (json.containsKey(ABANDONER)) {
            abandoner = new Account(json.getJSONObject(ABANDONER));
        }
    }

    /**
     * The Account that abandoned the change.
     * @return the abandoner.
     */
    public Account getAbandoner() {
        return abandoner;
    }

    /**
     * The Account that abandoned the change.
     * @param abandoner the abandoner
     */
    public void setAbandoner(Account abandoner) {
        this.abandoner = abandoner;
    }

    /**
     * The Change.
     * @return the change.
     */
    public Change getChange() {
        return change;
    }

    /**
     * The Change.
     * @param change the change.
     */
    public void setChange(Change change) {
        this.change = change;
    }

    /**
     * The patchSet.
     * @return The patchSet.
     */
    public PatchSet getPatchset() {
        return patchset;
    }

    /**
     * The patchSet.
     * @param patchset the patchSet.
     */
    public void setPatchset(PatchSet patchset) {
        this.patchset = patchset;
    }
}
