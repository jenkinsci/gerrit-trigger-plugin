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

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritAdministrativeMonitor;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.model.Describable;
import hudson.model.Failure;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ManagementLink;
import hudson.model.Saveable;
import hudson.util.FormValidation;

import java.io.IOException;
import java.util.LinkedList;

import jenkins.model.Jenkins;


import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil.PLUGIN_IMAGES_URL;

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
         * Returns the list containing the GerritServer descriptor.
         *
         * @return the list of descriptors containing GerritServer's descriptor.
         */
        public static DescriptorExtensionList<GerritServer, GerritServer.DescriptorImpl> serverDescriptorList() {
            return Jenkins.getInstance()
                    .<GerritServer, GerritServer.DescriptorImpl>getDescriptorList(GerritServer.class);
        }

    }

    /**
    * Used when redirected to a server.
    * @param serverName the name of the server.
    * @return the GerritServer object.
    */
    public GerritServer getServer(String serverName) {
        return PluginImpl.getInstance().getServer(serverName);
    }

    /**
     * Add a new server.
     *
     * @param req the StaplerRequest
     * @param rsp the StaplerResponse
     * @throws IOException when error sending redirect back to the list of servers
     * @return the new GerritServer
     */
    public GerritServer doAddNewServer(StaplerRequest req, StaplerResponse rsp) throws IOException {
        String serverName = req.getParameter("name");
        if (PluginImpl.getInstance().containsServer(serverName)) {
            throw new Failure("A server already exists with the name '" + serverName + "'");
        }
        GerritServer server = new GerritServer(serverName);

        String mode = req.getParameter("mode");
        if (mode != null && mode.equals("copy")) { //"Copy Existing Server Configuration" has been chosen
            String from = req.getParameter("from");
            GerritServer fromServer = PluginImpl.getInstance().getServer(from);
            if (fromServer != null) {
                server.setConfig(fromServer.getConfig());
                PluginImpl.getInstance().addServer(server);
                server.start();
            } else {
                throw new Failure("Server '" + from + "' does not exist!");
            }
        } else {
            PluginImpl.getInstance().addServer(server);
        }
        rsp.sendRedirect("./server/" + serverName);
        return server;
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
     * Convenience method for jelly. Get the list of Gerrit server names.
     *
     * @return the list of server names as a list.
     */
    public LinkedList<String> getServerNames() {
        return PluginImpl.getInstance().getServerNames();
    }

    /**
     * Checks whether server name already exists.
     * @param value the value of the name field.
     * @return ok or error.
     */
    public FormValidation doNameFreeCheck(
            @QueryParameter("value")
            final String value) {
        if (PluginImpl.getInstance().containsServer(value)) {
            return FormValidation.error("The server name " + value + " is already in use!");
        } else {
            return FormValidation.ok();
        }
    }
}
