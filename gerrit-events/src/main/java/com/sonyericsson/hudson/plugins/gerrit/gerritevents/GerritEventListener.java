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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;

/**
 * Base Listener interface for those that are interested in Gerrit events.
 * The event firering mechanism is using some reflection magic to try and find the best sutable method
 * to call the listener on. So for example if the listener class is implementing a method with the signature
 * {@code public void gerritEvent(com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.PatchsetAdded event) }
 * that method will be called directly for every PatchsetAdded event.
 * The method {@link #gerritEvent(com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent) }
 * is a fallback method if no other suitable method could be found.
 *
 * The events are fired on one of the event worker threads so any listener needs to handle that two or more events
 * theoretically could be fired at the same time.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public interface GerritEventListener {
    /**
     * Fallback method for all valid Gerrit events.
     * @param event the event
     */
    void gerritEvent(GerritEvent event);
}
