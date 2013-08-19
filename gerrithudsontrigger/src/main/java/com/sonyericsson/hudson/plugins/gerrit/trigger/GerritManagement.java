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
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ManagementLink;
import hudson.model.Saveable;
import hudson.util.FormValidation;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
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
     * Descriptor is only used for UI form bindings.
     */
    @Extension
    public static final class DescriptorImpl extends Descriptor<GerritManagement> {

        @Override
        public String getDisplayName() {
            return null; // unused
        }

        /**
         * Starts the event connection to Gerrit.
         * @return ok or error.
         */
        public FormValidation doStartConnection() {
            logger.debug("check permisson of doStartConnection().");
            Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
            try {
                PluginImpl.getInstance().startConnection();
                //TODO wait for the connection to actually be established.
                return FormValidation.ok();
            } catch (Exception ex) {
                logger.error("Could not start connection. ", ex);
                return FormValidation.error(ex.getMessage());
            }
        }

        /**
         * Stops the event connection to Gerrit.
         * @return ok or error.
         */
        public FormValidation doStopConnection() {
            logger.debug("check permisson of doStopConnection().");
            Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
            try {
                PluginImpl.getInstance().stopConnection();
                //TODO wait for the connection to actually be shut down.
                return FormValidation.ok();
            } catch (Exception ex) {
                logger.error("Could not stop connection. ", ex);
                return FormValidation.error(ex.getMessage());
            }
        }

        /**
         * Stops the event connection to Gerrit.
         * @return ok or error.
         */
        public FormValidation doRestartConnection() {
            logger.debug("check permisson of doRestartConnection().");
            Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
            try {
                PluginImpl.getInstance().restartConnection();
                //TODO wait for the connection to actually be shut down and connected again.
                return FormValidation.ok();
            } catch (Exception ex) {
                logger.error("Could not restart connection. ", ex);
                return FormValidation.error(ex.getMessage());
            }
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

        public FormValidation doTestRestConnection(
                @QueryParameter("gerritFrontEndUrl") final String gerritFrontEndUrl,
                @QueryParameter("gerritHttpUserName") final String gerritHttpUserName,
                @QueryParameter("gerritHttpPassword") final String gerritHttpPassword) {
            DefaultHttpClient httpclient = new DefaultHttpClient();
            HttpGet httpGet = new HttpGet(gerritFrontEndUrl + "/a/projects/?d");
            httpclient.getCredentialsProvider().setCredentials(new AuthScope(null, -1),
                    new UsernamePasswordCredentials(gerritHttpUserName,
                            gerritHttpPassword));
            HttpResponse execute;
            try {
                execute = httpclient.execute(httpGet);
            } catch (IOException e) {
                return FormValidation.error(Messages.ConnectionError(e.getMessage()));
            }

            int statusCode = execute.getStatusLine().getStatusCode();
            switch(statusCode) {
                case 200:
                    return FormValidation.ok(Messages.Success());
                case 401:
                    return FormValidation.error(Messages.HttpConnectionUnauthorized());
                default:
                    return FormValidation.error(Messages.HttpConnectionError(statusCode));
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

        try {
            getConfig().setValues(form);
            PluginImpl.getInstance().save();
        } catch (Exception ex) {
            throw new ServletException(ex);
        }
        rsp.sendRedirect(".");
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
     * Gets the global config.
     * @return the config.
     * @see PluginImpl#getConfig()
     */
    public static IGerritHudsonTriggerConfig getConfig() {
        if (PluginImpl.getInstance() != null) {
            return PluginImpl.getInstance().getConfig();
        }
        return null;
    }

    /**
     * The AdministrativeMonitor related to Gerrit.
     * convenience method for the jelly page.
     *
     * @return the monitor if it could be found.
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
     * Generates a list of helper objects for the jelly view
 * .
     * @return a list of helper objects.
     */
    public List<ExceptionDataHelper> generateHelper() {
        WatchTimeExceptionData data = getConfig().getExceptionData();
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
