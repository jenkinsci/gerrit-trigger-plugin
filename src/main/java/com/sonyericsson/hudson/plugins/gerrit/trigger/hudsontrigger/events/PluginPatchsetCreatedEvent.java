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
import hudson.util.ListBoxModel;
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
     * String value of TRIGGER_FOR_ALL.
     */
    public static final String TRIGGER_FOR_ALL = "all";
    /**
     * String value of TRIGGER_FOR_PUBLISHED.
     */
    public static final String TRIGGER_FOR_PUBLISHED = "published";
    /**
     * String value of TRIGGER_FOR_DRAFT.
     */
    public static final String TRIGGER_FOR_DRAFT = "draft";

    /**
     * Set {@link #triggerFor} = PluginPatchsetCreatedEvent.TRIGGER_FOR_PUBLISHED to have the same effect.
     */
    protected transient boolean excludeDrafts;
    private String triggerFor;
    private boolean excludeTrivialRebase;
    private boolean excludeNoCodeChange;


    /**
     * Default constructor.
     */
    public PluginPatchsetCreatedEvent() {
        this(TRIGGER_FOR_ALL, false, false);
    }

    /**
     * Standard DataBoundConstructor.
     * @param triggerFor which type of patchset should trigger this the job.
     * @param excludeTrivialRebase if trivial rebases should be excluded or not.
     * @param excludeNoCodeChange if message-only changes should be excluded.
     */
    @DataBoundConstructor
    public PluginPatchsetCreatedEvent(String triggerFor,
        boolean excludeTrivialRebase,
        boolean excludeNoCodeChange) {
        this.excludeTrivialRebase = excludeTrivialRebase;
        this.excludeNoCodeChange = excludeNoCodeChange;
        this.triggerFor = triggerFor;
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
     * Getter for the triggerFor field.
     * @return triggerFor
     */
    public String getTriggerFor() { return triggerFor; }

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

    /**
     * Check if job should be trigger by draft patchsets.
     * @return true or false
     */
    public boolean triggerForDrafts() {
        return (triggerFor.equals(TRIGGER_FOR_ALL) || triggerFor.equals(TRIGGER_FOR_DRAFT));
    }

    /**
     * Check if job should be trigger by published patchsets.
     * @return true or false
     */
    public boolean triggerForPublishedPatchsets() {
        return (triggerFor.equals(TRIGGER_FOR_ALL) || triggerFor.equals(TRIGGER_FOR_PUBLISHED));
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
        if (((PatchsetCreated)event).getPatchSet().isDraft() && !triggerForDrafts()) {
            return false;
        }
        if (!((PatchsetCreated)event).getPatchSet().isDraft() && !triggerForPublishedPatchsets()) {
            return false;
        }
        return true;
    }

    /**
     * Migrate data from versions previous to 2.27.6 to keep backward compatibility.
     * @return this
     */
    protected Object readResolve() {
        triggerFor = TRIGGER_FOR_ALL;
        if (excludeDrafts) {
            triggerFor = TRIGGER_FOR_PUBLISHED;
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

        /**
         * Fill the item for triggerFor select items.
         * @return ListBoxModel
         */
        public ListBoxModel doFillTriggerForItems() {
            return new ListBoxModel(
                    new ListBoxModel.Option("Draft And Published Patchsets", PluginPatchsetCreatedEvent.TRIGGER_FOR_ALL),
                    new ListBoxModel.Option("Published Patchsets", PluginPatchsetCreatedEvent.TRIGGER_FOR_PUBLISHED),
                    new ListBoxModel.Option("Draft Patchsets", PluginPatchsetCreatedEvent.TRIGGER_FOR_DRAFT)
            );
        }
    }
}
