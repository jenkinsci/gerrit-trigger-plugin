package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.rest.job;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.rest.object.ReviewInput;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;

public class BuildStartedRestCommandJob extends AbstractRestCommandJob {

    private final AbstractBuild build;
    private final BuildsStartedStats stats;

    public BuildStartedRestCommandJob(IGerritHudsonTriggerConfig config, AbstractBuild build, TaskListener listener, ChangeBasedEvent event, BuildsStartedStats stats) {
        super(config, listener, event);
        this.build = build;
        this.stats = stats;
    }

    protected ReviewInput createReview() {
        String startedCommand = parameterExpander.getBuildStartedCommand(build, listener, event, stats);
        String message = findMessage(startedCommand);
        return new ReviewInput(message);
    }

}
