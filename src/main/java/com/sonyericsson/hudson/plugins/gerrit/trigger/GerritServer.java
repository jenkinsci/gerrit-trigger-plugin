/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Mobile Communications Inc. All rights reserved.
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

import static com.sonymobile.tools.gerrit.gerritevents.watchdog.WatchTimeExceptionData.Time.MAX_HOUR;
import static com.sonymobile.tools.gerrit.gerritevents.watchdog.WatchTimeExceptionData.Time.MAX_MINUTE;
import static com.sonymobile.tools.gerrit.gerritevents.watchdog.WatchTimeExceptionData.Time.MIN_HOUR;
import static com.sonymobile.tools.gerrit.gerritevents.watchdog.WatchTimeExceptionData.Time.MIN_MINUTE;
import static com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil.PLUGIN_IMAGES_URL;
import hudson.Extension;
import hudson.Functions;
import hudson.RelativePath;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Action;
import hudson.model.Describable;
import hudson.model.Failure;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.security.Permission;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import hudson.util.ListBoxModel.Option;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.apache.commons.lang.CharEncoding;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonymobile.tools.gerrit.gerritevents.ConnectionListener;
import com.sonymobile.tools.gerrit.gerritevents.GerritDefaultValues;
import com.sonymobile.tools.gerrit.gerritevents.GerritEventListener;
import com.sonymobile.tools.gerrit.gerritevents.GerritHandler;
import com.sonymobile.tools.gerrit.gerritevents.GerritConnection;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.Notify;
import com.sonymobile.tools.gerrit.gerritevents.ssh.Authentication;
import com.sonymobile.tools.gerrit.gerritevents.ssh.SshAuthenticationException;
import com.sonymobile.tools.gerrit.gerritevents.ssh.SshConnectException;
import com.sonymobile.tools.gerrit.gerritevents.ssh.SshConnection;
import com.sonymobile.tools.gerrit.gerritevents.ssh.SshConnectionFactory;
import com.sonymobile.tools.gerrit.gerritevents.ssh.SshUtil;
import com.sonymobile.tools.gerrit.gerritevents.watchdog.WatchTimeExceptionData;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.ReplicationConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritConnectionListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritSlave;
import com.sonyericsson.hudson.plugins.gerrit.trigger.playback.GerritMissedEventsPlaybackManager;
import com.sonyericsson.hudson.plugins.gerrit.trigger.version.GerritVersionChecker;

/**
 * Every instance of this class represents a Gerrit server having its own unique name,
 * connection, project list updater, configuration, and lists of listeners.
 * All interactions with a Gerrit server should go through this class.
 * The list of GerritServer is kept in @PluginImpl.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 * @author Mathieu Wang &lt;mathieu.wang@ericsson.com&gt;
 *
 */
@ExportedBean(defaultVisibility = 2)
public class GerritServer implements Describable<GerritServer>, Action {
    private static final Logger logger = LoggerFactory.getLogger(GerritServer.class);
    private static final String START_SUCCESS = "Connection started";
    private static final String START_FAILURE = "Error establising conection";
    private static final String STOP_SUCCESS = "Connection stopped";
    private static final String STOP_FAILURE = "Error terminating connection";
    /**
     * Key that is used to select to trigger a build on events from any server.
     */
    public static final String ANY_SERVER = "__ANY__";
    private static final int THREADS_FOR_TEST_CONNECTION = 1;
    private static final int TIMEOUT_FOR_TEST_CONNECTION = 10;
    private static final int RESPONSE_COUNT = 1;
    private static final int RESPONSE_INTERVAL_MS = 1000;
    private static final int RESPONSE_TIMEOUT_S = 10;
    private String name;
    @Deprecated
    private transient boolean pseudoMode;
    private boolean noConnectionOnStartup;
    private transient boolean started;
    private transient boolean timeoutWakeup = false;
    private transient String connectionResponse = "";
    private transient GerritHandler gerritEventManager;
    private transient GerritConnection gerritConnection;
    private transient GerritProjectListUpdater projectListUpdater;
    private IGerritHudsonTriggerConfig config;
    private transient GerritConnectionListener gerritConnectionListener;
    private transient GerritMissedEventsPlaybackManager missedEventsPlaybackManager;

    @Override
    public DescriptorImpl getDescriptor() {
        return Hudson.getInstance().getDescriptorByType(DescriptorImpl.class);
    }

    /**
     * Returns the Missed Events playback manager.
     * @return GerritMissedEventsPlaybackManager
     */
    public GerritMissedEventsPlaybackManager getMissedEventsPlaybackManager() {
        return missedEventsPlaybackManager;
    }

     /**
     * Convenience method for jelly to get url of the server list's page relative to root.
     *
     * @see GerritManagement#getUrlName()
     * @return the relative url
     */
    public String getParentUrl() {
        return GerritManagement.get().getUrlName();
    }

    /**
     * Convenience method for jelly to get url of this server's config page relative to root.
     *
     * @see GerritManagement#getUrlName()
     * @return the relative url
     */
    public String getUrl() {
        return GerritManagement.get().getUrlName() + "/server/" + getUrlEncodedName();
    }
    /**
     * Constructor.
     *
     * @param name the name of the server.
     */
    public GerritServer(String name) {
        this(name, false);
    }

    /**
     * Constructor.
     *
     * @param name the name of the server.
     * @param noConnectionOnStartup if noConnectionOnStartup or not.
     */
    public GerritServer(String name, boolean noConnectionOnStartup) {
        this.name = name;
        this.pseudoMode = false;
        this.noConnectionOnStartup = noConnectionOnStartup;
        config = new Config();
    }

    /**
     * If the parameter represents {@link #ANY_SERVER}.
     * I.e. if serverName is null or empty or equal to {@link #ANY_SERVER}.
     *
     * @param serverName the String to test
     * @return true if so.
     * @see GerritTrigger#isAnyServer()
     */
    public static boolean isAnyServer(String serverName) {
        return serverName == null || serverName.isEmpty() || ANY_SERVER.equals(serverName);
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
        checkPermission();
        this.config = config;
    }

    /**
     * Get the name of the server.
     *
     * @return name the name of the server.
     */
    @Exported
    public String getName() {
        return name;
    }

    /**
     * Get hostname of the server.
     *
     * @return the hostname of the server.
     */
    @Exported
    public String getHostName() {
        return config.getGerritHostName();
    }

    /**
     * Get ssh port of the server.
     *
     * @return the ssh port of the server.
     */
    @Exported
    public int getSshPort() {
        return config.getGerritSshPort();
    }

    /**
     * Get username of the server.
     *
     * @return the username of the server.
     */
    @Exported
    public String getUserName() {
        return config.getGerritUserName();
    }

    /**
     * Get HTTP username of the server.
     *
     * @return HTTP username of the server.
     */
    @Exported
    public String getHttpUserName() {
        return config.getGerritHttpUserName();
    }

    /**
     * Get frontend url of the server.
     *
     * @return the frontend url of the server.
     */
    @Exported
    public String getFrontEndUrl() {
        return config.getGerritFrontEndUrl();
    }

    /**
     * If pseudo mode or not.
     *
     * @return true if so.
     */
    @Deprecated
    public boolean isPseudoMode() {
        return pseudoMode;
    }

    /**
     * Sets pseudo mode.
     *
     * @param pseudoMode true if pseudoMode connection.
     */
    @Deprecated
    public void setPseudoMode(boolean pseudoMode) {
        this.pseudoMode = pseudoMode;
    }

    /**
     * If no connection on startup or not.
     *
     * @return true if so.
     */
    @Exported
    public boolean isNoConnectionOnStartup() {
        return noConnectionOnStartup;
    }

    /**
     * Sets connect on startup.
     *
     * @param noConnectionOnStartup true if connect on startup.
     */
    public void setNoConnectionOnStartup(boolean noConnectionOnStartup) {
        this.noConnectionOnStartup = noConnectionOnStartup;
    }

    /**
     * Gets wakeup is failed by timeout or not.
     *
     * @return true if wakeup is failed by timeout.
     */
    @Exported
    public boolean isTimeoutWakeup() {
        return timeoutWakeup;
    }

    @Override
    public String getIconFileName() {
        return PLUGIN_IMAGES_URL + "icon24.png";
    }

    @Override
    public String getDisplayName() {
        return getName();
    }

    @Override
    public String getUrlName() {
        //Lets make an absolute url to circumvent some buggy things in core
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null && jenkins.getRootUrl() != null) {
            return Functions.joinPath(jenkins.getRootUrl(),
                    getParentUrl(), "server", getUrlEncodedName());
        } else {
            return Functions.joinPath("/", getParentUrl(), "server", getUrlEncodedName());
        }
    }

    /**
     * Get the url encoded name of the server.
     *
     * @return the url encoded name.
     */
    public String getUrlEncodedName() {
        String urlName;
        try {
            urlName = URLEncoder.encode(name, CharEncoding.UTF_8);
        } catch (Exception ex) {
            urlName = URLEncoder.encode(name);
        }
        return urlName;
    }

    /**
     * Check whether this server is the last one.
     * Used by jelly to stop removal if true.
     *
     * @return whether it is the last one;
     */
    public boolean isLastServer() {
        return PluginImpl.getServers_().size() == 1;
    }

    /**
     * Checks that the current user has {@link #getRequiredPermission()} permission.
     * If Jenkins is currently active.
     */
    private void checkPermission() {
        final Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null) {
            jenkins.checkPermission(getRequiredPermission());
        }
    }

    /**
     * {@link Jenkins#ADMINISTER} permission for viewing and changing the configuration.
     * Also used by Jelly
     *
     * @return the permission
     */
    public final Permission getRequiredPermission() {
        return Jenkins.ADMINISTER;
    }

    /**
     * Starts the server's project list updater, send command queue and event manager.
     *
     */
    public void start() {
        checkPermission();
        logger.info("Starting GerritServer: " + name);

        //do not try to connect to gerrit unless there is a URL or a hostname in the text fields
        List<VerdictCategory> categories = config.getCategories();
        if (categories == null) {
            categories = new LinkedList<VerdictCategory>();
        }
        if (categories.isEmpty()) {
            categories.add(new VerdictCategory("Code-Review", "Code Review"));
            categories.add(new VerdictCategory("Verified", "Verified"));
        }
        config.setCategories(categories);
        gerritEventManager = PluginImpl.getHandler_();

        if (missedEventsPlaybackManager == null) {
            missedEventsPlaybackManager = new GerritMissedEventsPlaybackManager(name);
        }

        initializeConnectionListener();

        projectListUpdater =
                new GerritProjectListUpdater(name);
        projectListUpdater.start();

        missedEventsPlaybackManager.checkIfEventsLogPluginSupported();
        addListener((GerritEventListener)missedEventsPlaybackManager);

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
        checkPermission();
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

        if (missedEventsPlaybackManager != null) {
            missedEventsPlaybackManager.shutdown();
            missedEventsPlaybackManager = null;
        }

        if (gerritConnection != null) {
            gerritConnection.shutdown(false);
            gerritConnection = null;
        }

        logger.info(name + " stopped");
        started = false;
    }

    /**
     * Adds a listener to the EventManager.  The listener will receive all events from Gerrit.
     *
     * @param listener the listener to add.
     * @see GerritHandler#addListener(com.sonymobile.tools.gerrit.gerritevents.GerritEventListener)
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
     * @see GerritHandler#removeListener(com.sonymobile.tools.gerrit.gerritevents.GerritEventListener)
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
        if (gerritConnection != null) {
            gerritConnection.removeListener(listener);
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
     * During startup it is called by
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritItemListener}.
     *
     */
    public synchronized void startConnection() {
        checkPermission();
        if (!config.hasDefaultValues()) {
            if (gerritConnection == null) {
                logger.debug("Starting Gerrit connection...");
                gerritConnection = new GerritConnection(name, config);
                if (config.isTriggerOnAllComments()) {
                    logger.info("Will trigger on all comments, even from the configured user.");
                } else {
                    logger.info("Will skip comments added by " + config.getGerritUserName());
                    gerritEventManager.setIgnoreEMail(name, config.getGerritEMail());
                }
                gerritConnection.setHandler(gerritEventManager);
                gerritConnection.addListener(gerritConnectionListener);
                gerritConnection.addListener(projectListUpdater);

                missedEventsPlaybackManager.checkIfEventsLogPluginSupported();
                gerritConnection.addListener(missedEventsPlaybackManager);

                gerritConnection.start();
            } else {
                logger.warn("Already started!");
            }
        }
    }

    /**
     * Stops the connection to Gerrit stream of events.
     *
     */
    public synchronized void stopConnection() {
        checkPermission();
        if (gerritConnection != null) {
            gerritConnection.shutdown(true);
            gerritConnection.removeListener(gerritConnectionListener);
            gerritConnection.removeListener(missedEventsPlaybackManager);
            gerritConnection = null;
            gerritEventManager.setIgnoreEMail(name, null);
        } else {
            logger.warn("Was told to shutdown again?");
        }
    }

    /**
     * A quick check if a connection to Gerrit is open.
     *
     * @return true if so.
     */
    @Exported
    public synchronized boolean isConnected() {
        if (gerritConnection != null) {
            return gerritConnection.isConnected();
        }
        return false;
    }

    /**
     * Restarts the connection to Gerrit stream of events.
     *
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
        if (gerritConnection != null) {
            gerritConnection.addListener(listener);
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
     * @see GerritHandler#triggerEvent(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)
     */
    public void triggerEvent(GerritEvent event) {
        if (gerritEventManager != null) {
            gerritEventManager.post(event);
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
     * Return if the current server support replication events.
     * @return true if replication events are supported, otherwise false
     */
    public boolean isReplicationEventsSupported() {
        return GerritVersionChecker.isCorrectVersion(GerritVersionChecker.Feature.replicationEvents, name);
    }

    /**
     * Checks whether the current server support project-created events or not.
     *
     * Note: We need to exclude snapshot versions from this check. Otherwise, snapshot versions
     * that are &lt; Gerrit 2.12 will default to waiting for Project Created events which are only
     * supported in Gerrit &gt;= 2.12.
     *
     * @return true if project-created events are supported, otherwise false
     */
    public boolean isProjectCreatedEventsSupported() {
        return GerritVersionChecker.isCorrectVersion(GerritVersionChecker.Feature.projectCreatedEvents, name,
                true);
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
                password = Secret.fromString(gerritAuthKeyFilePassword).getPlainText();
            }
            if (SshUtil.checkPassPhrase(file, password)) {
                if (file.exists() && file.isFile()) {
                    try {
                        final SshConnection sshConnection = SshConnectionFactory.getConnection(
                                gerritHostName,
                                gerritSshPort,
                                gerritProxy,
                                new Authentication(file, gerritUserName, password));
                        ExecutorService service = Executors.newFixedThreadPool(THREADS_FOR_TEST_CONNECTION);
                        Future<Integer> future = service.submit(new Callable<Integer>() {
                            @Override
                            public Integer call() throws Exception {
                                return sshConnection.executeCommandReader(GerritConnection.CMD_STREAM_EVENTS).read();
                            }
                        });
                        int readChar;
                        try {
                            readChar = future.get(TIMEOUT_FOR_TEST_CONNECTION, TimeUnit.SECONDS);
                        } catch (TimeoutException ex) {
                            readChar = 0;
                        } finally {
                            sshConnection.disconnect();
                        }
                        if (readChar < 0) {
                            return FormValidation.error(Messages.StreamEventsCapabilityException(gerritUserName));
                        } else {
                            return FormValidation.ok(Messages.Success());
                        }
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

        /**
         * Tests if the REST API settings can connect to Gerrit.
         *
         * @param gerritFrontEndUrl the url
         * @param gerritHttpUserName the user name
         * @param gerritHttpPassword the password
         * @return {@link FormValidation#ok()} if it works.
         */
        public FormValidation doTestRestConnection(
                @QueryParameter("gerritFrontEndUrl") final String gerritFrontEndUrl,
                @QueryParameter("gerritHttpUserName") final String gerritHttpUserName,
                @QueryParameter("gerritHttpPassword") final String gerritHttpPassword) {

            String password = Secret.fromString(gerritHttpPassword).getPlainText();

            String restUrl = gerritFrontEndUrl;
            if (gerritFrontEndUrl != null && !gerritFrontEndUrl.endsWith("/")) {
                restUrl = gerritFrontEndUrl + "/";
            }
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(new AuthScope(null, -1),
                new UsernamePasswordCredentials(gerritHttpUserName,
                        password));
            HttpClient httpclient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build();
            HttpGet httpGet = new HttpGet(restUrl + "a/projects/?d");
            HttpResponse execute;
            try {
                execute = httpclient.execute(httpGet);
            } catch (IOException e) {
                return FormValidation.error(Messages.ConnectionError(e.getMessage()));
            }

            int statusCode = execute.getStatusLine().getStatusCode();
            switch (statusCode) {
                case HttpURLConnection.HTTP_OK:
                    return FormValidation.ok(Messages.Success());
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                    return FormValidation.error(Messages.HttpConnectionUnauthorized());
                default:
                    return FormValidation.error(Messages.HttpConnectionError(statusCode));
            }

        }

        /**
         * Fill the Gerrit slave dropdown with the list of slaves configured with the selected server.
         * Expected to be called only when slave config is enabled at job level.
         *
         * @param serverName name of the server
         * @return list of slaves.
         */
        public ListBoxModel doFillDefaultSlaveIdItems(
            @QueryParameter("name") @RelativePath("../..") final String serverName) {
            ListBoxModel items = new ListBoxModel();
            logger.trace("filling default gerrit slave drop down for sever {}", serverName);
            GerritServer server = PluginImpl.getServer_(serverName);
            if (server == null) {
                logger.warn(Messages.CouldNotFindServer(serverName));
                items.add(Messages.CouldNotFindServer(serverName), "");
                return items;
            }
            ReplicationConfig replicationConfig = server.getConfig().getReplicationConfig();
            if (replicationConfig == null || !replicationConfig.isEnableReplication()
                || replicationConfig.getGerritSlaves().size() == 0) {
                logger.trace(Messages.GerritSlaveNotDefined());
                items.add(Messages.GerritSlaveNotDefined(), "");
                return items;
            }
            for (GerritSlave slave : replicationConfig.getGerritSlaves()) {
                boolean selected;
                if (slave.getId().equals(replicationConfig.getDefaultSlaveId())) {
                    selected = true;
                } else {
                    selected = false;
                }
                items.add(new ListBoxModel.Option(slave.getName(), slave.getId(), selected));
            }
            return items;
        }

        /**
         * Fill the dropdown for notification levels.
         *
         * @return the values.
         */
        public ListBoxModel doFillNotificationLevelItems() {
            Map<Notify, String> levelTextsById = notificationLevelTextsById();
            ListBoxModel items = new ListBoxModel(levelTextsById.size());
            for (Entry<Notify, String> level : levelTextsById.entrySet()) {
                items.add(new Option(level.getValue(), level.getKey().toString()));
            }
            return items;
        }
    }

    /**
     * Returns localized texts for each known notification value.
     *
     * @return a map with level id to level text.
     */
    public static Map<Notify, String> notificationLevelTextsById() {
        ResourceBundleHolder holder = ResourceBundleHolder.get(Messages.class);
        Map<Notify, String> textsById = new LinkedHashMap<Notify, String>(Notify.values().length, 1);
        for (Notify level : Notify.values()) {
            textsById.put(level, holder.format("NotificationLevel_" + level));
        }
        return textsById;
    }

   /**
     * Saves the form to the configuration and disk.
     * @param req StaplerRequest
     * @param rsp StaplerResponse
     * @throws ServletException if something unfortunate happens.
     * @throws IOException if something unfortunate happens.
     * @throws InterruptedException if something unfortunate happens.
     */
   @RequirePOST
    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws ServletException,
    IOException,
    InterruptedException {
        checkPermission();
        if (logger.isDebugEnabled()) {
            logger.debug("submit {}", req.toString());
        }
        JSONObject form = req.getSubmittedForm();

        String newName = form.getString("name");
        if (!name.equals(newName)) {
            if (PluginImpl.containsServer_(newName)) {
                throw new Failure("A server already exists with the name '" + newName + "'");
            } else if (ANY_SERVER.equals(newName)) {
                throw new Failure("Illegal name '" + newName + "'");
            }
            rename(newName);
        }
        noConnectionOnStartup = form.getBoolean("noConnectionOnStartup");
        config.setValues(form);

        PluginImpl.save_();

        if (!started) {
            this.start();
        } else {
            if (missedEventsPlaybackManager != null) {
                missedEventsPlaybackManager.checkIfEventsLogPluginSupported();
            }
        }

        rsp.sendRedirect("../..");
    }

    /**
     * Rename the server.
     * Assumes that newName is different from current name.
     *
     * @param newName the new name
     */
    private void rename(String newName) {
        checkPermission();
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
    public List<Job> getConfiguredJobs() {
        return PluginImpl.getConfiguredJobs_(name);
    }

    /**
     * Change the selectedServer value in jobs to select the new name.
     *
     * @param oldName the old name of the Gerrit server
     */
    private void changeSelectedServerInJobs(String oldName) {
        for (Job job : PluginImpl.getConfiguredJobs_(oldName)) {

            if (!(job instanceof AbstractProject)) {
                logger.warn("Unable to modify Gerrit Trigger configurations for job [" + job.getName()
                        + "] after Gerrit server has been renamed from [" + oldName + "] to [" + name + "]."
                        + " This feature is only supported for AbstractProject types e.g. Freestyle Jobs.");
                return;
            }
            AbstractProject project = (AbstractProject)job;

            GerritTrigger trigger = (GerritTrigger)project.getTrigger(GerritTrigger.class);
            if (trigger != null) {
                try {
                    trigger.setServerName(name);
                    trigger.start(job, false);
                    project.addTrigger(trigger);
                    project.save();
                } catch (IOException e) {
                    logger.error("Error saving Gerrit Trigger configurations for job [" + job.getName()
                            + "] after Gerrit server has been renamed from [" + oldName + "] to [" + name + "]");
                }
            }
        }
    }

    /**
     * Remove "Gerrit event" as a trigger in all jobs selecting this server.
     */
    private void removeGerritTriggerInJobs() {
        for (Job job : getConfiguredJobs()) {

            if (!(job instanceof AbstractProject)) {
                logger.warn("Unable to remove Gerrit Trigger ffrom job [" + job.getName() + "]. "
                        + " This feature is only supported for AbstractProject types e.g. Freestyle Jobs.");
                return;
            }
            AbstractProject project = (AbstractProject)job;

            GerritTrigger trigger = (GerritTrigger)project.getTrigger(GerritTrigger.class);
            trigger.stop();
            try {
                project.removeTrigger(trigger.getDescriptor());
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
     * Wakeup server. This method returns after actual connection status is changed or timeout.
     * Used by jelly.
     *
     * @return connection status.
     */
    @RequirePOST
    public JSONObject doWakeup() {
        checkPermission();
        Timer timer = new Timer();
        try {
            startConnection();

            final CountDownLatch responseLatch = new CountDownLatch(RESPONSE_COUNT);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (gerritConnectionListener != null && gerritConnectionListener.isConnected()) {
                        responseLatch.countDown();
                    }
                }
            }, RESPONSE_INTERVAL_MS, RESPONSE_INTERVAL_MS);

            if (responseLatch.await(RESPONSE_TIMEOUT_S, TimeUnit.SECONDS)) {
                timeoutWakeup = false;
                setConnectionResponse(START_SUCCESS);
            } else {
                timeoutWakeup = true;
                throw new InterruptedException("time out.");
            }
        } catch (Exception ex) {
            setConnectionResponse(START_FAILURE);
            logger.error("Could not start connection. ", ex);
        }
        timer.cancel();

        JSONObject obj = new JSONObject();
        String status = "down";
        if (gerritConnectionListener != null) {
            if (gerritConnectionListener.isConnected()) {
                status = "up";
            }
        }
        obj.put("status", status);
        return obj;
    }

    /**
     * Server to sleep. This method returns actual connection status is changed or timeout.
     * Used by jelly.
     *
     * @return connection status.
     */
    @RequirePOST
    public JSONObject doSleep() {
        checkPermission();
        Timer timer = new Timer();
        try {
            stopConnection();

            final CountDownLatch responseLatch = new CountDownLatch(RESPONSE_COUNT);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (gerritConnectionListener == null || !gerritConnectionListener.isConnected()) {
                        responseLatch.countDown();
                    }
                }
            }, RESPONSE_INTERVAL_MS, RESPONSE_INTERVAL_MS);

            if (responseLatch.await(RESPONSE_TIMEOUT_S, TimeUnit.SECONDS)) {
                setConnectionResponse(STOP_SUCCESS);
            } else {
                throw new InterruptedException("time out.");
            }
        } catch (Exception ex) {
            setConnectionResponse(STOP_FAILURE);
            logger.error("Could not stop connection. ", ex);
        }
        timer.cancel();

        JSONObject obj = new JSONObject();
        String status = "down";
        if (gerritConnectionListener != null) {
            if (gerritConnectionListener.isConnected()) {
                status = "up";
            }
        }
        obj.put("status", status);
        return obj;
    }

    /**
     * This server has errors or not.
     *
     * @return true if this server has errors.
     */
    public boolean hasErrors() {
        if (isConnectionError()) {
            return true;
        }
        return false;
    }

    /**
     * This server has warnings or not.
     *
     * @return true if this server has warnings.
     */
    public boolean hasWarnings() {
        if (isGerritSnapshotVersion() || hasDisabledFeatures()) {
            return true;
        }
        return false;
    }

    /**
     * If connection could not be established.
     *
     * @return true if so. false otherwise.
     */
    @JavaScriptMethod
    public boolean isConnectionError() {
        //if it is null then we haven't started at all.
        if (gerritConnectionListener != null && !gerritConnectionListener.isConnected()) {
            if (timeoutWakeup) {
                return true;
            }
        }
        return false;
    }

    /**
     * If Gerrit is a snapshot version.
     *
     * @return true if so, false otherwise.
     */
    @JavaScriptMethod
    public boolean isGerritSnapshotVersion() {
        if (gerritConnectionListener != null && gerritConnectionListener.isConnected()) {
            if (gerritConnectionListener.isSnapShotGerrit()) {
                return true;
            }
        }
        return false;
    }

    /**
     * If Gerrit Missed Events Playback is supported.
     *
     * @return true if so, false otherwise.
     */
    @JavaScriptMethod
    public boolean isGerritMissedEventsSupported() {
        if (gerritConnectionListener != null && gerritConnectionListener.isConnected()) {
            return missedEventsPlaybackManager.isSupported();
        }
        return false;
    }

    /**
     * If server with features disabled due to old Gerrit version.
     *
     * @return true if so, false otherwise.
     */
    @JavaScriptMethod
    public boolean hasDisabledFeatures() {
        if (gerritConnectionListener != null && gerritConnectionListener.isConnected()) {
            List<GerritVersionChecker.Feature> disabledFeatures = gerritConnectionListener.getDisabledFeatures();
            if (disabledFeatures != null && !disabledFeatures.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the list of disabled features.
     *
     * @return the list of disabled features or empty list if listener not found
     */
    public List<GerritVersionChecker.Feature> getDisabledFeatures() {
        if (gerritConnectionListener != null && gerritConnectionListener.isConnected()) {
            List<GerritVersionChecker.Feature> features = gerritConnectionListener.getDisabledFeatures();
            if (features != null) {
                return features;
            }
        }
        return new LinkedList<GerritVersionChecker.Feature>();
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
    @RequirePOST
    public void doRemoveConfirm(StaplerRequest req, StaplerResponse rsp) throws ServletException,
    IOException,
    InterruptedException {

        checkPermission();
        stopConnection();
        stop();
        PluginImpl plugin = PluginImpl.getInstance();
        removeGerritTriggerInJobs();
        if (plugin != null) {
            plugin.removeServer(this);
            plugin.save();
        }

        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null) {
            rsp.sendRedirect(jenkins.getRootUrl() + GerritManagement.get().getUrlName());
        }
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
        checkPermission();
        if (!value.equals(name)) {
            if (PluginImpl.containsServer_(value)) {
                return FormValidation.error("The server name " + value + " is already in use!");
            } else if (ANY_SERVER.equals(value)) {
                return FormValidation.error("Illegal name " + value + "!");
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
