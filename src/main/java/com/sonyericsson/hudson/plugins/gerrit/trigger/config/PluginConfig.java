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
package com.sonyericsson.hudson.plugins.gerrit.trigger.config;

import net.sf.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventType;
import com.sonymobile.tools.gerrit.gerritevents.workers.GerritWorkersConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.replication.ReplicationCache;

/**
 * Configuration bean for the global plugin configuration.
 *
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public class PluginConfig implements GerritWorkersConfig {

    /**
     * Default number of receiving worker threads.
     */
    public static final int DEFAULT_NR_OF_RECEIVING_WORKER_THREADS = 3;
    /**
     * Default number of sending worker threads.
     */
    public static final int DEFAULT_NR_OF_SENDING_WORKER_THREADS = 1;
    /**
     * Default event filter.
     */
    public static final List<String> DEFAULT_EVENT_FILTER = getDefaultEventFilter();
    /**
     * Full event list.
     */
    public static final List<String> ALL_EVENTS = getAllEvents();

    private static final Logger logger = LoggerFactory.getLogger(PluginImpl.class);
    private int numberOfReceivingWorkerThreads;
    private int numberOfSendingWorkerThreads;
    private int replicationCacheExpirationInMinutes;
    private List<String> filterIn;

    /**
     * Constructs a config with default data.
     */
    public PluginConfig() {
        this(new JSONObject(false));
    }

    /**
     * Constructor.
     *
     * @param formData the data.
     */
    public PluginConfig(JSONObject formData) {
        setValues(formData);
    }

    /**
     * Copy constructor.
     *
     * @param pluginConfig the PluginConfig object to be copied.
     */
    public PluginConfig(PluginConfig pluginConfig) {
        numberOfReceivingWorkerThreads = pluginConfig.getNumberOfReceivingWorkerThreads();
        numberOfSendingWorkerThreads = pluginConfig.getNumberOfSendingWorkerThreads();
        replicationCacheExpirationInMinutes = pluginConfig.getReplicationCacheExpirationInMinutes();
        filterIn = pluginConfig.getFilterIn();
    }

    /**
     * Unused Constructor?
     *
     * @param formData the data
     * @param req      a path.
     */
    public PluginConfig(JSONObject formData, StaplerRequest req) {
        this(formData);
    }

    /**
     * Sets all config values from the provided JSONObject.
     *
     * @param formData the JSON object with form data.
     */
    public void setValues(JSONObject formData) {
        numberOfReceivingWorkerThreads = formData.optInt(
                "numberOfReceivingWorkerThreads",
                DEFAULT_NR_OF_RECEIVING_WORKER_THREADS);
        if (numberOfReceivingWorkerThreads <= 0) {
            numberOfReceivingWorkerThreads = DEFAULT_NR_OF_RECEIVING_WORKER_THREADS;
        }

        numberOfSendingWorkerThreads = formData.optInt(
                "numberOfSendingWorkerThreads",
                DEFAULT_NR_OF_SENDING_WORKER_THREADS);
        if (numberOfSendingWorkerThreads <= 0) {
            numberOfSendingWorkerThreads = DEFAULT_NR_OF_SENDING_WORKER_THREADS;
        }

        replicationCacheExpirationInMinutes = formData.optInt("replicationCacheExpirationInMinutes",
            ReplicationCache.DEFAULT_EXPIRATION_IN_MINUTES);
        if (replicationCacheExpirationInMinutes <= 0) {
            replicationCacheExpirationInMinutes = ReplicationCache.DEFAULT_EXPIRATION_IN_MINUTES;
        }

        filterIn = getFilterInFromFormData(formData);
        updateEventFilter();
    }

    /**
     * The number of threads to handle incoming events with.
     *
     * @return the number of worker threads.
     */
    @Override
    public int getNumberOfReceivingWorkerThreads() {
        if (numberOfReceivingWorkerThreads <= 0) {
            numberOfReceivingWorkerThreads = DEFAULT_NR_OF_RECEIVING_WORKER_THREADS;
        }
        return numberOfReceivingWorkerThreads;
    }

    /**
     * NumberOfReceivingWorkerThreads.
     *
     * @param numberOfReceivingWorkerThreads nr of threads.
     * @see #getNumberOfReceivingWorkerThreads()
     */
    public void setNumberOfReceivingWorkerThreads(int numberOfReceivingWorkerThreads) {
        this.numberOfReceivingWorkerThreads = numberOfReceivingWorkerThreads;
    }

    /**
     * The number of worker threads that sends approvals/review commands.
     *
     * @return the number of worker threads.
     */
    @Override
    public int getNumberOfSendingWorkerThreads() {
        if (numberOfSendingWorkerThreads <= 0) {
            numberOfSendingWorkerThreads = DEFAULT_NR_OF_SENDING_WORKER_THREADS;
        }
        return numberOfSendingWorkerThreads;
    }

    /**
     * NumberOfSendingWorkerThreads.
     *
     * @param numberOfSendingWorkerThreads nr of threads.
     * @see #getNumberOfSendingWorkerThreads()
     */
    public void setNumberOfSendingWorkerThreads(int numberOfSendingWorkerThreads) {
        this.numberOfSendingWorkerThreads = numberOfSendingWorkerThreads;
    }

    /**
     * Replication cache expiration in minutes.
     * @return the replicationCacheExpirationInMinutes
     */
    public int getReplicationCacheExpirationInMinutes() {
        if (replicationCacheExpirationInMinutes <= 0) {
            replicationCacheExpirationInMinutes = ReplicationCache.DEFAULT_EXPIRATION_IN_MINUTES;
        }
        return replicationCacheExpirationInMinutes;
    }

    /**
     * Replication cache expiration in minutes.
     * @param replicationCacheExpirationInMinutes expiration time to set
     */
    public void setReplicationCacheExpirationInMinutes(int replicationCacheExpirationInMinutes) {
        this.replicationCacheExpirationInMinutes = replicationCacheExpirationInMinutes;
    }

    /**
     * Get the number of events that are supported.
     *
     * @return the size of gerrit event type enum.
     */
    public int getEventTypesSize() {
        return GerritEventType.values().length;
    }

    /**
     * Get the list of events that are filtered in.
     *
     * @return the list of events that are filtered in, if all events are included then it will return null.
     */
    public List<String> getFilterIn() {
        if (filterIn == null) {
            GerritEventType[] types = GerritEventType.values();
            List<String> typeList = new ArrayList<>();
            for (GerritEventType type : types) {
                if (type.isInteresting()) {
                    typeList.add(type.getTypeValue());
                }
            }
            Collections.sort(typeList);
            return typeList;
        }
        return filterIn;
    }

    /**
     * Get the filter event list from formdata.
     * @param formData the JSON object with form data.
     * @return value.
     */
    private List<String> getFilterInFromFormData(JSONObject formData) {
        // Getting the list as a JSONArray is not utilized since if the string is empty
        // the get method will throw an exception. The logic is such that if the filter
        // is null then the default of every event being interesting is assumed.
        String stringIn = formData.optString("filterIn", null);
        List<String> filter = null;
        String[] arrayIn = null;
        if (stringIn != null && !stringIn.equals("null")) {
            filter = new ArrayList<>();
            arrayIn = stringIn.split("\\s+");
            filter = Arrays.asList(arrayIn);
        }
        return filter;
    }

     /**
     * Update the server event filter.
     */
    public void updateEventFilter() {
        List<String> filter;
        if (filterIn != null) {
            filter = filterIn;
        } else {
            filter = getDefaultEventFilter();
        }
        logger.info("Listening to event types: {}", filter);
        for (GerritEventType type : GerritEventType.values()) {
            type.setInteresting(filter.contains(type.getTypeValue()));
        }
    }

    /**
     * Get the default event filter.
     * @return the event list
     */
    public static List<String> getDefaultEventFilter() {
        GerritEventType[] types = GerritEventType.values();
        List<String> typeList = new ArrayList<>();
        for (GerritEventType type : types) {
            // TODO if an event type is added with a default other than true
            // in the future then this needs to be updated to check each
            // events default value.
            // At the moment it gives the same result as getAllEvents()
            if (true) {
                typeList.add(type.getTypeValue());
            }
        }
        Collections.sort(typeList);
        return typeList;
    }

     /**
     * Get the full list of supported events.
     * @return the event list
     */
    public static List<String> getAllEvents() {
        GerritEventType[] types = GerritEventType.values();
        List<String> typeList = new ArrayList<>();
        for (GerritEventType type : types) {
            typeList.add(type.getTypeValue());
        }
        Collections.sort(typeList);
        return typeList;
    }
}
