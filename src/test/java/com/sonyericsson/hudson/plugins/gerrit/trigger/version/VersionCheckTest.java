/*
 *  The MIT License
 *
 *  Copyright 2012 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.version;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Tests for the version checking of Gerrit.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ PluginImpl.class })
public class VersionCheckTest {

    private final String testServer = "server";
    private GerritServer server;

    /**
     * Pre setup for all tests.
     */
    @Before
    public void setup() {
        mockStatic(PluginImpl.class);
        PluginImpl plugin = mock(PluginImpl.class);
        server = mock(GerritServer.class);
        when(PluginImpl.getInstance()).thenReturn(plugin);
        when(plugin.getServer(testServer)).thenReturn(server);
        when(PluginImpl.getServer_(eq(testServer))).thenReturn(server);
    }

    /**
     * Tests that the gerrit version is high enough to run the file trigger feature.
     */
    @Test
    public void testHighEnoughVersion() {
        when(server.getGerritVersion()).thenReturn("2.3.1-450");
        assertTrue(GerritVersionChecker.isCorrectVersion(GerritVersionChecker.Feature.fileTrigger, testServer));
    }

    /**
     * Tests that the gerrit version is not high enough to run the file trigger feature.
     */
    @Test
    public void testNotHighEnoughVersion() {
        when(server.getGerritVersion()).thenReturn("2.2.2.1-150");
        assertFalse(GerritVersionChecker.isCorrectVersion(GerritVersionChecker.Feature.fileTrigger, testServer));
    }

    /**
     * Tests that the gerrit version is not high enough to run the file trigger feature.
     */
    @Test
    public void testUnknownVersionEmpty() {
        when(server.getGerritVersion()).thenReturn("");
        assertTrue(GerritVersionChecker.isCorrectVersion(GerritVersionChecker.Feature.fileTrigger, testServer));
    }

    /**
     * Tests that the gerrit version is not high enough to run the file trigger feature.
     */
    @Test
    public void testUnknownVersionNull() {
        when(server.getGerritVersion()).thenReturn(null);
        assertTrue(GerritVersionChecker.isCorrectVersion(GerritVersionChecker.Feature.fileTrigger , testServer));
    }

    /**
     * Tests that the gerrit version is a snapshot and therefore high enough to run the file trigger feature..
     */
    @Test
    public void testSnapshotVersion() {
        String version = "2.2.2.1-340-g47084d4";
        when(server.getGerritVersion()).thenReturn(version);
        assertTrue(GerritVersionChecker.isCorrectVersion(GerritVersionChecker.Feature.fileTrigger , testServer));
        assertTrue(GerritVersionNumber.getGerritVersionNumber(version).isSnapshot());
    }
}
