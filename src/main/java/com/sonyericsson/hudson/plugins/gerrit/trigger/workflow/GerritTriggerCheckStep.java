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
import java.net.URL;
import java.sql.Timestamp;
import java.util.Collections;
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
 * Posts check results to the Gerrit change that triggered the current build
 * via the Gerrit checks REST API.
 *
 * <p>This step allows pipelines to report checker results (e.g. from
 * static analysis, CI builds, etc.) that appear in the Gerrit checks
 * tab. Compatible with Gerrit installations that have the
 * {@code checks} plugin enabled (including GerritHub).</p>
 *
 * <p>Usage:</p>
 * <pre>
 * // Report a single check as failed
 * gerritTriggerCheck checks: ['example:checker': 'FAILED']
 *
 * // Report multiple checks with a message
 * gerritTriggerCheck checks: ['checker-a': 'SUCCESSFUL', 'checker-b': 'FAILED'],
 *     message: 'Pipeline completed'
 *
 * // Report with a custom URL
 * gerritTriggerCheck checks: ['checker-a': 'SUCCESSFUL'],
 *     message: 'All tests passed',
 *     url: 'https://jenkins.example.com/job/myjob/42/'
 *
 * // Mark a check as running
 * gerritTriggerCheck checks: ['checker-a': 'RUNNING'],
 *     message: 'Tests in progress...'
 * </pre>
 *
 * @author Michael Trimarchi
 */
public class GerritTriggerCheckStep extends Step {

    private static final Logger LOGGER = Logger.getLogger(GerritTriggerCheckStep.class.getName());
    private static final Gson GSON = new Gson();

    private Map<String, String> checks;
    private String message;
    private String url;

    /**
     * Constructor.
     */
    @DataBoundConstructor
    public GerritTriggerCheckStep() {
    }

    /**
     * Gets the checks map.
     * @return the checks map (checker UUID to state).
     */
    @CheckForNull
    public Map<String, String> getChecks() {
        return checks;
    }

    /**
     * Sets the checks to report as a map of checker UUID to state.
     * e.g. {@code ['example:checker': 'FAILED']}.
     *
     * <p>Valid states: RUNNING, SUCCESSFUL, FAILED, NOT_STARTED,
     * SCHEDULED, NOT_RELEVANT.</p>
     *
     * @param checks the checks map.
     */
    @DataBoundSetter
    public void setChecks(Map<String, String> checks) {
        this.checks = checks;
    }

    /**
     * Gets the message.
     * @return the message.
     */
    @CheckForNull
    public String getMessage() {
        return message;
    }

    /**
     * Sets an optional short message explaining the check state.
     * @param message the message.
     */
    @DataBoundSetter
    public void setMessage(String message) {
        this.message = Util.fixEmptyAndTrim(message);
    }

    /**
     * Gets the URL.
     * @return the URL.
     */
    @CheckForNull
    public String getUrl() {
        return url;
    }

    /**
     * Sets an optional URL to detailed check results.
     * If not specified, the build's console log URL is used.
     * @param url the URL.
     */
    @DataBoundSetter
    public void setUrl(String url) {
        this.url = Util.fixEmptyAndTrim(url);
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    /**
     * Executes the GerritTriggerCheckStep.
     */
    public static class Execution extends SynchronousStepExecution<Void> {

        private static final long serialVersionUID = 1L;
        private final transient GerritTriggerCheckStep step;

        protected Execution(GerritTriggerCheckStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            Run<?, ?> build = getContext().get(Run.class);
            TaskListener listener = getContext().get(TaskListener.class);
            PrintStream buildLog = listener != null ? listener.getLogger() : null;

            // Parse checks
            Map<String, String> rawChecks = step.getChecks();
            if (rawChecks == null || rawChecks.isEmpty()) {
                throw new IllegalArgumentException(
                        "gerritTriggerCheck: 'checks' parameter is required, "
                        + "e.g. checks: ['example:checker': 'FAILED']");
            }

            // Validate states
            for (Map.Entry<String, String> entry : rawChecks.entrySet()) {
                String stateStr = entry.getValue();
                if (stateStr == null || stateStr.isEmpty()) {
                    throw new IllegalArgumentException(
                            "gerritTriggerCheck: state for checker '"
                            + entry.getKey() + "' must not be empty");
                }
                try {
                    CheckState.valueOf(stateStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    StringBuilder validStates = new StringBuilder();
                    for (CheckState s : CheckState.values()) {
                        if (validStates.length() > 0) {
                            validStates.append(", ");
                        }
                        validStates.append(s.name());
                    }
                    throw new IllegalArgumentException(
                            "gerritTriggerCheck: invalid state '" + stateStr
                            + "' for checker '" + entry.getKey()
                            + "'. Valid states: " + validStates);
                }
            }

            // Get the triggering event
            GerritCause cause = build.getCause(GerritCause.class);
            if (cause == null || cause.getEvent() == null) {
                String msg = "gerritTriggerCheck: build was not triggered by Gerrit.";
                if (buildLog != null) {
                    buildLog.println("ERROR: " + msg);
                }
                throw new IllegalStateException(msg);
            }

            if (!(cause.getEvent() instanceof ChangeBasedEvent)) {
                String msg = "gerritTriggerCheck: triggering event is not a change-based event.";
                if (buildLog != null) {
                    buildLog.println("ERROR: " + msg);
                }
                throw new IllegalStateException(msg);
            }

            ChangeBasedEvent event = (ChangeBasedEvent) cause.getEvent();

            // Resolve server
            String serverName = event.getProvider() != null ? event.getProvider().getName() : null;
            if (serverName == null) {
                String msg = "gerritTriggerCheck: could not determine Gerrit server.";
                if (buildLog != null) {
                    buildLog.println("ERROR: " + msg);
                }
                throw new IllegalStateException(msg);
            }

            GerritServer server = PluginImpl.getServer_(serverName);
            if (server == null) {
                String msg = "gerritTriggerCheck: Gerrit server '" + serverName + "' not found.";
                if (buildLog != null) {
                    buildLog.println("ERROR: " + msg);
                }
                throw new IllegalStateException(msg);
            }

            IGerritHudsonTriggerConfig config = server.getConfig();
            if (config == null) {
                String msg = "gerritTriggerCheck: no configuration for server '" + serverName + "'.";
                if (buildLog != null) {
                    buildLog.println("ERROR: " + msg);
                }
                throw new IllegalStateException(msg);
            }

            // Resolve URL
            String resolvedUrl = step.getUrl();
            if (resolvedUrl == null) {
                resolvedUrl = getConsoleLogUri();
            }

            // Post each check
            String endpoint = buildCheckEndpoint(config, event);
            for (Map.Entry<String, String> entry : rawChecks.entrySet()) {
                String checkerUuid = entry.getKey();
                CheckState state = CheckState.valueOf(entry.getValue().toUpperCase());

                CheckInput input = new CheckInput();
                input.setCheckerUuid(checkerUuid);
                input.setState(state.name());
                input.setMessage(step.getMessage());
                input.setUrl(resolvedUrl);

                // Set timestamps based on state
                setTimestamps(input, state);

                int statusCode = postCheck(config, input, endpoint, buildLog, checkerUuid);

                if (buildLog != null) {
                    if (statusCode == HttpStatus.SC_OK) {
                        buildLog.println("Posted check '" + checkerUuid
                                + "' (" + state.name() + ") to Gerrit");
                    } else {
                        buildLog.println("ERROR: Gerrit check '" + checkerUuid
                                + "' returned HTTP " + statusCode);
                    }
                }
            }

            return null;
        }

        /**
         * Sets timestamps on the check input based on its state.
         * RUNNING state sets the started timestamp,
         * terminal states (SUCCESSFUL, FAILED) set the finished timestamp.
         */
        private static void setTimestamps(CheckInput input, CheckState state) {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            switch (state) {
                case RUNNING:
                    input.setStarted(now);
                    break;
                case SUCCESSFUL:
                case FAILED:
                    input.setFinished(now);
                    break;
                case NOT_STARTED:
                case NOT_RELEVANT:
                case SCHEDULED:
                default:
                    break;
            }
        }

        /**
         * Builds the Gerrit checks REST API endpoint URL.
         */
        private static String buildCheckEndpoint(RestConnectionConfig config, ChangeBasedEvent event) {
            String frontEndUrl = config.getGerritFrontEndUrl();
            if (!frontEndUrl.endsWith("/")) {
                frontEndUrl = frontEndUrl + "/";
            }

            ChangeId changeId = new ChangeId(
                    event.getChange().getProject(),
                    event.getChange().getBranch(),
                    event.getChange().getId());

            return frontEndUrl + "a/changes/" + changeId.asUrlPart()
                    + "/revisions/" + event.getPatchSet().getRevision() + "/checks";
        }

        /**
         * Posts a check to the Gerrit checks REST API.
         */
        private static int postCheck(RestConnectionConfig config,
                CheckInput input, String endpoint,
                PrintStream buildLog, String checkerUuid) throws IOException {

            HttpPost httpPost = new HttpPost(endpoint);
            String json = GSON.toJson(input);

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
                    URL proxyUrl = new URL(config.getGerritProxy());
                    builder.setProxy(new org.apache.http.HttpHost(
                            proxyUrl.getHost(), proxyUrl.getPort(), proxyUrl.getProtocol()));
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
                    buildLog.println("ERROR: Gerrit check '" + checkerUuid
                            + "' response: " + response);
                } catch (Exception e) {
                    buildLog.println("ERROR: Gerrit check '" + checkerUuid
                            + "' response: "
                            + httpResponse.getStatusLine().getReasonPhrase());
                }
            }

            return statusCode;
        }

        /**
         * Builds the console log URL for this build.
         */
        private String getConsoleLogUri() throws IOException, InterruptedException {
            String rootUrl = jenkins.model.Jenkins.getInstance().getRootUrl();
            if (rootUrl == null) {
                throw new NullPointerException(
                        "Jenkins URL has to be set in the Jenkins configuration.");
            }
            return rootUrl + getContext().get(Run.class).getUrl() + "console";
        }
    }

    /**
     * Valid check states for the Gerrit checks plugin.
     */
    public enum CheckState {
        /** The check terminated and failed. */
        FAILED,
        /** The check is relevant but the checker has not started work. */
        NOT_STARTED,
        /** The checker has acknowledged the work and will start later. */
        SCHEDULED,
        /** The checker is currently running the check. */
        RUNNING,
        /** The check terminated and succeeded. */
        SUCCESSFUL,
        /** The check is not relevant for the change. */
        NOT_RELEVANT;
    }

    /**
     * Descriptor for the GerritTriggerCheckStep.
     */
    @Extension(optional = true)
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(Run.class);
        }

        @Override
        public String getFunctionName() {
            return "gerritTriggerCheck";
        }

        @Override
        public String getDisplayName() {
            return "Post Gerrit check results";
        }
    }
}
