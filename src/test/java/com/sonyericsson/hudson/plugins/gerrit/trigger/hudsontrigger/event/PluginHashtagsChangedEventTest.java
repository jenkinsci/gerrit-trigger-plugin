package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.event;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginHashtagsChangedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.HashtagsChanged;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.TopicChanged;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link PluginHashtagsChangedEvent}.
 */
public class PluginHashtagsChangedEventTest {

    /**
     * Tests that it should fire on all type of patchset.
     */
    @Test
    public void shouldFireOnHashtagsChanged() {
        PluginHashtagsChangedEvent pluginHashtagsChangedEvent =
                new PluginHashtagsChangedEvent(".*Hashtag.*");
        HashtagsChanged hashtagsChanged = Setup.createHashtagsChanged();

        //should fire on hashtags changed.
        assertTrue(pluginHashtagsChangedEvent.shouldTriggerOn(hashtagsChanged));

        TopicChanged topicChanged = Setup.createTopicChanged();

        //should not fire on topic changed.
        assertFalse(pluginHashtagsChangedEvent.shouldTriggerOn(topicChanged));
    }

    /**
     * Tests that it should not fire on draft patchset when they are excluded.
     */
    @Test
    public void hashtagsRegExCheck() {
        PluginHashtagsChangedEvent pluginHashtagsChangedEvent =
                new PluginHashtagsChangedEvent("mHashtags.*");
        HashtagsChanged hashtagsChanged = Setup.createHashtagsChanged();

        //should not fire on pattern not match.
        assertFalse(pluginHashtagsChangedEvent.shouldTriggerOn(hashtagsChanged));

        PluginHashtagsChangedEvent pluginHashtagsChangedEvent1 =
                new PluginHashtagsChangedEvent("aHashtag.*");

        //should fire on pattern match.
        assertTrue(pluginHashtagsChangedEvent1.shouldTriggerOn(hashtagsChanged));

        PluginHashtagsChangedEvent pluginHashtagsChangedEvent2 =
                new PluginHashtagsChangedEvent("");

        //should fire on pattern empty.
        assertTrue(pluginHashtagsChangedEvent2.shouldTriggerOn(hashtagsChanged));
    }
}
