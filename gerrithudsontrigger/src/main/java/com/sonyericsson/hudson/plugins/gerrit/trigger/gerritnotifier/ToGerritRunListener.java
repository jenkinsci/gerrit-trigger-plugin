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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier;

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.PatchSetKey;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.util.List;

/**
 * The Big RunListener in charge of coordinating build results and reporting back to Gerrit.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@Extension
public class ToGerritRunListener extends RunListener<AbstractBuild> {

    private static ToGerritRunListener instance;
    private transient BuildMemory memory;
    private transient GerritNotifier notifier;

    /**
     * Default Constructor.
     */
    public ToGerritRunListener() {
        super(AbstractBuild.class);
        memory = new BuildMemory();
        notifier = new GerritNotifier(Config.get(), new GerritSSHCmdRunner(Config.get()));
    }

    /**
     * Returns the registered instance of this class from the list of all listeners.
     * @return the instance.
     */
    public static ToGerritRunListener getInstance() {
        if (instance == null) {
            for (RunListener listener : all()) {
                if (listener instanceof ToGerritRunListener) {
                    instance = (ToGerritRunListener) listener;
                    break;
                }
            }
        }
        return instance;
    }

    @Override
    public void onCompleted(AbstractBuild r, TaskListener listener) {
        GerritCause cause = getCause(r);
        if (cause != null) {
            PatchsetCreated event = cause.getEvent();
            PatchSetKey key = memory.completed(event, r);
            if (memory.isAllBuildsCompleted(key)) {
                notifier.buildCompleted(memory.getMemoryImprint(key), listener);
                memory.forget(key);
            }
        }
    }

    @Override
    public void onStarted(AbstractBuild r, TaskListener listener) {
        GerritCause cause = getCause(r);
        if (cause != null) {
            PatchSetKey key = memory.started(cause.getEvent(), r);
            BuildsStartedStats stats = memory.getBuildsStartedStats(key);
            notifier.buildStarted(r, listener, cause.getEvent(), stats);
        }
    }

    /**
     * Called just before a build is beeing scheduled by the trigger.
     * @param project the project that will be built.
     * @param event the event that caused the build to be scheduled.
     */
    public void onTriggered(AbstractProject project, PatchsetCreated event) {
        //TODO stop builds for earlier patch-sets on same change.
        memory.triggered(event, project);
    }

    /**
     * Finds the GerritCause for a build if there is one.
     * @param build the build to look in.
     * @return the GerritCause or null if there is none.
     */
    private GerritCause getCause(AbstractBuild build) {
        List<Cause> causes = build.getCauses();
        for (Cause c : causes) {
            if (c instanceof GerritCause) {
                return (GerritCause) c;
            }
        }
        return null;
    }
}
