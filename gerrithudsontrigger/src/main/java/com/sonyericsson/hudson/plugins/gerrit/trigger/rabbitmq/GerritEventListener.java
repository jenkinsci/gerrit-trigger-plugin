/*
 * The MIT License
 *
 * Copyright 2012 Intel, Inc. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.rabbitmq;

import hudson.Extension;
import net.sf.json.JSONObject;

import org.jenkinsci.plugins.rabbitmqconsumer.listeners.ApplicationMessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritJsonEventFactory;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;

/**
 * A listener for getting gerrit event from RabbitMQ.
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
@Extension
public class GerritEventListener implements ApplicationMessageListener {

    private static final String NAME = "Gerrit Event";
    private static final String APPID = "gerrit";
    private static final Logger logger = LoggerFactory.getLogger(GerritEventListener.class);

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getAppId() {
        return APPID;
    }

    @Override
    public void onBind(String queueName) {
        logger.debug("Bind {} for {}", queueName, APPID);
    }

    @Override
    public void onUnbind(String queueName) {
        logger.debug("Unbind {} for {}", queueName, APPID);
    }

    @Override
    public void onReceive(String queueName, JSONObject json) {
        if (PluginImpl.getInstance().getConfig().isGerritUseRabbitMQ()) {
            GerritEvent event = GerritJsonEventFactory.getEvent(json);
            if (event != null) {
                logger.debug("Send event to trigger.");
                PluginImpl.getInstance().triggerEvent(event);
            }
        }
    }
}
