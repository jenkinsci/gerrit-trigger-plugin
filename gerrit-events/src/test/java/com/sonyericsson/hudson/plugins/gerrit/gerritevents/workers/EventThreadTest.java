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

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.EventThread}.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(LoggerFactory.class)
public class EventThreadTest {
    private static Logger mockLogger;

    //CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: Test data.

    /**
     * Creates a mock of the logger before this test-class starts.
     */
    @BeforeClass
    public static void beforeClass() {
        mockLogger = mock(Logger.class);
        PowerMockito.mockStatic(LoggerFactory.class);
        when(LoggerFactory.getLogger(EventThread.class)).thenReturn(mockLogger);
    }

    //CS IGNORE EmptyBlock FOR NEXT 60 LINES. REASON: Expected test behaviour.

    /**
     * Tests {@link EventThread#run()}.
     * @throws Exception if so.
     */
    @Test(timeout = 1000)
    public void testRun() throws Exception {
        Coordinator coordinator = mock(Coordinator.class);
        BlockingQueue<Work> queue = mock(BlockingQueue.class);
        Work work = mock(Work.class);
        doThrow(new RuntimeException("Stop the loop!")).when(work).perform(coordinator);
        when(queue.take()).thenReturn(work);
        when(coordinator.getWorkQueue()).thenReturn(queue);

        EventThread thread = new EventThread(coordinator);

        try {
            thread.run();
        } catch (RuntimeException re) {
            //Expected
        }
        verify(work).perform(same(coordinator));

    }

    /**
     * Tests {@link EventThread#run()} when it gets interrupted when taking something from the queue.
     * @throws Exception if so.
     */
    @Test(timeout = 1000)
    public void testRunInterrupted() throws Exception {
        Coordinator coordinator = mock(Coordinator.class);
        BlockingQueue<Work> queue = mock(BlockingQueue.class);
        InterruptedException interruptedException = new InterruptedException("You are so cool!");
        doThrow(interruptedException).when(queue).take();
        when(coordinator.getWorkQueue()).thenReturn(queue);

        doThrow(new RuntimeException("Stop the loop!")).
                when(mockLogger).debug(any(String.class), same(interruptedException));

        EventThread thread = new EventThread(coordinator);
        try {
            thread.run();
        } catch (RuntimeException re) {
            //Expected
        }
        verify(mockLogger).debug(any(String.class), same(interruptedException));
    }

    /**
     * Tests {@link EventThread#shutdown()}.
     * @throws Exception if so.
     */
    @Test(timeout = 1500)
    public void testShutdown() throws Exception {

        Coordinator coordinator = mock(Coordinator.class);
        BlockingQueue<Work> queue = mock(BlockingQueue.class);
        Work work = mock(Work.class);
        when(queue.take()).thenReturn(work);
        when(coordinator.getWorkQueue()).thenReturn(queue);

        final EventThread thread = new EventThread(coordinator);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                thread.shutdown();
                return null;
            }
        }).when(work).perform(coordinator);

        thread.start();
        thread.join();

        verify(work).perform(same(coordinator));
        verify(mockLogger).debug(eq("Shutting down worker: {}"), same(thread));
        Boolean shutdown = Whitebox.getInternalState(thread, "shutdown");
        assertTrue(shutdown);
    }
}
