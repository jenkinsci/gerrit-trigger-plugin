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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext;
import hudson.triggers.SCMTrigger.SCMTriggerCause;

/**
 * A Cause why a build was scheduled.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritCause extends SCMTriggerCause {

    private GerritTriggeredEvent tEvent;
    private boolean silentMode;
    private TriggerContext context;

    /**
     * Default DataBound Constructor.
     * @param event the event that triggered the build.
     * @param silentMode Silent Mode on or off.
     */
    public GerritCause(GerritTriggeredEvent event, boolean silentMode) {
        super("");
        this.tEvent = event;
        this.silentMode = silentMode;
        this.context = new TriggerContext(event);
    }

    /**
     * Default DataBound Constructor.
     * @param event the event that triggered the build.
     * @param silentMode Silent Mode on or off.
     * @param context The context with information about other builds triggered for the same event as this one.
     */
    public GerritCause(GerritTriggeredEvent event, boolean silentMode, TriggerContext context) {
        super("");
        this.tEvent = event;
        this.silentMode = silentMode;
        this.context = context;
    }

    /**
     * Default Constructor.
     */
    public GerritCause() {
        super("");
    }

    /**
     * The event.
     * @return the event.
     */
    public GerritTriggeredEvent getEvent() {
        return tEvent;
    }

    /**
     * The event.
     * @param event the event.
     */
    public void setEvent(GerritTriggeredEvent event) {
        this.tEvent = event;
    }

    /**
     * Gets the indication if silent mode was on or off when the build was triggered.
     * When silent mode is on there will be no communication back to Gerrit,
     * i.e. no build started/failed/successful approve messages etc.
     * Default is false.
     * @return true if silent mode was on.
     * @see GerritTrigger#isSilentMode()
     */
    public boolean isSilentMode() {
        return silentMode;
    }

    /**
     * Sets the indication if silent mode was on or off when the build was triggered.
     * When silent mode is on there will be no communication back to Gerrit,
     * i.e. no build started/failed/successful approve messages etc.
     * Default is false.
     * @param silentMode true if silent mode was on.
     * @see GerritTrigger#setSilentMode(boolean)
     */
    public void setSilentMode(boolean silentMode) {
        this.silentMode = silentMode;
    }

    /**
     * The context with information about other builds triggered for the same event as this one.
     * @return the context.
     */
    public TriggerContext getContext() {
        return context;
    }

    /**
     * The context with information about other builds triggered for the same event as this one.
     * @param context the context.
     */
    public void setContext(TriggerContext context) {
        this.context = context;
    }

    /**
     * Gives the short description of the GerritCause.
     * Intended to be overridden by subclasses.
     * @return the short description.
     * @see #getShortGerritDescriptionSilentMode()
     * @see #getShortDescription()
     */
    protected String getShortGerritDescription() {
        return Messages.TriggeredShortDescription(getUrl());
    }

    /**
     * Gives the short description of the GerritCause in silent mode.
     * Intended to be overridden by subclasses.
     * @return the short description for silent mode.
     * @see #getShortGerritDescription()
     * @see #getShortDescription()
     */
    protected String getShortGerritDescriptionSilentMode() {
        return Messages.TriggeredShortDescriptionInSilentMode(getUrl());
    }

    @Override
    public String getShortDescription() {
        if (isSilentMode()) {
            return getShortGerritDescriptionSilentMode();
        } else {
            return getShortGerritDescription();
        }
    }

    /**
     * Gets the URL to the Gerrit patchSet.
     * @return the URL.
     */
    public String getUrl() {
        if (tEvent instanceof ChangeBasedEvent) {
            ChangeBasedEvent changeBasedEvent = (ChangeBasedEvent)tEvent;
            if (null == changeBasedEvent.getPatchSet()) {
                return PluginImpl.getInstance().getConfig().getGerritFrontEndUrlFor(
                        changeBasedEvent.getChange().getNumber(), null);
            }
            return PluginImpl.getInstance().getConfig().getGerritFrontEndUrlFor(
                    changeBasedEvent.getChange().getNumber(),
                    changeBasedEvent.getPatchSet().getNumber());
        }
        return PluginImpl.getInstance().getConfig().getGerritFrontEndUrl();
    }

    @Override
    public String toString() {
        return "GerritCause: " + tEvent + " silent: " + silentMode;
    }

    //CS IGNORE InlineConditionals FOR NEXT 40 LINES. REASON: Auto generated code
    //CS IGNORE MagicNumber FOR NEXT 40 LINES. REASON: Auto generated code

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GerritCause that = (GerritCause)o;

        if (silentMode != that.silentMode) {
            return false;
        }
        if (!tEvent.equals(that.tEvent)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = tEvent.hashCode();
        result = 31 * result + (silentMode ? 1 : 0);
        return result;
    }

    @Deprecated //Kept for backwards compatibility
    private transient PatchsetCreated event;

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
