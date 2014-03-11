/*
 *  The MIT License
 *
 *  Copyright 2014 Ericsson.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

import java.util.UUID;

import net.sf.json.JSONObject;

/**
 * Represents a Gerrit slave after which we wait for replication events.
 * @author Mathieu Wang &lt;mathieu.wang@ericsson.com&gt;
 *
 */
public class GerritSlave extends AbstractDescribableImpl<GerritSlave> {
    /**
     * Disabled timeout value.
     */
    public static final int DISABLED_TIMEOUT_VALUE = 0;
    private static final String ID_JSON_KEY = "id";
    private static final String NAME_JSON_KEY = "name";
    private static final String HOST_JSON_KEY = "host";
    private static final String TIMEOUT_JSON_KEY = "timeout";

    private String id;
    private String name;
    private String host;
    private int timeoutInSeconds;

    /**
     * Standard Constructor.
     * @param id the ID or {@code null} to generate a new one
     * @param name the name to represent/identify this gerritSlave
     * @param host the host for the gerritSlave, can include port(e.g. someHost:1234).
     * @param timeoutInSeconds maximum time we wait for a replication event.
     */
    public GerritSlave(String id, String name, String host, int timeoutInSeconds) {
        if (id == null || id.isEmpty()) {
            this.id = UUID.randomUUID().toString();
        } else {
            this.id = id;
        }
        this.name = name;
        this.host = host;
        this.timeoutInSeconds = timeoutInSeconds;
    }

    /**
     * Create a new GerritSlave, a new id will be generated.
     * @param name the name to represent/identify this gerritSlave
     * @param host the host for the gerritSlave, can include port(e.g. someHost:1234).
     * @param timeout maximum time we wait for a replication event.
     */
    public GerritSlave(String name, String host, int timeout) {
        this(null, name, host, timeout);
    }

    /**
     * Getter for id.
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * Getter for name.
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Getter for host.
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * Getter for time-out.
     * @return the time-out
     */
    public int getTimeoutInSeconds() {
        return timeoutInSeconds;
    }

    /**
     * Creates a GerritSlave from a JSONObject.
     * @param obj the JSONObject.
     * @return a GerritSlave.
     */
    public static GerritSlave createGerritSlaveFromJSON(JSONObject obj) {
        String id = obj.getString(ID_JSON_KEY);
        String name = obj.getString(NAME_JSON_KEY);
        String host = obj.getString(HOST_JSON_KEY);
        int timeout = obj.getInt(TIMEOUT_JSON_KEY);
        return new GerritSlave(id, name, host, timeout);
    }

    /**
     * The Descriptor for a GerritSlave.
     */
    @Extension
    public static class GerritSlaveDescriptor extends Descriptor<GerritSlave> {
        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
