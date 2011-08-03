package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events;

import net.sf.json.JSONObject;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.PatchSet;

/**
 * Represents a patchset which should be rebuild due to changes in the upstream
 * branch.
 * @author Sergey Vidyuk &lt;sir.vestnik@gmail.com&gt;
 */
public class AutoRebuildPatchset extends PatchsetCreated {

    private ChangeMerged mergedEvent;

    /**
     * Standard Constructor.
     * @param change JSONObject containing the change information.
     * @param patch JSONObject containing the patchSet information.
     * @param userName the user that manually fired the Gerrit event.
     */
    public AutoRebuildPatchset(JSONObject change, JSONObject patch, ChangeMerged mergedEvent) {
        fromJson(change, patch);
        this.setMergedEvent(mergedEvent);
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

    public void setMergedEvent(ChangeMerged mergedEvent) {
        this.mergedEvent = mergedEvent;
    }

    public ChangeMerged getMergedEvent() {
        return mergedEvent;
    }
}
