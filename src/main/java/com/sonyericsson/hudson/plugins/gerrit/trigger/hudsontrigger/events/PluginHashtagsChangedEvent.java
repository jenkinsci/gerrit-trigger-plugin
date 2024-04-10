/*
 * The MIT License
 *
 * Copyright 2012 Intel, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events;

import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.HashtagsChanged;

import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * An event configuration that causes the build to be triggered
 * when a hashTag changed which matching a configurable RegEx.
 */
public class PluginHashtagsChangedEvent extends PluginGerritEvent implements Serializable {

    /**
     * The descriptor for PluginHashtagChangedEvent.
     */
    @Extension
    @Symbol("hashtagChanged")
    public static class PluginHashtagsChangedEventDescriptor extends PluginGerritEventDescriptor {
        /**
         * Display name in jenkins page for PluginHashtagChangedEvent.
         *
         * @return display name in jenkins page.
         */
        @Override
        public String getDisplayName() {
            return Messages.HashtagChangedDisplayName();
        }
    }

    private static final long serialVersionUID = -7504226521891459396L;

    private String hashtagChanged;

    private static final Logger logger = LoggerFactory.getLogger(PluginHashtagsChangedEvent.class);

    /**
     * Empty Default constructor for Serializer.
     */
    public PluginHashtagsChangedEvent() {

    }

    /**
     * Standard dataBoundConstructor for configuration.
     *
     * @param hashtagChanged String contains a regular expression to match changed hasgtag.
     */
    @DataBoundConstructor
    public PluginHashtagsChangedEvent(String hashtagChanged) {
        this.hashtagChanged = hashtagChanged;
    }

    /**
     * Get a regular expression to show in jenkins page.
     *
     * @return String contains a regular expression to match changed hasgtag.
     */
    public String getHashtagChanged() {
        return this.hashtagChanged;
    }

    /**
     * Get class for watched gerrit event.
     *
     * @return Hashtag changed event class
     */
    @Override
    public Class getCorrespondingEventClass() {
        return HashtagsChanged.class;
    }


    @Override
    public boolean shouldTriggerOn(GerritTriggeredEvent event) {
        if (event instanceof HashtagsChanged) {
            return match((HashtagsChanged)event);
        }
        return false;
    }

    /**
     * Check if changed hashtag match pattern.
     * @param event gerrit hashtag changed event
     * @return match or not.
     */
    private boolean match(HashtagsChanged event) {
        Pattern p = Pattern.compile(hashtagChanged);
        logger.debug("Hashtags after change {}, added hashtags {}, removed hashtags {}",
                event.getHashtags(), event.getAddedHashtags(), event.getRemovedHashtags());
        return event.getAddedHashtags().stream().anyMatch(tag -> p.matcher(tag).find())
                || event.getRemovedHashtags().stream().anyMatch(tag -> p.matcher(tag).find());
    }
}
