package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import hudson.Extension;
import hudson.model.Cause;
import hudson.model.Job;
import hudson.model.Queue.LeftItem;
import hudson.model.queue.QueueListener;

import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;

/**
 * Listens to delete events in the Jenkins Queue to clean up the BuildMemory.
 */
@Extension
public class GerritQueueListener extends QueueListener {

    @Override
    public void onLeft(LeftItem item) {
        if (item.isCancelled() && item.task instanceof Job) {
            for (Cause cause : item.getCauses()) {
                if (cause instanceof GerritCause && !((GerritCause)cause).isSilentMode()) {
                    GerritCause gerritCause = (GerritCause)cause;
                    GerritTriggeredEvent event = gerritCause.getEvent();
                    ToGerritRunListener runListener = ToGerritRunListener.getInstance();
                    runListener.setQueueCancelled((Job)item.task, event);
                }
            }
        }
    }

}
