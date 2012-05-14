/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import hudson.model.BuildBadgeAction;

/**
 * Adds an icon to the build-schedule telling users that the build was triggered by Gerrit.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class BadgeAction implements BuildBadgeAction {

    @Deprecated //Kept for backwards compatibility
    private transient PatchsetCreated event;
    private GerritTriggeredEvent tEvent;

    /**
     * Constructor.
     *
     * @param event the event to show.
     */
    public BadgeAction(GerritTriggeredEvent event) {
        this.tEvent = event;
    }

    /**
     * Default Constructor.
     */
    public BadgeAction() {
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return null;
    }

    /**
     * The event to show.
     *
     * @return the event.
     */
    public GerritTriggeredEvent getEvent() {
        return tEvent;
    }

    /**
     * The event to show.
     *
     * @param event the event.
     */
    public void setEvent(GerritTriggeredEvent event) {
        this.tEvent = event;
    }

    /**
     * Gets the URL for the change.
     *
     * @return the URL to the change.
     */
    public String getUrl() {
        if (tEvent.getChange() != null) {
            if (tEvent.getChange().getUrl() != null && tEvent.getChange().getUrl().length() > 0) {
                return tEvent.getChange().getUrl();
            } else {
                return PluginImpl.getInstance().getConfig().getGerritFrontEndUrlFor(
                        tEvent.getChange().getNumber(),
                        tEvent.getPatchSet().getNumber());
            }
        } else {
            return PluginImpl.getInstance().getConfig().getGerritFrontEndUrl();
        }
    }

    /**
     * For backwards compatibility {@link #event} is kept to be able to deserialize old builds, here event gets resolved
     * to the more abstract version.
     * @return the resolved instance.
     */
    Object readResolve() {
        if (this.event != null) {
            this.tEvent = this.event;
            this.event = null;
        }
        return this;
    }
}
