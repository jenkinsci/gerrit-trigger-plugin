/*
 * The MIT License
 *
 * Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventType;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import net.sf.json.JSONObject;
import org.junit.Test;

import java.util.concurrent.BlockingQueue;

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.BRANCH;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.CHANGE;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.EMAIL;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.ID;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.NAME;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.NUMBER;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.OWNER;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PATCH_SET;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PROJECT;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.REF;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.REVISION;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.SUBJECT;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.StreamEventsStringWork}.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class StreamEventsStringWorkTest {
    /**
     * Tests {@link StreamEventsStringWork#perform(Coordinator)}. with a standard scenario.
     * @throws Exception if so.
     */
    @Test
    public void testPerform() throws Exception {
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

        JSONObject jsonEvent = new JSONObject();
        jsonEvent.put("type", GerritEventType.PATCHSET_CREATED.getTypeValue());
        jsonEvent.put(CHANGE, change);
        jsonEvent.put(PATCH_SET, patch);

        StreamEventsStringWork work = new StreamEventsStringWork(jsonEvent.toString());

        final GerritEvent[] notifiedEvent = {null};
        Coordinator coordinator = new Coordinator() {

            @Override
            public BlockingQueue<Work> getWorkQueue() {
                return mock(BlockingQueue.class);
            }
            @Override
            public void notifyListeners(GerritEvent event) {
                notifiedEvent[0] = event;
            }

            @Override
            public void reconnect() {
                //nada
            }
        };
        work.perform(coordinator);

        assertNotNull(notifiedEvent[0]);
        PatchsetCreated event = (PatchsetCreated)notifiedEvent[0];
        assertEquals("project", event.getChange().getProject());
        assertEquals("100", event.getChange().getNumber());
        assertEquals("2", event.getPatchSet().getNumber());
    }
}
