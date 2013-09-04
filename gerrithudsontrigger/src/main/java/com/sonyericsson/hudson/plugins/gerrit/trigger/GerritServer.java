/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
 *  Copyright 2013 Ericsson.
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

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData.Time.MAX_HOUR;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData.Time.MAX_MINUTE;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData.Time.MIN_HOUR;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData.Time.MIN_MINUTE;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Describable;
import hudson.model.Failure;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.util.FormValidation;

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
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ConnectionListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritHandler;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritSendCommandQueue;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshAuthenticationException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectionFactory;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshUtil;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritConnectionListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.UnreviewedPatchesListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;

/**
 * Every instance of this class represents a Gerrit server having its own unique name,
 * event manager, project list updater, configuration, and lists of listeners.
 * All interactions with a Gerrit server should go through this class.
 * The list of GerritServer is kept in @PluginImpl.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 * @author Mathieu Wang &lt;mathieu.wang@ericsson.com&gt;
 *
 */
public class GerritServer implements Describable<GerritServer> {
    private static final Logger logger = LoggerFactory.getLogger(GerritServer.class);
    private static final String START_SUCCESS = "Connection started";
    private static final String START_FAILURE = "Error establising conection";
    private static final String STOP_SUCCESS = "Connection stopped";
    private static final String STOP_FAILURE = "Error terminating connection";
    private static final String RESTART_SUCCESS = "Connection restarted";
    private static final String RESTART_FAILURE = "Error restarting connection";
    private String name;
    private transient boolean started;
    private transient String connectionResponse = "";
    private transient GerritHandler gerritEventManager;
    private transient GerritConnection gerritConnection;
    private transient GerritProjectListUpdater projectListUpdater;
    private transient UnreviewedPatchesListener unreviewedPatchesListener;
    private IGerritHudsonTriggerConfig config;
    private transient GerritConnectionListener gerritConnectionListener;

    @Override
    public DescriptorImpl getDescriptor() {
        return Hudson.getInstance().getDescriptorByType(DescriptorImpl.class);
    }

    /**
     * Convenience method for jelly to get url of the server list's page relative to root.
     * @link {@link GerritManagement#getUrlName()}.
     *
     * @return the relative url
     */
    public String getParentUrl() {
        return GerritManagement.get().getUrlName();
    }
    /**
     * Convenience method for jelly to get url of this server's config page relative to root.
     * @link {@link GerritManagement#getUrlName()}.
     *
     * @return the relative url
     */
    public String getUrl() {
        return GerritManagement.get().getUrlName() + "/server/" + name;
    }
    /**
     * Constructor.
     *
     * @param name the name of the server.
     */
    public GerritServer(String name) {
        this.name = name;
        config = new Config();
    }

    /**
     * Gets the global config of this server.
     *
     * @return the config.
     */
    public IGerritHudsonTriggerConfig getConfig() {
        return config;
    }

    /**
     * Sets the global config of this server.
     *
     * @param config the config.
     */
    public void setConfig(IGerritHudsonTriggerConfig config) {
        this.config = config;
    }

    /**
     * Get the name of the server.
     *
     * @return name the name of the server.
     */
    public String getName() {
        return name;
    }

    /**
     * Starts the server's project list updater, send command queue and event manager.
     *
     */
    public void start() {
        logger.info("Starting GerritServer: " + name);
        projectListUpdater = new GerritProjectListUpdater(name);
        projectListUpdater.start();

        //Starts the send-command-queue
        GerritSendCommandQueue.getInstance(config);

        //do not try to connect to gerrit unless there is a URL or a hostname in the text fields
        List<VerdictCategory> categories = config.getCategories();
        if (categories == null) {
            categories = new LinkedList<VerdictCategory>();
        }
        if (categories.isEmpty()) {
            categories.add(new VerdictCategory("CRVW", "Code Review"));
            categories.add(new VerdictCategory("VRIF", "Verified"));
        }
        config.setCategories(categories);
        gerritEventManager = new GerritHandler(config.getNumberOfReceivingWorkerThreads(), config.getGerritEMail());

        initializeConnectionListener();

        //Starts unreviewed patches listener
        unreviewedPatchesListener = new UnreviewedPatchesListener(name);
        logger.info(name + " started");
        started = true;
    }

    /**
     * Initializes the Gerrit connection listener for this server.
     */
    private void initializeConnectionListener() {
        gerritConnectionListener = new GerritConnectionListener(name);
        addListener(gerritConnectionListener);
        gerritConnectionListener.setConnected(isConnected());
        gerritConnectionListener.checkGerritVersionFeatures();
    }

    /**
     * Stops the server's project list updater, send command queue and event manager.
     *
     */
    public void stop() {
        logger.info("Stopping GerritServer " + name);
        if (projectListUpdater != null) {
            projectListUpdater.shutdown();
            try {
                projectListUpdater.join();
            } catch (InterruptedException ie) {
                logger.error("project list updater of " + name + "interrupted", ie);
            }
            projectListUpdater = null;
        }

        if (unreviewedPatchesListener != null) {
            unreviewedPatchesListener.shutdown();
            unreviewedPatchesListener = null;
        }

        if (gerritConnection != null) {
            gerritConnection.shutdown(false);
            gerritConnection = null;
        }
        if (gerritEventManager != null) {
            gerritEventManager.shutdown(false);
            //TODO save to registered listeners?
            gerritEventManager = null;
        }
        GerritSendCommandQueue.shutdown();
        logger.info(name + " stopped");
        started = false;
    }

    /**
     * Adds a listener to the EventManager.  The listener will receive all events from Gerrit.
     *
     * @param listener the listener to add.
     * @see GerritHandler#addListener(com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener)
     */
    public void addListener(GerritEventListener listener) {
        if (gerritEventManager != null) {
            gerritEventManager.addListener(listener);
        }
    }

    /**
     * Removes a listener from the manager.
     *
     * @param listener the listener to remove.
     * @see GerritHandler#removeListener(com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener)
     */
    public void removeListener(GerritEventListener listener) {
        if (gerritEventManager != null) {
            gerritEventManager.removeListener(listener);
        }
    }

    /**
     * Removes a connection listener from the manager.
     *
     * @param listener the listener to remove.
     */
    public void removeListener(ConnectionListener listener) {
        if (gerritEventManager != null) {
            gerritEventManager.removeListener(listener);
        }
    }

    /**
     * Get the GerritConnectionListener for GerritAdministrativeMonitor.
     * @return the GerritConnectionListener, or null if it has not yet been initialized.
     */
    public GerritConnectionListener getGerritConnectionListener() {
        return gerritConnectionListener;
    }

    /**
     * Starts the connection to Gerrit stream of events.
     *
     * @see GerritManagement.DescriptorImpl#doStartConnection()
     */
    public synchronized void startConnection() {
        if (!config.hasDefaultValues()) {
            if (gerritConnection == null) {
                logger.debug("Starting Gerrit connection...");
                gerritConnection = new GerritConnection(name, config);
                gerritEventManager.setIgnoreEMail(config.getGerritEMail());
                gerritEventManager.setNumberOfWorkerThreads(config.getNumberOfReceivingWorkerThreads());
                gerritConnection.setHandler(gerritEventManager);
                gerritConnection.start();
            } else {
                logger.warn("Already started!");
            }
        }
    }

    /**
     * Stops the connection to Gerrit stream of events.
     *
     * @see GerritManagement.DescriptorImpl#doStopConnection()
     */
    public synchronized void stopConnection() {
        if (gerritConnection != null) {
            gerritConnection.shutdown(true);
            gerritConnection = null;
        } else {
            logger.warn("Was told to shutdown again?");
        }

    }

    /**
     * A quick check if a connection to Gerrit is open.
     *
     * @return true if so.
     */

    public synchronized boolean isConnected() {
        if (gerritConnection != null) {
            return gerritConnection.isConnected();
        }
        return false;
    }

    /**
     * Restarts the connection to Gerrit stream of events.
     *
     * @see GerritManagement.DescriptorImpl#doRestartConnection()
     */
    public void restartConnection() {
        stopConnection();
        startConnection();
    }

    /**
     * Adds a Connection Listener to the manager.
     * Return the current connection status so that listeners that
     * are added later than a connectionestablished/ connectiondown
     * will get the current connection status.
     *
     * @param listener the listener to be added.
     */
    public void addListener(ConnectionListener listener) {
        if (gerritEventManager != null) {
            gerritEventManager.addListener(listener);
        }
    }

    /**
     * Returns a list of Gerrit projects.
     *
     * @return list of gerrit projects
     */
    public List<String> getGerritProjects() {
        if (projectListUpdater != null) {
            return projectListUpdater.getGerritProjects();
        } else {
            return new ArrayList<String>();
        }
    }

    /**
     * Adds the given event to the stream of events.
     * It gets added to the same event queue as any event coming from the stream-events command in Gerrit.
     * Throws IllegalStateException if the event manager is null
     *
     * @param event the event.
     * @see GerritHandler#triggerEvent(com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent)
     */
    public void triggerEvent(GerritEvent event) {
        if (gerritEventManager != null) {
            gerritEventManager.triggerEvent(event);
        } else {
            throw new IllegalStateException("Manager not started!");
        }
    }

    /**
     * Returns the current Gerrit version.
     *
     * @return the current Gerrit version as a String if connected, or null otherwise.
     */
    public String getGerritVersion() {
        if (gerritConnection != null) {
            return gerritConnection.getGerritVersion();
        } else {
            return null;
        }
    }

    /**
     * Descriptor is only used for UI form bindings.
     */
    @Extension
    public static final class DescriptorImpl extends Descriptor<GerritServer> {

        @Override
        public String getDisplayName() {
            return "Gerrit Server with Default Configurations";
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

        String newName = form.getString("name");
        boolean renamed = false;
        if (!name.equals(newName)) {
            if (PluginImpl.getInstance().containsServer(newName)) {
                throw new Failure("A server already exists with the name '" + newName + "'");
            }
            rename(newName);
            renamed = true;
        }
        config.setValues(form);

        PluginImpl.getInstance().save();

        if (!started) {
            this.start();
        }
        if (renamed) {
            rsp.sendRedirect("../..");
            return;
        } else {
            rsp.sendRedirect(".");
        }
    }

    /**
     * Rename the server.
     * Assumes that newName is different from current name.
     *
     * @param newName the new name
     */
    private void rename(String newName) {
        if (isConnected()) {
            stopConnection();
            stop();
            String oldName = name;
            name = newName;
            start();
            startConnection();
            changeSelectedServerInJobs(oldName);
        } else {
            stop();
            String oldName = name;
            name = newName;
            start();
            changeSelectedServerInJobs(oldName);
        }
    }

    /**
     * Convenience method for remove.jelly.
     *
     * @return the list of jobs configured with this server.
     */
    public List<AbstractProject> getConfiguredJobs() {
        return PluginImpl.getInstance().getConfiguredJobs(name);
    }

    /**
     * Change the selectedServer value in jobs to select the new name.
     *
     * @param oldName the old name of the Gerrit server
     */
    private void changeSelectedServerInJobs(String oldName) {
        for (AbstractProject job : PluginImpl.getInstance().getConfiguredJobs(oldName)) {
            GerritTrigger trigger = (GerritTrigger)job.getTrigger(GerritTrigger.class);
            try {
                trigger.setServerName(name);
                trigger.start(job, false);
                job.addTrigger(trigger);
                job.save();
            } catch (IOException e) {
                logger.error("Error saving Gerrit Trigger configurations for job [" + job.getName()
                        + "] after Gerrit server has been renamed from [" + oldName + "] to [" + name + "]");
            }
        }
    }

    /**
     * Remove "Gerrit event" as a trigger in all jobs selecting this server.
     */
    private void removeGerritTriggerInJobs() {
        for (AbstractProject job : getConfiguredJobs()) {
            GerritTrigger trigger = (GerritTrigger)job.getTrigger(GerritTrigger.class);
            trigger.stop();
            try {
                job.removeTrigger(trigger.getDescriptor());
            } catch (IOException e) {
                logger.error("Error removing Gerrit trigger from job [" + job.getName()
                        + "]. Please check job config");
            }
            trigger = null;
            try {
                job.save();
            } catch (IOException e) {
                logger.error("Error saving configuration of job [" + job.getName()
                        + "] while trying to remove Gerrit server [" + name + "]. Please check job config.");
            }
        }
    }

    /**
     *
     * @param req the StaplerRequest
     * @param rsp the StaplerResponse
     * @throws IOException if unable to send redirect.
     */
    public void doConnectionSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException {

        setConnectionResponse("");
        if (req.getParameter("button").equals("Start")) {
            try {
                startConnection();
                //TODO wait for the connection to actually be established.
                //setConnectionResponse(START_SUCCESS);
            } catch (Exception ex) {
                setConnectionResponse(START_FAILURE);
                logger.error("Could not start connection. ", ex);
            }
        } else if (req.getParameter("button").equals("Stop")) {
            try {
                stopConnection();
                //TODO wait for the connection to actually be shutdown.
                //setConnectionResponse(STOP_SUCCESS);
            } catch (Exception ex) {
                setConnectionResponse(STOP_FAILURE);
                logger.error("Could not stop connection. ", ex);
            }
        } else {
            try {
                restartConnection();
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
     * Saves the form to the configuration and disk.
     * @param req StaplerRequest
     * @param rsp StaplerResponse
     * @throws ServletException if something unfortunate happens.
     * @throws IOException if something unfortunate happens.
     * @throws InterruptedException if something unfortunate happens.
     */
    public void doRemoveConfirm(StaplerRequest req, StaplerResponse rsp) throws ServletException,
    IOException,
    InterruptedException {

        stopConnection();
        stop();
        PluginImpl plugin = PluginImpl.getInstance();
        removeGerritTriggerInJobs();
        plugin.removeServer(this);
        plugin.save();

        rsp.sendRedirect(Jenkins.getInstance().getRootUrl() + GerritManagement.get().getUrlName());
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
        if (!value.equals(name)) {
            if (PluginImpl.getInstance().containsServer(value)) {
                return FormValidation.error("The server name " + value + " is already in use!");
            } else {
                return FormValidation.warning("The server " + name + " will be renamed");
            }
        } else {
            return FormValidation.ok();
        }
    }

    /**
     * Generates a list of helper objects for the jelly view.
     *
     * @return a list of helper objects.
     */
    public List<ExceptionDataHelper> generateHelper() {
        WatchTimeExceptionData data = config.getExceptionData();
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
