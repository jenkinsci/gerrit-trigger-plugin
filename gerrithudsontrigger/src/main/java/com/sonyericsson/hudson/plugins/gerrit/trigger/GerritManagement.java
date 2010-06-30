/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
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

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshAuthenticationException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshUtil;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ManagementLink;
import hudson.model.Saveable;
import hudson.util.FormValidation;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Management link for configuring the global configuration of this trigger.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@Extension
public class GerritManagement extends ManagementLink implements StaplerProxy, Describable<GerritManagement>, Saveable {

    private static final Logger logger = LoggerFactory.getLogger(GerritManagement.class);

    @Override
    public String getIconFileName() {
        return "/plugin/gerrit-trigger/images/icon.png";
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
         * @param gerritUserName the username
         * @param gerritAuthKeyFile the private key file
         * @param gerritAuthKeyFilePassword the password for the keyfile or null if there is none.
         * @return {@link FormValidation#ok() } if can be done,
         *         {@link FormValidation#error(java.lang.String) } otherwise.
         */
        public FormValidation doTestConnection(
                @QueryParameter("gerritHostName") final String gerritHostName,
                @QueryParameter("gerritSshPort") final int gerritSshPort,
                @QueryParameter("gerritUserName") final String gerritUserName,
                @QueryParameter("gerritAuthKeyFile") final String gerritAuthKeyFile,
                @QueryParameter("gerritAuthKeyFilePassword") final String gerritAuthKeyFilePassword) {
            if (logger.isDebugEnabled()) {
                logger.debug("gerritHostName = {}\n"
                        + "gerritSshPort = {}\n"
                        + "gerritUserName = {}\n"
                        + "gerritAuthKeyFile = {}\n"
                        + "gerritAuthKeyFilePassword = {}",
                        new Object[]{gerritHostName,
                            gerritSshPort,
                            gerritUserName,
                            gerritAuthKeyFile,
                            gerritAuthKeyFilePassword, });
            }

            File file = new File(gerritAuthKeyFile);
            String password = null;
            if (gerritAuthKeyFilePassword != null && gerritAuthKeyFilePassword.length() > 0) {
                password = gerritAuthKeyFilePassword;
            }
            if (SshUtil.checkPassPhrase(file, password)) {
                if (file.exists() && file.isFile()) {
                    try {
                        SshConnection sshConnection = new SshConnection(gerritHostName, gerritSshPort,
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
     * Reads a list of gerrit projects from a Reader, one project per line. As from command: gerrit ls-projects.
     * @param commandReader the Reader.
     * @throws IOException if something unfortunate happens.
     */
    private static void readProjects(Reader commandReader) throws IOException {
        List<String> projects = new ArrayList<String>();
        BufferedReader br = new BufferedReader(commandReader);
        String line = br.readLine();

        while (line != null) {
            projects.add(line);
            line = br.readLine();
        }
        if (PluginImpl.getInstance() != null && PluginImpl.getInstance().getConfig() != null) {
            PluginImpl.getInstance().getConfig().setGerritProjects(projects);
        }
    }
}
