/*
 *  The MIT License
 *
 *  Copyright 2013 Jyrki Puttonen. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.job.rest;

import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.workers.rest.AbstractRestCommandJob;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Constants;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ParameterExpander;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.Notify;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.ReviewInput;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.ReviewLabel;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.util.ArrayList;
import java.util.Collection;

/**
 * A job for the {@link com.sonymobile.tools.gerrit.gerritevents.GerritSendCommandQueue} that
 * sends a build started message.
 */
public class BuildStartedRestCommandJob extends AbstractRestCommandJob {


    private static final String LABEL_CODEREVIEW = "Code-Review";
    private static final String LABEL_VERIFIED   = "Verified";

    private final Run build;
    private final BuildsStartedStats stats;
    private final TaskListener listener;
    private final ParameterExpander parameterExpander;
    private final IGerritHudsonTriggerConfig config;

    /**
     * Constructor.
     *
     * @param config   config
     * @param build    build
     * @param listener listener
     * @param event    event
     * @param stats    stats
     */
    public BuildStartedRestCommandJob(IGerritHudsonTriggerConfig config, Run build, TaskListener listener,
                                      ChangeBasedEvent event, BuildsStartedStats stats) {
        //CS IGNORE AvoidInlineConditionals FOR NEXT 1 LINES. REASON: Only more hard to read alternatives apply.
        super(config, (listener != null ? listener.getLogger() : null), event);
        this.build = build;
        this.stats = stats;
        this.listener = listener;
        this.parameterExpander = new ParameterExpander(config);
        this.config = config;
    }

    /**
     * Review input message.
     *
     * @return ReviewInput
     */
    @Override
    protected ReviewInput createReview() {
        String message = parameterExpander.getBuildStartedMessage(build, listener, event, stats);

        Collection<ReviewLabel> scoredLabels = new ArrayList<>();

        if (event.isScorable()) {
            if (config.isRestCodeReview()) {
                Integer crValue = parameterExpander.getBuildStartedCodeReviewValue(build);
                if (crValue != null && crValue != Integer.MAX_VALUE) {
                    scoredLabels.add(new ReviewLabel(
                            LABEL_CODEREVIEW,
                            crValue));
                }
            }
            if (config.isRestVerified()) {
                Integer verValue = parameterExpander.getBuildStartedVerifiedValue(build);
                if (verValue != null && verValue != Integer.MAX_VALUE) {
                    scoredLabels.add(new ReviewLabel(
                            LABEL_VERIFIED,
                            verValue));
                }
            }
        }

        Notify notificationLevel = Notify.ALL;
        GerritTrigger trigger = GerritTrigger.getTrigger(build.getParent());
        if (trigger != null) {
            notificationLevel = parameterExpander.getNotificationLevel(trigger);
        }
        return new ReviewInput(message, scoredLabels).setNotify(notificationLevel).setTag(Constants.TAG_VALUE);
    }

}
