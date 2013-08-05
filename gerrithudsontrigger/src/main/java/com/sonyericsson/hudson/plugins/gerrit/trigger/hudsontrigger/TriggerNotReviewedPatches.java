package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Approval;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritQueryException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritQueryHandler;

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
public class TriggerNotReviewedPatches {

    /**
     * changeSets data structure has Gerrit project's current open change sets as keys
     * and as value reviewers of the latest patch set.
     *
     * changeSet : [list of reviewers]
     */
    private Map<JSONObject, List<String>> changeSets = new HashMap<JSONObject, List<String>>();
    private static final Logger logger = LoggerFactory.getLogger(TriggerNotReviewedPatches.class);
    private List<PatchsetCreated> events = new ArrayList<PatchsetCreated>();

    /**
     * Initializes changeSet.
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
    public List<JSONObject> getCurrentPatchesFromGerrit(String project) throws IOException {
        final String queryString = "project:" + project + " is:open";
        List<JSONObject> changeList = new ArrayList<JSONObject>();
        try {
            IGerritHudsonTriggerConfig config = PluginImpl.getInstance().getConfig();
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
     * Class constructor.
     * @param project the name of the Gerrit project.
     * @throws IOException if the unfortunate happens.
     */
    public TriggerNotReviewedPatches(String project) throws IOException {
        List<JSONObject> changeList = getCurrentPatchesFromGerrit(project);
        createPatchReviwersList(changeList);
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
            logger.warn("Invalid changeSet: " + changeSet);
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
     * Triggers all patches in events-list.
     * @param trigger the GerritTrigger
     */
    public void triggerUnreviewedPatches(GerritTrigger trigger) {
        if (trigger != null && trigger.isAllowTriggeringUnreviewedPatches()) {
            for (PatchsetCreated event : this.events) {
                trigger.gerritEvent(event);
            }
        }
    }

    /**
     * Searches unreviewed patches and adds those to events-list.
     * Function goes through every current open patch sets in gerrit project.
     * Function adds patches to events-list, which aren't reviewed by Gerrit user.
     * @param username the Jenkins plugin's Gerrit username.
     */
    public void searchUnreviewedPatches(String username) {
        for (JSONObject changedPatch : this.changeSets.keySet()) {
            if (!hasUserReviewedChange(changedPatch, username)) {
                Change change = new Change(changedPatch);
                Object patchSetObj = changedPatch.get("currentPatchSet");
                if (patchSetObj instanceof JSONObject) {
                    JSONObject currentPatchSet = (JSONObject)patchSetObj;
                    PatchSet patchSet = new PatchSet(currentPatchSet);
                    PatchsetCreated event = new PatchsetCreated();
                    event.setChange(change);
                    event.setPatchset(patchSet);
                    this.events.add(event);
                } else {
                    logger.error("Parsing JSON object failed: " + changedPatch.toString());
                }
            }
        }
    }
}
