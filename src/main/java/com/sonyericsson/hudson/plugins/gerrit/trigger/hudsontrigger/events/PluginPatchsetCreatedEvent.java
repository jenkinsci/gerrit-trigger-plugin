/*
 *  The MIT License
 *
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events;

import com.sonymobile.tools.gerrit.gerritevents.dto.GerritChangeKind;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

/**
 * An event configuration that causes the build to be triggered when a patchset is created.
 * @author Tomas Westling &lt;tomas.westling@sonymobile.com&gt;
 */
public class PluginPatchsetCreatedEvent extends PluginGerritEvent implements Serializable {
    private static final long serialVersionUID = 970946986242309088L;

    /**
     * Uncheck {@link #triggerForDrafts} to have the same effect.
     */
    protected transient boolean excludeDrafts;
    private boolean triggerForPublishedPatchsets;
    private boolean triggerForDrafts;
    private boolean excludeTrivialRebase;
    private boolean excludeNoCodeChange;


    /**
     * Default constructor.
     */
    public PluginPatchsetCreatedEvent() {
        this(true, true, false, false);
    }

    /**
     * Standard DataBoundConstructor.
     * @param triggerForPublishedPatchsets if should trigger for published patchSets.
     * @param triggerForDrafts if should trigger for drafts.
     * @param excludeTrivialRebase if trivial rebases should be excluded or not.
     * @param excludeNoCodeChange if message-only changes should be excluded.
     */
    @DataBoundConstructor
    public PluginPatchsetCreatedEvent(boolean triggerForPublishedPatchsets,
        boolean triggerForDrafts,
        boolean excludeTrivialRebase,
        boolean excludeNoCodeChange) {
        this.excludeTrivialRebase = excludeTrivialRebase;
        this.excludeNoCodeChange = excludeNoCodeChange;
        this.triggerForDrafts = triggerForDrafts;
        this.triggerForPublishedPatchsets = triggerForPublishedPatchsets;
    }


    /**
     * Getter for the Descriptor.
     * @return the Descriptor for the PluginPatchsetCreatedEvent.
     */
    @Override
    public Descriptor<PluginGerritEvent> getDescriptor() {
        return Hudson.getInstance().getDescriptorByType(PluginPatchsetCreatedEventDescriptor.class);
    }

    @Override
    public Class getCorrespondingEventClass() {
        return PatchsetCreated.class;
    }


    /**
     * Getter for the triggerForPublishedPatchsets field.
     * @return triggerForPublishedPatchsets
     */
    public boolean isTriggerForPublishedPatchsets() { return triggerForPublishedPatchsets; }

    /**
     * Getter for the triggerForDrafts field.
     * @return triggerForDrafts
     */
    public boolean isTriggerForDrafts() { return triggerForDrafts; }

    /**
     * Getter for the excludeTrivialRebase field.
     * @return excludeTrivialRebase
     */
    public boolean isExcludeTrivialRebase() {
        return excludeTrivialRebase;
    }

    /**
     * Getter for the excludeNoCodeChange field.
     * @return excludeNoCodeChange
     */
    public boolean isExcludeNoCodeChange() {
        return excludeNoCodeChange;
    }

    @Override
    public boolean shouldTriggerOn(GerritTriggeredEvent event) {
        if (!super.shouldTriggerOn(event)) {
            return false;
        }
        if (event instanceof ManualPatchsetCreated) {
            // always trigger build when the build is triggered manually
            return true;
        }
        if (excludeTrivialRebase
            && GerritChangeKind.TRIVIAL_REBASE == ((PatchsetCreated)event).getPatchSet().getKind()) {
            return false;
        }
        if (excludeNoCodeChange
            && GerritChangeKind.NO_CODE_CHANGE == ((PatchsetCreated)event).getPatchSet().getKind()) {
            return false;
        }
        if (((PatchsetCreated)event).getPatchSet().isDraft() && !triggerForDrafts) {
            return false;
        }
        if (!((PatchsetCreated)event).getPatchSet().isDraft() && !triggerForPublishedPatchsets) {
            return false;
        }
        return true;
    }

    /**
     * This method migrates data from versions previous to 2.27.6 of this plugin to keep backwards compatibility.
     * @return this
     */
    protected Object readResolve() {
        triggerForPublishedPatchsets = true;
        triggerForDrafts = true;
        if (excludeDrafts) {
            triggerForDrafts = false;
        }
        return this;
    }

    /**
     * The Descriptor for the PluginPatchsetCreatedEvent.
     */
    @Extension
    @Symbol("patchsetCreated")
    public static class PluginPatchsetCreatedEventDescriptor extends PluginGerritEventDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.PatchsetCreatedDisplayName();
        }
    }
}
