/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;

import hudson.model.Hudson;
import hudson.model.AbstractProject;
import hudson.model.BuildBadgeAction;
import java.io.IOException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Adds an icon to the build-schedule telling users that the build was triggered by Gerrit.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class BadgeAction implements BuildBadgeAction {

    private PatchsetCreated event;
    private String parentProject;
    /**
     * Constructor.
     * @param event the event to show.
     * @param parentProject the name of the parent project
     */
    public BadgeAction(PatchsetCreated event, String parentProject) {
        this.event = event;
        this.parentProject = parentProject;
    }

    /**
     * Default Constructor.
     */
    public BadgeAction() {
    }

    /**
     * Gets the owning project.
     * @return the AbstractProject object corresponding to the parent project name, null if
     *           parentProject itself is null.
     */
    public AbstractProject getProject() {
        if (parentProject != null) {
            return Hudson.getInstance().getItemByFullName(parentProject, AbstractProject.class);
        }
        return null;
    }

    @Override
    public String getIconFileName() {
        // Only return a displayable value if we have a defined project.
        // This is here and in the other methods to handle builds from before the parentProject was added.
        if (getProject() != null) {
            return "clock.gif";
        }
        return null;
    }

    @Override
    public String getDisplayName() {
        if (getProject() != null) {
            return "Rebuild Gerrit Change";
        }
        return null;
    }

    @Override
    public String getUrlName() {
        if (getProject() != null) {
            return "gerrit-trigger";
        }
        return null;
    }

    /**
     * The event to show.
     * @return the event.
     */
    public PatchsetCreated getEvent() {
        return event;
    }

    /**
     * The event to show.
     * @param event the event.
     */
    public void setEvent(PatchsetCreated event) {
        this.event = event;
    }

    /**
     * Gets the URL for the change.
     * @return the URL to the change.
     */
    public String getUrl() {
        return PluginImpl.getInstance().getConfig().getGerritFrontEndUrlFor(
                event.getChange().getNumber(),
                event.getPatchSet().getNumber());
    }

    /**
     * Handles the actual re-build launch and redirects back to the project page.
     * @param req StaplerRequest
     * @param rsp StaplerResponse
     * @throws IOException in case of Stapler issues
     */
    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (getProject() == null) {
            return;
        }

        if (!this.getProject().hasPermission(AbstractProject.BUILD)) {
            return;
        }

        GerritTrigger gt = (GerritTrigger) getProject().getTrigger(GerritTrigger.class);
        if (gt == null) {
            return;
        }

        gt.gerritEvent(event);

        rsp.sendRedirect2(getProject().getAbsoluteUrl());
    }
}
