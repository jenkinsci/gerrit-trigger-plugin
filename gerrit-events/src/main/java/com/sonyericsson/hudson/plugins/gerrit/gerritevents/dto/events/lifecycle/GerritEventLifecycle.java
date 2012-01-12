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

package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.lifecycle;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import java.util.LinkedList;
import java.util.List;

/**
 * Adaptor class for handling event listeners for the lifecycle of a GerritEvent.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public abstract class GerritEventLifecycle {

    private transient List<GerritEventLifecycleListener> listeners;

    /**
     * Adds the listener to the list of listeners.
     * @param listener the listener to add.
     */
    public synchronized void addListener(GerritEventLifecycleListener listener) {
        if (listeners == null) {
            listeners = new LinkedList<GerritEventLifecycleListener>();
        }
        if (!listeners.contains(listener)) {
           listeners.add(listener);
        }
    }

    /**
     * Removes the listener from the list of listeners.
     * @param listener the listener to remove.
     * @return true if it was removed.
     * @see List#remove(java.lang.Object)
     */
    public synchronized boolean removeListener(GerritEventLifecycleListener listener) {
        if (listeners != null) {
            return listeners.remove(listener);
        } else {
            return false;
        }
    }

    /**
     * Fires the event {@link GerritEventLifecycleListener#triggerScanStarting(PatchsetCreated)}.
     */
    public synchronized void fireTriggerScanStarting() {
        fireEvent(new ListenerVisitor() {
            @Override
            public void visit(GerritEventLifecycleListener listener, PatchsetCreated event) {
                listener.triggerScanStarting(event);
            }
        });
    }

    /**
     * Fires the event {@link GerritEventLifecycleListener#triggerScanDone(PatchsetCreated)}.
     */
    public synchronized void fireTriggerScanDone() {
        fireEvent(new ListenerVisitor() {
            @Override
            public void visit(GerritEventLifecycleListener listener, PatchsetCreated event) {
                listener.triggerScanDone(event);
            }
        });
    }

    /**
     * Fires the event {@link GerritEventLifecycleListener#projectTriggered(PatchsetCreated, AbstractProject)}.
     * @param project the project that is triggered.
     */
    public synchronized void fireProjectTriggered(final AbstractProject project) {
        fireEvent(new ListenerVisitor() {
            @Override
            public void visit(GerritEventLifecycleListener listener, PatchsetCreated event) {
                listener.projectTriggered(event, project);
            }
        });
    }

    /**
     * Fires the event {@link GerritEventLifecycleListener#buildStarted(PatchsetCreated, AbstractBuild)}.
     * @param build the build that has started.
     */
    public synchronized void fireBuildStarted(final AbstractBuild build) {
        fireEvent(new ListenerVisitor() {
            @Override
            public void visit(GerritEventLifecycleListener listener, PatchsetCreated event) {
                listener.buildStarted(event, build);
            }
        });
    }

    /**
     * Fires the event {@link GerritEventLifecycleListener#buildCompleted(PatchsetCreated, AbstractBuild)}.
     * @param build the build that is completed.
     */
    public synchronized void fireBuildCompleted(final AbstractBuild build) {
        fireEvent(new ListenerVisitor() {
            @Override
            public void visit(GerritEventLifecycleListener listener, PatchsetCreated event) {
                listener.buildCompleted(event, build);
            }
        });
    }

    /**
     * Fires the event {@link GerritEventLifecycleListener#allBuildsCompleted(PatchsetCreated)}.
     */
    public synchronized void fireAllBuildsCompleted() {
        fireEvent(new ListenerVisitor() {
            @Override
            public void visit(GerritEventLifecycleListener listener, PatchsetCreated event) {
                listener.allBuildsCompleted(event);
            }
        });
    }

    /**
     * Fires an event with the help of a visitor.
     * It iterates through all the listeners, and for each listener asks the visitor to do stuff.
     * @param visitor the visitor that calls the correct method.
     */
    private synchronized void fireEvent(ListenerVisitor visitor) {
        if (listeners != null) {
            PatchsetCreated event = null;
            if (this instanceof PatchsetCreated) {
                event = (PatchsetCreated)this;
            }
            //Get a cloned list so we don't risk modifying it on the same thread.
            List<GerritEventLifecycleListener> list = getListeners();
            for (GerritEventLifecycleListener listener : list) {
                visitor.visit(listener, event);
            }
        }
    }

    /**
     * Gets a copy of the internal transient list of listeners.
     * @return the copy of the list of listeners, or null if the origin list is null.
     */
    protected synchronized List<GerritEventLifecycleListener> getListeners() {
        if (listeners != null) {
            return new LinkedList<GerritEventLifecycleListener>(listeners);
        } else {
            return null;
        }
    }

    /**
     * Internal Visitor for different events.
     */
    private interface ListenerVisitor {
        /**
         * Visits the listener.
         * @param listener the listener to visit.
         * @param event the event.
         */
        void visit(GerritEventLifecycleListener listener, PatchsetCreated event);
    }
}
