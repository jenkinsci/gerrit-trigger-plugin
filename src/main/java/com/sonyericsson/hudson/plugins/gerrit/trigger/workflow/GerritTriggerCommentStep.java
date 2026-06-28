/*
 * The MIT License
 *
 * Copyright (c) 2024 Amarula Solutions. All rights reserved.
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

import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.PrintStream;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import edu.umd.cs.findbugs.annotations.CheckForNull;

/**
 * Adds a review comment that will be posted to Gerrit when the
 * {@code gerritReview} step is executed.
 *
 * <p>Comments are collected during pipeline execution and batched
 * into the review posted by {@code gerritReview}. If no
 * {@code gerritReview} step is present in the pipeline, comments
 * are not posted to Gerrit.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * // File-level comment
 * gerritTriggerComment path: 'src/main/java/Foo.java', message: 'Nice work overall'
 *
 * // Line-level comment
 * gerritTriggerComment path: 'src/main/java/Foo.java', line: 42, message: 'This can be simplified'
 * </pre>
 *
 * @author Michael Trimarchi
 */
public class GerritTriggerCommentStep extends Step {

    private static final Logger LOGGER = Logger.getLogger(GerritTriggerCommentStep.class.getName());
    private static final int MAX_MESSAGE_LENGTH = 500;

    private String path;
    private Integer line;
    private String message;

    /**
     * Constructor.
     */
    @DataBoundConstructor
    public GerritTriggerCommentStep() {
    }

    /**
     * Gets the file path.
     * @return the file path.
     */
    @CheckForNull
    public String getPath() {
        return path;
    }

    /**
     * Sets the relative file path to comment on.
     * @param path the file path (mandatory).
     */
    @DataBoundSetter
    public void setPath(String path) {
        this.path = Util.fixEmptyAndTrim(path);
    }

    /**
     * Gets the line number.
     * @return the line number, or null for a file-level comment.
     */
    @CheckForNull
    public Integer getLine() {
        return line;
    }

    /**
     * Sets the line number for the comment.
     * When not specified, the comment applies to the entire file.
     * @param line the line number.
     */
    @DataBoundSetter
    public void setLine(Integer line) {
        this.line = line;
    }

    /**
     * Gets the comment message.
     * @return the message.
     */
    @CheckForNull
    public String getMessage() {
        return message;
    }

    /**
     * Sets the comment message.
     * @param message the message (mandatory).
     */
    @DataBoundSetter
    public void setMessage(String message) {
        this.message = Util.fixEmptyAndTrim(message);
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    /**
     * Executes the GerritTriggerCommentStep.
     */
    public static class Execution extends SynchronousStepExecution<Void> {

        private static final long serialVersionUID = 1L;
        private final transient GerritTriggerCommentStep step;

        protected Execution(GerritTriggerCommentStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            String path = step.getPath();
            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException("gerritTriggerComment: 'path' parameter is required");
            }

            String message = step.getMessage();
            if (message == null || message.isEmpty()) {
                throw new IllegalArgumentException("gerritTriggerComment: 'message' parameter is required");
            }

            String sanitized = sanitizeMessage(message);

            Run<?, ?> build = getContext().get(Run.class);
            PipelineReviewCollector collector = PipelineReviewCollector.get(build);

            Integer line = step.getLine();
            if (line != null) {
                collector.addComment(path, line, sanitized);
            } else {
                collector.addComment(path, sanitized);
            }

            TaskListener listener = getContext().get(TaskListener.class);
            if (listener != null) {
                PrintStream log = listener.getLogger();
                log.println("Gerrit comment queued: " + path
                        + (line != null ? ":" + line : ""));
            }
            return null;
        }

        /**
         * Sanitizes a comment message.
         */
        private static String sanitizeMessage(String raw) {
            if (raw == null) {
                return null;
            }
            String sanitized = raw.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
            if (sanitized.length() > MAX_MESSAGE_LENGTH) {
                sanitized = sanitized.substring(0, MAX_MESSAGE_LENGTH);
            }
            return sanitized.trim();
        }
    }

    /**
     * Descriptor for the GerritTriggerCommentStep.
     */
    @Extension(optional = true)
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(Run.class);
        }

        @Override
        public String getFunctionName() {
            return "gerritTriggerComment";
        }

        @Override
        public String getDisplayName() {
            return "Add a Gerrit review comment";
        }

        /**
         * Validates the path parameter.
         * @param value the value.
         * @return ok or error.
         */
        public hudson.util.FormValidation doCheckPath(@QueryParameter String value) {
            if (value == null || value.trim().isEmpty()) {
                return hudson.util.FormValidation.error("Path is required");
            }
            return hudson.util.FormValidation.ok();
        }

        /**
         * Validates the message parameter.
         * @param value the value.
         * @return ok or error.
         */
        public hudson.util.FormValidation doCheckMessage(@QueryParameter String value) {
            if (value == null || value.trim().isEmpty()) {
                return hudson.util.FormValidation.error("Message is required");
            }
            return hudson.util.FormValidation.ok();
        }
    }
}
