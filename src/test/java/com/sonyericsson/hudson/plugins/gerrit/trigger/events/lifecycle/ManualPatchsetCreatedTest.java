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

package com.sonyericsson.hudson.plugins.gerrit.trigger.events.lifecycle;

import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import net.sf.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.BRANCH;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.CHANGE;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.EMAIL;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.ID;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.NAME;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.NUMBER;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.OWNER;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.PATCH_SET;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.PROJECT;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.REF;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.REVISION;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.SUBJECT;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.URL;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class ManualPatchsetCreatedTest {
    /**
     * Tests {@link ManualPatchsetCreated#addListener(GerritEventLifecycleListener)}.
     * @throws Exception if the unfortunate happens.
     */
    @Test
    public void testAddListener() throws Exception {
        GerritEventLifecycleListener listener = mock(GerritEventLifecycleListener.class);
        TestableManualPatchset lifecycle = new TestableManualPatchset();
        lifecycle.addListener(listener);

        assertFalse(lifecycle.getListeners().isEmpty());
        assertSame(listener, lifecycle.getListeners().get(0));
    }

    /**
     * Tests {@link ManualPatchsetCreated#removeListener(GerritEventLifecycleListener)}.
     * @throws Exception if the unfortunate happens.
     */
    @Test
    public void testRemoveListener() throws Exception {
        GerritEventLifecycleListener listener = mock(GerritEventLifecycleListener.class);

        TestableManualPatchset lifecycle = new TestableManualPatchset();
        lifecycle.addListener(listener);
        assertFalse(lifecycle.getListeners().isEmpty());
        lifecycle.removeListener(listener);
        assertTrue(lifecycle.getListeners().isEmpty());
    }

    /**
     * Tests {@link ManualPatchsetCreated#fireTriggerScanStarting()}.
     * @throws Exception if the unfortunate happens.
     */
    @Test
    public void testFireTriggerScanStarting() throws Exception {
        GerritEventLifecycleListener listener = mock(GerritEventLifecycleListener.class);

        TestableManualPatchset lifecycle = new TestableManualPatchset();
        lifecycle.addListener(listener);

        lifecycle.fireTriggerScanStarting();

        verify(listener).triggerScanStarting(same(lifecycle));
        verifyNoMoreInteractions(listener);
    }

    /**
     * Tests {@link ManualPatchsetCreated#fireTriggerScanDone()}.
     * @throws Exception if the unfortunate happens.
     */
    @Test
    public void testFireTriggerScanDone() throws Exception {
        GerritEventLifecycleListener listener = mock(GerritEventLifecycleListener.class);

        TestableManualPatchset lifecycle = new TestableManualPatchset();
        lifecycle.addListener(listener);

        lifecycle.fireTriggerScanDone();

        verify(listener).triggerScanDone(same(lifecycle));
        verifyNoMoreInteractions(listener);
    }

    /**
     * Tests {@link ManualPatchsetCreated#fireProjectTriggered(hudson.model.Job)}.
     * @throws Exception if the unfortunate happens.
     */
    @Test
    public void testFireProjectTriggered() throws Exception {
        GerritEventLifecycleListener listener = mock(GerritEventLifecycleListener.class);
        AbstractProject project = mock(AbstractProject.class);
        TestableManualPatchset lifecycle = new TestableManualPatchset();
        lifecycle.addListener(listener);

        lifecycle.fireProjectTriggered(project);

        verify(listener).projectTriggered(same(lifecycle), same(project));
        verifyNoMoreInteractions(listener);
    }

    /**
     * Tests {@link ManualPatchsetCreated#fireBuildStarted(hudson.model.Run)}.
     * @throws Exception if the unfortunate happens.
     */
    @Test
    public void testFireBuildStarted() throws Exception {
        GerritEventLifecycleListener listener = mock(GerritEventLifecycleListener.class);
        AbstractBuild build = mock(AbstractBuild.class);
        TestableManualPatchset lifecycle = new TestableManualPatchset();
        lifecycle.addListener(listener);

        lifecycle.fireBuildStarted(build);

        verify(listener).buildStarted(same(lifecycle), same(build));
        verifyNoMoreInteractions(listener);
    }

    /**
     * Tests {@link ManualPatchsetCreated#fireBuildCompleted(hudson.model.Run)}.
     * @throws Exception if the unfortunate happens.
     */
    @Test
    public void testFireBuildCompleted() throws Exception {
        GerritEventLifecycleListener listener = mock(GerritEventLifecycleListener.class);
        AbstractBuild build = mock(AbstractBuild.class);
        TestableManualPatchset lifecycle = new TestableManualPatchset();
        lifecycle.addListener(listener);

        lifecycle.fireBuildCompleted(build);

        verify(listener).buildCompleted(same(lifecycle), same(build));
        verifyNoMoreInteractions(listener);
    }

    /**
     * Tests {@link ManualPatchsetCreated#fireAllBuildsCompleted()}.
     * @throws Exception if the unfortunate happens.
     */
    @Test
    public void testFireAllBuildsCompleted() throws Exception {
        GerritEventLifecycleListener listener = mock(GerritEventLifecycleListener.class);
        TestableManualPatchset lifecycle = new TestableManualPatchset();
        lifecycle.addListener(listener);

        lifecycle.fireAllBuildsCompleted();

        verify(listener).allBuildsCompleted(same(lifecycle));
        verifyNoMoreInteractions(listener);
    }

    /**
     * Sub class of Manualpatchset that expose lifecycle listeners.
     */
    static class TestableManualPatchset extends ManualPatchsetCreated {

        /**
         * Default constructor with test data.
         */
        TestableManualPatchset() {
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

            fromJson(jsonEvent);
        }

        /**
         * Make the method public for assertions.
         *
         * @return the list of listeners.
         */
        @Override
        public synchronized List<GerritEventLifecycleListener> getListeners() {
            return super.getListeners();
        }
    }
}
