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
 * Listener interface for listening in on a specific GerritEvent's lifecycle.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public interface GerritEventLifecycleListener {

    /**
     * Called before any triggers are enumerated.
     * @param event the event.
     */
    void triggerScanStarting(GerritEvent event);

    /**
     * Called after all triggers has been enumerated.
     * @param event the event.
     */
    void triggerScanDone(GerritEvent event);

    /**
     * Called when the trigger of a project has decided to trigger on the event.
     * @param event the event.
     * @param project the project that was triggered.
     */
    void projectTriggered(GerritEvent event, Job project);

    /**
     * Called when a build has started.
     * @param event the event.
     * @param build the build.
     */
    void buildStarted(GerritEvent event, Run build);

    /**
     * Called when a build is completed.
     * @param event the event.
     * @param build the build.
     */
    void buildCompleted(GerritEvent event, Run build);

    /**
     * Called when all builds triggered by the event are completed.
     * @param event the event.
     */
    void allBuildsCompleted(GerritEvent event);
}
