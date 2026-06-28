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

import com.sonyericsson.jenkins.plugins.bfa.test.utils.Whitebox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockedStatic;

import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GerritServer}.
 */
public class GerritServerTest {

    /**
     * Jenkins rule instance.
     */
    private JenkinsRule j;

    private final String gerritServerOneName = "testServer1";
    private GerritServer gerritServerOne;
    private GerritConnectionListener listener;
    private MockedStatic<PluginImpl> pluginMockedStatic;

    /**
     * Setup the mock'ed environment.
     */
    @BeforeEach
    void setup() {
        PluginImpl plugin = mock(PluginImpl.class);
        pluginMockedStatic = mockStatic(PluginImpl.class);
        pluginMockedStatic.when(PluginImpl::getInstance).thenReturn(plugin);
        gerritServerOne = new GerritServer(gerritServerOneName);
        listener = new GerritConnectionListener(gerritServerOne.getName());
        listener.setConnected(true);
        Whitebox.setInternalState(gerritServerOne, "gerritConnectionListener", listener);
        gerritServerOne = spy(gerritServerOne);
        when(plugin.getServer(eq(gerritServerOneName))).thenReturn(gerritServerOne);
    }

    @AfterEach
    void tearDown() {
        pluginMockedStatic.close();
    }

    /**
     * Tests {@link GerritServer#isGerritSnapshotVersion()} is true when it should.
     *
     */
    @Test
    void testIsGerritSnapshotVersion() {
        String version = "2.2.2.1-340-g47084d4";
        when(gerritServerOne.getGerritVersion()).thenReturn(version);
        listener.checkGerritVersionFeatures();
        assertTrue(gerritServerOne.isGerritSnapshotVersion());
    }

    /**
     * Tests {@link GerritServer#isGerritSnapshotVersion()} is false for an official version.
     *
     */
    @Test
    void testIsGerritSnapshotVersionNot() {
        String version = "2.2.2.1";
        when(gerritServerOne.getGerritVersion()).thenReturn(version);
        listener.checkGerritVersionFeatures();
        assertFalse(gerritServerOne.isGerritSnapshotVersion());
    }

    /**
     * Tests {@link GerritServer#isGerritSnapshotVersion()} is false for a RC version.
     *
     */
    @Test
    void testIsGerritSnapshotVersionNotRc() {
        String version = "2.3-rc0";
        when(gerritServerOne.getGerritVersion()).thenReturn(version);
        listener.checkGerritVersionFeatures();
        assertFalse(gerritServerOne.isGerritSnapshotVersion());
    }

    /**
     * Tests {@link GerritServer#getDisabledFeatures()}.
     *
     */
    @Test
    void testGetDisabledFeatures() {
        String version = "2.2.2.1";
        when(gerritServerOne.getGerritVersion()).thenReturn(version);
        listener.checkGerritVersionFeatures();

        List<GerritVersionChecker.Feature> disabledFeatures = new LinkedList<>();
        if (gerritServerOne.hasDisabledFeatures()) {
            disabledFeatures = gerritServerOne.getDisabledFeatures();
        }
        assertFalse(disabledFeatures.isEmpty());
        boolean foundFileTrigger = false;
        for (GerritVersionChecker.Feature feature : disabledFeatures) {
            if (feature == GerritVersionChecker.Feature.fileTrigger) {
                foundFileTrigger = true;
                break;
            }
        }
        assertTrue(foundFileTrigger, "Expected to find the file trigger feature!");
    }

    /**
     * Tests {@link GerritServer#getDisabledFeatures()} is empty for version 2.9. TODO update this test's
     * version check whenever we get a new feature requiring a newer version.
     *
     */
    @Test
    void testGetDisabledFeaturesNone() {
        String version = "3.3";
        when(gerritServerOne.getGerritVersion()).thenReturn(version);
        listener.checkGerritVersionFeatures();

        List<GerritVersionChecker.Feature> disabledFeatures = gerritServerOne.getDisabledFeatures();
        assertTrue(disabledFeatures.isEmpty());
    }

    /**
     * Tests {@link GerritServer#getDisabledFeatures()} is true when it should be.
     *
     */
    @Test
    void testHasDisabledFeatures() {
        String version = "2.2.2.1";
        when(gerritServerOne.getGerritVersion()).thenReturn(version);
        listener.checkGerritVersionFeatures();

        assertTrue(gerritServerOne.hasDisabledFeatures());
    }

    /**
     * Tests {@link GerritServer#getDisabledFeatures()} is false when it should. TODO update this test's
     * version check whenever we get a new feature requiring a newer version.
     *
     */
    @Test
    void testHasDisabledFeaturesNot() {
        String version = "3.3";
        when(gerritServerOne.getGerritVersion()).thenReturn(version);
        listener.checkGerritVersionFeatures();

        assertFalse(gerritServerOne.hasDisabledFeatures());
    }
}
