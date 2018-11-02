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

import static com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.BuildMemoryReport.TS_FORMAT;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.BuildMemoryReport;
import com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.Diagnostics;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.security.Permission;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Support Core Component writing the {@link BuildMemoryReport}.
 *
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 */
@Extension(optional = true)
public class BuildMemoryComponent extends Component {
    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Diagnostics.getRequiredPermission());
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Gerrit: " + Messages.BuildMemoryReport_DisplayName();
    }

    @Override
    public boolean isSelectedByDefault() {
        return false;
    }

    @Override
    public void addContents(@NonNull Container container) {
        container.add(new PrintedContent("gerrit/buildmemory.md") {
            @Override
            protected void printTo(PrintWriter out) throws IOException {
                ToGerritRunListener toGerritRunListener = ToGerritRunListener.getInstance();
                if (toGerritRunListener == null) {
                    //logging is performed by getInstance() above
                    return;
                }
                BuildMemoryReport report = toGerritRunListener.report();
                out.println("#" + getDisplayName());
                out.println();

                List<Map.Entry<GerritTriggeredEvent, List<BuildMemory.MemoryImprint.Entry>>> entries =
                        report.getSortedEntrySet();

                for (Map.Entry<GerritTriggeredEvent, List<BuildMemory.MemoryImprint.Entry>> entry : entries) {
                    out.print("###");
                    out.println(report.getDisplayNameFor(entry.getKey()));

                    for (BuildMemory.MemoryImprint.Entry en : entry.getValue()) {
                        Job job = en.getProject();
                        Run run = en.getBuild();
                        String name = "<unknown>";
                        if (job != null) {
                            name = job.getFullName(); //Or should it be fullDisplayName?
                        }
                        String number = "";
                        if (run != null) {
                            number = run.getDisplayName();
                        }
                        String result = "";
                        if (run != null && run.getResult() != null) {
                            result = run.getResult().toString();
                        }
                        String startedAt = "";
                        if (en.getStartedTimestamp() != null) {
                            startedAt = TS_FORMAT.get().format(new Date(en.getStartedTimestamp()));
                        }
                        String completedAt = "";
                        if (en.getCompletedTimestamp() != null) {
                            completedAt = TS_FORMAT.get().format(new Date(en.getCompletedTimestamp()));
                        }

                        out.println(" * Job: " + name);
                        out.println("    - Run: " + number);
                        out.println("    - Completed: " + en.isBuildCompleted());
                        out.println("    - Cancelled: " + en.isCancelled());
                        out.println("    - Result: " + result);
                        out.println("    - Triggered@ " + TS_FORMAT.get().format(en.getTriggeredTimestamp()));
                        out.println("    - Started@ " + startedAt);
                        out.println("    - Completed@ " + completedAt);
                    }
                }
            }
        });
    }
}
