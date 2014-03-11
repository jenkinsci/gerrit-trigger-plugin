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
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.GerritMessageProvider;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ParameterExpander;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.CommentedFile;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.ReviewInput;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.ReviewLabel;
import hudson.model.TaskListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
* A job for the {@link com.sonymobile.tools.gerrit.gerritevents.GerritSendCommandQueue} that
* sends a build completed message.
*/
public class BuildCompletedRestCommandJob extends AbstractRestCommandJob {

    private final BuildMemory.MemoryImprint memoryImprint;
    private final TaskListener listener;
    private final ParameterExpander parameterExpander;

    /**
     * Constructor.
     *
     * @param config        config
     * @param memoryImprint memory
     * @param listener      listener
     */
    public BuildCompletedRestCommandJob(IGerritHudsonTriggerConfig config, BuildMemory.MemoryImprint memoryImprint,
                                        TaskListener listener) {
        //CS IGNORE AvoidInlineConditionals FOR NEXT 1 LINES. REASON: Only more hard to read alternatives apply.
        super(config, (listener != null ? listener.getLogger() : null), (ChangeBasedEvent)memoryImprint.getEvent());
        this.memoryImprint = memoryImprint;
        this.listener = listener;
        parameterExpander = new ParameterExpander(config);
    }

    @Override
    protected ReviewInput createReview() {
        String message = parameterExpander.getBuildCompletedMessage(memoryImprint, listener);

        int verified = 0;
        int codeReview = 0;
        if (memoryImprint.getEvent().isScorable()) {
            verified = parameterExpander.getMinimumVerifiedValue(memoryImprint, true);
            codeReview = parameterExpander.getMinimumCodeReviewValue(memoryImprint, true);
        }
        List<GerritMessageProvider> gerritMessageProviders = GerritMessageProvider.all();
        Collection<CommentedFile> commentedFiles = new ArrayList<CommentedFile>();
        if (gerritMessageProviders != null) {
            for (GerritMessageProvider gerritMessageProvider : gerritMessageProviders) {
                for (BuildMemory.MemoryImprint.Entry e : memoryImprint.getEntries()) {
                    try {
                        commentedFiles.addAll(gerritMessageProvider.getFileComments(e.getBuild()));
                    } catch (Exception ef) {
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
