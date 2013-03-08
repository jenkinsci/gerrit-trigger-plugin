/*
 * The MIT License
 *
 * Copyright 2011 Sony Ericsson Mobile Communications. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeMerged;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeRestored;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.DraftPublished;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.RefUpdated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.collection.IsIn.isIn;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

//CS IGNORE MagicNumber FOR NEXT 400 LINES. REASON: Test data.

/**
 * Tests for {@link GerritHandler}.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(SshConnectionFactory.class)
public class GerritHandlerTest {

    private SshConnection sshConnectionMock;
    private GerritHandler handler;
    private BufferedWriter pipedWriter;
    private PipedReader pipedReader;

    /**
     * Creates a SshConnection mock and starts a GerritHandler with that connection-mock.
     *
     * @throws Exception if so.
     */
    @Before
    public void setup() throws Exception {
        sshConnectionMock = mock(SshConnection.class);
        when(sshConnectionMock.isAuthenticated()).thenReturn(true);
        when(sshConnectionMock.isConnected()).thenReturn(true);
        PipedWriter piped = new PipedWriter();
        pipedReader = new PipedReader(piped);
        pipedWriter = new BufferedWriter(piped);
        when(sshConnectionMock.executeCommandReader(isA(String.class))).thenReturn(pipedReader);
        PowerMockito.mockStatic(SshConnectionFactory.class);
        PowerMockito.doReturn(sshConnectionMock).when(SshConnectionFactory.class, "getConnection",
                isA(String.class), isA(Integer.class), isA(String.class), isA(Authentication.class));
        handler = new GerritHandler("localhost", 29418, new Authentication(null, ""));
        handler.start();
        try {
            Thread.sleep(1000); //Lots and lots of timing issues here
        } catch (InterruptedException e) {
            System.out.println("Interrupted while sleeping.");
        }
    }

    /**
     * Shuts down the GerritHandler and the mocked connection.
     */
    @After
    public void shutDown() {
        if (handler != null) {
            handler.shutdown(false);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                System.out.println("Interrupted while sleeping.");
            }
            if (!handler.isShutdownInProgress()) {
                fail("Failed to set shutdown flag!");
            }
            try {
                pipedWriter.append("hello");
                pipedWriter.newLine();
                pipedWriter.close();
                handler.join();
            } catch (InterruptedException e) {
                System.err.println("interupted while waiting for handler to shut down.");
            } catch (IOException e) {
                System.err.println("Could not close the pipe.");
            }
        }
        handler = null;
        sshConnectionMock = null;
        pipedReader = null;
        pipedWriter = null;
    }

    /**
     * Tests {@link GerritHandler#addListener(GerritEventListener)}.
     *
     * @throws Exception if so.
     */
    @Test
    public void testAddListener() throws Exception {
        GerritEventListener listenerMock = mock(GerritEventListener.class);
        handler.addListener(listenerMock);
        Collection<GerritEventListener> gerritEventListeners =
                Whitebox.getInternalState(handler, "gerritEventListeners");
        assertThat(listenerMock, isIn(gerritEventListeners));
        assertEquals(1, gerritEventListeners.size());
    }

    /**
     * Tests {@link GerritHandler#addListener(GerritEventListener)}. With 10000 listeners added by 10 threads at the
     * same time.
     *
     * @throws Exception if so.
     */
    @Test
    public void testAddListenerManyAtTheSameTime() throws Exception {
        final int nrOfListeners = 100000;
        BlockingQueue<Runnable> listeners = new LinkedBlockingQueue<Runnable>(nrOfListeners);
        System.out.print("Creating Listeners");
        for (int i = 0; i < nrOfListeners; i++) {
            listeners.add(new Runnable() {
                GerritEventListener listener = new ListenerMock();
                @Override
                public void run() {
                    handler.addListener(listener);
                }
            });
            if (i % 1000 == 0) {
                System.out.print(".");
            }
        }
        System.out.println(".Done!");
        ThreadPoolExecutor executor = new ThreadPoolExecutor(100, 100, 1, TimeUnit.MINUTES, listeners);
        executor.prestartAllCoreThreads();
        executor.shutdown();
        do {
            System.out.printf("Waiting for listeners to be added...Running#: %5d  Left#: %5d  Count#: %5d\n",
                    executor.getActiveCount(), listeners.size(), handler.getEventListenersCount());
        } while (!executor.awaitTermination(1, TimeUnit.SECONDS));
        System.out.printf("              Listeners are added...Running#: %5d  Left#: %5d  Count#: %5d\n",
                    executor.getActiveCount(), listeners.size(), handler.getEventListenersCount());
        assertEquals(nrOfListeners, handler.getEventListenersCount());
    }

    /**
     * Tests {@link GerritHandler#addEventListeners(java.util.Map)}.
     *
     * @throws Exception if so.
     */
    @Test
    public void testAddEventListeners() throws Exception {
        Collection<GerritEventListener> listeners = new HashSet<GerritEventListener>();
        GerritEventListener listenerMock = mock(GerritEventListener.class);
        listeners.add(listenerMock);
        listenerMock = mock(GerritEventListener.class);
        listeners.add(listenerMock);
        listenerMock = mock(GerritEventListener.class);
        listeners.add(listenerMock);
        listenerMock = mock(GerritEventListener.class);
        listeners.add(listenerMock);
        listenerMock = mock(GerritEventListener.class);
        listeners.add(listenerMock);
        handler.addEventListeners(listeners);
        Collection<GerritEventListener> gerritEventListeners =
                Whitebox.getInternalState(handler, "gerritEventListeners");
        assertThat(listenerMock, isIn(gerritEventListeners));
        assertEquals(5, gerritEventListeners.size());
    }

    /**
     * Tests {@link GerritHandler#removeListener(GerritEventListener)}.
     *
     * @throws Exception if so.
     */
    @Test
    public void testRemoveListener() throws Exception {
        GerritEventListener listenerMock = mock(GerritEventListener.class);
        handler.addListener(listenerMock);
        handler.removeListener(listenerMock);
        Collection<GerritEventListener> gerritEventListeners =
                Whitebox.getInternalState(handler, "gerritEventListeners");
        assertTrue(gerritEventListeners.isEmpty());
    }

    /**
     * Tests {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritHandler#removeAllEventListeners()}.
     *
     * @throws Exception if so.
     */
    @Test
    public void testRemoveAllEventListeners() throws Exception {
        Collection<GerritEventListener> listeners = new HashSet<GerritEventListener>();
        GerritEventListener listenerMock = mock(GerritEventListener.class);
        listeners.add(listenerMock);
        listenerMock = mock(GerritEventListener.class);
        listeners.add(listenerMock);
        listenerMock = mock(GerritEventListener.class);
        listeners.add(listenerMock);
        listenerMock = mock(GerritEventListener.class);
        listeners.add(listenerMock);
        listenerMock = mock(GerritEventListener.class);
        listeners.add(listenerMock);
        handler.addEventListeners(listeners);
        listeners = handler.removeAllEventListeners();
        assertThat(listenerMock, isIn(listeners));
        assertEquals(5, listeners.size());
        listeners = Whitebox.getInternalState(handler, "gerritEventListeners");
        assertTrue(listeners.isEmpty());
    }

    /**
     * Tests {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritHandler#removeAllEventListeners()} when
     * one listener's hashCode has changed mid air.
     *
     * @throws Exception if so.
     */
    @Test
    public void testRemoveAllEventListenersOneChanged() throws Exception {
        Collection<GerritEventListener> listeners = new HashSet<GerritEventListener>();
        ListenerMock listenerMock = new ListenerMock();
        listeners.add(listenerMock);
        listenerMock = new ListenerMock();
        listeners.add(listenerMock);
        listenerMock = new ListenerMock();
        listeners.add(listenerMock);
        listenerMock = new ListenerMock();
        listeners.add(listenerMock);
        listenerMock = new ListenerMock();
        listeners.add(listenerMock);
        handler.addEventListeners(listeners);
        listenerMock.code = (10000);
        listeners = handler.removeAllEventListeners();
        assertThat(listenerMock, isIn(listeners));
        assertEquals(5, listeners.size());
        listeners = Whitebox.getInternalState(handler, "gerritEventListeners");
        assertTrue(listeners.isEmpty());
    }

    /**
     * Tests to remove all eventlisteners and then re add them.
     *
     * @throws Exception if so.
     */
    @Test
    public void testReAddAllEventListenersOneChanged() throws Exception {
        Collection<GerritEventListener> listeners = new HashSet<GerritEventListener>();
        ListenerMock listenerMock = new ListenerMock();
        listeners.add(listenerMock);
        listenerMock = new ListenerMock();
        listeners.add(listenerMock);
        listenerMock = new ListenerMock();
        listeners.add(listenerMock);
        listenerMock = new ListenerMock();
        listeners.add(listenerMock);
        listenerMock = new ListenerMock();
        listeners.add(listenerMock);
        handler.addEventListeners(listeners);
        listenerMock.code = (10000);
        listeners = handler.removeAllEventListeners();
        assertThat(listenerMock, isIn(listeners));
        assertEquals(5, listeners.size());
        Collection<GerritEventListener> gerritEventListeners =
                Whitebox.getInternalState(handler, "gerritEventListeners");
        assertTrue(gerritEventListeners.isEmpty());
        handler.addEventListeners(listeners);
        gerritEventListeners = Whitebox.getInternalState(handler, "gerritEventListeners");
        assertThat(listenerMock, isIn(gerritEventListeners));
        assertEquals(5, gerritEventListeners.size());
    }

    /**
     * Tests that circular CommentAdded events are ignored correctly.
     * @throws Exception if so.
     */
    @Test
    public void testIgnoreCommentAdded() throws Exception {
        String email = "e@mail.com";
        handler.setIgnoreEMail(email);
        ListenerMock listenerMock = mock(ListenerMock.class);
        Collection<GerritEventListener> listeners = new HashSet<GerritEventListener>();
        listeners.add(listenerMock);
        handler.addEventListeners(listeners);
        Account account = new Account("name", email);
        CommentAdded ca = new CommentAdded();
        ca.setAccount(account);
        handler.notifyListeners(ca);
        verifyNoMoreInteractions(listenerMock);
    }


    /**
     * A GerritListener mock that can change it's hashCode
     */
    static class ListenerMock implements GerritEventListener {
        private static int count = 0;
        int code;

        /**
         * Default constructor.
         */
        ListenerMock() {
            code = 54 + count++;
        }

        @Override
        public void gerritEvent(GerritEvent event) {

        }

        @Override
        public void gerritEvent(PatchsetCreated event) {

        }

        @Override
        public void gerritEvent(DraftPublished event) {

        }

        @Override
        public void gerritEvent(ChangeAbandoned event) {

        }

        @Override
        public void gerritEvent(ChangeMerged event) {

        }

        @Override
        public void gerritEvent(ChangeRestored event) {

        }

        @Override
        public void gerritEvent(CommentAdded event) {

        }

        @Override
        public void gerritEvent(RefUpdated event) {

        }

        @Override
        public int hashCode() {
            return code;
        }
    }
}
