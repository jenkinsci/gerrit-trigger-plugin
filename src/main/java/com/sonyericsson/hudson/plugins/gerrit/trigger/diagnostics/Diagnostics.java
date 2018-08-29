/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 CloudBees Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.manual.ManualTriggerAction;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritChangeKind;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Account;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.PatchSet;
import hudson.Main;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithChildren;
import jenkins.model.ModelObjectWithContextMenu;
import org.acegisecurity.Authentication;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Sub page on {@link com.sonyericsson.hudson.plugins.gerrit.trigger.GerritManagement} containing some diagnostic views.
 */
public class Diagnostics implements ModelObjectWithChildren, ModelObjectWithContextMenu {

    @Override
    public ContextMenu doChildrenContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        return getContextMenu(null);
    }

    //CS IGNORE LineLength FOR NEXT 8 LINES. REASON: Javadoc
    /**
     * Helper method to produce the breadcrumb context menu.
     *
     * @param context the url prefix to put on all urls.
     * @return the selectable reports.
     * @see #doChildrenContextMenu(StaplerRequest, StaplerResponse)
     * @see com.sonyericsson.hudson.plugins.gerrit.trigger.GerritManagement#doContextMenu(StaplerRequest, StaplerResponse)
     */
    @Restricted(NoExternalUse.class)
    public ContextMenu getContextMenu(String context) {
        ContextMenu menu = new ContextMenu();
        String url = makeRelativeUrl(context, "buildMemory");
        menu.add(new MenuItem()
                         .withUrl(url)
                         .withStockIcon("clipboard.png")
                         .withDisplayName(Messages.BuildMemoryReport_DisplayName()));
        url = makeRelativeUrl(context, "eventListeners");
        menu.add(new MenuItem()
                         .withUrl(url)
                         .withStockIcon("clipboard.png")
                         .withDisplayName(Messages.EventListenersReport_DisplayName()));
        if (isDebugMode()) {
            menu.add("triggerDebugEvent", "warning.png", "Trigger Debug", false, true);
        }
        return menu;
    }

    /**
     * Construct an url with a potential context for {@link #getContextMenu(String)}.
     * @param context potential string to prefix with
     * @param name the url
     * @return an url, relative or not.
     */
    private String makeRelativeUrl(String context, String name) {
        StringBuilder url = new StringBuilder(name);
        if (!StringUtils.isBlank(context)) {
            if (!context.endsWith("/")) {
                url.insert(0, '/');
            }
            url.insert(0, context);
        }
        return url.toString();
    }

    /**
     * The Jenkins permission required to view the diagnostic reports.
     *
     * @return the permission
     */
    @Nonnull
    public static Permission getRequiredPermission() {
        return Jenkins.ADMINISTER;
    }

    @Override
    public String getDisplayName() {
        return Messages.GerritManagement_Diagnostics_DisplayName();
    }

    /**
     * A report of the currently coordinated builds.
     *
     * @return the build memory coordination report.
     * @see com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory#report()
     */
    @CheckForNull
    public BuildMemoryReport getBuildMemory() {
        ToGerritRunListener instance = ToGerritRunListener.getInstance();
        if (instance != null) {
            return instance.report();
        }
        return null;
    }

    /**
     * A report of all registered {@link com.sonymobile.tools.gerrit.gerritevents.GerritEventListener}s
     * in the system.
     *
     * Intended to be accessed via Stapler URL mapping.
     *
     * @return the listeners report.
     */
    @CheckForNull
    public EventListenersReport getEventListeners() {
        return EventListenersReport.report();
    }

    @Override
    public ContextMenu doContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        return getContextMenu(null);
    }

    //CS IGNORE MagicNumber FOR NEXT 56 LINES. REASON: Test data.
    /**
     * Triggers a {@link ManualPatchsetCreated} event with some random data for debug purposes in a dev environment.
     *
     * @param request stapler
     * @param response stapler
     * @throws IOException if so
     * @see #isDebugMode()
     */
    public void doTriggerDebugEvent(StaplerRequest request, StaplerResponse response) throws IOException {
        if (!isDebugMode()) {
            throw new IllegalStateException("Can only be done in a dev environment!");
        }
        List<GerritServer> servers = PluginImpl.getServers_();
        if (servers.isEmpty()) {
            throw new IllegalStateException("Need at least one server configured!");
        }
        GerritServer srv;
        if (servers.size() == 1) {
            srv = servers.get(0);
        } else {
            srv = servers.get(RND.nextInt(servers.size()));
        }
        //Todo maybe some configuration from the request?
        ManualPatchsetCreated event = new ManualPatchsetCreated();
        Authentication authentication = Jenkins.getAuthentication();
        event.setUserName(authentication.getName());
        event.setAccount(new Account(authentication.getName(), authentication.getName() + "@example.com"));
        Change change = new Change();
        change.setOwner(event.getAccount());
        change.setCommitMessage("Debug Commit Message");
        change.setSubject("Debug Subject");
        change.setBranch("master");
        change.setCreatedOn(new Date());
        change.setId("I" + UUID.randomUUID().toString().replace("-", ""));
        change.setLastUpdated(new Date());
        change.setProject("debug/project");
        change.setTopic("debug");
        change.setNumber(String.valueOf(RND.nextInt(10001)));
        change.setUrl("http://gerrit/" + change.getNumber());
        event.setChange(change);
        PatchSet patchSet = new PatchSet();
        patchSet.setUploader(event.getAccount());
        patchSet.setNumber(String.valueOf(RND.nextInt(32)));
        patchSet.setCreatedOn(new Date());
        patchSet.setDraft(false);
        patchSet.setKind(GerritChangeKind.REWORK);
        patchSet.setRevision(UUID.randomUUID().toString().replace("-", ""));
        patchSet.setRef("refs/changes/" + change.getNumber() + "/" + patchSet.getNumber()); //Hopefully close enough
        event.setPatchset(patchSet);
        event.setEventCreatedOn(String.valueOf(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())));
        event.setReceivedOn(System.currentTimeMillis());
        event.setProvider(ManualTriggerAction.createProvider(srv));
        srv.triggerEvent(event);

        response.sendRedirect2(request.getReferer());
    }

    /**
     * Helping {@link #doTriggerDebugEvent(StaplerRequest, StaplerResponse)}.
     */
    private static final Random RND = new Random();

    /**
     * Checks if the plugin is running in a development environment.
     *
     * @return true if debug
     */
    @Restricted(NoExternalUse.class)
    public boolean isDebugMode() {
        if (Main.isDevelopmentMode || Main.isUnitTest) {
            return true;
        } else {
            return System.getProperty("hudson.hpi.run") != null;
        }
    }
}
