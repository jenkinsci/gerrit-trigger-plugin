package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeMerged;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;

/**
 * A Cause of a {@link AutoRebuildPatchset}
 * @author Sergey Vidyuk &lt;sir.vestnik@gmail.com&gt;
 */
public class GerritAutoRebuildCause extends GerritCause {

    private ChangeMerged mergedEvent;

    GerritAutoRebuildCause(ChangeMerged mergedEvent, PatchsetCreated event, boolean silentMode) {
        super(event, silentMode);
        this.mergedEvent = mergedEvent;
    }

    @Override
    public String getShortDescription() {
        if (isSilentMode())
            return Messages.AutoRebuidTriggeredShortDescriptionInSilentMode(getUrl(), getMergedUrl(), getBranch());
        return Messages.AutoRebuidTriggeredShortDescription(getUrl(), getMergedUrl(), getBranch());
	}

    /**
     * Gets the URL to the merged Gerrit patchSet.
     * @return the URL.
     */
    public String getMergedUrl() {
        return PluginImpl.getInstance().getConfig().getGerritFrontEndUrlFor(
                mergedEvent.getChange().getNumber(),
                mergedEvent.getPatchSet().getNumber());
    }

    /**
     * Gets the upstream branch modified.
     * @return the name of the upstream branch which was modified.
     */ 
    public String getBranch() {
        return mergedEvent.getChange().getBranch();
    }
    
    @SuppressWarnings("unused") // Called from description.jelly
    public boolean isAutoRebuild() {
        return true;
    }
}
