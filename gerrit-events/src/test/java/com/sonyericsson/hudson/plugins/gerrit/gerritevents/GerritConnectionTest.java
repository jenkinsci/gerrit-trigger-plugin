/*
 * The MIT License
 *
 * Copyright 2011 Sony Ericsson Mobile Communications. All rights reserved.
 * Copyright 2012 Sony Mobile Communications AB. All rights reserved.
 * Copyright 2012 rinrinne All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;

import java.util.concurrent.CountDownLatch;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Provider;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectionFactory;

//CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: TestData


/**
 * Tests for {@link GerritConnection}.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(SshConnectionFactory.class)
public class GerritConnectionTest {

    private static SshConnection sshConnectionMock;
    private static GerritConnection connection;
    private static BufferedWriter pipedWriter;
    private static PipedReader pipedReader;

    private static CountDownLatch establishedLatch = new CountDownLatch(1);
    private static CountDownLatch finishLatch = new CountDownLatch(1);
    private static CountDownLatch downLatch = new CountDownLatch(1);

    private static final String FINISH_WORD = "FINISH";
    /**
     * Creates a SshConnection mock and starts a GerritConnection with that connection-mock.
     *
     * @throws Exception if so.
     */
    @BeforeClass
    public static void setUp() throws Exception {
        sshConnectionMock = mock(SshConnection.class);
        when(sshConnectionMock.isAuthenticated()).thenReturn(true);
        when(sshConnectionMock.isConnected()).thenReturn(true);
        PipedWriter piped = new PipedWriter();
        pipedReader = new PipedReader(piped);
        pipedWriter = new BufferedWriter(piped);
        when(sshConnectionMock.executeCommand(eq("gerrit version"))).thenReturn("gerrit version 2.5.2");
        when(sshConnectionMock.executeCommandReader(eq("gerrit stream-events"))).thenReturn(pipedReader);
        PowerMockito.mockStatic(SshConnectionFactory.class);
        PowerMockito.doReturn(sshConnectionMock).when(SshConnectionFactory.class, "getConnection",
                isA(String.class), isA(Integer.class), isA(String.class), isA(Authentication.class));
        connection = new GerritConnection("", "localhost", 29418, new Authentication(null, ""));
        connection.setHandler(new HandlerMock());
        connection.start();
        try {
            establishedLatch.await();
        } catch (InterruptedException e) {
            System.out.println("Interrupted while sleeping.");
        }
        assertTrue(connection.isConnected());
        assertFalse(connection.isShutdownInProgress());
    }

    /**
     * Shuts down the GerritConnection and the mocked connection.
     */
    @AfterClass
    public static void shutDown() {
        try {
            finishLatch.await();
        } catch (InterruptedException e) {
            System.out.println("Interrupted while sleeping.");
        }
        if (connection != null) {
            connection.shutdown(false);
            assertTrue(connection.isShutdownInProgress());

            try {
                pipedWriter.append("hello");
                pipedWriter.newLine();
                pipedWriter.close();
                downLatch.await();
                assertFalse(connection.isConnected());
                connection.join();
            } catch (InterruptedException e) {
                System.err.println("interupted while waiting for connection to shut down.");
            } catch (IOException e) {
                System.err.println("Could not close the pipe.");
            }
        }
        connection = null;
        sshConnectionMock = null;
        pipedReader = null;
        pipedWriter = null;
    }

    /**
     * Tests {@link GerritConnection#getGerritVersion()}.
     */
    @Test
    public void testGetGerritVersion() {
        assertEquals("2.5.2", connection.getGerritVersion());
    }

    /**
     * Tests {@link GerritConnection#getGerritHostName()}.
     */
    @Test
    public void testGetGerritHostName() {
        assertEquals("localhost", connection.getGerritHostName());
    }

    /**
     * Tests {@link GerritConnection#getAuthentication()}.
     */
    @Test
    public void testGetAuthentication() {
        assertEquals(null, connection.getAuthentication().getPrivateKeyFile());
        assertEquals("", connection.getAuthentication().getUsername());
        assertEquals(null, connection.getAuthentication().getPrivateKeyFilePassword());
    }

    /**
     * Tests {@link GerritConnection#getGerritSshPort()}.
     */
    @Test
    public void testGetGerritSshPort() {
        assertEquals(29418, connection.getGerritSshPort());
    }

    /**
     * Tests {@link GerritConnection#getGerritProxy()}.
     */
    @Test
    public void testGetGerritProxy() {
        assertEquals("", connection.getGerritProxy());
    }

    /**
     * Tests stream event receiver for {@link GerritConnection}.
     *
     * @throws Exception if so.
     */
    @Test
    public void testReceiveEvent() throws Exception {
        // String
        pipedWriter.append("Test");
        pipedWriter.newLine();
        pipedWriter.flush();
        // JSON String
        pipedWriter.append("{\"say\":\"hello\"}");
        pipedWriter.newLine();
        pipedWriter.flush();
        // Send finish
        pipedWriter.append(FINISH_WORD);
        pipedWriter.newLine();
        pipedWriter.flush();
    }

    /**
     * A Handler mock
     */
    static class HandlerMock extends GerritHandler {

        @Override
        public void post(String data) {
            post(data, null);
        }

        @Override
        public void post(String data, Provider provider) {
            System.out.println("INFO: Posted string: " + data);
            if (provider != null) {
                System.out.println("INFO: Posted " + provider);
            }
            if (data.equals(FINISH_WORD)) {
                finishLatch.countDown();
            }
        }

        @Override
        public void notifyConnectionEstablished() {
            System.out.println("INFO: Handled connection established");
            establishedLatch.countDown();
        }

        @Override
        public void notifyConnectionDown() {
            System.out.println("INFO: Handled connection down");
            downLatch.countDown();
        }
    }
}
