/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2013 Sony Mobile Communications AB. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions;

import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggeredItemEntity;
import hudson.model.Action;
import java.io.IOException;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import static com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil.getPluginImageUrl;

/**
 * Action that retriggers one build with the same event parameters as the build this trigger is in.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class RetriggerAction implements Action {

    private TriggerContext context;

    /**
     * Standard Constructor.
     * @param context the original GerritCause's context.
     */
    public RetriggerAction(TriggerContext context) {
        this.context = context;
    }

    /**
     * Default constructor.
     * <strong>Only use this if you are a serializer.</strong>
     */
    public RetriggerAction() {
    }

    @Override
    public String getIconFileName() {
        if (!hasPermission() || isBuilding()) {
            return null;
        } else {
            return getPluginImageUrl("icon_retrigger24.png");
        }
    }

    @Override
    public String getDisplayName() {
        if (!hasPermission() || isBuilding()) {
            return null;
        } else {
            return Messages.Retrigger();
        }
    }

    @Override
    public String getUrlName() {
        if (!hasPermission() || isBuilding()) {
            return null;
        } else {
            return "gerrit-trigger-retrigger-this";
        }
    }

    /**
     * Checks the current "memory" if the project is currently building this event.
     * @return true if so.
     */
    private boolean isBuilding() {
        if (context == null || context.getThisBuild() == null || context.getEvent() == null) {
            return false;
        } else {
            ToGerritRunListener listener = ToGerritRunListener.getInstance();
            if (listener == null) {
                return context.getThisBuild().getBuild().isBuilding();
            }
            return context.getThisBuild().getBuild().isBuilding()
                    || listener.isBuilding(context.getThisBuild().getProject(), context.getEvent());
        }
    }

    /**
     * checks if the current user has permission to build/retrigger the project.
     * @return true if so.
     */
    private boolean hasPermission() {
        if (context == null || context.getThisBuild() == null || context.getThisBuild().getProject() == null) {
            return false;
        } else {
            return context.getThisBuild().getProject().hasPermission(PluginImpl.RETRIGGER);
        }
    }

    /**
     * Handles the request to re-trigger and redirects back to the page that called.
     * @param request StaplerRequest the request.
     * @param response StaplerResponse the response handler.
     * @throws IOException in case of Stapler issues
     */
    public void doIndex(StaplerRequest request, StaplerResponse response) throws IOException {

        if (context == null || context.getThisBuild() == null) {
            return;
        }

        if (!hasPermission()) {
            //TODO Access denied message to user?
            return;
        }

        if (isBuilding()) {
            //TODO show error to user?
            return;
        }

        TriggeredItemEntity entity = context.getThisBuild();
        GerritTrigger trigger = GerritTrigger.getTrigger(entity.getProject());
        if (trigger == null) {
            //TODO show config error to user?
            return;
        }

        trigger.retriggerThisBuild(context);
        response.sendRedirect2(entity.getProject().getAbsoluteUrl());
    }
}
