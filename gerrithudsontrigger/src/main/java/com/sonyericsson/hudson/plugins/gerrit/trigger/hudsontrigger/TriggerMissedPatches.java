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
 * Runs Jenkins jobs to missed Gerrit patches.
 *
 * TODO: Support to all types of events.
 */
public class TriggerMissedPatches {

    private List<JSONObject> changeSets;
    private Map<String, Integer> patchIndexes;  // patch id : index
    private Map<String, List<String>> patchReviewers;  // patch id : [reviewers]
    private static final Logger logger = LoggerFactory.getLogger(TriggerMissedPatches.class);

    /**
     * Initializes patchIndexes and patchReviewers data structures.
     */
    private void createPatchReviwersList() {
        this.patchIndexes = new HashMap<String, Integer>();
        this.patchReviewers = new HashMap<String, List<String>>();
        for (int i = 0; i < this.changeSets.size(); i++) {
            JSONObject changeObj = this.changeSets.get(i);
            Change change = new Change(changeObj);
            String changeId = change.getId();
            Object patchSetObj = changeObj.get("currentPatchSet");
            if (changeId == null || changeId.isEmpty() || (!(patchSetObj instanceof JSONObject))) {
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
            this.patchReviewers.put(changeId, usernames);
            this.patchIndexes.put(changeId, i);
        }
    }

    /**
     * Gets current open patches from gerrit.
     * @param project the name of the project.
     * @throws IOException if the unfortunate happens.
     */
    public void getCurrentPatchesFromGerrit(String project) throws IOException {
        final String queryString = "project:" + project + " is:open";
        try {
            IGerritHudsonTriggerConfig config = PluginImpl.getInstance().getConfig();
            GerritQueryHandler handler = new GerritQueryHandler(config);
            this.changeSets = handler.queryJava(queryString, false, true, false);
        } catch (GerritQueryException gqe) {
            logger.debug("Bad query. ", gqe);
        } catch (Exception ex) {
            logger.warn("Could not query Gerrit for [" + queryString + "]", ex);
        }
    }

    /**
     * Class constructor.
     * @param project the name of the Gerrit project.
     * @throws IOException if the unfortunate happens.
     */
    public TriggerMissedPatches(String project) throws IOException {
        logger.info("Fetching data from Gerrit server's current open patches.");
        getCurrentPatchesFromGerrit(project);
        createPatchReviwersList();
    }

    /**
     * Searches userName from patch change and returns true if username was found.
     *
     * @param changeId the patch set's id.
     * @param userName the Jenkins plugin's Gerrit username.
     * @return true if userName was found or given input was invalid - else false
     */
    private boolean hasUserReviewedChange(String changeId, String userName) {
        if (changeId == null || changeId.isEmpty()) {
            logger.warn("Invalid changeID: " + changeId);
            return true;
        }
        List<String> names = this.patchReviewers.get(changeId);
        if (names == null) {
            logger.error("Failed to read names from a patch set. ChangeId: " + changeId);
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
     * Trigger missed patches.
     * Function goes through every current open patch in gerrit project.
     * and triggers patches which aren't reviewed by Gerrit user.
     * @param username the Jenkins plugin's Gerrit username.
     */
    public void triggerMissedPatches(String username) {
        logger.info("Starting checking missed patches from project!");
        for (String changeId : this.patchReviewers.keySet()) {
            if (hasUserReviewedChange(changeId, username)) {
                logger.debug(username + ", Change up to date! ChangeId: " + changeId);
            } else {
                JSONObject changedPatch = this.changeSets.get(this.patchIndexes.get(changeId));
                Change change = new Change(changedPatch);
                logger.info(change.getChangeInfo("Located a new patchset in Gerrit:"));
                Object patchSetObj = changedPatch.get("currentPatchSet");
                if (patchSetObj instanceof JSONObject) {
                    JSONObject currentPatchSet = (JSONObject)patchSetObj;
                    PatchSet patchSet = new PatchSet(currentPatchSet);
                    PatchsetCreated event = new PatchsetCreated();
                    event.setChange(change);
                    event.setPatchset(patchSet);
                    PluginImpl.getInstance().triggerEvent(event);
                } else {
                    logger.error("Parsing JSON object failed: " + changedPatch.toString());
                }
            }
        }
    }
}
