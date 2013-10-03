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

import com.google.gson.Gson;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.cmd.AbstractSendCommandJob;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ParameterExpander;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.rest.object.ChangeId;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.rest.object.ReviewInput;
import hudson.model.TaskListener;
import hudson.util.IOUtils;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.UnsupportedEncodingException;

/**
 * TODO Missing JavaDoc.
 */
public abstract class AbstractRestCommandJob extends AbstractSendCommandJob implements Runnable {

    /**
     * The GSON API.
     */
    private static final Gson GSON = new Gson();
    /**
     * The config.
     */
    private final IGerritHudsonTriggerConfig config;
    /**
     * The listener.
     */
    protected final TaskListener listener;
    /**
     * The {@link ParameterExpander}.
     */
    protected final ParameterExpander parameterExpander;
    /**
     * The Event.
     */
    protected final ChangeBasedEvent event;

    /**
     * Constructor.
     *
     * @param config   config
     * @param listener listener
     * @param event    event
     */
    public AbstractRestCommandJob(IGerritHudsonTriggerConfig config, TaskListener listener, ChangeBasedEvent event) {
        super(config);
        this.config = config;
        this.listener = listener;
        this.event = event;
        this.parameterExpander = new ParameterExpander(config);
    }

    @Override
    public boolean sendCommand(String command) {
        return true;
    }

    @Override
    public String sendCommandStr(String command) {
        return null;
    }

    @Override
    public void run() {
        ReviewInput reviewInput = createReview();

        String reviewEndpoint = resolveEndpointURL();

        HttpPost httpPost = createHttpPostEntity(reviewInput, reviewEndpoint);

        if (httpPost == null) {
            return;
        }

        DefaultHttpClient httpclient = new DefaultHttpClient();
        httpclient.getCredentialsProvider().setCredentials(new AuthScope(null, -1),
                new UsernamePasswordCredentials(config.getGerritHttpUserName(),
                        config.getGerritHttpPassword()));
        try {
            HttpResponse httpResponse = httpclient.execute(httpPost);
            String response = IOUtils.toString(httpResponse.getEntity().getContent(), "UTF-8");
            listener.getLogger().print(response);
            if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                listener.error(httpResponse.getStatusLine().getReasonPhrase());
            }
        } catch (Exception e) {
            listener.error("Failed to submit result", e);
        }
    }

    /**
     * Create the input for the command.
     *
     * @return the input
     */
    protected abstract ReviewInput createReview();

    /**
     * Construct the post.
     *
     * @param reviewInput    input
     * @param reviewEndpoint end point
     * @return the entity
     */
    private HttpPost createHttpPostEntity(ReviewInput reviewInput, String reviewEndpoint) {
        HttpPost httpPost = new HttpPost(reviewEndpoint);
        String asJson = GSON.toJson(reviewInput);

        StringEntity entity = null;
        try {
            entity = new StringEntity(asJson);
        } catch (UnsupportedEncodingException e) {
            listener.error("Failed to create JSON for posting", e);
            return null;
        }
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        return httpPost;
    }

    /**
     * What it says resolve Endpoint URL.
     *
     * @return the url.
     */
    private String resolveEndpointURL() {
        String gerritFrontEndUrl = config.getGerritFrontEndUrl();
        if (!gerritFrontEndUrl.endsWith("/")) {
            gerritFrontEndUrl =  gerritFrontEndUrl + "/";
        }

        ChangeId changeId = new ChangeId(event.getChange().getProject(), event.getChange().getBranch(),
                event.getChange().getId());

        return gerritFrontEndUrl + "a/changes/" + changeId.asUrlPart()
                + "/revisions/" + event.getPatchSet().getRevision() + "/review";
    }
}
