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
package com.sonyericsson.hudson.plugins.gerrit.trigger;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectionFactory;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshAuthenticationException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshUtil;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritAdministrativeMonitor;
import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.model.Describable;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ManagementLink;
import hudson.model.Saveable;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil.PLUGIN_IMAGES_URL;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData.Time.MIN_HOUR;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData.Time.MAX_HOUR;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData.Time.MIN_MINUTE;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData.Time.MAX_MINUTE;

/**
 * Management link for configuring the global configuration of this trigger.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@Extension
public class GerritManagement extends ManagementLink implements StaplerProxy, Describable<GerritManagement>, Saveable {

    private static final Logger logger = LoggerFactory.getLogger(GerritManagement.class);
    private static final String START_SUCCESS = "Connection started";
    private static final String START_FAILURE = "Error establising conection";
    private static final String STOP_SUCCESS = "Connection stopped";
    private static final String STOP_FAILURE = "Error terminating connection";
    private static final String RESTART_SUCCESS = "Connection restarted";
    private static final String RESTART_FAILURE = "Error restarting connection";
    private static final String NEW_SERVER = "New";

    private String selectedServer;
    private String connectionResponse = "";

    @Override
    public String getIconFileName() {
        return PLUGIN_IMAGES_URL + "icon.png";
    }

    @Override
    public String getUrlName() {
        return "gerrit-trigger";
    }

    @Override
    public String getDisplayName() {
        return Messages.DisplayName();
    }

    @Override
    public String getDescription() {
        return Messages.PluginDescription();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return Hudson.getInstance().getDescriptorByType(DescriptorImpl.class);
    }

    /**
     * Convenience method for Jelly.
     * @return newServer a String to indicate a new server
     */
    public String getNewServer() {
        return NEW_SERVER;
    }

    /**
     * Descriptor is only used for UI form bindings.
     */
    @Extension
    public static final class DescriptorImpl extends Descriptor<GerritManagement> {

        @Override
        public String getDisplayName() {
            return null; // unused
        }

        /**
         * Tests if the provided parameters can connect to Gerrit.
         * @param gerritHostName the hostname
         * @param gerritSshPort the ssh-port
         * @param gerritProxy the proxy url
         * @param gerritUserName the username
         * @param gerritAuthKeyFile the private key file
         * @param gerritAuthKeyFilePassword the password for the keyfile or null if there is none.
         * @return {@link FormValidation#ok() } if can be done,
         *         {@link FormValidation#error(java.lang.String) } otherwise.
         */
        public FormValidation doTestConnection(
                @QueryParameter("gerritHostName") final String gerritHostName,
                @QueryParameter("gerritSshPort") final int gerritSshPort,
                @QueryParameter("gerritProxy") final String gerritProxy,
                @QueryParameter("gerritUserName") final String gerritUserName,
                @QueryParameter("gerritAuthKeyFile") final String gerritAuthKeyFile,
                @QueryParameter("gerritAuthKeyFilePassword") final String gerritAuthKeyFilePassword) {
            if (logger.isDebugEnabled()) {
                logger.debug("gerritHostName = {}\n"
                        + "gerritSshPort = {}\n"
                        + "gerritProxy = {}\n"
                        + "gerritUserName = {}\n"
                        + "gerritAuthKeyFile = {}\n"
                        + "gerritAuthKeyFilePassword = {}",
                        new Object[]{gerritHostName,
                            gerritSshPort,
                            gerritProxy,
                            gerritUserName,
                            gerritAuthKeyFile,
                            gerritAuthKeyFilePassword,  });
            }

            File file = new File(gerritAuthKeyFile);
            String password = null;
            if (gerritAuthKeyFilePassword != null && gerritAuthKeyFilePassword.length() > 0) {
                password = gerritAuthKeyFilePassword;
            }
            if (SshUtil.checkPassPhrase(file, password)) {
                if (file.exists() && file.isFile()) {
                    try {
                        SshConnection sshConnection = SshConnectionFactory.getConnection(
                                gerritHostName,
                                gerritSshPort,
                                gerritProxy,
                                new Authentication(file, gerritUserName, password));
                        sshConnection.disconnect();
                        return FormValidation.ok(Messages.Success());

                    } catch (SshConnectException ex) {
                        return FormValidation.error(Messages.SshConnectException());
                    } catch (SshAuthenticationException ex) {
                        return FormValidation.error(Messages.SshAuthenticationException(ex.getMessage()));
                    } catch (Exception e) {
                        return FormValidation.error(Messages.ConnectionError(e.getMessage()));
                    }
                } else {
                    return FormValidation.error(Messages.SshKeyFileNotFoundError(gerritAuthKeyFile));
                }
            } else {
                return FormValidation.error(Messages.BadSshkeyOrPasswordError());
            }

        }
    }

    /**
     * Saves the form to the configuration and disk.
     * @param req StaplerRequest
     * @param rsp StaplerResponse
     * @throws ServletException if something unfortunate happens.
     * @throws IOException if something unfortunate happens.
     * @throws InterruptedException if something unfortunate happens.
     */
    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws ServletException,
            IOException,
            InterruptedException {
        if (logger.isDebugEnabled()) {
            logger.debug("submit {}", req.toString());
        }
        JSONObject form = req.getSubmittedForm();
        JSONObject dropdown = form.getJSONObject("dropdown");

        PluginImpl plugin = PluginImpl.getInstance();

        /*
         * Multiple dropdownListBlock causes the key to be empty.
         * This is a known bug as referenced at:
         *
         * https://issues.jenkins-ci.org/browse/JENKINS-7517?page=
         * com.atlassian.jira.plugin.system.issuetabpanels:all-tabpanel
         *
         * As such we have to use the empty key and check the
         * corresponding value to determine which server we are configuring.
         */
        ArrayList<GerritServer> servers = plugin.getServers();
        if (form.getString("").equals(NEW_SERVER)) { //"New.." has been selected in the dropdown list
            String newServerName = dropdown.getString("name");
            if (!serverListContainsName(newServerName)) {
                GerritServer server = new GerritServer(newServerName);
                servers = plugin.addServer(server);
                selectedServer = newServerName;
                server.start();
            }
        } else { //An existing server has been selected in the dropdown list
            selectedServer = form.getString("");
            if (dropdown.getBoolean("removeServer")) { //"remove server" option is checked
                GerritServer server = plugin.getServer(selectedServer);
                List<AbstractProject> configuredJobs = server.getConfiguredJobs();

                if (configuredJobs.isEmpty()) { //No job has selected this server, remove safely.
                    server.stopConnection();
                    server.stop();
                    servers = plugin.removeServer(server);
                } else { //Display error message and the names of the jobs selecting this server.
                    StringBuilder sb = new StringBuilder();
                    sb.append("Cannot remove server; configured in the following jobs:\n");
                    String rootURL = Jenkins.getInstance().getRootUrl();
                    if (rootURL != null) {
                        for (AbstractProject job : configuredJobs) {
                            sb.append("<div>");
                            sb.append("<a href=");
                            sb.append(rootURL);
                            sb.append(job.getUrl());
                            sb.append("configure>");
                            sb.append(job.getName());
                            sb.append("</a>");
                            sb.append("</div>");
                        }
                    } else {
                        sb.append("(Links disabled because root URL is not set in Manage Jenkins > Configure System)\n");
                        for (AbstractProject job : configuredJobs) {
                            sb.append("<div>");
                            sb.append(job.getName());
                            sb.append("</div>");
                        }
                    }
                    throw FormValidation.respond(Kind.ERROR, sb.toString());
                }
            } else {
                for (GerritServer s : servers) {
                    if (s.getName().equals(selectedServer)) {
                        s.getConfig().setValues(dropdown);
                    }
                }
            }
        }
        plugin.setServers(servers);
        plugin.save();

        rsp.sendRedirect(".");
    }

    /**
     *
     * @param req the StaplerRequest
     * @param rsp the StaplerResponse
     * @throws IOException if unable to send redirect.
     */
    public void doConnectionSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException {

        GerritServer server = PluginImpl.getInstance().getServer(selectedServer);
        setConnectionResponse("");
        if (req.getParameter("button").equals("Start")) {
            try {
                server.startConnection();
                //TODO wait for the connection to actually be established.
                //setConnectionResponse(START_SUCCESS);
            } catch (Exception ex) {
                setConnectionResponse(START_FAILURE);
                logger.error("Could not start connection. ", ex);
            }
        } else if (req.getParameter("button").equals("Stop")) {
            try {
                server.stopConnection();
                //TODO wait for the connection to actually be shutdown.
                //setConnectionResponse(STOP_SUCCESS);
            } catch (Exception ex) {
                setConnectionResponse(STOP_FAILURE);
                logger.error("Could not stop connection. ", ex);
            }
        } else {
            try {
                server.restartConnection();
                //TODO wait for the connection to actually be shut down and connected again.
                //setConnectionResponse(RESTART_SUCCESS);
            } catch (Exception ex) {
                setConnectionResponse(RESTART_FAILURE);
                logger.error("Could not restart connection. ", ex);
            }
        }
        rsp.sendRedirect(".");
    }

    /**
     * Get the response after a start/stop/restartConnection; Used by jelly.
     * @return the connection response
     */
    public String getConnectionResponse() {
        return connectionResponse;
    }

    /**
     * Set the connection status.
     * @param response the response to be set.
     */
    private void setConnectionResponse(String response) {
        connectionResponse = response;
    }

    /**
     * Checks that the provided parameter is an integer and not negative.
     * @param value the value.
     * @return {@link FormValidation#validatePositiveInteger(String)}
     */
    public FormValidation doPositiveIntegerCheck(
            @QueryParameter("value")
            final String value) {

        return FormValidation.validatePositiveInteger(value);
    }

    /**
     * Checks that the provided parameter is an integer and not negative, zero is accepted.
     *
     * @param value the value.
     * @return {@link FormValidation#validateNonNegativeInteger(String)}
     */
    public FormValidation doNonNegativeIntegerCheck(
            @QueryParameter("value")
            final String value) {

        return FormValidation.validateNonNegativeInteger(value);
    }

    /**
     * Checks that the provided parameter is an integer, not negative, that is larger
     * than the minimum value.
     * @param value the value.
     * @return {@link FormValidation#validatePositiveInteger(String)}
     */
    public FormValidation doDynamicConfigRefreshCheck(
            @QueryParameter("value")
            final String value) {

        FormValidation validatePositive = FormValidation.validatePositiveInteger(value);
        if (!validatePositive.kind.equals(FormValidation.Kind.OK)) {
            return validatePositive;
        } else {
            int intValue = Integer.parseInt(value);
            if (intValue < GerritDefaultValues.MINIMUM_DYNAMIC_CONFIG_REFRESH_INTERVAL) {
                return FormValidation.error(Messages.DynamicConfRefreshTooLowError(
                        GerritDefaultValues.MINIMUM_DYNAMIC_CONFIG_REFRESH_INTERVAL));
            }
        }
        return FormValidation.ok();
    }

    /**
     * Checks that the provided parameter is an integer.
     * @param value the value.
     * @return {@link FormValidation#validatePositiveInteger(String)}
     */
    public FormValidation doIntegerCheck(
            @QueryParameter("value")
            final String value) {

        try {
            Integer.parseInt(value);
            return FormValidation.ok();
        } catch (NumberFormatException e) {
            return FormValidation.error(hudson.model.Messages.Hudson_NotANumber());
        }
    }

    /**
     * Checks that the provided parameter is an empty string or an integer.
     * @param value the value.
     * @return {@link FormValidation#validatePositiveInteger(String)}
     */
    public FormValidation doEmptyOrIntegerCheck(
            @QueryParameter("value")
            final String value) {

        if (value == null || value.length() <= 0) {
            return FormValidation.ok();
        } else {
            try {
                Integer.parseInt(value);
                return FormValidation.ok();
            } catch (NumberFormatException e) {
                return FormValidation.error(hudson.model.Messages.Hudson_NotANumber());
            }
        }
    }

    /**
     * Checks if the value is a valid URL. It does not check if the URL is reachable.
     * @param value the value
     * @return {@link FormValidation#ok() } if it is so.
     */
    public FormValidation doUrlCheck(
            @QueryParameter("value")
            final String value) {

        if (value == null || value.length() <= 0) {
            return FormValidation.error(Messages.EmptyError());
        } else {
            try {
                new URL(value);

                return FormValidation.ok();
            } catch (MalformedURLException ex) {
                return FormValidation.error(Messages.BadUrlError());
            }
        }
    }

    /**
     * Checks to see if the provided value is a file path to a valid private key file.
     * @param value the value.
     * @return {@link FormValidation#ok() } if it is so.
     */
    public FormValidation doValidKeyFileCheck(
            @QueryParameter("value")
            final String value) {

        File f = new File(value);
        if (!f.exists()) {
            return FormValidation.error(Messages.FileNotFoundError(value));
        } else if (!f.isFile()) {
            return FormValidation.error(Messages.NotFileError(value));
        } else {
            if (SshUtil.isPrivateKeyFileValid(f)) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(Messages.InvalidKeyFileError(value));
            }
        }
    }

    /**
     * Checks to see if the provided value represents a time on the hh:mm format.
     * Also checks that from is before to.
     *
     * @param fromValue the from value.
     * @param toValue the to value.
     * @return {@link FormValidation#ok() } if it is so.
     */
    public FormValidation doValidTimeCheck(
            @QueryParameter final String fromValue, @QueryParameter final String toValue) {
        String[] splitFrom = fromValue.split(":");
        String[] splitTo = toValue.split(":");
        int fromHour;
        int fromMinute;
        int toHour;
        int toMinute;
        if (splitFrom.length != 2 || splitTo.length != 2) {
            return FormValidation.error(Messages.InvalidTimeString());
        }
        try {
            fromHour = Integer.parseInt(splitFrom[0]);
            fromMinute = Integer.parseInt(splitFrom[1]);
            toHour = Integer.parseInt(splitTo[0]);
            toMinute = Integer.parseInt(splitTo[1]);

        } catch (NumberFormatException nfe) {
            return FormValidation.error(Messages.InvalidTimeString());
        }

        if (fromHour < MIN_HOUR || fromHour > MAX_HOUR || fromMinute < MIN_MINUTE || fromMinute > MAX_MINUTE
                || toHour < MIN_HOUR || toHour > MAX_HOUR || toMinute < MIN_MINUTE || toMinute > MAX_MINUTE) {
                return FormValidation.error(Messages.InvalidTimeString());
        }
        if (fromHour > toHour || (fromHour == toHour && fromMinute > toMinute)) {
            return FormValidation.error(Messages.InvalidTimeSpan());
        }
        return FormValidation.ok();
    }

    /**
     * Checks whether server name already exists.
     * @param value the value of the name field.
     * @return ok or error.
     */
    public FormValidation doNameFreeCheck(
            @QueryParameter("value")
            final String value) {
        if (serverListContainsName(value)) {
            return FormValidation.error("The server name " + value + " is already in use!");
        } else {
            return FormValidation.ok();
        }
    }

    /**
     * Checks whether a server other than "New" is chosen in the dropdown.
     * Used by jelly to hide control box when adding new server.
     * @return whether a valid server is chosen.
     */
    public boolean isValidServerChosen() {
        //TODO: get the value of "selectedServer" from the dropdown json object to actually make this method work.
        return !selectedServer.equals(NEW_SERVER);
    }

    @Override
    public Object getTarget() {
        Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
        return this;
    }

    @Override
    public void save() throws IOException {
        logger.debug("SAVE!!!");
    }

    /**
     * Returns this singleton.
     * @return the single loaded instance if this class.
     */
    public static GerritManagement get() {
        return ManagementLink.all().get(GerritManagement.class);
    }

    /**
     * Get the config of a server.
     *
     * @param serverName the name of the server for which we want to get the config.
     * @return the config.
     * @see GerritServer#getConfig()
     */
    public static IGerritHudsonTriggerConfig getConfig(String serverName) {
        return PluginImpl.getInstance().getServer(serverName).getConfig();
    }

    /**
     * The AdministrativeMonitor related to Gerrit.
     * convenience method for the jelly page.
     *
     * @return the monitor if it could be found, or null otherwise.
     */
    @SuppressWarnings("unused") //Called from Jelly
    public GerritAdministrativeMonitor getAdministrativeMonitor() {
        for (AdministrativeMonitor monitor : AdministrativeMonitor.all()) {
            if (monitor instanceof GerritAdministrativeMonitor) {
                return (GerritAdministrativeMonitor)monitor;
            }
        }
        return null;
    }

    /**
     * Get the list of Gerrit server names.
     *
     * @return the list of server names as an array.
     */
    public String[] getServerNames() {
        ArrayList<String> names = new ArrayList<String>();
        for (GerritServer s : PluginImpl.getInstance().getServers()) {
            names.add(s.getName());
        }
        return names.toArray(new String[names.size()]);
    }

    /**
     * Check whether the list of servers contains a GerritServer object of a specific name.
     *
     * @param name the name to check.
     * @return whether the list contains a server with the given name.
     */
    private boolean serverListContainsName(String name) {
        boolean contains = false;
        for (GerritServer s : PluginImpl.getInstance().getServers()) {
            if (s.getName().equals(name)) {
                contains = true;
            }
        }
        return contains;
    }

    /**
     * Get the selected server.
     *
     * @return the selected server as a String.
     */
    public String getSelectedServer() {
        if (selectedServer == null) {
            ArrayList<GerritServer> servers = PluginImpl.getInstance().getServers();
            if (servers.isEmpty()) { //no server previously configured
                selectedServer = NEW_SERVER;
            } else { //at least one server previously configured, return the first one.
                selectedServer = servers.get(0).getName();
            }
        }
        return selectedServer;
    }

    /**
     * Generates a list of helper objects for the jelly view.
     *
     * @param serverName the name of the server for which we want to generate helper objects.
     * @return a list of helper objects.
     */
    public List<ExceptionDataHelper> generateHelper(String serverName) {
        WatchTimeExceptionData data = getConfig(serverName).getExceptionData();
        List<ExceptionDataHelper> list = new LinkedList<ExceptionDataHelper>();
        list.add(new ExceptionDataHelper(Messages.MondayDisplayName(), Calendar.MONDAY, data));
        list.add(new ExceptionDataHelper(Messages.TuesdayDisplayName(), Calendar.TUESDAY, data));
        list.add(new ExceptionDataHelper(Messages.WednesdayDisplayName(), Calendar.WEDNESDAY, data));
        list.add(new ExceptionDataHelper(Messages.ThursdayDisplayName(), Calendar.THURSDAY, data));
        list.add(new ExceptionDataHelper(Messages.FridayDisplayName(), Calendar.FRIDAY, data));
        list.add(new ExceptionDataHelper(Messages.SaturdayDisplayName(), Calendar.SATURDAY, data));
        list.add(new ExceptionDataHelper(Messages.SundayDisplayName(), Calendar.SUNDAY, data));
        return list;
    }
}
