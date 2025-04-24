/*
 * The MIT License
 *
 * Copyright 2021 Intel . All rights reserved.
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

import java.io.Serial;
import java.io.Serializable;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.RerunCheck;

import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * An event configuration that causes the build to be (re-)triggered when the
 * gerrit checks plugin rerun check is triggered.
 */
public class PluginRerunCheckEvent extends PluginGerritEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 5530163420962242330L;

    /**
     * Standard constructor.
     */
    @DataBoundConstructor
    public PluginRerunCheckEvent() {
    }

    /**
     * Getter for the Descriptor.
     *
     * @return the Descriptor for the PluginRerunCheckEvent.
     */
    @Override
    public Descriptor<PluginGerritEvent> getDescriptor() {
        return Jenkins.get().getDescriptorByType(PluginRerunCheckEventDescriptor.class);
    }

    @Override
    public Class getCorrespondingEventClass() {
        return RerunCheck.class;
    }

    /**
     * The descriptor for the PluginRerunCheckEvent.
     */
    @Extension
    @Symbol("rerunCheck")
    public static class PluginRerunCheckEventDescriptor extends PluginGerritEventDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.RerunCheckDisplayName();
        }
    }
}
