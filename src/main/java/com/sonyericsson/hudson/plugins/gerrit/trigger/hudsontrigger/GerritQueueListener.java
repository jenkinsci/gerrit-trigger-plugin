package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.Cause;
import hudson.model.Job;
import hudson.model.Queue.LeftItem;
import hudson.model.queue.QueueListener;
import hudson.util.LogTaskListener;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;

/**
 * Listens to delete events in the Jenkins Queue to clean up the BuildMemory.
 */
@Extension
public class GerritQueueListener extends QueueListener {

    private static final Logger logger = Logger.getLogger(GerritQueueListener.class.getName());

    @Override
    public void onLeft(LeftItem item) {
        if (item.isCancelled() && item.task instanceof Job) {
            for (Cause cause : item.getCauses()) {
                if (cause instanceof GerritCause gerritCause && !gerritCause.isSilentMode()) {
                    GerritTriggeredEvent event = gerritCause.getEvent();
                    ToGerritRunListener runListener = ToGerritRunListener.getInstance();
                    runListener.setQueueCancelled((Job)item.task, event);
                    TaskListener taskListener = new LogTaskListener(logger, Level.WARNING);
                    runListener.allBuildsCompleted(event, gerritCause, taskListener);
                }
            }
        }
    }

}
