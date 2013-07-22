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
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.*;

public abstract class AbstractRestCommandJob extends AbstractSendCommandJob implements Runnable {

    private static final Gson GSON = new Gson();
    private final IGerritHudsonTriggerConfig config;
    protected final TaskListener listener;
    protected final ParameterExpander parameterExpander;
    protected final ChangeBasedEvent event;

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

        if (httpPost == null) return;

        DefaultHttpClient httpclient = new DefaultHttpClient();
        httpclient.getCredentialsProvider().setCredentials(new AuthScope(null, -1),
                new UsernamePasswordCredentials(config.getGerritHttpUserName(),
                        config.getGerritHttpPassword()));
        try {
            HttpResponse httpResponse = httpclient.execute(httpPost);
            String response = IOUtils.toString(httpResponse.getEntity().getContent(), "UTF-8");
            listener.getLogger().print(response);
            if( httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                listener.error(httpResponse.getStatusLine().getReasonPhrase());
            }
        } catch (Exception e) {
            listener.error("Failed to submit result", e);
        }
    }

    protected abstract ReviewInput createReview();

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

    private String resolveEndpointURL() {
        String gerritFrontEndUrl = config.getGerritFrontEndUrl();
        gerritFrontEndUrl = gerritFrontEndUrl.endsWith("/") ? gerritFrontEndUrl : gerritFrontEndUrl + "/";

        ChangeId changeId = new ChangeId(event.getChange().getProject(), event.getChange().getBranch(), event.getChange().getId());

        return gerritFrontEndUrl + "a/changes/" + changeId.asUrlPart() + "/revisions/" + event.getPatchSet().getRevision() + "/review";
    }

    protected String findMessage(String completedCommand) {
        String messageStart = "--message '";
        String fromMessage = completedCommand.substring(completedCommand.indexOf(messageStart));
        int endIndex = fromMessage.indexOf("' --");
        return fromMessage.substring(messageStart.length(), endIndex != -1 ? endIndex : fromMessage.length());
    }

}