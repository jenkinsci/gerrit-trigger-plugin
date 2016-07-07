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
package com.sonyericsson.hudson.plugins.gerrit.trigger.config;

import hudson.model.Failure;

import java.util.LinkedList;
import java.util.List;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritSlave;

/**
 * Each instance of this class holds data needed to trigger builds on replication events,
 * and is associated to one specific GerritServer.
 * A GerritServer can have at most one ReplicationConfig in its Config object.
 *
 * @author Mathieu Wang &lt;mathieu.wang@ericsson.com&gt;
 *
 */
public class ReplicationConfig {

    private static final String DEFAULT_SLAVE_ID_JSON_KEY = "defaultSlaveId";
    private static final String ENABLE_REPLICATION_JSON_KEY = "enableReplication";
    private static final String ENABLE_SLAVE_SELECTION_IN_JOBS_JSON_KEY = "enableSlaveSelectionInJobs";
    private static final String SLAVES_JSON_KEY = "slaves";

    private boolean enableReplication;
    private List<GerritSlave> slaves;
    private boolean enableSlaveSelectionInJobs;
    private String defaultSlaveId;

    /**
     * Standard constructor.
     * @param enableReplication whether to allow waiting on replication events.
     * @param slaves the GerritSlave objects.
     * @param enableSlaveSelectionInJobs whether to allow slave selection in jobs.
     * @param defaultSlaveId The default slave that will be selected in jobs
     */
    private ReplicationConfig(boolean enableReplication,
                                List<GerritSlave> slaves,
                                boolean enableSlaveSelectionInJobs,
                                String defaultSlaveId) {
        this.enableReplication = enableReplication;
        this.slaves = slaves;
        this.enableSlaveSelectionInJobs = enableSlaveSelectionInJobs;
        this.defaultSlaveId = defaultSlaveId;
    }

    /**
     * Copy constructor.
     * @param config the ReplicationConfig object to be copied, never null.
     */
    public ReplicationConfig(ReplicationConfig config) {
        enableReplication = config.isEnableReplication();
        if (config.getGerritSlaves() != null) {
            slaves = new LinkedList<GerritSlave>();
            for (GerritSlave slave : config.getGerritSlaves()) {
                GerritSlave slaveCopy = new GerritSlave(slave.getName(), slave.getHost(), slave.getTimeoutInSeconds());
                slaves.add(slaveCopy);
                if (slave.getId().equals(config.getDefaultSlaveId())) {
                    defaultSlaveId = slaveCopy.getId();
                }
            }
        }
        enableSlaveSelectionInJobs = config.isEnableSlaveSelectionInJobs();
    }

    /**
     * Default constructor.
     */
    public ReplicationConfig() {
        this(false, new LinkedList<GerritSlave>(), false, null);
    }

    /**
     * If we enable waiting on replication events.
     *
     * @return true if so.
     */
    public boolean isEnableReplication() {
        return enableReplication;
    }

    /**
     * Get the list of GerritSlave objects.
     * @return the list.
     */
    public List<GerritSlave> getGerritSlaves() {
        return slaves;
    }

    /**
     * Whether slave selection in enabled in job config.
     * @return true if so.
     */
    public boolean isEnableSlaveSelectionInJobs() {
        return enableSlaveSelectionInJobs;
    }

    /**
     * The id of the default slave to be selected in job config.
     * @return the id of default slave.
     */
    public String getDefaultSlaveId() {
        return defaultSlaveId;
    }

    /**
     * Get a specific Gerrit slave using its id.
     * @param id the id of the slave.
     * @param defaultSlave if true, will return the default slave when specified id do not exists.
     * @return the Gerrit slave that has the given id, otherwise return null.
     */
    public GerritSlave getGerritSlave(String id, boolean defaultSlave) {
        for (GerritSlave slave : slaves) {
            if (slave.getId().equals(id)) {
                return slave;
            }
        }
        if (defaultSlave) {
            return getGerritSlave(defaultSlaveId, false);
        }
        return null;
    }

    /**
     * Create a ReplicationConfig object from JSON.
     * @param formData the JSON data.
     * @return the ReplicationConfig object.
     */
    public static ReplicationConfig createReplicationConfigFromJSON(JSONObject formData) {
        ReplicationConfig replicationConfig;
        List<GerritSlave> slaves = new LinkedList<GerritSlave>();

        boolean enableReplication = formData.has(ENABLE_REPLICATION_JSON_KEY);
        if (enableReplication) {
            JSONObject replicationConfigAsJSON = formData.getJSONObject(ENABLE_REPLICATION_JSON_KEY);
            Object slavesAsJSON = replicationConfigAsJSON.get(SLAVES_JSON_KEY);
            if (slavesAsJSON instanceof JSONArray) {
                for (Object jsonObject : (JSONArray)slavesAsJSON) {
                    slaves.add(GerritSlave.createGerritSlaveFromJSON((JSONObject)jsonObject));
                }
            } else if (slavesAsJSON instanceof JSONObject) {
                slaves.add(GerritSlave.createGerritSlaveFromJSON((JSONObject)slavesAsJSON));
            }
            if (slaves.size() == 0) {
                throw new Failure(Messages.OneSlaveMustBeDefined());
            }

            boolean enableSlaveSelectionInJobs = replicationConfigAsJSON
                .has(ENABLE_SLAVE_SELECTION_IN_JOBS_JSON_KEY);
            String defaultSlaveId = "";
            if (enableSlaveSelectionInJobs) {
                JSONObject defaultSlaveAsJSON = replicationConfigAsJSON
                    .getJSONObject(ENABLE_SLAVE_SELECTION_IN_JOBS_JSON_KEY);
                defaultSlaveId = defaultSlaveAsJSON.getString(DEFAULT_SLAVE_ID_JSON_KEY);
                if (defaultSlaveId == null || defaultSlaveId.trim().isEmpty()) {
                    enableSlaveSelectionInJobs = false;
                } else {
                    boolean defaultSlaveExist = false;
                    for (GerritSlave gerritSlave : slaves) {
                        if (defaultSlaveId.equals(gerritSlave.getId())) {
                            defaultSlaveExist = true;
                        }
                    }
                    if (!defaultSlaveExist) {
                        throw new Failure(Messages.CannotDeleteDefaultSlave());
                    }
                }
            }
            replicationConfig = new ReplicationConfig(true, slaves, enableSlaveSelectionInJobs, defaultSlaveId);
        } else {
            replicationConfig = new ReplicationConfig();
        }
        return replicationConfig;
    }
}
