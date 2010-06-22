/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.PatchSetKey;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Big RunListener in charge of coordinating build results and reporting back to Gerrit.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@Extension
public class ToGerritRunListener extends RunListener<AbstractBuild> {

    private static final Logger logger = LoggerFactory.getLogger(ToGerritRunListener.class);

    private static ToGerritRunListener instance;
    private transient BuildMemory memory;

    /**
     * Default Constructor.
     */
    public ToGerritRunListener() {
        super(AbstractBuild.class);
        memory = new BuildMemory();
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
        logger.info("Completed. Build: {} Cause: {}", r, cause);
        if (cause != null) {
            PatchsetCreated event = cause.getEvent();
            PatchSetKey key = memory.completed(event, r);
            if (memory.isAllBuildsCompleted(key)) {
                logger.info("All Builds are completed for cause: {}", cause);
                createNotifier().buildCompleted(memory.getMemoryImprint(key), listener);
                memory.forget(key);
            } else {
                logger.info("Waiting for more builds to complete for cause [{}]. Status: \n{}",
                        cause, memory.getStatusReport(key));
            }
        }
    }

    @Override
    public void onStarted(AbstractBuild r, TaskListener listener) {
        GerritCause cause = getCause(r);
        if (cause != null) {
            PatchSetKey key = memory.started(cause.getEvent(), r);
            BuildsStartedStats stats = memory.getBuildsStartedStats(key);
            createNotifier().buildStarted(r, listener, cause.getEvent(), stats);
            logger.info("Gerrit build [{}] Started for cause: [{}].", r, cause);
            logger.info("MemoryStatus:\n{}", memory.getStatusReport(key));
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

        //Logging
        String name = null;
        if (project != null) {
           name = project.getName();
        }
        logger.info("Project [{}] triggered by Gerrit: [{}]", name, event);
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

    /**
     * Load a new notifier to get the possible new settings.
     * @return a GerritNotifierObject with fresh config.
     * @fixfor HUDSON-6814
     */
    private static GerritNotifier createNotifier() {
        if(PluginImpl.getInstance() == null) {
            //If this happens we are sincerely screwed anyways.
            throw new IllegalStateException("PluginImpl has not been loaded yet!");
        }
        IGerritHudsonTriggerConfig config = PluginImpl.getInstance().getConfig();
        return new GerritNotifier(config, new GerritSSHCmdRunner(config));
    }
}
