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
package com.sonyericsson.hudson.plugins.gerrit.trigger.extension;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import jenkins.model.Jenkins;

import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;

import net.sf.json.JSONObject;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;

/**
 * An extension to receive gerrit events from other plugins.
 *
 * @author rinrinne &lt;rinrin.ne@gmail.om&gt;
 */
@Extension
public class GerritEventReceiver implements ExtensionPoint {

    private transient Collection<GerritEventReceiverListener> listeners;

    /**
     * Constructor.
     */
    public GerritEventReceiver() {
        listeners = Collections.synchronizedSet(new HashSet<GerritEventReceiverListener>());
    }

    /**
     * Post string data to working queue.
     * @param data a line of text from the stream-events stream of events.
     */
    public void post(String data) {
        PluginImpl.getInstance().getHandler().post(data);
    }

    /**
     * Post json object to working queue.
     * @param json a json object from the stream-events stream of events.
     */
    public void post(JSONObject json) {
        PluginImpl.getInstance().getHandler().post(json);
    }

    /**
     * Adds a listener.
     *
     * @param listener the listener.
     */
    public void addListener(GerritEventReceiverListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener.
     *
     * @param listener the listener.
     */
    public void removeListener(GerritEventReceiverListener listener) {
        listeners.remove(listener);
    }

    /**
     * Remove all listeners.
     */
    public void removeAllListeners() {
        listeners.clear();
    }

    /**
     * Fire OnDisposed event.
     */
    public void fireOnDisposed() {
        for (GerritEventReceiverListener listener : listeners) {
            listener.onDisposed();
        }
    }

    /**
     * Gets instance from extension list.
     *
     * @return the instance.
     */
    public static GerritEventReceiver get() {
        ExtensionList<GerritEventReceiver> extensions =
                Jenkins.getInstance().getExtensionList(GerritEventReceiver.class);
        if (extensions.isEmpty()) {
            return null;
        } else {
            return extensions.get(0);
        }
    }
}
