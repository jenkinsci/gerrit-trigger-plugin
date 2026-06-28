package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginChangeMergedEvent;

import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;

import org.junit.jupiter.api.Test;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeMerged;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;

/**
 * Tests for {@link PluginChangeMergedEvent}.
 */
class PluginChangeMergedEventTest {

    /**
     * Tests that it should fire on all merged changes when no regex is set.
     */
    @Test
    void shouldFireOnAllMergedChanges() {
        PluginChangeMergedEvent event = new PluginChangeMergedEvent();
        assertTrue(event.shouldTriggerOn(createChangeMerged("some commit message")));
    }

    /**
     * Tests that it fires when commit message matches the regex.
     */
    @Test
    void shouldFireWhenCommitMessageMatchesRegEx() {
        PluginChangeMergedEvent event = new PluginChangeMergedEvent();
        event.setCommitMessageContainsRegEx("^fix:");
        assertTrue(event.shouldTriggerOn(createChangeMerged("fix: correct a bug")));
    }

    /**
     * Tests that it does not fire when commit message does not match the regex.
     */
    @Test
    void shouldNotFireWhenCommitMessageDoesNotMatchRegEx() {
        PluginChangeMergedEvent event = new PluginChangeMergedEvent();
        event.setCommitMessageContainsRegEx("^fix:");
        assertFalse(event.shouldTriggerOn(createChangeMerged("docs: update readme")));
    }

    /**
     * Tests that the cached pattern is reused on subsequent calls.
     */
    @Test
    void shouldReuseCompiledPattern() {
        PluginChangeMergedEvent event = new PluginChangeMergedEvent();
        event.setCommitMessageContainsRegEx("^fix:");
        assertTrue(event.shouldTriggerOn(createChangeMerged("fix: first change")));
        assertTrue(event.shouldTriggerOn(createChangeMerged("fix: second change")));
    }

    /**
     * Tests that it fires when regex is null or empty.
     */
    @Test
    void shouldFireWhenRegExIsNullOrEmpty() {
        PluginChangeMergedEvent event = new PluginChangeMergedEvent();
        ChangeMerged changeMerged = createChangeMerged("any commit message");
        event.setCommitMessageContainsRegEx(null);
        assertTrue(event.shouldTriggerOn(changeMerged));
        event.setCommitMessageContainsRegEx("");
        assertTrue(event.shouldTriggerOn(changeMerged));
    }

    /**
     * Tests that the getter returns the value set by the setter.
     */
    @Test
    void getterReturnsSetValue() {
        PluginChangeMergedEvent event = new PluginChangeMergedEvent();
        event.setCommitMessageContainsRegEx("^feat:");
        assertEquals("^feat:", event.getCommitMessageContainsRegEx());
    }

    /**
     * Tests that it does not fire for a non-ChangeMerged event type.
     */
    @Test
    void shouldNotFireForWrongEventType() {
        PluginChangeMergedEvent event = new PluginChangeMergedEvent();
        PatchsetCreated patchsetCreated = new PatchsetCreated();
        patchsetCreated.setPatchset(new PatchSet());
        Change change = new Change();
        change.setCommitMessage("some message");
        patchsetCreated.setChange(change);
        assertFalse(event.shouldTriggerOn(patchsetCreated));
    }

    /**
     * Creates a ChangeMerged event with the given commit message.
     * @param commitMessage the commit message to set
     * @return a ChangeMerged instance
     */
    private ChangeMerged createChangeMerged(String commitMessage) {
        ChangeMerged changeMerged = new ChangeMerged();
        changeMerged.setPatchset(new PatchSet());
        Change change = new Change();
        change.setCommitMessage(commitMessage);
        changeMerged.setChange(change);
        return changeMerged;
    }
}
