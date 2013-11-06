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
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public class PluginConfigTest {

    //CS IGNORE MagicNumber FOR NEXT 100 LINES. REASON: Mocks tests.

    /**
     * test.
     */
    @Test
    public void testSetValues() {
        String formString = "{"
                + "\"numberOfSendingWorkerThreads\":\"4\","
                + "\"numberOfReceivingWorkerThreads\":\"6\"}";
        JSONObject form = (JSONObject)JSONSerializer.toJSON(formString);
        PluginConfig config = new PluginConfig(form);
        assertEquals(6, config.getNumberOfReceivingWorkerThreads());
        assertEquals(4, config.getNumberOfSendingWorkerThreads());
    }

    //CS IGNORE MagicNumber FOR NEXT 100 LINES. REASON: Mocks tests.

    /**
     * Test creation of a config object from an existing one.
     */
    @Test
    public void testCopyConfig() {
        String formString = "{"
                + "\"numberOfSendingWorkerThreads\":\"4\","
                + "\"numberOfReceivingWorkerThreads\":\"6\"}";
        JSONObject form = (JSONObject)JSONSerializer.toJSON(formString);
        PluginConfig initialConfig = new PluginConfig(form);
        PluginConfig config = new PluginConfig(initialConfig);
        assertEquals(6, config.getNumberOfReceivingWorkerThreads());
        assertEquals(4, config.getNumberOfSendingWorkerThreads());
    }
}
