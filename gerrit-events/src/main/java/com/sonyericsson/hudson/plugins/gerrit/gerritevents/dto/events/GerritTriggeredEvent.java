/*
 *  The MIT License
 *
 *  Copyright 2011 Sony Ericsson Mobile Communications. All rights reserved.
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

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.CHANGE;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.REFUPDATE;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PATCHSET;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PATCH_SET;

import net.sf.json.JSONObject;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.RefUpdate;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.lifecycle.GerritEventLifecycle;

/**
 * A DTO representation of a Gerrit triggered Event.
 * @author David Pursehouse &lt;david.pursehouse@sonyericsson.com&gt;
 */
public class GerritTriggeredEvent extends GerritEventLifecycle {

    /**
     * The Gerrit change the event is related to.
     */
    protected Change change;

    /**
     * The Gerrit ref update the event is related to.
     */
    protected RefUpdate refUpdate;

    /**
     * Refers to a specific patchset within a change.
     */
    protected PatchSet patchSet;

    /**
     * The account that triggered the event.
     */
    protected Account account;

    /**
     * Takes a JSON object and fills its internal data-structure.
     * @param json the JSON Object.
     */
    public void fromJson(JSONObject json) {
        if (json.containsKey(CHANGE)) {
            change = new Change(json.getJSONObject(CHANGE));
        }
        if (json.containsKey(REFUPDATE)) {
            refUpdate = new RefUpdate(json.getJSONObject(REFUPDATE));
        }
        if (json.containsKey(PATCH_SET)) {
            patchSet = new PatchSet(json.getJSONObject(PATCH_SET));
        } else if (json.containsKey(PATCHSET)) {
            patchSet = new PatchSet(json.getJSONObject(PATCHSET));
        }
    }

    /**
     * The account that triggered the event.
     * @return the account.
     */
    public Account getAccount() {
        return account;
    }

    /**
     * The account that triggered the change.
     * @param account the account.
     */
    public void setAccount(Account account) {
        this.account = account;
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
     * The ref update.
     * @return the refupdate.
     */
    public RefUpdate getRefUpdate() {
        return refUpdate;
    }

    /**
     * The ref update.
     * @param refUpdate the refupdate.
     */
    public void setRefUpdate(RefUpdate refUpdate) {
        this.refUpdate = refUpdate;
    }

    /**
     * The patchSet.
     * @return The patchSet.
     */
    public PatchSet getPatchSet() {
        return patchSet;
    }

    /**
     * The patchSet.
     * @param patchset the patchSet.
     */
    public void setPatchset(PatchSet patchset) {
        this.patchSet = patchset;
    }
}
