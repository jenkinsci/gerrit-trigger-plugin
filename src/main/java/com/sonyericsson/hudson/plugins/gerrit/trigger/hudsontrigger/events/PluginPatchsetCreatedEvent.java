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
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * An event configuration that causes the build to be triggered when a patchset is created.
 *
 * @author Tomas Westling &lt;tomas.westling@sonymobile.com&gt;
 */
public class PluginPatchsetCreatedEvent extends PluginGerritEvent implements Serializable {
    private static final long serialVersionUID = 970946986242309088L;

    private boolean excludeDrafts = false;
    private boolean excludeTrivialRebase = false;
    private boolean excludeNoCodeChange = false;
    private boolean excludePrivateState = false;
    private boolean excludeWipState = false;
    private String commitMessageContainsRegEx = "";

    private transient Pattern commitMessagePattern = null;


    /**
     * Default constructor.
     */
    @DataBoundConstructor
    public PluginPatchsetCreatedEvent() {
        this.excludeDrafts = false;
        this.excludeTrivialRebase = false;
        this.excludeNoCodeChange = false;
        this.excludePrivateState = false;
        this.excludeWipState = false;
        this.commitMessageContainsRegEx = "";
    }

    /**
     * Standard DataBoundConstructor.
     *
     * @param excludeDrafts        if drafts should be excluded or not.
     * @param excludeTrivialRebase if trivial rebases should be excluded or not.
     * @param excludeNoCodeChange  if message-only changes should be excluded.
     */
    @Deprecated
    public PluginPatchsetCreatedEvent(boolean excludeDrafts,
                                      boolean excludeTrivialRebase,
                                      boolean excludeNoCodeChange) {
        this.excludeDrafts = excludeDrafts;
        this.excludeTrivialRebase = excludeTrivialRebase;
        this.excludeNoCodeChange = excludeNoCodeChange;
    }

    /**
     * Setter for excludeDrafts.
     *
     * @param excludeDrafts if drafts should be excluded or not.
     */
    @DataBoundSetter
    public void setExcludeDrafts(boolean excludeDrafts) {
        this.excludeDrafts = excludeDrafts;
    }

    /**
     * Setter for excludeTrivialRebase.
     *
     * @param excludeTrivialRebase if trivial rebases should be excluded or not.
     */
    @DataBoundSetter
    public void setExcludeTrivialRebase(boolean excludeTrivialRebase) {
        this.excludeTrivialRebase = excludeTrivialRebase;
    }

    /**
     * Setter for excludeNoCodeChange.
     *
     * @param excludeNoCodeChange if message-only changes should be excluded.
     */
    @DataBoundSetter
    public void setExcludeNoCodeChange(boolean excludeNoCodeChange) {
        this.excludeNoCodeChange = excludeNoCodeChange;
    }

    /**
     * Setter for excludePrivateState.
     *
     * @param excludePrivateState if private state changes should be excluded.
     */
    @DataBoundSetter
    public void setExcludePrivateState(boolean excludePrivateState) {
        this.excludePrivateState = excludePrivateState;
    }

    /**
     * Setter for excludeWipState.
     *
     * @param excludeWipState if wip state changes should be excluded.
     */
    @DataBoundSetter
    public void setExcludeWipState(boolean excludeWipState) {
        this.excludeWipState = excludeWipState;
    }

    /**
     * Setter for commitMessageContains.
     *
     * @param commitMessageContainsRegEx Trigger if this text is included in the commit message
     */
    @DataBoundSetter
    public void setCommitMessageContainsRegEx(String commitMessageContainsRegEx) {
        this.commitMessageContainsRegEx = commitMessageContainsRegEx;
        this.commitMessagePattern = null;
    }

    /**
     * Getter for the Descriptor.
     *
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
     * Getter for the excludeDrafts field.
     *
     * @return excludeDrafts
     */
    public boolean isExcludeDrafts() {
        return excludeDrafts;
    }

    /**
     * Getter for the excludeTrivialRebase field.
     *
     * @return excludeTrivialRebase
     */
    public boolean isExcludeTrivialRebase() {
        return excludeTrivialRebase;
    }

    /**
     * Getter for the excludeNoCodeChange field.
     *
     * @return excludeNoCodeChange
     */
    public boolean isExcludeNoCodeChange() {
        return excludeNoCodeChange;
    }

    /**
     * Getter for the excludePrivateState field.
     *
     * @return excludePrivateState
     */
    public boolean isExcludePrivateState() {
        return excludePrivateState;
    }

    /**
     * Getter for the excludeWipState field.
     *
     * @return excludeWipState
     */
    public boolean isExcludeWipState() {
        return excludeWipState;
    }

    /**
     * Getter for commitMessageContains field.
     *
     * @return commitMessageContains
     */
    public String getCommitMessageContainsRegEx() {
        return commitMessageContainsRegEx;
    }

    @Override
    public boolean shouldTriggerOn(GerritTriggeredEvent event) {
        if (!super.shouldTriggerOn(event)) {
            return false;
        }
        if (excludeDrafts && ((PatchsetCreated)event).getPatchSet().isDraft()) {
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
        if (excludePrivateState && ((PatchsetCreated)event).getChange().isPrivate()) {
            return false;
        }
        if (excludeWipState && ((PatchsetCreated)event).getChange().isWip()) {
            return false;
        }
        if (StringUtils.isNotEmpty(commitMessageContainsRegEx)) {
            if (commitMessagePattern == null) {
                commitMessagePattern = Pattern.compile(
                        this.commitMessageContainsRegEx, Pattern.DOTALL | Pattern.MULTILINE);
            }
            String commitMessage = ((PatchsetCreated)event).getChange().getCommitMessage();
            return commitMessagePattern.matcher(commitMessage).find();
        }
        return true;
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
