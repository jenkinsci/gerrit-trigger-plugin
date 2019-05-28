/*
 *  The MIT License
 *
 *  Copyright 2013 rinrinne All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.config;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.junit.After;
import org.junit.Test;

import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventType;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

/**
 *
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public class PluginConfigTest {

    //CS IGNORE MagicNumber FOR NEXT 100 LINES. REASON: Mocks tests.

    /**
     * Resets the GerritEventType enum.
     */
    @After
    public void afterTest() {
     // TODO if an event type is added with a default other than true
        // in the future then this needs to be updated to check each
        // events default value.
        for (GerritEventType type : GerritEventType.values()) {
            type.setInteresting(true);
        }
    }

    /**
     * Test creation of a config object from form data.
     * filterIn using partial event list.
     */
    @Test
    public void testSetValues() {
        String events = "change-abandoned change-merged change-restored";
        String formString = "{"
                + "\"numberOfSendingWorkerThreads\":\"4\","
                + "\"numberOfReceivingWorkerThreads\":\"6\","
                + "\"filterIn\":\"" + events + "\"}";
        JSONObject form = (JSONObject)JSONSerializer.toJSON(formString);
        PluginConfig config = new PluginConfig(form);
        assertEquals(6, config.getNumberOfReceivingWorkerThreads());
        assertEquals(4, config.getNumberOfSendingWorkerThreads());
        assertEquals(Arrays.asList(events.split(" ")), config.getFilterIn());
        for (GerritEventType type : GerritEventType.values()) {
            if (events.contains(type.getTypeValue())) {
                assertEquals(true, type.isInteresting());
            } else {
                assertEquals(false, type.isInteresting());
            }
        }
    }

    //CS IGNORE MagicNumber FOR NEXT 100 LINES. REASON: Mocks tests.

    /**
     * Test creation of a config object from an existing one.
     * filterIn using empty event list.
     */
    @Test
    public void testCopyConfig() {
        String events = "";
        String formString = "{"
                + "\"numberOfSendingWorkerThreads\":\"4\","
                + "\"numberOfReceivingWorkerThreads\":\"6\","
                + "\"filterIn\":\"" + events + "\"}";
        JSONObject form = (JSONObject)JSONSerializer.toJSON(formString);
        PluginConfig initialConfig = new PluginConfig(form);
        PluginConfig config = new PluginConfig(initialConfig);
        assertEquals(6, config.getNumberOfReceivingWorkerThreads());
        assertEquals(4, config.getNumberOfSendingWorkerThreads());
        assertEquals(Arrays.asList(events.split(" ")), config.getFilterIn());
        for (GerritEventType type : GerritEventType.values()) {
            assertEquals(false, type.isInteresting());
        }
    }

    /**
     * Test empty filterIn form data which should result in default settings
     * for event filter.
     */
    @Test
    public void testDefaultEventFilter() {
        List<String> defaultEventFilter = PluginConfig.getDefaultEventFilter();
        String events = "null";
        String formString = "{"
                + "\"numberOfSendingWorkerThreads\":\"4\","
                + "\"numberOfReceivingWorkerThreads\":\"6\","
                + "\"filterIn\":\"" + events + "\"}";
        JSONObject form = (JSONObject)JSONSerializer.toJSON(formString);
        new PluginConfig(form);
        for (GerritEventType type : GerritEventType.values()) {
            if (defaultEventFilter.contains(type.getTypeValue())) {
                assertEquals(true, type.isInteresting());
            } else {
                assertEquals(false, type.isInteresting());
            }
        }
    }
}
