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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.rest;

import com.google.gson.Gson;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.rest.ChangeId;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.rest.ReviewInput;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.rest.RestConnectionConfig;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * An abstract Job implementation
 * to be scheduled on {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritSendCommandQueue}.
 *
 */
public abstract class AbstractRestCommandJob implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(AbstractRestCommandJob.class);

    /**
     * The GSON API.
     */
    private static final Gson GSON = new Gson();
    /**
     * The config.
     */
    private final RestConnectionConfig config;
    /**
     * The listener.
     */
    protected final PrintStream altLogger;

    /**
     * The Event.
     */
    protected final ChangeBasedEvent event;

    /**
     * Constructor.
     *
     * @param config    config
     * @param altLogger alternative stream to also write log output to (ex: a build log)
     * @param event     event
     */
    public AbstractRestCommandJob(RestConnectionConfig config, PrintStream altLogger, ChangeBasedEvent event) {
        this.config = config;
        this.altLogger = altLogger;
        this.event = event;
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
                        config.getHttpCredentials());
        if (config.getGerritProxy() != null && !config.getGerritProxy().isEmpty()) {
            try {
                URL url = new URL(config.getGerritProxy());
                HttpHost proxy = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());
                httpclient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
            } catch (MalformedURLException e) {
                logger.error("Could not parse proxy URL, attempting without proxy.", e);
                if (altLogger != null) {
                    altLogger.print("ERROR Could not parse proxy URL, attempting without proxy. "
                            + e.getMessage());
                }
            }
        }
        try {
            HttpResponse httpResponse = httpclient.execute(httpPost);
            String response = IOUtils.toString(httpResponse.getEntity().getContent(), "UTF-8");

            if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                logger.error("Gerrit response: {}", httpResponse.getStatusLine().getReasonPhrase());
                if (altLogger != null) {
                    altLogger.print("ERROR Gerrit response: " + httpResponse.getStatusLine().getReasonPhrase());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to submit result to Gerrit", e);
            if (altLogger != null) {
                altLogger.print("ERROR Failed to submit result to Gerrit" + e.toString());
            }
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
            logger.error("Failed to create JSON for posting to Gerrit", e);
            if (altLogger != null) {
                altLogger.print("ERROR Failed to create JSON for posting to Gerrit: " + e.toString());
            }
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
            gerritFrontEndUrl = gerritFrontEndUrl + "/";
        }

        ChangeId changeId = new ChangeId(event.getChange().getProject(), event.getChange().getBranch(),
                event.getChange().getId());

        return gerritFrontEndUrl + "a/changes/" + changeId.asUrlPart()
                + "/revisions/" + event.getPatchSet().getRevision() + "/review";
    }

    /**
     * REST related configuration.
     * @return the config
     */
    public RestConnectionConfig getConfig() {
        return config;
    }
}
