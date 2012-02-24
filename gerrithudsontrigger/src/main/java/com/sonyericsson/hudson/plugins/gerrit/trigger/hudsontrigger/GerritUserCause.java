/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext;
import hudson.model.Hudson;
import org.kohsuke.stapler.export.Exported;

/**
 * Represents a Cause for a re-triggered/user-triggered Gerrit job.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritUserCause extends GerritCause {

    private final String authenticationName;

    /**
     * Default constructor.
     * <strong>Do not use this unless you are a serializer.</strong>
     */
    public GerritUserCause() {
        authenticationName = null;
    }

    /**
     * Standard Constructor.
     * @param event the event.
     * @param silentMode if silentMode.
     * @param context the context.
     */
    public GerritUserCause(GerritTriggeredEvent event, boolean silentMode, TriggerContext context) {
        super(event, silentMode, context);
        this.authenticationName = Hudson.getAuthentication().getName();
    }

    /**
     * Standard Constructor.
     * @param event the event.
     * @param silentMode if silentMode.
     * @param context the context.
     * @param authenticationName the username.
     */
    public GerritUserCause(GerritTriggeredEvent event, boolean silentMode,
            TriggerContext context, String authenticationName) {
        super(event, silentMode, context);
        this.authenticationName = authenticationName;
    }

    /**
     * Standard Constructor.
     * @param event the event.
     * @param silentMode if silentMode.
     * @param authenticationName the username.
     */
    public GerritUserCause(GerritTriggeredEvent event, boolean silentMode,
            String authenticationName) {
        super(event, silentMode);
        this.authenticationName = authenticationName;
    }

    /**
     * Standard Constructor.
     * Will take the userName from the current web-context.
     * @param event the event.
     * @param silentMode if silentMode.
     */
    public GerritUserCause(GerritTriggeredEvent event, boolean silentMode) {
        super(event, silentMode);
        this.authenticationName = Hudson.getAuthentication().getName();
    }

    //CS IGNORE MagicNumber FOR NEXT 9 LINES. REASON: As it should be.

    /**
     * The username.
     * @return the username.
     */
    @Exported(visibility = 3)
    public String getUserName() {
        return authenticationName;
    }

    @Override
    protected String getShortGerritDescription() {
        return Messages.ReTriggeredShortDescription(getUrl(), getUserName());
    }

    @Override
    protected String getShortGerritDescriptionSilentMode() {
        return Messages.ReTriggeredShortDescriptionInSilentMode(getUrl(), getUserName());
    }
}
