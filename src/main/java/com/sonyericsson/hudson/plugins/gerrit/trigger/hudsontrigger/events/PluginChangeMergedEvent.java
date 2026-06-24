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

import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeMerged;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serial;
import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * An event configuration that causes the build to be triggered when a change is merged.
 * @author Tomas Westling &lt;tomas.westling@sonymobile.com&gt;
 */
public class PluginChangeMergedEvent extends PluginGerritEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 2277980034044218950L;

    private String commitMessageContainsRegEx = "";

    private transient Pattern commitMessagePattern = null;

    /**
     * Standard constructor.
     */
    @DataBoundConstructor
    public PluginChangeMergedEvent() {
    }

    /**
     * Setter for commitMessageContainsRegEx.
     *
     * @param commitMessageContainsRegEx Trigger if this regex matches the commit message
     */
    @DataBoundSetter
    public void setCommitMessageContainsRegEx(String commitMessageContainsRegEx) {
        this.commitMessageContainsRegEx = commitMessageContainsRegEx;
        this.commitMessagePattern = null;
    }

    /**
     * Getter for commitMessageContainsRegEx field.
     *
     * @return commitMessageContainsRegEx
     */
    public String getCommitMessageContainsRegEx() {
        return commitMessageContainsRegEx;
    }

    /**
     * Getter for the Descriptor.
     * @return the Descriptor for the PluginChangeMergedEvent.
     */
    @Override
    public Descriptor<PluginGerritEvent> getDescriptor() {
        return Jenkins.get().getDescriptorByType(PluginChangeMergedEventDescriptor.class);
    }

    @Override
    public Class getCorrespondingEventClass() {
        return ChangeMerged.class;
    }

    @Override
    public boolean shouldTriggerOn(GerritTriggeredEvent event) {
        if (!super.shouldTriggerOn(event)) {
            return false;
        }
        if (StringUtils.isNotEmpty(commitMessageContainsRegEx)) {
            if (commitMessagePattern == null) {
                commitMessagePattern = Pattern.compile(
                        this.commitMessageContainsRegEx, Pattern.DOTALL | Pattern.MULTILINE);
            }
            String commitMessage = ((ChangeMerged)event).getChange().getCommitMessage();
            return commitMessagePattern.matcher(commitMessage).find();
        }
        return true;
    }

    /**
     * The descriptor for the PluginChangeMergedEvent.
     */
    @Extension
    @Symbol("changeMerged")
    public static class PluginChangeMergedEventDescriptor extends PluginGerritEventDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.ChangeMergedDisplayName();
        }
    }
}
