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

import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import java.io.Serializable;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * An event configuration that causes the build to be triggered when a change is abandoned.
 */
public class PluginChangeAbandonedEvent extends PluginGerritEvent implements Serializable {
    private static final long serialVersionUID = -8490721342780735276L;

    /**
     * Standard constructor.
     */
    @DataBoundConstructor
    public PluginChangeAbandonedEvent() {
    }

    /**
     * Getter for the Descriptor.
     *
     * @return the Descriptor for the PluginChangeAbandonedEvent.
     */
    @Override
    public Descriptor<PluginGerritEvent> getDescriptor() {
        return Hudson.getInstance().getDescriptorByType(PluginChangeAbandonedEventDescriptor.class);
    }

    @Override
    public Class getCorrespondingEventClass() {
        return ChangeAbandoned.class;
    }

    /**
     * The descriptor for the PluginChangeAbandonedEvent.
     */
    @Extension
    @Symbol("changeAbandoned")
    public static class PluginChangeAbandonedEventDescriptor extends PluginGerritEventDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.ChangeAbandonedDisplayName();
        }
    }
}
