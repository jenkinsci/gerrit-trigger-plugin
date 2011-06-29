package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events;

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.CHANGE;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PATCH_SET;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.SUBMITTER;

import net.sf.json.JSONObject;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventType;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritJsonEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.lifecycle.GerritEventLifecycle;

/**
 * A DTO representation of the patchset merged Gerrit event.
 * @author Sergey Vidyuk &lt;sir.vestnik@gmail.com&gt;
 */
public class ChangeMerged extends GerritEventLifecycle implements GerritJsonEvent {

    /**
     * The Gerrit change the event is related to.
     */
    private Change change;

    /**
     * Refers to a specific patchset within a change.
     */
    private PatchSet patchSet;

    /**
     * The uploader of the patch-set.
     */
    private Account uploader;

    @Override
    public void fromJson(JSONObject json) {
        if (json.containsKey(CHANGE)) {
            change = new Change(json.getJSONObject(CHANGE));
        }
        if (json.containsKey(PATCH_SET)) {
            this.patchSet = new PatchSet(json.getJSONObject(PATCH_SET));
        }
        if (json.containsKey(SUBMITTER)) {
            this.uploader = new Account(json.getJSONObject(SUBMITTER));
        }
    }

    @Override
    public GerritEventType getEventType() {
        return GerritEventType.CHANGE_MERGED;
    }

    @Override
    public String toString() {
        return "ChangeMerged: " + change + " " + patchSet;
    }

    public Change getChange() {
        return change;
    }

    public PatchSet getPatchSet() {
        return patchSet;
    }

    public Account getUploader() {
        return uploader;
    }
}
