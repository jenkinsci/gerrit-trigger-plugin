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


import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

/**
 * Super class to all the events that can be configured to trigger on.
  * @author Tomas Westling &lt;tomas.westling@sonymobile.com&gt;
 */
public abstract class PluginGerritEvent extends AbstractDescribableImpl<PluginGerritEvent> {

    /**
     * Getter for the corresponding gerrit event class.
     * @return the gerrit event class.
     */
    public abstract Class getCorrespondingEventClass();

    /**
     * Return if it should trigger build for the specified event.
     * Default implementation only check if the specified event is an instance of the corresponding event class.
     * Sub class can override to add additional validation.
     *
     * @param event The event to validate.
     * @return true if it should trigger on the specified event, otherwise false.
     */
    public boolean shouldTriggerOn(GerritTriggeredEvent event) {
        return getCorrespondingEventClass().isInstance(event);
    }

    /**
     * The Descriptor for the PluginGerritEvent.
     */
    public abstract static class PluginGerritEventDescriptor extends Descriptor<PluginGerritEvent> {
    }
}
