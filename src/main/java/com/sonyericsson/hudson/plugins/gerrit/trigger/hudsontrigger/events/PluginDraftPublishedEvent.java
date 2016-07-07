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

import com.sonymobile.tools.gerrit.gerritevents.dto.events.DraftPublished;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.ReplicationConfig;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Hudson;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

/**
 * An event configuration that causes the build to be triggered when a draft is published.
 * @author David Pursehouse &lt;david.pursehouse@sonymobile.com&gt;
 */
public class PluginDraftPublishedEvent extends PluginGerritEvent implements Serializable {
    private static final long serialVersionUID = -8543595301119872587L;


    /**
     * Standard DataBoundConstructor.
     */
    @DataBoundConstructor
    public PluginDraftPublishedEvent() {
    }

    /**
     * Getter for the Descriptor.
     * @return the Descriptor for the PluginDraftPublishedEvent.
     */
    @Override
    public Descriptor<PluginGerritEvent> getDescriptor() {
        return Hudson.getInstance().getDescriptorByType(PluginDraftPublishedEventDescriptor.class);
    }

    @Override
    public Class getCorrespondingEventClass() {
        return DraftPublished.class;
    }

    /**
     * The Descriptor for the PluginDraftPublishedEvent.
     */
    @Extension
    @Symbol("draftPublished")
    public static class PluginDraftPublishedEventDescriptor extends PluginGerritEventDescriptor {

        /**
         * Whether replication is enabled.
         * If so, the user will see a warning to read the help regarding replication
         * @return true if so.
         */
        public boolean isReplicationEnabled() {
            for (GerritServer server : PluginImpl.getServers_()) {
                ReplicationConfig replicationConfig = server.getConfig().getReplicationConfig();
                if (replicationConfig != null) {
                    return replicationConfig.isEnableReplication();
                }
            }
            return false;
        }


        @Override
        public String getDisplayName() {
            return Messages.DraftPublishedDisplayName();
        }
    }
}
