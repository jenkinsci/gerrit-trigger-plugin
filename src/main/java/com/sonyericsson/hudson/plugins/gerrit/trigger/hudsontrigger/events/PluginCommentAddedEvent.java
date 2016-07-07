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

import static com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer.ANY_SERVER;

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.VerdictCategory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.util.ListBoxModel;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * An event configuration that causes the build to be triggered when a comment is added.
 * @author Tomas Westling &lt;tomas.westling@sonymobile.com&gt;
 */
public class PluginCommentAddedEvent extends PluginGerritEvent implements Serializable {
    private static final long serialVersionUID = -1190562081236235819L;
    private String verdictCategory;
    private String commentAddedTriggerApprovalValue;

    /**
     * Standard DataBoundConstructor.
     * @param verdictCategory the value part of the VerdictCategory.
     * @param commentAddedTriggerApprovalValue the approval value.
     */
    @DataBoundConstructor
    public PluginCommentAddedEvent(String verdictCategory, String commentAddedTriggerApprovalValue) {
        this.verdictCategory = verdictCategory;
        this.commentAddedTriggerApprovalValue = commentAddedTriggerApprovalValue;
    }

    /**
     * Empty constructor for serializer.
     */
    public PluginCommentAddedEvent() {
    }

    /**
     * Getter for the commentAddedTriggerApprovalValue.
     * @return the value.
     */
    public String getCommentAddedTriggerApprovalValue() {
        return commentAddedTriggerApprovalValue;
    }

    /**
     * Getter for the verdictCategory.
     * @return the verdictCategory.
     */
    public String getVerdictCategory() {
        return verdictCategory;
    }

    /**
     * Getter for the Descriptor.
     * @return the Descriptor for the PluginCommentAddedEvent.
     */
    @Override
    public Descriptor<PluginGerritEvent> getDescriptor() {
        return Hudson.getInstance().getDescriptorByType(PluginCommentAddedEventDescriptor.class);
    }

    @Override
    public Class getCorrespondingEventClass() {
        return CommentAdded.class;
    }

    /**
     * The Descriptor for the PluginCommentAddedEvent.
     */
    @Extension
    @Symbol("commentAdded")
    public static class PluginCommentAddedEventDescriptor extends PluginGerritEventDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.CommentAddedDisplayName();
        }

        /**
         * Fills the verdict category drop-down list.
         *
         * @param serverName the name of the server selected in the "Choose Server" dropdown.
         * @return a ListBoxModel for the drop-down list.
         */
        public ListBoxModel doFillVerdictCategoryItems(
                    @QueryParameter("serverName") @RelativePath(value = "..") String serverName) {
            ListBoxModel m = new ListBoxModel();

            Collection<VerdictCategory> list = null;
            if (ANY_SERVER.equals(serverName)) { //list all configured VCs in all servers
                Map<String, VerdictCategory> map = new HashMap<String, VerdictCategory>();
                for (GerritServer server : PluginImpl.getServers_()) {
                    for (VerdictCategory vc : server.getConfig().getCategories()) {
                        if (!map.containsKey(vc.getVerdictValue())) {
                            map.put(vc.getVerdictValue(), vc);
                        }
                    }
                }
                list = map.values();
            } else {
                GerritServer server = PluginImpl.getServer_(serverName);
                if (server != null) {
                    IGerritHudsonTriggerConfig config = server.getConfig();
                    if (config != null) {
                        list = config.getCategories();
                    }
                }
            }
            if (list != null && !list.isEmpty()) {
                for (VerdictCategory v : list) {
                    m.add(v.getVerdictDescription(), v.getVerdictValue());
                }
            }
            return m;
        }
    }

}
