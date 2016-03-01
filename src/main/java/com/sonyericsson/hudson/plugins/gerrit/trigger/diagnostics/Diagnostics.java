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

import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import jenkins.model.ModelObjectWithChildren;
import jenkins.model.ModelObjectWithContextMenu;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.CheckForNull;

/**
 * Sub page on {@link com.sonyericsson.hudson.plugins.gerrit.trigger.GerritManagement} containing some diagnostic views.
 */
public class Diagnostics implements ModelObjectWithChildren, ModelObjectWithContextMenu {

    @Override
    public ContextMenu doChildrenContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        return getContextMenu(null);
    }

    /**
     * Helper method to produce the breadcrumb context menu.
     *
     * @param context the url prefix to put on all urls.
     * @return the selectable reports.
     * @see #doChildrenContextMenu(StaplerRequest, StaplerResponse)
     * @see GerritManagement#doContextMenu(StaplerRequest, StaplerResponse)
     */
    @Restricted(NoExternalUse.class)
    public ContextMenu getContextMenu(String context) {
        ContextMenu menu = new ContextMenu();
        StringBuilder url = new StringBuilder("buildMemory");
        if (!StringUtils.isBlank(context)) {
            if (!context.endsWith("/")) {
                url.insert(0, '/');
            }
            url.insert(0, context);
        }
        menu.add(new MenuItem()
                         .withUrl(url.toString())
                         .withStockIcon("clipboard.png")
                         .withDisplayName(Messages.BuildMemoryReport_DisplayName()));
        url = new StringBuilder("eventListeners");
        if (!StringUtils.isBlank(context)) {
            if (!context.endsWith("/")) {
                url.insert(0, '/');
            }
            url.insert(0, context);
        }
        menu.add(new MenuItem()
                         .withUrl(url.toString())
                         .withStockIcon("clipboard.png")
                         .withDisplayName(Messages.EventListenersReport_DisplayName()));
        return menu;
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
    @SuppressWarnings("unused")
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
    @SuppressWarnings("unused")
    public EventListenersReport getEventListeners() {
        return EventListenersReport.report();
    }

    @Override
    public ContextMenu doContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
        return getContextMenu(null);
    }
}
