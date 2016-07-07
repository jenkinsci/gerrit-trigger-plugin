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

import com.sonymobile.tools.gerrit.gerritevents.GerritSendCommandQueue;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Tests for {@link GerritAdministrativeMonitor}.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritAdministrativeMonitorTest {

    /**
     * Tests {@link GerritAdministrativeMonitor#isSendQueueWarning()} is true when it should.
     *
     * @throws Exception if so.
     */
    @Test
    public void testIsSendQueueWarningWithEqualThreshold() throws Exception {
        GerritAdministrativeMonitor monitor = new GerritAdministrativeMonitor();
        monitor = spy(monitor);
        when(monitor.getSendQueueSize()).thenReturn(GerritSendCommandQueue.SEND_QUEUE_SIZE_WARNING_THRESHOLD);
        assertTrue(monitor.isSendQueueWarning());
    }

    /**
     * Tests {@link GerritAdministrativeMonitor#isSendQueueWarning()} is true when it should.
     *
     * @throws Exception if so.
     */
    @Test
    public void testIsSendQueueWarningWithOverThreshold() throws Exception {
        GerritAdministrativeMonitor monitor = new GerritAdministrativeMonitor();
        monitor = spy(monitor);
        when(monitor.getSendQueueSize()).thenReturn(GerritSendCommandQueue.SEND_QUEUE_SIZE_WARNING_THRESHOLD + 1);
        assertTrue(monitor.isSendQueueWarning());
    }

    /**
     * Tests {@link GerritAdministrativeMonitor#isSendQueueWarning()} is false when it should.
     *
     * @throws Exception if so.
     */
    @Test
    public void testIsSendQueueWarningWithUnderThreshold() throws Exception {
        GerritAdministrativeMonitor monitor = new GerritAdministrativeMonitor();
        monitor = spy(monitor);
        when(monitor.getSendQueueSize()).thenReturn(GerritSendCommandQueue.SEND_QUEUE_SIZE_WARNING_THRESHOLD - 1);
        assertFalse(monitor.isSendQueueWarning());
    }
}
