/*
 * The MIT License
 *
 * Copyright 2014 Ericsson.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.replication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import hudson.AbortException;
import hudson.model.Environment;
import hudson.model.AbstractBuild;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests {@link com.sonyericsson.hudson.plugins.gerrit.trigger.replication.ReplicationFailedHandler}.
 * @author Hugo Arès &lt;hugo.ares@ericsson.com&gt;
 *
 */
public class ReplicationFailedHandlerTest {

    private ReplicationFailedHandler handler;
    private AbstractBuild<?, ?> abstractBuildMock;

    /**
     * Create ReplicationFailedHandler and a mocked AbstractBuild.
     */
    @Before
    public void setUp() {
        handler = new ReplicationFailedHandler();
        abstractBuildMock = mock(AbstractBuild.class);
    }

    /**
     * Test that setUpEnvironment return an empty Environment when build does not contain
     * a ReplicationFailedAction.
     * @throws IOException if test is failing
     * @throws InterruptedException if test is failing
     */
    @Test
    public void shouldReturnAnEmptyEnvironmentWhenActionNotFound() throws IOException, InterruptedException {
        Environment env = handler.setUpEnvironment(abstractBuildMock, null, null);
        assertNotNull(env);
    }

    /**
     * Test that setUpEnvironment throws an AbortException when build does contain
     * a ReplicationFailedAction.
     * @throws IOException if test is failing
     * @throws InterruptedException if test is failing
     */
    @Test
    public void shouldThrowAbortExceptionWheReplicationFailedActionIsFound() throws IOException, InterruptedException {
        when(abstractBuildMock.getAction(ReplicationFailedAction.class)).thenReturn(
            new ReplicationFailedAction("someReason"));
        try {
            handler.setUpEnvironment(abstractBuildMock, null, null);
            fail("should have raise an AbortException");
        } catch (AbortException e) {
            assertEquals("someReason", e.getMessage());
        }
    }
}
