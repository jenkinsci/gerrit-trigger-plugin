/*
 *  The MIT License
 *
 *  Copyright 2013 Joel Huttunen. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Approval;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Provider;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonymobile.tools.gerrit.gerritevents.ConnectionListener;
import com.sonymobile.tools.gerrit.gerritevents.GerritQueryException;
import com.sonymobile.tools.gerrit.gerritevents.GerritQueryHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;


/**
 * Checks changes from Gerrit server, when ssh connection is created.
 * Runs Jenkins jobs to unreviewed Gerrit patches.
 *
 * TODO: Support to all types of events.
 */
public class UnreviewedPatchesListener implements ConnectionListener {

    private static final Logger logger = LoggerFactory.getLogger(UnreviewedPatchesListener.class);

    /**
     * changeSets data structure has Gerrit project's current open change sets as keys
     * and as value reviewers of the latest patch set.
     *
     * changeSet : [list of reviewers]
     */
    private Map<JSONObject, List<String>> changeSets = new HashMap<JSONObject, List<String>>();
    private List<PatchsetCreated> events = new ArrayList<PatchsetCreated>();
    private String serverName;

    /**
     * Class constructor.
     *
     * @param name the name of the GerritServer
     */
    public UnreviewedPatchesListener(String name) {
        this.serverName = name;
        addThisAsListener();
    }

    /**
     * Add this patches listener to the GerritServer object to which it is associated.
     */
    private void addThisAsListener() {
        GerritServer server = PluginImpl.getServer_(serverName);
        if (server != null) {
            server.addListener(this);
        } else {
            logger.error("Could not find server {}", serverName);
        }
    }

    @Override
    public void connectionEstablished() {
        if (getConfig() != null) {
            runUnreviewedPatchSets();
        }
    }

    @Override
    public void connectionDown() {
    }

    /**
     * Shutdown the listener.
     */
    public void shutdown() {
        GerritServer server = PluginImpl.getServer_(serverName);
        if (server != null) {
            server.removeListener(this);
        } else {
            logger.error("Could not find server {}", serverName);
        }
    }

    /**
     * Initializes changeSets.
     * @param changeList the list of Gerrit project's current open change sets
     */
    private void createPatchReviwersList(List<JSONObject> changeList) {
        for (JSONObject changeObj : changeList) {
            Change change = new Change(changeObj);
            Object patchSetObj = changeObj.get("currentPatchSet");
            if (!(patchSetObj instanceof JSONObject)) {
                continue;
            }

            JSONObject currentPatchSet = (JSONObject)patchSetObj;
            List<String> usernames = new ArrayList<String>();
            if (currentPatchSet.has("approvals")) {
                JSONArray approvals = currentPatchSet.getJSONArray("approvals");
                if (approvals == null) {
                    logger.warn(change.getChangeInfo("Parsing approvals from a patch set FAILED! Patch set data:"));
                    continue;
                }
                for (Object obj : approvals) {
                    if (obj instanceof JSONObject) {
                        Approval approval = new Approval((JSONObject)obj);
                        String username = approval.getUsername();
                        usernames.add(username);
                    }
                }
            }
            this.changeSets.put(changeObj, usernames);
        }
    }

    /**
     * Gets current open patches from gerrit.
     * @param project the name of the project.
     * @return list of Gerrit patch sets.
     * @throws IOException if the unfortunate happens.
     */
    private List<JSONObject> getCurrentPatchesFromGerrit(String project) throws IOException {
        final String queryString = "project:" + project + " is:open";
        List<JSONObject> changeList = new ArrayList<JSONObject>();
        try {
            IGerritHudsonTriggerConfig config = getConfig();
            GerritQueryHandler handler = new GerritQueryHandler(config);
            changeList = handler.queryJava(queryString, false, true, false);
        } catch (GerritQueryException gqe) {
            logger.debug("Bad query. ", gqe);
        } catch (Exception ex) {
            logger.warn("Could not query Gerrit for [" + queryString + "]", ex);
        }
        return changeList;
    }

    /**
     * Get the server config.
     * @return the server config or null if not found.
     */
    private IGerritHudsonTriggerConfig getConfig() {
        GerritServer server = PluginImpl.getServer_(serverName);
        if (server != null) {
            IGerritHudsonTriggerConfig config = server.getConfig();
            if (config != null) {
                return config;
            } else {
                logger.error("Could not find the config for server {}", serverName);
            }
        } else {
            logger.error("Could not find server {}", serverName);
        }
        return null;
    }

    /**
     * Triggers all patches in events-list.
     * @param trigger the GerritTrigger
     */
    public void triggerUnreviewedPatches(GerritTrigger trigger) {
        if (trigger != null && trigger.isAllowTriggeringUnreviewedPatches()) {
            EventListener listener = trigger.createListener();
            for (PatchsetCreated event : this.events) {
                listener.gerritEvent(event);
            }
        }
    }

    /**
     * Checks changes in Gerrit.
     * Triggers Jenkins jobs which are related to unreviewed Gerrit patch sets.
     */
    private void runUnreviewedPatchSets() {
        logger.info("Checking non-reviewed patch sets from allowed jobs for: {}.", serverName);
        Map<String, ArrayList<GerritTrigger>> gerritProjectContainer = GerritProjectList.getGerritProjects();
        for (Map.Entry<String, ArrayList<GerritTrigger>> entry : gerritProjectContainer.entrySet()) {
            IGerritHudsonTriggerConfig config = getConfig();
            String projectName = entry.getKey();
            ArrayList<GerritTrigger> triggers = entry.getValue();
            if (triggers == null || triggers.isEmpty()) {
                continue;
            }

            try {
                List<JSONObject> changeList = getCurrentPatchesFromGerrit(projectName);
                createPatchReviwersList(changeList);
                searchUnreviewedPatches(config.getGerritUserName());
                for (GerritTrigger trigger : triggers) {
                    triggerUnreviewedPatches(trigger);
                }
            } catch (Exception ex) {
                logger.error("Unable to identify unreviewed patch sets!\nProject name: " + projectName, ex);
            }
        }
    }

    /**
     * Searches userName from patch change and returns true if username was found.
     *
     * @param changeSet the change set.
     * @param userName the Jenkins plugin's Gerrit username.
     * @return true if userName was found or given input was invalid - else false
     */
    private boolean hasUserReviewedChange(JSONObject changeSet, String userName) {
        if (changeSet == null) {
            logger.warn("No provided changeSet!");
            return true;
        }
        List<String> names = this.changeSets.get(changeSet);
        if (names == null) {
            logger.error("Failed to read names from a patch set.");
            return true;
        }
        for (String name : names) {
            if (name.equals(userName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Searches unreviewed patches and adds those to events-list.
     * Function goes through every current open patch sets in gerrit project.
     * Function adds patches to events-list, which aren't reviewed by Gerrit user.
     * @param username the Jenkins plugin's Gerrit username.
     */
    private void searchUnreviewedPatches(String username) {
        for (JSONObject changedPatch : this.changeSets.keySet()) {
            if (!hasUserReviewedChange(changedPatch, username)) {
                Change change = new Change(changedPatch);
                Object patchSetObj = changedPatch.get("currentPatchSet");
                if (patchSetObj instanceof JSONObject) {
                    JSONObject currentPatchSet = (JSONObject)patchSetObj;
                    PatchSet patchSet = new PatchSet(currentPatchSet);
                    PatchsetCreated event = new PatchsetCreated();
                    Provider provider = new Provider();
                    provider.setName(serverName);
                    event.setChange(change);
                    event.setPatchset(patchSet);
                    event.setProvider(provider);
                    this.events.add(event);
                } else {
                    logger.error("Parsing JSON object failed: " + changedPatch.toString());
                }
            }
        }
    }
}
