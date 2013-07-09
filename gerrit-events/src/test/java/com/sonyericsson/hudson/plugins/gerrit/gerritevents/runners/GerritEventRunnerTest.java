/*
 * The MIT License
 *
 * Copyright 2013 rinrinne All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.runners;

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.BRANCH;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.EMAIL;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.ID;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.NAME;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.NUMBER;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.OWNER;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PROJECT;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.REF;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.REVISION;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.SUBJECT;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.URL;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.sf.json.JSONObject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritConnectionEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.Handler;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Provider;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ManualPatchsetCreated;

//CS IGNORE MagicNumber FOR NEXT 400 LINES. REASON: Test data.

/**
 * Tests for {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.runner.GerritEventRunner}.
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public class GerritEventRunnerTest {

    private Handler handler;
    private final List<GerritEvent> handledEvent = new ArrayList<GerritEvent>();

    /**
     * Setup before each tests.
     * @throws Exception if so.
     */
    @Before
    public void setUp() throws Exception {
        handler = new Handler() {

            @Override
            public void post(JSONObject json) {
            }

            @Override
            public void post(String data) {
            }

            @Override
            public void post(String data, Provider provider) {
            }

            @Override
            public void handleEvent(GerritConnectionEvent event) {
            }

            @Override
            public void handleEvent(GerritEvent event) {
                handledEvent.add(event);
            }
        };
    }

    /**
     * Cleanup after each tests.
     * @throws Exception if so.
     */
    @After
    public void tearDown() throws Exception {
        handledEvent.clear();
        handler = null;
    }

    /**
     * Tests {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.GerritEventRunner}.
     * With a standard scenario.
     */
    @Test
    public void test() {
        JSONObject patch = new JSONObject();
        patch.put(NUMBER, "2");
        patch.put(REVISION, "ad123456789");
        patch.put(REF, "refs/changes/00/100/2");

        JSONObject jsonAccount = new JSONObject();
        jsonAccount.put(EMAIL, "robert.sandell@sonyericsson.com");
        jsonAccount.put(NAME, "Bobby");

        JSONObject change = new JSONObject();
        change.put(PROJECT, "project");
        change.put(BRANCH, "branch");
        change.put(ID, "I2343434344");
        change.put(NUMBER, "100");
        change.put(SUBJECT, "subject");
        change.put(OWNER, jsonAccount);
        change.put(URL, "http://localhost:8080");

        ManualPatchsetCreated event = new ManualPatchsetCreated(change, patch, "bobby");

        try {
            GerritEventRunner runner = new GerritEventRunner(handler, event);
            runner.run();
            assertSame(event, handledEvent.get(0));
        } catch (IOException ex) {
            fail("IOException!!");
        }
    }

}
