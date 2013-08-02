package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.AutoRebuildPatchset;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;

/**
 * A Cause of a {@link AutoRebuildPatchset}.
 * @author Sergey Vidyuk &lt;sir.vestnik@gmail.com&gt;
 */
public class GerritAutoRebuildCause extends GerritCause {

    /**
     * Create cause of a {@link AutoRebuildPatchset}.
     * @param event cause event.
     * @param silentMode project trigger mode.
     */
    GerritAutoRebuildCause(AutoRebuildPatchset event, boolean silentMode) {
        super(event, silentMode);
    }

    @Override
    public String getShortDescription() {
        if (isSilentMode()) {
            return Messages.AutoRebuidTriggeredShortDescriptionInSilentMode(getUrl(), getBranch(),
                    getOldRev(), getNewRev());
        }
        return Messages.AutoRebuidTriggeredShortDescription(getUrl(), getBranch(),
                getOldRev(), getNewRev());
    }

    /**
     * Gets the upstream branch modified.
     * @return the name of the upstream branch which was modified.
     */
    public String getBranch() {
        return ((AutoRebuildPatchset)getEvent()).getRefUpdate().getRefName();
    }

    /**
     * Gets revision before git ref-update.
     * @return the name of the revision of the old git-ref state
     */
    public String getOldRev() {
        return ((AutoRebuildPatchset)getEvent()).getRefUpdate().getOldRev();
    }

    /**
     * Gets revision avter git ref-update.
     * @return the name of the revision of the new git-ref state
     */
    public String getNewRev() {
        return ((AutoRebuildPatchset)getEvent()).getRefUpdate().getNewRev();
    }

    /**
     * Bool flag to identify cause in jelly templates.
     * @return always true
     */
    @SuppressWarnings("unused") // Called from description.jelly
    public boolean isAutoRebuild() {
        return true;
    }
}
