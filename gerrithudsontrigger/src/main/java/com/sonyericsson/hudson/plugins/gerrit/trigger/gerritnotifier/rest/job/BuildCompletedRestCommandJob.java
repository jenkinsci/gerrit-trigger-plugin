/*
 *  The MIT License
 *
 *  Copyright 2013 Jyrki Puttonen. All rights reserved.
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
        String message = parameterExpander.getBuildCompletedMessage(memoryImprint, listener);

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
