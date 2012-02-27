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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import hudson.model.BuildBadgeAction;

/**
 * Adds an icon to the build-schedule telling users that the build was triggered by Gerrit.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class BadgeAction implements BuildBadgeAction {

    private GerritTriggeredEvent event;

    /**
     * Constructor.
     *
     * @param event the event to show.
     */
    public BadgeAction(GerritTriggeredEvent event) {
        this.event = event;
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
        return event;
    }

    /**
     * The event to show.
     *
     * @param event the event.
     */
    public void setEvent(GerritTriggeredEvent event) {
        this.event = event;
    }

    /**
     * Gets the URL for the change.
     *
     * @return the URL to the change.
     */
    public String getUrl() {
        if (event.getChange() != null) {
            if (event.getChange().getUrl() != null && event.getChange().getUrl().length() > 0) {
                return event.getChange().getUrl();
            } else {
                return PluginImpl.getInstance().getConfig().getGerritFrontEndUrlFor(
                        event.getChange().getNumber(),
                        event.getPatchSet().getNumber());
            }
        } else {
            return PluginImpl.getInstance().getConfig().getGerritFrontEndUrl();
        }
    }
}
