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

package com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;

/**
 * Base work when all other work is done.
 * Notifies the listeners of the event.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public abstract class AbstractGerritEventWork implements Work {

    //CS IGNORE LineLength FOR NEXT 5 LINES. REASON: Javadoc link.

    /**
     * Performs the work.
     * I.e. calls {@link Coordinator#notifyListeners(com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent)}.
     * @param event the event to ask the Coordinator to notify with.
     * @param coordinator the coordinator to call.
     */
    protected void perform(GerritEvent event, Coordinator coordinator) {
        coordinator.notifyListeners(event);
    }
}
