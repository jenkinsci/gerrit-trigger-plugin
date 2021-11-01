/*
 * The MIT License
 *
 * Copyright (c) 2016 Teemu Murtola.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.workflow;

import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import java.util.Collections;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Allows altering the Gerrit review posted at the end of build during the build.
 * @author Teemu Murtola &lt;teemu.murtola@gmail.com&gt;
 */
public class SetGerritReviewStep extends Step {

    private String customUrl;
    private String unsuccessfulMessage;

    /**
     * Constructor.
     *
     * There are no mandatory parameters to the step.
     */
    @DataBoundConstructor
    public SetGerritReviewStep() {
    }

    /**
     * Gets the custom URL for a step.
     * @return the URL.
     */
    @CheckForNull
    public String getCustomUrl() {
        return customUrl;
    }

    /**
     * Sets a custom URL to post for a build.
     * @param customUrl the URL to post.
     */
    @DataBoundSetter
    public void setCustomUrl(String customUrl) {
        this.customUrl = Util.fixEmptyAndTrim(customUrl);
    }

    /**
     * Gets the unsuccessful message for a step.
     * @return the message.
     */
    @CheckForNull
    public String getUnsuccessfulMessage() {
        return unsuccessfulMessage;
    }

    /**
     * Sets additional information to post for a failed build.
     * @param unsuccessfulMessage Additional information to post for a failed build.
     */
    @DataBoundSetter
    public void setUnsuccessfulMessage(String unsuccessfulMessage) {
        this.unsuccessfulMessage = Util.fixEmptyAndTrim(unsuccessfulMessage);
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    /**
     * Executes the SetGerritReviewStep.
     */
    public static class Execution extends SynchronousStepExecution<Void> {

        private final transient SetGerritReviewStep step;

        /**
         * Constructor.
         *
         * @param step The step.
         * @param context The step context.
         */
        protected Execution(SetGerritReviewStep step, @Nonnull StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            ToGerritRunListener listener = ToGerritRunListener.getInstance();
            String customUrl = step.getCustomUrl();
            if (customUrl != null) {
                listener.setBuildCustomUrl(getContext().get(Run.class), customUrl);
            }
            String unsuccessfulMessage = step.getUnsuccessfulMessage();
            if (unsuccessfulMessage != null) {
                listener.setBuildUnsuccessfulMessage(getContext().get(Run.class), unsuccessfulMessage);
            }
            return null;
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Adds the step as a workflow extension.
     */
    @Extension(optional = true)
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(Run.class);
        }

        @Override
        public String getFunctionName() {
            return "setGerritReview";
        }

        @Override
        public String getDisplayName() {
            return "Set Gerrit review";
        }
    }
}
