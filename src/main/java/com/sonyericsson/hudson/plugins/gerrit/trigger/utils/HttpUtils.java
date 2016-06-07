/*
 * The MIT License
 *
 * Copyright (c) 2014 Ericsson
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.utils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;

/**
 * Helper class for HTTP operations.
 */
public final class HttpUtils {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);

    /**
     * Helper methods for Http operations.
     */
    private HttpUtils() {
    }

    /**
     * @param config Gerrit Server Configuration.
     * @param url URL to get.
     * @return httpresponse.
     * @throws IOException if found.
     */
    public static CloseableHttpResponse performHTTPGet(IGerritHudsonTriggerConfig config,
                                                       String url) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        if (config.getGerritProxy() != null && !config.getGerritProxy().isEmpty()) {
            try {
                URL proxyUrl = new URL(config.getGerritProxy());
                HttpHost proxy = new HttpHost(proxyUrl.getHost(), proxyUrl.getPort(), proxyUrl.getProtocol());
                DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxy);
                httpClient = HttpClients.custom().setRoutePlanner(routePlanner).build();
            } catch (MalformedURLException e) {
                logger.error("Could not parse proxy URL, attempting without proxy.", e);
            }
        }

        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(null, -1), config.getHttpCredentials());
        HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);
        return httpClient.execute(httpGet, context);
    }
}
