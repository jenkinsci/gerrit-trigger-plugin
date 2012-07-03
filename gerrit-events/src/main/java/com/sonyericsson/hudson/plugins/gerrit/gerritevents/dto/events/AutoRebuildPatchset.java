package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events;

import net.sf.json.JSONObject;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.RefUpdate;

/**
 * Represents a patchset which should be rebuild due to changes in the upstream
 * branch.
 * @author Sergey Vidyuk &lt;sir.vestnik@gmail.com&gt;
 */
public class AutoRebuildPatchset extends PatchsetCreated {

    /**
     * The Gerrit ref update the event caused by.
     */
    protected RefUpdate refUpdate;

    /**
     * Default constructor.
     */
    public AutoRebuildPatchset() {
    }

    /**
     * Standard Constructor.
     * @param change JSONObject containing the change information.
     * @param patch JSONObject containing the patchSet information.
     * @param refUpdateEvent git ref-update caused this rebuild.
     */
    public AutoRebuildPatchset(JSONObject change, JSONObject patch, RefUpdated refUpdateEvent) {
        fromJson(change, patch);
        this.setRefUpdate(refUpdateEvent.getRefUpdate());
    }

    /**
     * The ref update.
     *
     * @return the refupdate.
     */
    public RefUpdate getRefUpdate() {
        return refUpdate;
    }

    /**
     * The ref update.
     *
     * @param refUpdate the refupdate.
     */
    public void setRefUpdate(RefUpdate refUpdate) {
        this.refUpdate = refUpdate;
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

    @Override
    public String toString() {
        return "AutoRebuildPatchset: " + change + " " + patchSet
                + " due to " + getRefUpdate();
    }
}
