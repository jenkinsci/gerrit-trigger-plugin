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
import com.sonymobile.tools.gerrit.gerritevents.dto.events.TopicChanged;
import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

/**
 * An event configuration that causes the build to be triggered when a topic is changed.
 */
public class PluginTopicChangedEvent extends PluginGerritEvent implements Serializable {
    private static final long serialVersionUID = -8490721342780735276L;

    /**
     * Standard constructor.
     */
    @DataBoundConstructor
    public PluginTopicChangedEvent() {
    }

    /**
     * Getter for the Descriptor.
     *
     * @return the Descriptor for the PluginTopicChangedEvent.
     */
    @Override
    public Descriptor<PluginGerritEvent> getDescriptor() {
        return Jenkins.get().getDescriptorByType(PluginTopicChangedEventDescriptor.class);
    }

    @Override
    public Class getCorrespondingEventClass() {
        return TopicChanged.class;
    }

    /**
     * The descriptor for the PluginTopicChangedEvent.
     */
    @Extension
    @Symbol("topicChanged")
    public static class PluginTopicChangedEventDescriptor extends PluginGerritEventDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.TopicChangedDisplayName();
        }
    }
}
