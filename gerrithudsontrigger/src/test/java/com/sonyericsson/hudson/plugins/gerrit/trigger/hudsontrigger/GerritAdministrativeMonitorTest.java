/*
 * The MIT License
 *
 * Copyright 2012 Sony Mobile Communications AB. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ConnectionListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.version.GerritVersionChecker;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Tests for {@link GerritAdministrativeMonitor}.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(PluginImpl.class)
public class GerritAdministrativeMonitorTest {

    private PluginImpl plugin;

    /**
     * Static mocking setup of {@link #plugin}.
     */
    @Before
    public void setUp() {
        plugin = mock(PluginImpl.class);
        when(plugin.addListener(any(ConnectionListener.class))).thenReturn(true);
        PowerMockito.mockStatic(PluginImpl.class);
        when(PluginImpl.getInstance()).thenReturn(plugin);
    }

    /**
     * Tests {@link GerritAdministrativeMonitor#isGerritSnapshotVersion()} is true when it should.
     *
     * @throws Exception if so.
     */
    @Test
    public void testIsGerritSnapshotVersion() throws Exception {
        String version = "2.2.2.1-340-g47084d4";
        when(plugin.getGerritVersion()).thenReturn(version);
        GerritAdministrativeMonitor monitor = new GerritAdministrativeMonitor();
        assertTrue(monitor.isGerritSnapshotVersion());
    }

    /**
     * Tests {@link GerritAdministrativeMonitor#isGerritSnapshotVersion()} is false for an official version.
     *
     * @throws Exception if so.
     */
    @Test
    public void testIsGerritSnapshotVersionNot() throws Exception {
        String version = "2.2.2.1";
        when(plugin.getGerritVersion()).thenReturn(version);
        GerritAdministrativeMonitor monitor = new GerritAdministrativeMonitor();
        assertFalse(monitor.isGerritSnapshotVersion());
    }

    /**
     * Tests {@link GerritAdministrativeMonitor#isGerritSnapshotVersion()} is false for a RC version.
     *
     * @throws Exception if so.
     */
    @Test
    public void testIsGerritSnapshotVersionNotRc() throws Exception {
        String version = "2.3-rc0";
        when(plugin.getGerritVersion()).thenReturn(version);
        GerritAdministrativeMonitor monitor = new GerritAdministrativeMonitor();
        assertFalse(monitor.isGerritSnapshotVersion());
    }

    /**
     * Tests {@link GerritAdministrativeMonitor#getDisabledFeatures()}.
     *
     * @throws Exception if so.
     */
    @Test
    public void testGetDisabledFeatures() throws Exception {
        String version = "2.2.2.1";
        when(plugin.getGerritVersion()).thenReturn(version);
        GerritAdministrativeMonitor monitor = new GerritAdministrativeMonitor();
        List<GerritVersionChecker.Feature> disabledFeatures = monitor.getDisabledFeatures();
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
     * Tests {@link GerritAdministrativeMonitor#getDisabledFeatures()} is empty for version 2.5. TODO update this test's
     * version check whenever we get a new feature requiring a newer version.
     *
     * @throws Exception if so.
     */
    @Test
    public void testGetDisabledFeaturesNone() throws Exception {
        String version = "2.5";
        when(plugin.getGerritVersion()).thenReturn(version);
        GerritAdministrativeMonitor monitor = new GerritAdministrativeMonitor();
        List<GerritVersionChecker.Feature> disabledFeatures = monitor.getDisabledFeatures();
        assertTrue(disabledFeatures.isEmpty());
    }

    /**
     * Tests {@link GerritAdministrativeMonitor#getDisabledFeatures()} is true when it should be.
     *
     * @throws Exception if so.
     */
    @Test
    public void testHasDisabledFeatures() throws Exception {
        String version = "2.2.2.1";
        when(plugin.getGerritVersion()).thenReturn(version);
        GerritAdministrativeMonitor monitor = new GerritAdministrativeMonitor();
        assertTrue(monitor.hasDisabledFeatures());
    }

    /**
     * Tests {@link GerritAdministrativeMonitor#getDisabledFeatures()} is false when it should. TODO update this test's
     * version check whenever we get a new feature requiring a newer version.
     *
     * @throws Exception if so.
     */
    @Test
    public void testHasDisabledFeaturesNot() throws Exception {
        String version = "2.5";
        when(plugin.getGerritVersion()).thenReturn(version);
        GerritAdministrativeMonitor monitor = new GerritAdministrativeMonitor();
        assertFalse(monitor.hasDisabledFeatures());
    }
}
