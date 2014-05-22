package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.event;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginPatchsetCreatedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;

/**
 * Tests for {@link PluginPatchsetCreatedEvent}.
 *
 * @author Hugo Arès &lt;hugo.ares@ericsson.com&gt;
 */
public class PluginPatchsetCreatedEventTest {

    /**
     * Tests that it should fire on all type of patchset.
     */
    @Test
    public void shouldFireOnAllTypeOfPatchset() {
        PluginPatchsetCreatedEvent pluginPatchsetCreatedEvent = new PluginPatchsetCreatedEvent(false);
        PatchsetCreated patchsetCreated = new PatchsetCreated();
        patchsetCreated.setPatchset(new PatchSet());

        //should fire on regular patchset and drafts
        assertTrue(pluginPatchsetCreatedEvent.shouldTriggerOn(patchsetCreated));
        patchsetCreated.getPatchSet().setDraft(true);
        assertTrue(pluginPatchsetCreatedEvent.shouldTriggerOn(patchsetCreated));
    }

    /**
     * Tests that it should not fire on draft patchset when they are excluded.
     */
    @Test
    public void shouldNotFireOnDraftPatchsetWhenExcluded() {
        PluginPatchsetCreatedEvent pluginPatchsetCreatedEvent = new PluginPatchsetCreatedEvent(true);
        PatchsetCreated patchsetCreated = new PatchsetCreated();
        patchsetCreated.setPatchset(new PatchSet());

        //should fire only on regular patchset (no drafts)
        assertTrue(pluginPatchsetCreatedEvent.shouldTriggerOn(patchsetCreated));
        patchsetCreated.getPatchSet().setDraft(true);
        assertFalse(pluginPatchsetCreatedEvent.shouldTriggerOn(patchsetCreated));
    }
}
