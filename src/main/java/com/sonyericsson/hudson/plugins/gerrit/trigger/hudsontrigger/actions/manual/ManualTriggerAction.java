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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.manual;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerParameters;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil;
import com.sonymobile.tools.gerrit.gerritevents.GerritDefaultValues;
import com.sonymobile.tools.gerrit.gerritevents.GerritQueryException;
import com.sonymobile.tools.gerrit.gerritevents.GerritQueryHandler;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Provider;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.Extension;
import hudson.model.Hudson;
import hudson.model.ParameterValue;
import hudson.model.RootAction;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil.getPluginImageUrl;

/**
 * RootAction for manually triggering a "Gerrit-build".
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@Extension
public class ManualTriggerAction implements RootAction {

    /**
     * The expected number of parts separated by _ in a generated id.
     * Each part is actually another id.
     *
     * @see #generateTheId(net.sf.json.JSONObject, net.sf.json.JSONObject)
     */
    public static final int EXPECTED_NR_OF_PARTS_IN_A_GENERATED_ID = 3;

    private static final String SESSION_RESULT = "result";
    private static final String SESSION_SEARCH_ERROR = "error_search";
    private static final String SESSION_BUILD_ERROR = "error_build";
    private static final String SESSION_TRIGGER_MONITOR = "trigger_monitor";
    private static final Logger logger = LoggerFactory.getLogger(ManualTriggerAction.class);
    /**
     * The char that separates the different id components in a search-result-row.
     */
    public static final String ID_SEPARATOR = ":";
    /**
     * The maximum length of a change subject to display.
     */
    private static final int MAX_SUBJECT_STR_LENGTH = 65;

    @Override
    public String getIconFileName() {
        if (hasEnabledServers() && Hudson.getInstance().hasPermission(PluginImpl.MANUAL_TRIGGER)) {
            return getPluginImageUrl("icon_retrigger24.png");
        } else {
            return null;
        }
    }

    @Override
    public String getDisplayName() {
        if (hasEnabledServers() && Hudson.getInstance().hasPermission(PluginImpl.MANUAL_TRIGGER)) {
            return Messages.ManualGerritTrigger();
        } else {
        return null;
        }
    }

    @Override
    public String getUrlName() {
        return "/gerrit_manual_trigger";
    }

    /**
     * If this page/link is enabled or not, depending on whether at least one server is enabled.
     *
     * @return true if at least one server is enabled, false otherwise.
     * @see com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig#isEnableManualTrigger()
     */
    public boolean hasEnabledServers() {
        for (GerritServer s : PluginImpl.getServers_()) {
            if (s.getConfig().isEnableManualTrigger()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether a server is allowed to be queried and manually triggered.
     *
     * @param serverName the name of the server selected in the dropdown.
     * @return true if server exists and manual trigger is enabled.
     * @see com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig#isEnableManualTrigger()
     */
    private boolean isServerEnabled(String serverName) {
        if (getServerConfig(serverName) != null) {
            return getServerConfig(serverName).isEnableManualTrigger();
        } else {
            return false;
        }
    }

    /**
     * Get the server config.
     *
     * @param serverName the name of the server.
     * @return the config of the server or null if config not found.
     */
    private IGerritHudsonTriggerConfig getServerConfig(String serverName) {
        GerritServer server = PluginImpl.getServer_(serverName);
        if (server != null) {
            IGerritHudsonTriggerConfig config = server.getConfig();
            if (config != null) {
                return config;
            } else {
                logger.error("Could not find the config of server: {}", serverName);
            }
        } else {
            logger.error("Could not find server {}", serverName);
        }
        return null;
    }

    /**
     * Return the front end url of selected server, or first enabled server if
     * selected doesn't exists (happens when serverName is null also). First
     *
     * @param serverName the name of the GerritServer or null.
     * @return url to the frontend
     */
    @SuppressWarnings("unused")
    //called from jelly
    public String getFrontEndUrl(String serverName) {
        IGerritHudsonTriggerConfig serverConfig = getServerConfig(serverName);
        if (serverConfig != null) {
            return serverConfig.getGerritFrontEndUrl();
        } else {
            ArrayList<String> enabledServers = getEnabledServers();
            if (!enabledServers.isEmpty()) {
                return getServerConfig(enabledServers.get(0)).getGerritFrontEndUrl();
            }
        }
        return null;
    }


    /**
     * Returns the list of servers allowed to be queried and manually triggered.
     *
     * @return the enabled server names
     *
     */
    public ArrayList<String> getEnabledServers() {
        ArrayList<String> enabledServers = new ArrayList<String>();
        for (GerritServer s : PluginImpl.getServers_()) {
            if (s.getConfig().isEnableManualTrigger()) {
                enabledServers.add(s.getName());
            }
        }
        if (enabledServers.isEmpty()) {
            logger.trace("No enabled server for manual triggering found.");
        }
        return enabledServers;
    }

    /**
     * Serves the permission required to perform this action.
     * Used by index.jelly
     *
     * @return the permission.
     */
    @SuppressWarnings("unused")
    public Permission getRequiredPermission() {
        return PluginImpl.MANUAL_TRIGGER;
    }

    /**
     * Gets the full path to the provided javascript file.
     * For use by jelly files to give to the client browser.
     *
     * @param jsName the javascript filename.
     * @return the full path from the web-context root.
     */
    @SuppressWarnings("unused")
    //called from jelly
    public String getJsUrl(String jsName) {
        return StringUtil.getPluginJsUrl(jsName);
    }

    /**
     * Finds the highest and lowest code review vote for the provided patch set.
     *
     * @param res the patch set.
     * @return the highest and lowest code review vote for the patch set.
     */
    @Deprecated
    public HighLow getCodeReview(JSONObject res) {
        return getCodeReview(res, 0);
    }

    /**
     * Finds the highest and lowest code review vote for the provided patch set.
     *
     * @param res the patch set.
     * @param patchSetNumber the patch set number.
     * @return the highest and lowest code review vote for the patch set.
     */
    public HighLow getCodeReview(JSONObject res, int patchSetNumber) {
        return Approval.CODE_REVIEW.getApprovals(res, patchSetNumber);
    }

    /**
     * Finds the lowest and highest verified vote for the provided patch set.
     *
     * @param res the patch-set.
     * @return the highest and lowest verified vote.
     */
    @Deprecated
    public HighLow getVerified(JSONObject res) {
        return getVerified(res, 0);
    }

    /**
     * Finds the lowest and highest verified vote for the provided patch set.
     *
     * @param res the patch-set.
     * @param patchSetNumber the patch set number.
     * @return the highest and lowest verified vote.
     */
    public HighLow getVerified(JSONObject res, int patchSetNumber) {
        return Approval.VERIFIED.getApprovals(res, patchSetNumber);
    }

    /**
     * Cuts the string to a max length of {@link #MAX_SUBJECT_STR_LENGTH}.
     *
     * @param subject the string to fix if needed.
     * @return the fixed string.
     */
    @SuppressWarnings("unused")
    //Called from jelly
    public String toReadableHtml(String subject) {
        if (subject != null && subject.length() > MAX_SUBJECT_STR_LENGTH) {
            subject = subject.substring(0, MAX_SUBJECT_STR_LENGTH);
        }
        if (subject != null) {
            return subject;
        } else {
            return "";
        }
    }

    /**
     * Does a search.
     *
     * @param queryString the query to send to Gerrit.
     * @param request     the request.
     * @param selectedServer the selected Gerrit server.
     * @param allPatchSets if the result includes all patchsets in a change.
     * @param response    the response.
     * @throws IOException if the query fails.
     */
    @SuppressWarnings("unused")
    //Called from jelly
    public void doGerritSearch(@QueryParameter("queryString") final String queryString,
        @QueryParameter("selectedServer") final String selectedServer,
        @QueryParameter("allPatchSets") final boolean allPatchSets, StaplerRequest request,
                               StaplerResponse response) throws IOException {

        HttpSession session = request.getSession();
        // Create session if nothing.
        if (session == null) {
            session = request.getSession(true);
        }
        session.setAttribute("allPatchSets", allPatchSets);
        session.setAttribute("selectedServer", selectedServer);
        if (!isServerEnabled(selectedServer)) {
            response.sendRedirect2(".");
            return;
        }
        Jenkins jenkins = Jenkins.getInstance();
        assert jenkins != null;
        jenkins.checkPermission(PluginImpl.MANUAL_TRIGGER);
        IGerritHudsonTriggerConfig config = getServerConfig(selectedServer);

        if (config != null) {
            GerritQueryHandler handler = new GerritQueryHandler(config);
            clearSessionData(session);
            session.setAttribute("queryString", queryString);

            try {
                List<JSONObject> json = handler.queryJava(queryString, allPatchSets, true, false);
                if (!allPatchSets) {
                    for (JSONObject j : json) {
                        if (j.containsKey("id")) {
                            JSONArray jsonArray = new JSONArray();
                            jsonArray.add(j.getJSONObject("currentPatchSet"));
                            j.put("patchSets", jsonArray);
                        }
                    }
                }
                session.setAttribute(SESSION_RESULT, json);
                //TODO Implement some smart default selection.
                //That can notice that a specific revision is searched or that there is only one result etc.
            } catch (GerritQueryException gqe) {
                logger.debug("Bad query {}", gqe);
                session.setAttribute(SESSION_SEARCH_ERROR, gqe);
            } catch (Exception ex) {
                logger.warn("Could not query Gerrit for [" + queryString + "]", ex);
                session.setAttribute(SESSION_SEARCH_ERROR, ex);
            }
            response.sendRedirect2(".");
        } else {
            logger.error("Could not find config for the server {}", selectedServer);
        }
    }

    /**
     * Builds the selected patch-set(s).
     *
     * @param selectedIds the selected rows in the form's search-result separated by "[]".
     * @param request     the request.
     * @param response    the response.
     * @throws IOException if the query fails.
     */
    @SuppressWarnings("unused")
    //Called from jelly
    public void doBuild(@QueryParameter("selectedIds") String selectedIds, StaplerRequest request,
                        StaplerResponse response) throws IOException {

        HttpSession session = request.getSession();
        if (session == null) {
            logger.debug("Session alreay closed.");
            session = request.getSession(true);
            session.setAttribute(SESSION_BUILD_ERROR, Messages.ErrorSessionAlreadyClosed());
            response.sendRedirect2(".");
            return;
        }

        String selectedServer = (String)session.getAttribute("selectedServer");
        if (!isServerEnabled(selectedServer)) {
            response.sendRedirect2(".");
            return;
        }
        Hudson.getInstance().checkPermission(PluginImpl.MANUAL_TRIGGER);

        session.removeAttribute(SESSION_BUILD_ERROR);
        String[] selectedRows = null;
        if (selectedIds != null && selectedIds.length() > 0) {
            selectedRows = selectedIds.split("\\[\\]");
        }
        if (selectedRows == null || selectedRows.length <= 0) {
            logger.debug("No builds selected.");
            session.setAttribute(SESSION_BUILD_ERROR, Messages.ErrorSelectSomethingToBuild());
            response.sendRedirect2(".");
        } else {
            logger.debug("Something to build.");
            List<JSONObject> result = (List<JSONObject>)session.getAttribute(SESSION_RESULT);
            TriggerMonitor monitor = new TriggerMonitor();
            logger.trace("Putting monitor into session.");
            session.setAttribute(SESSION_TRIGGER_MONITOR, monitor);
            logger.trace("Calling to index the search result.");
            HashMap<String, JSONObject> indexed = indexResult(result);
            logger.debug("Creating and triggering events.");
            for (String rowId : selectedRows) {
                ManualPatchsetCreated event = findAndCreatePatchSetEvent(rowId, indexed);
                logger.debug("Created event: {}", event);
                if (event != null) {
                    if (event.getProvider() == null || event.getProvider().getName() == null) {
                        event.setProvider(createProviderFromGerritServer(selectedServer));
                    }
                    monitor.add(event);
                    logger.trace("Triggering event: {}", event);
                    triggerEvent(event);
                }
            }
            logger.debug("Sending redirect.");
            response.sendRedirect2(".");
        }
    }

    /**
     * Clears the HTTP session from search and manual-trigger related data.
     *
     * @param session the HTTP session.
     */
    private void clearSessionData(HttpSession session) {
        session.removeAttribute(SESSION_SEARCH_ERROR);
        session.removeAttribute(SESSION_BUILD_ERROR);
        session.removeAttribute(SESSION_RESULT);
        session.removeAttribute(SESSION_TRIGGER_MONITOR);
    }

    /**
     * Generates a "unique" id for the change and/or patch.
     * So it can be identified as a single row in the search result.
     *
     * @param change the change.
     * @param patch  the patch-set in the change.
     * @return the generated id.
     */
    public String generateTheId(JSONObject change, JSONObject patch) {
        StringBuilder theId = new StringBuilder(change.getString("id"));
        if (patch != null) {
            theId.append(ID_SEPARATOR);
            theId.append(patch.getString("revision"));
        }
        theId.append(ID_SEPARATOR);
        theId.append(change.getString("number"));
        if (patch != null) {
            theId.append(ID_SEPARATOR);
            theId.append(patch.getString("number"));
        }
        return theId.toString();
    }

    /**
     * Indexes the search result based on each change and patchset's rowId.
     *
     * @param result the search result.
     * @return an indexed map where the rowId is the key.
     * @see #generateTheId(net.sf.json.JSONObject, net.sf.json.JSONObject)
     */
    private HashMap<String, JSONObject> indexResult(List<JSONObject> result) {
        HashMap<String, JSONObject> map = new HashMap<String, JSONObject>();
        for (JSONObject res : result) {
            if (!res.has("type")) {
                String changeId = generateTheId(res, null);
                map.put(changeId, res);
                JSONArray arr = res.getJSONArray("patchSets");
                for (Object obj : arr) {
                    if (obj instanceof JSONObject) {
                        JSONObject patch = (JSONObject)obj;
                        String theId = generateTheId(res, patch);
                        map.put(theId, patch);
                    }
                }
            }
        }
        return map;
    }

    /**
     * Create event Provider from GerritServer.
     *
     * @param serverName the name of the GerritServer
     * @return the Provider with info from the GerritServer, or an empty provider if server not found
     */
    @Restricted(NoExternalUse.class)
    public static Provider createProviderFromGerritServer(String serverName) {
        GerritServer server = PluginImpl.getServer_(serverName);
        if (server == null) {
            logger.warn("Could not find GerritServer: {}", serverName);
        }
        return createProvider(server);
    }

    /**
     * Create event Provider from GerritServer.
     *
     * @param server the GerritServer to create the provider for
     * @return the Provider with info from the GerritServer, or an empty provider if server is null
     */
    @Restricted(NoExternalUse.class)
    public static Provider createProvider(GerritServer server) {
        if (server != null) {
            return new Provider(
                    server.getName(),
                    server.getConfig().getGerritHostName(),
                    String.valueOf(server.getConfig().getGerritSshPort()),
                    GerritDefaultValues.DEFAULT_GERRIT_SCHEME,
                    server.getConfig().getGerritFrontEndUrl(),
                    server.getGerritVersion()
            );
        } else {
            return new Provider();
        }
    }

    /**
     * Creates a list of the parameters as they would be in a scheduled build.
     * Without escaped quotes.
     *
     * @param jsonChange   the JSON data for the change.
     * @param jsonPatchSet the JSON data for the patch-set.
     * @param serverName the name of the GerritServer from the current session.
     * @return a list of the parameters.
     */
    @SuppressWarnings("unused") //called from jelly.
    public List<ParameterValue> getParametersForPatchSet(
            JSONObject jsonChange,
            JSONObject jsonPatchSet,
            String serverName) {
        List<ParameterValue> parameters = new LinkedList<ParameterValue>();
        Change change = new Change(jsonChange);
        PatchSet patchSet = new PatchSet(jsonPatchSet);
        PatchsetCreated event = new PatchsetCreated();
        Provider provider = createProviderFromGerritServer(serverName);
        event.setChange(change);
        event.setPatchset(patchSet);
        event.setProvider(provider);
        GerritTriggerParameters.setOrCreateParameters(event, parameters);
        return parameters;
    }

    /**
     * Returns {@link ParameterValue} for parameter with name {@link GerritTriggerParameters#GERRIT_CHANGE_URL}
     * or <code>null</code> if such parameter is not present.
     *
     * @param jsonChange   the JSON data for the change.
     * @param jsonPatchSet the JSON data for the patch-set.
     * @param serverName the name of the GerritServer from the current session.
     * @return ParameterValue or <code>null</code>
     */
    @SuppressWarnings("unused") //called from jelly.
    public ParameterValue getChangeUrlParamForPatchSet(
            JSONObject jsonChange,
            JSONObject jsonPatchSet,
            String serverName) {
        List<ParameterValue> parameters = getParametersForPatchSet(jsonChange, jsonPatchSet, serverName);
        for (ParameterValue parameterValue : parameters) {
            if (hasUrl(parameterValue)) {
                return parameterValue;
            }
        }
        return null;
    }

    /**
     * Tells if the given parameter should have a URL or not.
     * i.e. if the parameter represents {@link GerritTriggerParameters#GERRIT_CHANGE_URL}.
     *
     * @param parameterValue the parameter.
     * @return true if so.
     */
    @SuppressWarnings("unused") //called from jelly.
    public boolean hasUrl(ParameterValue parameterValue) {
        return GerritTriggerParameters.GERRIT_CHANGE_URL.name().equals(parameterValue.getName());
    }

    /**
     * Generates the URL to the provided change in Gerrit
     * If the change already has a URL provided, that URL will be used.
     *
     * @param change the change to link to.
     * @param serverName the name of the selected Gerrit server.
     * @return the URL to the change.
     */
    public String getGerritUrl(JSONObject change, String serverName) {
        String url = change.optString("url", null);
        if (url != null && url.length() > 0) {
            return url;
        } else if (change.optString("number", "").length() > 0) {
            if (getServerConfig(serverName) != null) {
                return getServerConfig(serverName).getGerritFrontEndUrlFor(
                    change.getString("number"), "1");
            } else {
                logger.error("Could not get config for the server: {}", serverName);
            }
        } else {
            return "";
        }
        return " ";
    }

    /**
     * Finds the patch-set in the indexed search result and creates a {@link ManualPatchsetCreated} from its data.
     *
     * @param rowId   the generated rowId in the search result.
     * @param indexed the indexed search result.
     * @return the event, or null if there is no patch-set in the search result.
     */
    private ManualPatchsetCreated findAndCreatePatchSetEvent(String rowId,
                                                             HashMap<String, JSONObject> indexed) {
        logger.trace("Searching for {}", rowId);
        String[] ids = rowId.split(ID_SEPARATOR);
        if (ids.length >= EXPECTED_NR_OF_PARTS_IN_A_GENERATED_ID) {
            logger.debug("Correct nr of ids: {}", ids.length);
            JSONObject patch = indexed.get(rowId);
            if (patch != null) {
                logger.debug("Found the patch: {}", patch);
                String changeId = ids[0] + ID_SEPARATOR + ids[2];
                logger.debug("ChangeId calculated to: {}", changeId);
                JSONObject change = indexed.get(changeId);
                if (change != null) {
                    logger.debug("Found the change: {}", change);
                    return new ManualPatchsetCreated(change, patch, Hudson.getAuthentication().getName());
                } else {
                    logger.trace("No change found with id {}", changeId);
                    return null;
                }
            } else {
                logger.trace("No patch found for id {}", rowId);
                return null;
            }
        } else {
            logger.trace("Bad nr of ids.");
            return null;
        }
    }

    /**
     * Triggers the event by putting it into the event queue.
     *
     * @param event the event to trigger.
     * @see GerritServer#triggerEvent(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)
     */
    private void triggerEvent(ManualPatchsetCreated event) {
        logger.trace("Going to trigger event: {}", event);
        String serverName = event.getProvider().getName(); //null handled by caller method doBuild
        GerritServer server = PluginImpl.getServer_(serverName);
        if (server != null) {
            server.triggerEvent(event);
        } else {
            logger.error("Could not find Gerrit server {}", serverName);
        }

    }

    /**
     * A tuple of a high and a low number.
     */
    public static class HighLow {

        private final int high;
        private final int low;

        /**
         * Standard constructor.
         *
         * @param high the highest number.
         * @param low  the lowest number.
         */
        public HighLow(int high, int low) {
            this.high = high;
            this.low = low;
        }

        /**
         * Get the High number.
         *
         * @return the high number.
         */
        public int getHigh() {
            return high;
        }

        /**
         * Get the Low number.
         *
         * @return the low number.
         */
        public int getLow() {
            return low;
        }

        @Override
        public String toString() {
            return "HighLow(" + high + "," + low + ")";
        }
    }

    /**
     * Represents a "vote"-type or Approval of a change in the JSON structure.
     */
    public static enum Approval {
        /**
         * A Code Review Approval type <i>Code-Review</i>.
         */
        CODE_REVIEW("Code-Review"),
        /**
         * A Verified Approval type <i>Verified</i>.
         */
        VERIFIED("Verified");
        private String type;

        /**
         * Standard constructor.
         *
         * @param type the approval type.
         */
        Approval(String type) {
            this.type = type;
        }

        /**
         * Finds the highest and lowest approval value of the approval's type for the specified change.
         *
         * @param res the change.
         * @return the highest and lowest value. Or 0,0 if there are no values.
         */
        @Deprecated
        public HighLow getApprovals(JSONObject res) {
            return getApprovals(res, 0);
        }

        /**
         * Finds the highest and lowest approval value of the approval's type for the specified change.
         *
         * @param res the change.
         * @param patchSetNumber the patch set number.
         * @return the highest and lowest value. Or 0,0 if there are no values.
         */
        public HighLow getApprovals(JSONObject res, int patchSetNumber) {
            logger.trace("Get Approval: {} {}", type, res);
            int highValue = Integer.MIN_VALUE;
            int lowValue = Integer.MAX_VALUE;
            if (res.has("currentPatchSet")) {
                logger.trace("Has currentPatchSet");
                JSONObject patchSet = res.getJSONObject("currentPatchSet");
                if (patchSet.has("number") && patchSet.has("approvals")) {
                    if (patchSet.getInt("number") == patchSetNumber) {
                        JSONArray approvals = patchSet.getJSONArray("approvals");
                        logger.trace("Approvals: {}", approvals);
                        for (Object o : approvals) {
                            JSONObject ap = (JSONObject)o;
                            if (type.equalsIgnoreCase(ap.optString("type"))) {
                                logger.trace("A {}", type);
                                try {
                                    int approval = Integer.parseInt(ap.getString("value"));
                                    highValue = Math.max(highValue, approval);
                                    lowValue = Math.min(lowValue, approval);
                                } catch (NumberFormatException nfe) {
                                    logger.warn("Gerrit is bad at giving me Approval-numbers! {}", nfe);
                                }
                            }
                        }
                    }
                }
            }
            if (highValue == Integer.MIN_VALUE && lowValue == Integer.MAX_VALUE) {
                logger.debug("Returning all 0");
                return new HighLow(0, 0);
            } else {
                HighLow r = new HighLow(highValue, lowValue);
                logger.debug("Returning something {}", r);
                return r;
            }
        }
    }
}
