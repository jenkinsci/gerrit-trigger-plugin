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

package com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.support;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.Diagnostics;
import com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.EventListenersReport;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.EventListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginGerritEvent;
import com.sonymobile.tools.gerrit.gerritevents.GerritEventListener;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.security.Permission;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Support Core Component writing the {@link EventListenersReport}.
 *
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 */
@Extension(optional = true)
public class EventListenersComponent extends Component {

    /**
     * The max number of interesting Project or branch patterns to list for a job.
     */
    private static final int MAX_PROJECT_BRANCH_LIST_LENGTH = 5;

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Diagnostics.getRequiredPermission());
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Gerrit: " + Messages.EventListenersReport_DisplayName();
    }

    @Override
    public boolean isSelectedByDefault() {
        return false;
    }

    @Override
    public void addContents(@NonNull Container container) {
        container.add(new PrintedContent("gerrit/event-listeners.md") {
            @Override
            protected void printTo(PrintWriter out) throws IOException {
                out.println("#" + getDisplayName());
                out.println();
                EventListenersReport report = EventListenersReport.report();
                if (report == null) {
                    out.println("No report could be generated, see the system log for indications.");
                    return;
                }
                out.println("##Jobs");
                for (EventListener listener : report.getJobs()) {
                    Job job = listener.findJob();
                    GerritTrigger trigger = listener.getTrigger();
                    if (job != null) {
                        out.format(" * __Job: %s _(%s)___\n", job.getFullDisplayName(), listener.getJob());
                    } else {
                        out.format(" * __Job: _(%s)___\n", listener.getJob());
                    }
                    if (trigger != null) {
                        out.println("    - Trigger on: ");
                        List<GerritProject> projects = trigger.getGerritProjects();
                        for (int i = 0; i < Math.min(MAX_PROJECT_BRANCH_LIST_LENGTH, projects.size()); i++) {
                            GerritProject pr = projects.get(i);
                            out.format("        * _%s_: %s\n", pr.getCompareType().getDisplayName(), pr.getPattern());
                            List<Branch> branches = pr.getBranches();
                            for (int j = 0; j < Math.min(MAX_PROJECT_BRANCH_LIST_LENGTH, branches.size()); j++) {
                                Branch branch = branches.get(0);
                                out.format("            - _%s_: %s\n",
                                           branch.getCompareType().getDisplayName(), branch.getPattern());
                            }
                            if (branches.size() >= MAX_PROJECT_BRANCH_LIST_LENGTH) {
                                out.println("            - ...");
                            }
                        }
                        if (projects.size() >= MAX_PROJECT_BRANCH_LIST_LENGTH) {
                            out.println("        * ...");
                        }
                        out.println(("    - Server: " + trigger.getServerName()).replace("_", "\\_"));
                        out.println("    - Silent Mode: " + trigger.isSilentMode());
                        out.println("    - Silent Start: " + trigger.isSilentStartMode());
                        out.println("    - Trigger Types: ");
                        for (PluginGerritEvent event : trigger.getTriggerOnEvents()) {
                            Descriptor<PluginGerritEvent> descriptor = event.getDescriptor();
                            if (descriptor != null) {
                                out.println("        * " + descriptor.getDisplayName());
                            } else {
                                out.println("        * <unknown>");
                            }
                        }
                    }
                }
                out.println();
                out.println("##Others");
                for (GerritEventListener listener : report.getOthers()) {
                    out.println(" * " + report.getName(listener));
                }
            }
        });
    }
}
