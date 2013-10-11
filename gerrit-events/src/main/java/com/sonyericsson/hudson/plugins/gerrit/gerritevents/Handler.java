/*
 *  The MIT License
 *
 *  Copyright 2013 rinrinne All rights reserved.
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

import net.sf.json.JSONObject;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Provider;

/**
 * A handler to deliver gerrit events and connection ones to listener.
 *
 * @author rinrinne &lt;rinrin.ne@gmail.om&gt;
 */
public interface Handler {
    /**
     * Post string data to working queue.
     * Note that you must not update objects passed to this method.
     *
     * @param data a line of text from the stream-events stream of events.
     */
    void post(String data);
    /**
     * Post string data to working queue.
     * Note that you must not update objects passed to this method.
     *
     * @param data a line of text from the stream-events stream of events.
     * @param provider the Gerrit server info.
     */
    void post(String data, Provider provider);
    /**
     * Post json object to working queue.
     * Note that you must not update objects passed to this method.
     *
     * @param json a json object from the stream-events stream of events.
     */
    void post(JSONObject json);
    /**
     * Post json object to working queue.
     * Note that you must not update objects passed to this method.
     *
     * @param json a json object from the stream-events stream of events.
     * @param provider the Gerrit server info.
     */
    void post(JSONObject json, Provider provider);
    /**
     * Post GerritEvent object to working queue.
     * Note that you must not update objects passed to this method.
     *
     * @param event a GerritEvent object.
     */
    void post(GerritEvent event);
    /**
     * Add listener for GerritEvent.
     *
     * @param listener the listener.
     */
    void addListener(GerritEventListener listener);
    /**
     * Remove listener for GerritEvent.
     *
     * @param listener the listener.
     */
    void removeListener(GerritEventListener listener);
}
