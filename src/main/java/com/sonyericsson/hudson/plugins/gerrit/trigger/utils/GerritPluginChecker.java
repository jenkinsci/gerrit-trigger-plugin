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

import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;

/**
 * Helper to determine if a Gerrit Plugin is installed
 * on the Gerrit server.
 * @author scott.hebert@ericsson.com
 */
public final class GerritPluginChecker {

    /**
     * Gerrit Plugin Checker.
     */
    private GerritPluginChecker() {
    }

    private static final Logger logger = LoggerFactory.getLogger(GerritPluginChecker.class);

    /**
     * Build URL using Gerrit Server config.
     * @param config Gerrit Server config.
     * @return URL
     */
    private static String buildURL(IGerritHudsonTriggerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Gerrit Server Configuration cannot be null");
        }

        if (!config.isUseRestApi()) {
            logger.warn("REST API is not enabled.");
            return null;
        }

        String gerritFrontEndUrl = config.getGerritFrontEndUrl();
        String restUrl = gerritFrontEndUrl;
        if (gerritFrontEndUrl != null && !gerritFrontEndUrl.endsWith("/")) {
            restUrl = gerritFrontEndUrl + "/";
        }
        return restUrl;
    }

    /**
     * Given a status code, decode its status.
     * @param statusCode HTTP code
     * @param pluginName plugin that was checked.
     * @return true/false if installed or not.
     */
    private static boolean decodeStatus(int statusCode, String pluginName) {
        switch (statusCode) {
            case HttpURLConnection.HTTP_OK:
                logger.info(Messages.PluginInstalled(pluginName));
                return true;
            case HttpURLConnection.HTTP_NOT_FOUND:
                logger.info(Messages.PluginNotInstalled(pluginName));
                return false;
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                logger.warn(Messages.PluginHttpConnectionUnauthorized(pluginName,
                        Messages.HttpConnectionUnauthorized()));
                return false;
            case HttpURLConnection.HTTP_FORBIDDEN:
                logger.warn(Messages.PluginHttpConnectionForbidden(pluginName,
                        Messages.HttpConnectionUnauthorized()));
                return false;
            default:
                logger.warn(Messages.PluginHttpConnectionGeneralError(pluginName,
                        Messages.HttpConnectionError(statusCode)));
                return false;
        }
    }
    /**
     * Query Gerrit to determine if plugin is enabled.
     * @param config Gerrit Server Config
     * @param pluginName The Gerrit Plugin name.
     * @return true if enabled.
     */
    public static boolean isPluginEnabled(IGerritHudsonTriggerConfig config, String pluginName) {

        String restUrl = buildURL(config);
        if (restUrl == null) {
            logger.warn(Messages.PluginInstalledRESTApiNull(pluginName));
            return false;
        }
        logger.trace("{}plugins/{}/", restUrl, pluginName);

        CloseableHttpResponse execute = null;
        try {
            execute = HttpUtils.performHTTPGet(config, restUrl + "plugins/" + pluginName + "/");
            int statusCode = execute.getStatusLine().getStatusCode();
            logger.debug("status code: {}", statusCode);
            return decodeStatus(statusCode, pluginName);
        } catch (IOException e) {
            logger.warn(Messages.PluginHttpConnectionGeneralError(pluginName,
                    e.getMessage()), e);
            return false;
        } finally {
            if (execute != null) {
                try {
                    execute.close();
                } catch (Exception exp) {
                    logger.trace("Error happened when close http client.", exp);
                }
            }
        }
    }
}
