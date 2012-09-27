package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.job;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.cmd.AbstractSendCommandJob;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.GerritNotifier;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.NotificationFactory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import hudson.model.TaskListener;

import java.util.Set;

/**
 * A send-command-job that sends the add reviewers command.
 *
 * @author Emanuele Zattin
 */
public class AddReviewersCommandJob extends AbstractSendCommandJob {

    private BuildMemory.MemoryImprint memoryImprint;
    private TaskListener listener;

    public AddReviewersCommandJob(IGerritHudsonTriggerConfig config,
                                  BuildMemory.MemoryImprint memoryImprint,
                                  TaskListener listener) {
        super(config);
        this.memoryImprint = memoryImprint;
        this.listener = listener;
    }

    @Override
    public void run() {
        Set<String> reviewers = FindReviewersHelper.perform(memoryImprint);
        GerritNotifier notifier = NotificationFactory.getInstance()
                .createGerritNotifier((IGerritHudsonTriggerConfig)getConfig(), this);
        notifier.addReviewers(memoryImprint, listener, reviewers);
    }
}
