package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.rest.job;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.GerritMessageProvider;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.rest.object.CommentedFile;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.rest.object.ReviewInput;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.rest.object.ReviewLabel;
import hudson.model.TaskListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BuildCompletedRestCommandJob extends AbstractRestCommandJob {

    private final BuildMemory.MemoryImprint memoryImprint;

    public BuildCompletedRestCommandJob(IGerritHudsonTriggerConfig config, BuildMemory.MemoryImprint memoryImprint, TaskListener listener) {
        super(config, listener, (ChangeBasedEvent) memoryImprint.getEvent());
        this.memoryImprint = memoryImprint;
    }

    @Override
    protected ReviewInput createReview() {
        String completedCommand = parameterExpander.getBuildCompletedCommand(memoryImprint, listener);
        String message = findMessage(completedCommand);

        int verified = 0;
        int codeReview = 0;
        if (memoryImprint.getEvent().isScorable()) {
            verified = parameterExpander.getMinimumVerifiedValue(memoryImprint, true);
            codeReview = parameterExpander.getMinimumCodeReviewValue(memoryImprint, true);
        }
        List<GerritMessageProvider> gerritMessageProviders = GerritMessageProvider.all();
        Collection<CommentedFile> commentedFiles = new ArrayList<CommentedFile>();
        if(gerritMessageProviders != null) {
            for(GerritMessageProvider gerritMessageProvider : gerritMessageProviders) {
                for(BuildMemory.MemoryImprint.Entry e : memoryImprint.getEntries()) {
                    try {
                        commentedFiles.addAll(gerritMessageProvider.getFileComments(e.getBuild()));
                    } catch(Exception ef) {
                        listener.error(ef.getMessage());

                    }
                }
            }
        }
        return new ReviewInput(message,
                commentedFiles, ReviewLabel.verified(verified),
                ReviewLabel.codeReview(codeReview)
        );
    }
}
