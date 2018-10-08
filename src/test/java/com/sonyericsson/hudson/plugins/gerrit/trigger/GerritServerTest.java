/*
 * The MIT License
 *
 * Copyright 2012 Sony Mobile Communications Inc. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritConnectionListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.version.GerritVersionChecker;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.spy;

/**
 * Tests for {@link GerritServer}.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(PluginImpl.class)
@PowerMockIgnore({"javax.crypto.*" })
public class GerritServerTest {

    /**
     * Jenkins rule instance.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 3 LINES. REASON: Mocks tests.
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private final String gerritServerOneName = "testServer1";
    private GerritServer gerritServerOne;
    private GerritConnectionListener listener;

    /**
     * Setup the mock'ed environment.
     */
    @Before
    public void setup() {
        PluginImpl plugin = mock(PluginImpl.class);
        mockStatic(PluginImpl.class);
        when(PluginImpl.getInstance()).thenReturn(plugin);
        gerritServerOne = new GerritServer(gerritServerOneName);
        listener = new GerritConnectionListener(gerritServerOne.getName());
        listener.setConnected(true);
        Whitebox.setInternalState(gerritServerOne, "gerritConnectionListener", listener);
        gerritServerOne = spy(gerritServerOne);
        when(plugin.getServer(eq(gerritServerOneName))).thenReturn(gerritServerOne);
    }

    /**
     * Tests {@link GerritServer#isGerritSnapshotVersion()} is true when it should.
     *
     * @throws Exception if so.
     */
    @Test
    public void testIsGerritSnapshotVersion() throws Exception {
        String version = "2.2.2.1-340-g47084d4";
        when(gerritServerOne.getGerritVersion()).thenReturn(version);
        listener.checkGerritVersionFeatures();
        assertTrue(gerritServerOne.isGerritSnapshotVersion());
    }

    /**
     * Tests {@link GerritServer#isGerritSnapshotVersion()} is false for an official version.
     *
     * @throws Exception if so.
     */
    @Test
    public void testIsGerritSnapshotVersionNot() throws Exception {
        String version = "2.2.2.1";
        when(gerritServerOne.getGerritVersion()).thenReturn(version);
        listener.checkGerritVersionFeatures();
        assertFalse(gerritServerOne.isGerritSnapshotVersion());
    }

    /**
     * Tests {@link GerritServer#isGerritSnapshotVersion()} is false for a RC version.
     *
     * @throws Exception if so.
     */
    @Test
    public void testIsGerritSnapshotVersionNotRc() throws Exception {
        String version = "2.3-rc0";
        when(gerritServerOne.getGerritVersion()).thenReturn(version);
        listener.checkGerritVersionFeatures();
        assertFalse(gerritServerOne.isGerritSnapshotVersion());
    }

    /**
     * Tests {@link GerritServer#getDisabledFeatures()}.
     *
     * @throws Exception if so.
     */
    @Test
    public void testGetDisabledFeatures() throws Exception {
        String version = "2.2.2.1";
        when(gerritServerOne.getGerritVersion()).thenReturn(version);
        listener.checkGerritVersionFeatures();

        List<GerritVersionChecker.Feature> disabledFeatures = new LinkedList<GerritVersionChecker.Feature>();
        if (gerritServerOne.hasDisabledFeatures()) {
            disabledFeatures = gerritServerOne.getDisabledFeatures();
        }
        assertFalse(disabledFeatures.isEmpty());
        boolean foundFileTrigger = false;
        for (GerritVersionChecker.Feature feature : disabledFeatures) {
            if (feature == GerritVersionChecker.Feature.fileTrigger) {
                foundFileTrigger = true;
            }
        }
        assertTrue("Expected to find the file trigger feature!", foundFileTrigger);
    }

    /**
     * Tests {@link GerritServer#getDisabledFeatures()} is empty for version 2.9. TODO update this test's
     * version check whenever we get a new feature requiring a newer version.
     *
     * @throws Exception if so.
     */
    @Test
    public void testGetDisabledFeaturesNone() throws Exception {
        String version = "2.15";
        when(gerritServerOne.getGerritVersion()).thenReturn(version);
        listener.checkGerritVersionFeatures();

        List<GerritVersionChecker.Feature> disabledFeatures = gerritServerOne.getDisabledFeatures();
        assertTrue(disabledFeatures.isEmpty());
    }

    /**
     * Tests {@link GerritServer#getDisabledFeatures()} is true when it should be.
     *
     * @throws Exception if so.
     */
    @Test
    public void testHasDisabledFeatures() throws Exception {
        String version = "2.2.2.1";
        when(gerritServerOne.getGerritVersion()).thenReturn(version);
        listener.checkGerritVersionFeatures();

        assertTrue(gerritServerOne.hasDisabledFeatures());
    }

    /**
     * Tests {@link GerritServer#getDisabledFeatures()} is false when it should. TODO update this test's
     * version check whenever we get a new feature requiring a newer version.
     *
     * @throws Exception if so.
     */
    @Test
    public void testHasDisabledFeaturesNot() throws Exception {
        String version = "2.15";
        when(gerritServerOne.getGerritVersion()).thenReturn(version);
        listener.checkGerritVersionFeatures();

        assertFalse(gerritServerOne.hasDisabledFeatures());
    }
}
