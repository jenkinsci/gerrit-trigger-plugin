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

package com.sonyericsson.hudson.plugins.gerrit.trigger.events;

import java.util.LinkedList;
import java.util.List;

import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.lifecycle.GerritEventLifecycle;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.lifecycle.GerritEventLifecycleListener;

import hudson.model.Job;
import hudson.model.Run;
import net.sf.json.JSONObject;

/**
 * Represents a Patchset manually selected to be built by a user.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class ManualPatchsetCreated extends PatchsetCreated implements GerritEventLifecycle {

    private String userName;
    private transient List<GerritEventLifecycleListener> listeners;

    /**
     * Default Constructor.
     */
    public ManualPatchsetCreated() {
    }

    /**
     * Standard Constructor.
     * @param change JSONObject containing the change information.
     * @param patch JSONObject containing the patchSet information.
     * @param userName the user that manually fired the Gerrit event.
     */
    public ManualPatchsetCreated(JSONObject change, JSONObject patch, String userName) {
        fromJson(change, patch);
        this.userName = userName;
    }

    /**
     * Sets the relevant values from the JSONObjects.
     * @param change the change info.
     * @param patch the patchSet info.
     */
    public void fromJson(JSONObject change, JSONObject patch) {
        setChange(new Change(change));
        setPatchset(new PatchSet(patch));
    }

    /**
     * The name of the user who "created" the event.
     * @return the userName.
     */
    public String getUserName() {
        return userName;
    }

    /**
     * The name of the user who "created" the event.
     * @param userName the userName.
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("[");
        str.append(getClass().getSimpleName());
        str.append(" Change: ").append(getChange());
        str.append(" PatchSet: ").append(getPatchSet());
        str.append("]");
        return str.toString();
    }

    @Override
    public GerritEvent getEvent() {
        return this;
    }

    @Override
    public synchronized void addListener(GerritEventLifecycleListener listener) {
        if (listeners == null) {
            listeners = new LinkedList<GerritEventLifecycleListener>();
        }
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public synchronized boolean removeListener(GerritEventLifecycleListener listener) {
        if (listeners != null) {
            return listeners.remove(listener);
        } else {
            return false;
        }
    }

    @Override
    public synchronized void fireTriggerScanStarting() {
        fireEvent(new ListenerVisitor() {
            @Override
            public void visit(GerritEventLifecycleListener listener, GerritEvent event) {
                listener.triggerScanStarting(event);
            }
        });
    }

    @Override
    public synchronized void fireTriggerScanDone() {
        fireEvent(new ListenerVisitor() {
            @Override
            public void visit(GerritEventLifecycleListener listener, GerritEvent event) {
                listener.triggerScanDone(event);
            }
        });
    }

    @Override
    public synchronized void fireProjectTriggered(final Job project) {
        fireEvent(new ListenerVisitor() {
            @Override
            public void visit(GerritEventLifecycleListener listener, GerritEvent event) {
                listener.projectTriggered(event, project);
            }
        });
    }

    @Override
    public synchronized void fireBuildStarted(final Run build) {
        fireEvent(new ListenerVisitor() {
            @Override
            public void visit(GerritEventLifecycleListener listener, GerritEvent event) {
                listener.buildStarted(event, build);
            }
        });
    }

    @Override
    public synchronized void fireBuildCompleted(final Run build) {
        fireEvent(new ListenerVisitor() {
            @Override
            public void visit(GerritEventLifecycleListener listener, GerritEvent event) {
                listener.buildCompleted(event, build);
            }
        });
    }

    @Override
    public synchronized void fireAllBuildsCompleted() {
        fireEvent(new ListenerVisitor() {
            @Override
            public void visit(GerritEventLifecycleListener listener, GerritEvent event) {
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
            //Get a cloned list so we don't risk modifying it on the same thread.
            List<GerritEventLifecycleListener> list = getListeners();
            for (GerritEventLifecycleListener listener : list) {
                visitor.visit(listener, this);
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
        void visit(GerritEventLifecycleListener listener, GerritEvent event);
    }
}
