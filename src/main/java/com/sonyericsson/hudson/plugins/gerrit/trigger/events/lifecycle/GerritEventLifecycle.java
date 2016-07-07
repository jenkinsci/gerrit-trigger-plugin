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

package com.sonyericsson.hudson.plugins.gerrit.trigger.events.lifecycle;

import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import hudson.model.Job;
import hudson.model.Run;

/**
 * Interface representing a class handling event listeners for the lifecycle of a GerritEvent.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 * @author Hugo Arès &lt;hugo.ares@ericsson.com&gt;
 */
public interface GerritEventLifecycle {

    /**
     * The event associated with the lifecycle.
     * @return The event
     */
    GerritEvent getEvent();

    /**
     * Adds the listener to the list of listeners.
     * @param listener the listener to add.
     */
    void addListener(GerritEventLifecycleListener listener);

    /**
     * Removes the listener from the list of listeners.
     * @param listener the listener to remove.
     * @return true if it was removed.
     * @see java.util.List#remove(java.lang.Object)
     */
    boolean removeListener(GerritEventLifecycleListener listener);

    /**
     * Fires the event {@link GerritEventLifecycleListener#triggerScanStarting(GerritEvent)}.
     */
    void fireTriggerScanStarting();

    /**
     * Fires the event {@link GerritEventLifecycleListener#triggerScanDone(GerritEvent)}.
     */
    void fireTriggerScanDone();

    /**
     * Fires the event {@link GerritEventLifecycleListener#projectTriggered(GerritEvent, Job)}.
     * @param project the project that is triggered.
     */
    void fireProjectTriggered(final Job<?, ?> project);

    /**
     * Fires the event {@link GerritEventLifecycleListener#buildStarted(GerritEvent, Run)}.
     * @param build the build that has started.
     */
    void fireBuildStarted(final Run<?, ?> build);

    /**
     * Fires the event {@link GerritEventLifecycleListener#buildCompleted(GerritEvent, Run)}.
     * @param build the build that is completed.
     */
    void fireBuildCompleted(final Run<?, ?> build);

    /**
     * Fires the event {@link GerritEventLifecycleListener#allBuildsCompleted(GerritEvent)}.
     */
    void fireAllBuildsCompleted();
}
