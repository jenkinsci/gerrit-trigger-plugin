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

import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext;

/**
 * A Cause of a {@link ManualPatchsetCreated}.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritManualCause extends GerritUserCause {

    /**
     * Default constructor.
     * <strong>Do not use this unless you are a serializer.</strong>
     */
    public GerritManualCause() {
    }

    //CS IGNORE LineLength FOR NEXT 19 LINES. REASON: Documentation.

    /**
     * Standard constructor.
     * @param event the event.
     * @param silentMode if silent mode.
     * @param context the trigger context.
     * @see GerritCause#GerritCause(com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent, boolean, TriggerContext)
     */
    public GerritManualCause(ManualPatchsetCreated event, boolean silentMode, TriggerContext context) {
        super(event, silentMode, context, event.getUserName());
    }

    /**
     * Standard constructor.
     * @param event the event.
     * @param silentMode if it is in silent mode.
     * @see GerritCause#GerritCause(com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent, boolean)
     */
    public GerritManualCause(ManualPatchsetCreated event, boolean silentMode) {
        super(event, silentMode, event.getUserName());
    }

    /**
     * The userName of the user who triggered the manual event.
     * @return the userName
     */
    @Override
    public String getUserName() {
        if (super.getUserName() != null) {
            return super.getUserName();
        } else {
            return ((ManualPatchsetCreated)getEvent()).getUserName();
        }
    }

    /**
     * Is this cause of a manually triggered event.
     * Helper for the jelly scripts, it always returns true.
     * @return true.
     */
    @SuppressWarnings("unused") //Called from index.jelly
    public boolean isManuallyTriggered() {
        return true;
    }

    @Override
    protected String getShortGerritDescription() {
        return Messages.ManuallyTriggeredShortDescription(getUrl(), getUserName());
    }

    @Override
    protected String getShortGerritDescriptionSilentMode() {
        return Messages.ManuallyTriggeredShortDescriptionInSilentMode(getUrl(), getUserName());
    }
}
