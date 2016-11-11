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

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.PluginConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.Diagnostics;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritAdministrativeMonitor;
import com.sonymobile.tools.gerrit.gerritevents.GerritSendCommandQueue;
import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.Functions;
import hudson.model.AdministrativeMonitor;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Failure;
import hudson.model.Hudson;
import hudson.model.ManagementLink;
import hudson.model.Saveable;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithContextMenu;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.CharEncoding;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;

import static com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil.PLUGIN_IMAGES_URL;

/**
 * Management link for configuring the global configuration of this trigger.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@Extension
public class GerritManagement extends ManagementLink implements StaplerProxy, Describable<GerritManagement>,
        Saveable, ModelObjectWithContextMenu {

    /**
     * The relative url name for this management link.
     * As returned by {@link #getUrlName()}.
     */
    public static final String URL_NAME = "gerrit-trigger";

    private static final Logger logger = LoggerFactory.getLogger(GerritManagement.class);

    private static final Diagnostics DIAGNOSTICS = new Diagnostics();


    @Override
    public String getIconFileName() {
        return PLUGIN_IMAGES_URL + "icon.png";
    }

    @Override
    public String getUrlName() {
        return URL_NAME;
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

    @Override
    public ContextMenu doContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        Jenkins jenkins = Jenkins.getInstance();
        assert jenkins != null;
        ContextMenu menu = new ContextMenu();
        menu.add("newServer", Functions.joinPath(jenkins.getRootUrl(), Functions.getResourcePath(),
                                                 "images", "24x24", "new-package.png"), Messages.AddNewServer());
        for (GerritServer server : getServers()) {
            menu.add(server);
        }
        MenuItem item = new MenuItem()
                .withUrl("diagnostics")
                .withDisplayName(DIAGNOSTICS)
                .withStockIcon("folder.png");
        item.subMenu = DIAGNOSTICS.getContextMenu("diagnostics");
        menu.add(item);
        return menu;
    }

    /**
     * A sub page displaying some diagnostic views.
     *
     * @return the diagnostics page.
     */
    public Diagnostics getDiagnostics() {
        return DIAGNOSTICS;
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
            Jenkins jenkins = Jenkins.getInstance();
            assert jenkins != null;
            return jenkins
                    .<GerritServer, GerritServer.DescriptorImpl>getDescriptorList(GerritServer.class);
        }

        /**
         * Auto-completion for the "copy from" field in the new server page.
         *
         * @param value the value that the user has typed in the textbox.
         * @return the list of server names, depending on the current value in the textbox.
         */
        public AutoCompletionCandidates doAutoCompleteCopyNewItemFrom(@QueryParameter final String value) {
            final AutoCompletionCandidates r = new AutoCompletionCandidates();

            PluginImpl plugin = PluginImpl.getInstance();
            if (plugin != null) {
                for (String s : plugin.getServerNames()) {
                    if (s.startsWith(value)) {
                        r.add(s);
                    }
                }
            }
            return r;
        }
    }

    /**
     * Gets the list of GerritServer.
     *
     * @return the list of GerritServer.
     */
    @SuppressWarnings("unused") //Called from Jelly
    public List<GerritServer> getServers() {
        PluginImpl plugin = PluginImpl.getInstance();
        if (plugin == null) {
            return Collections.emptyList();
        }
        return plugin.getServers();
    }

    /**
     * Used when redirected to a server.
     *
     * @param encodedServerName the server name encoded by URLEncoder.encode(name,"UTF-8").
     * @return the GerritServer object.
     */
    @SuppressWarnings("unused") //Called from Jelly
    public GerritServer getServer(String encodedServerName) {
        String serverName;
        try {
            serverName = URLDecoder.decode(encodedServerName, CharEncoding.UTF_8);
        } catch (Exception ex) {
            serverName = URLDecoder.decode(encodedServerName);
        }
        PluginImpl plugin = PluginImpl.getInstance();
        if (plugin == null) {
            return null;
        }
        return plugin.getServer(serverName);
    }

    /**
     * Used when getting server status from JavaScript.
     *
     * @return the json array of server status.
     */
    @JavaScriptMethod
    public JSONObject getServerStatuses() {
        JSONObject root = new JSONObject();
        JSONArray array = new JSONArray();
        JSONObject obj;

        for (GerritServer server : PluginImpl.getServers_()) {
            String status;
            if (server.isConnected()) {
                status = "up";
            } else {
                status = "down";
            }

            obj = new JSONObject();
            obj.put("name", server.getName());
            obj.put("frontEndUrl", server.getConfig().getGerritFrontEndUrl());
            obj.put("serverUrl", server.getUrlName());
            obj.put("version", server.getGerritVersion());
            obj.put("status", status);
            obj.put("hasErrors", server.hasErrors());
            obj.put("hasWarnings", server.hasWarnings());
            array.add(obj);
        }

        root.put("servers", array);
        return root;
    }

    /**
     * Add a new server.
     *
     * @param req the StaplerRequest
     * @param rsp the StaplerResponse
     * @return the new GerritServer
     * @throws IOException when error sending redirect back to the list of servers
     */
    public GerritServer doAddNewServer(StaplerRequest req, StaplerResponse rsp) throws IOException {
        String serverName = req.getParameter("name");
        PluginImpl plugin = PluginImpl.getInstance();
        assert plugin != null;
        if (plugin.containsServer(serverName)) {
            throw new Failure("A server already exists with the name '" + serverName + "'");
        } else if (GerritServer.ANY_SERVER.equals(serverName)) {
            throw new Failure("Illegal server name '" + serverName + "'");
        }
        GerritServer server = new GerritServer(serverName);
        PluginConfig pluginConfig = plugin.getPluginConfig();
        server.getConfig().setNumberOfSendingWorkerThreads(pluginConfig.getNumberOfSendingWorkerThreads());

        String mode = req.getParameter("mode");
        if (mode != null && mode.equals("copy")) { //"Copy Existing Server Configuration" has been chosen
            String from = req.getParameter("from");
            GerritServer fromServer = plugin.getServer(from);
            if (fromServer != null) {
                server.setConfig(new Config(fromServer.getConfig()));
                plugin.addServer(server);
                server.start();
            } else {
                throw new Failure("Server '" + from + "' does not exist!");
            }
        } else {
            plugin.addServer(server);
            server.start();
        }
        plugin.save();

        rsp.sendRedirect("./server/" + URLEncoder.encode(serverName, CharEncoding.UTF_8));
        return server;
    }

    @Override
    public Object getTarget() {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            throw new IllegalStateException("Jenkins is not alive.");
        }
        jenkins.checkPermission(Jenkins.ADMINISTER);
        return this;
    }

    @Override
    public void save() throws IOException {
        logger.debug("SAVE!!!");
    }

    /**
     * Returns this singleton.
     *
     * @return the single loaded instance if this class.
     */
    public static GerritManagement get() {
        return ManagementLink.all().get(GerritManagement.class);
    }

    /**
     * Get the plugin config.
     *
     * @return the plugin config.
     */
    @CheckForNull
    public static PluginConfig getPluginConfig() {
        PluginImpl plugin = PluginImpl.getInstance();
        if (plugin == null) {
            return null;
        }
        return plugin.getPluginConfig();
    }

    /**
     * Get the config of a server.
     *
     * @param serverName the name of the server for which we want to get the config.
     * @return the config.
     * @see GerritServer#getConfig()
     */
    public static IGerritHudsonTriggerConfig getConfig(String serverName) {
        GerritServer server = PluginImpl.getServer_(serverName);
        if (server != null) {
            return server.getConfig();
        } else {
            logger.error("Could not find the Gerrit Server: {}", serverName);
            return null;
        }
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
    public List<String> getServerNames() {
        PluginImpl plugin = PluginImpl.getInstance();
        if (plugin == null) {
            return Collections.emptyList();
        }
        return plugin.getServerNames();
    }

    /**
     * Checks whether server name already exists.
     *
     * @param value the value of the name field.
     * @return ok or error.
     */
    public FormValidation doNameFreeCheck(@QueryParameter("value") final String value) {
        PluginImpl plugin = PluginImpl.getInstance();
        assert plugin != null;
        if (plugin.containsServer(value)) {
            return FormValidation.error("The server name " + value + " is already in use!");
        } else if (GerritServer.ANY_SERVER.equals(value)) {
            return FormValidation.error("Illegal name " + value + "!");
        } else {
            return FormValidation.ok();
        }
    }

    /**
     * Saves the form to the configuration and disk.
     *
     * @param req StaplerRequest
     * @param rsp StaplerResponse
     * @throws ServletException     if something unfortunate happens.
     * @throws IOException          if something unfortunate happens.
     * @throws InterruptedException if something unfortunate happens.
     */
    public void doConfigSubmit(StaplerRequest req, StaplerResponse rsp) throws ServletException,
            IOException,
            InterruptedException {
        if (logger.isDebugEnabled()) {
            logger.debug("submit {}", req.toString());
        }
        JSONObject form = req.getSubmittedForm();
        PluginConfig pluginConfig = PluginImpl.getPluginConfig_();
        if (pluginConfig != null) {
            pluginConfig.setValues(form);
            PluginImpl.save_();
            GerritSendCommandQueue.configure(pluginConfig);
        }
        //TODO reconfigure the incoming worker threads as well

        rsp.sendRedirect(".");
    }

}
