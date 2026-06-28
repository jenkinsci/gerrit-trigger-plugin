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

import com.sonymobile.tools.gerrit.gerritevents.dto.rest.ChangeId;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.CommentedFile;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.Notify;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.ReviewInput;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.ReviewLabel;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.rest.RestConnectionConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;

import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.CheckForNull;

/**
 * Posts a review (labels, message, and collected comments) to the
 * Gerrit change that triggered the current build via the REST API.
 *
 * <p>This step also publishes any comments previously collected by
 * {@code gerritComment} steps within the same pipeline run.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * // Post a single label
 * gerritTriggerReview labels: [Verified: 1], message: 'Build passed'
 *
 * // Post multiple labels
 * gerritTriggerReview labels: [Verified: -1, 'Code-Review': -1], message: 'Failed tests'
 *
 * // Post with comments collected by gerritComment steps
 * gerritTriggerReview labels: [Verified: 1], message: 'Looks good', notify: 'NONE'
 * </pre>
 *
 * @author Michael Trimarchi
 */
public class GerritTriggerReviewStep extends Step {

    private static final Logger LOGGER = Logger.getLogger(GerritTriggerReviewStep.class.getName());
    private static final Gson GSON = new Gson();
    private static final int MAX_MESSAGE_LENGTH = 500;

    private Map<String, Object> labels;
    private String message;
    private String notify;

    /**
     * Constructor.
     */
    @DataBoundConstructor
    public GerritTriggerReviewStep() {
    }

    /**
     * Gets the labels.
     * @return the labels map.
     */
    @CheckForNull
    public Map<String, Object> getLabels() {
        return labels;
    }

    /**
     * Sets the labels as a map (e.g. [Verified: 1, 'Code-Review': -1]).
     * Key is label name, value is integer score.
     * @param labels the labels map.
     */
    @DataBoundSetter
    public void setLabels(Map<String, Object> labels) {
        this.labels = labels;
    }

    /**
     * Gets the review message.
     * @return the message.
     */
    @CheckForNull
    public String getMessage() {
        return message;
    }

    /**
     * Sets the review message.
     * @param message the message.
     */
    @DataBoundSetter
    public void setMessage(String message) {
        this.message = Util.fixEmptyAndTrim(message);
    }

    /**
     * Gets the notification level.
     * @return the notify value.
     */
    @CheckForNull
    public String getNotify() {
        return notify;
    }

    /**
     * Sets whom to notify.
     * @param notify the notification level (NONE, OWNER, OWNER_REVIEWERS, ALL).
     */
    @DataBoundSetter
    public void setNotify(String notify) {
        this.notify = Util.fixEmptyAndTrim(notify);
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    /**
     * Executes the GerritTriggerReviewStep.
     */
    public static class Execution extends SynchronousStepExecution<Void> {

        private static final long serialVersionUID = 1L;
        private final transient GerritTriggerReviewStep step;

        protected Execution(GerritTriggerReviewStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            Run<?, ?> build = getContext().get(Run.class);
            TaskListener listener = getContext().get(TaskListener.class);
            PrintStream buildLog = listener != null ? listener.getLogger() : null;

            // Parse labels
            Map<String, Object> rawLabels = step.getLabels();
            if (rawLabels == null || rawLabels.isEmpty()) {
                throw new IllegalArgumentException(
                        "gerritTriggerReview: 'labels' parameter is required, "
                        + "e.g. labels: [Verified: 1]");
            }

            List<ReviewLabel> parsedLabels = parseLabels(rawLabels);

            // Get the triggering event
            GerritCause cause = build.getCause(GerritCause.class);
            if (cause == null || cause.getEvent() == null) {
                String msg = "gerritTriggerReview: build was not triggered by Gerrit.";
                if (buildLog != null) {
                    buildLog.println("ERROR: " + msg);
                }
                throw new IllegalStateException(msg);
            }

            if (!(cause.getEvent() instanceof ChangeBasedEvent)) {
                String msg = "gerritTriggerReview: triggering event is not a change-based event.";
                if (buildLog != null) {
                    buildLog.println("ERROR: " + msg);
                }
                throw new IllegalStateException(msg);
            }

            ChangeBasedEvent event = (ChangeBasedEvent)cause.getEvent();

            // Resolve server
            String serverName = event.getProvider() != null ? event.getProvider().getName() : null;
            if (serverName == null) {
                String msg = "gerritTriggerReview: could not determine Gerrit server.";
                if (buildLog != null) {
                    buildLog.println("ERROR: " + msg);
                }
                throw new IllegalStateException(msg);
            }

            GerritServer server = PluginImpl.getServer_(serverName);
            if (server == null) {
                String msg = "gerritTriggerReview: Gerrit server '" + serverName + "' not found.";
                if (buildLog != null) {
                    buildLog.println("ERROR: " + msg);
                }
                throw new IllegalStateException(msg);
            }

            IGerritHudsonTriggerConfig config = server.getConfig();
            if (config == null) {
                String msg = "gerritTriggerReview: no configuration for server '" + serverName + "'.";
                if (buildLog != null) {
                    buildLog.println("ERROR: " + msg);
                }
                throw new IllegalStateException(msg);
            }

            // Collect any comments from gerritComment steps
            PipelineReviewCollector collector = PipelineReviewCollector.get(build);
            Collection<CommentedFile> commentedFiles = Collections.emptyList();
            if (collector.hasComments()) {
                commentedFiles = collector.drainComments();
            }

            // Build and post the review
            String sanitizedMsg = sanitizeMessage(step.getMessage());
            String endpoint = buildEndpoint(config, event);
            int statusCode = postReview(config, parsedLabels, sanitizedMsg,
                    commentedFiles, step.getNotify(), endpoint, buildLog);

            // Clean up collector
            PipelineReviewCollector.remove(build);

            if (buildLog != null) {
                if (statusCode == HttpStatus.SC_OK) {
                    StringBuilder labelStr = new StringBuilder();
                    for (ReviewLabel l : parsedLabels) {
                        if (labelStr.length() > 0) {
                            labelStr.append(", ");
                        }
                        labelStr.append(l.getName()).append("=").append(l.getValue());
                    }
                    buildLog.println("Posted review to Gerrit: " + labelStr);
                    if (sanitizedMsg != null) {
                        buildLog.println("  message: " + sanitizedMsg);
                    }
                    if (!commentedFiles.isEmpty()) {
                        int total = 0;
                        for (CommentedFile cf : commentedFiles) {
                            total += cf.getLineComments().size();
                        }
                        buildLog.println("  comments: " + total + " on "
                                + commentedFiles.size() + " file(s)");
                    }
                } else {
                    buildLog.println("ERROR: Gerrit responded with HTTP " + statusCode);
                }
            }

            return null;
        }

        /**
         * Parses the labels map from the pipeline DSL into ReviewLabel objects.
         */
        private static List<ReviewLabel> parseLabels(Map<String, Object> rawLabels) {
            List<ReviewLabel> result = new ArrayList<ReviewLabel>();
            for (Map.Entry<String, Object> entry : rawLabels.entrySet()) {
                String name = entry.getKey();
                int value;
                Object val = entry.getValue();
                if (val instanceof Number) {
                    value = ((Number)val).intValue();
                } else if (val instanceof String) {
                    try {
                        value = Integer.parseInt((String)val);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException(
                                "gerritTriggerReview: label '" + name + "' has invalid value: " + val);
                    }
                } else {
                    throw new IllegalArgumentException(
                            "gerritTriggerReview: label '" + name + "' has unsupported value type: "
                                    + (val != null ? val.getClass().getName() : "null"));
                }
                result.add(new ReviewLabel(name, value));
            }
            return result;
        }

        /**
         * Sanitizes a review message.
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

        /**
         * Builds the Gerrit REST API endpoint URL.
         */
        private static String buildEndpoint(RestConnectionConfig config, ChangeBasedEvent event) {
            String frontEndUrl = config.getGerritFrontEndUrl();
            if (!frontEndUrl.endsWith("/")) {
                frontEndUrl = frontEndUrl + "/";
            }

            ChangeId changeId = new ChangeId(
                    event.getChange().getProject(),
                    event.getChange().getBranch(),
                    event.getChange().getId());

            return frontEndUrl + "a/changes/" + changeId.asUrlPart()
                    + "/revisions/" + event.getPatchSet().getRevision() + "/review";
        }

        /**
         * Posts a review to the Gerrit REST API.
         */
        private static int postReview(RestConnectionConfig config,
                List<ReviewLabel> labels, String message,
                Collection<CommentedFile> commentedFiles,
                String notify, String endpoint, PrintStream buildLog) throws IOException {

            ReviewInput review;
            if (commentedFiles.isEmpty()) {
                review = new ReviewInput(message, labels);
            } else {
                review = new ReviewInput(message, labels, commentedFiles);
            }

            // Set notification level if specified
            if (notify != null && !notify.isEmpty()) {
                try {
                    review.setNotify(Notify.valueOf(notify.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    LOGGER.warning("gerritTriggerReview: invalid notify value '"
                            + notify + "', ignoring");
                }
            }

            HttpPost httpPost = new HttpPost(endpoint);
            String json = GSON.toJson(review);

            StringEntity entity;
            try {
                entity = new StringEntity(json);
            } catch (UnsupportedEncodingException e) {
                if (buildLog != null) {
                    buildLog.println("ERROR: " + e.getMessage());
                }
                return -1;
            }
            entity.setContentType("application/json");
            httpPost.setEntity(entity);

            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(AuthScope.ANY, config.getHttpCredentials());
            HttpClientBuilder builder = HttpClients.custom()
                    .setDefaultCredentialsProvider(credsProvider);

            if (config.getGerritProxy() != null && !config.getGerritProxy().isEmpty()) {
                try {
                    java.net.URL url = new java.net.URL(config.getGerritProxy());
                    builder.setProxy(new org.apache.http.HttpHost(
                            url.getHost(), url.getPort(), url.getProtocol()));
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Could not parse proxy URL", e);
                }
            }

            HttpClient httpClient = builder.build();
            HttpResponse httpResponse = httpClient.execute(httpPost);
            int statusCode = httpResponse.getStatusLine().getStatusCode();

            if (statusCode != HttpStatus.SC_OK && buildLog != null) {
                try {
                    String response = IOUtils.toString(
                            httpResponse.getEntity().getContent(), "UTF-8");
                    buildLog.println("ERROR: Gerrit response: " + response);
                } catch (Exception e) {
                    buildLog.println("ERROR: Gerrit response: "
                            + httpResponse.getStatusLine().getReasonPhrase());
                }
            }

            return statusCode;
        }
    }

    /**
     * Descriptor for the GerritTriggerReviewStep.
     */
    @Extension(optional = true)
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(Run.class);
        }

        @Override
        public String getFunctionName() {
            return "gerritTriggerReview";
        }

        @Override
        public String getDisplayName() {
            return "Post a Gerrit review";
        }
    }
}
