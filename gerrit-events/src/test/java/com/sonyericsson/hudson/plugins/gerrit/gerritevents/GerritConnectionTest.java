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

import static org.junit.Assert.fail;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

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

    private SshConnection sshConnectionMock;
    private GerritConnection connection;
    private BufferedWriter pipedWriter;
    private PipedReader pipedReader;
    private Thread connectionThread;
    /**
     * Creates a SshConnection mock and starts a GerritConnection with that connection-mock.
     *
     * @throws Exception if so.
     */
    @Before
    public void setUp() throws Exception {
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
        connection = new GerritConnection("localhost", 29418, new Authentication(null, ""));
        connectionThread = new Thread(connection);
        connectionThread.start();
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
        if (connection != null) {
            connection.shutdown();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                System.out.println("Interrupted while sleeping.");
            }
            if (!connection.isShutdownInProgress()) {
                fail("Failed to set shutdown flag!");
            }
            try {
                pipedWriter.append("hello");
                pipedWriter.newLine();
                pipedWriter.close();
                connectionThread.join();
            } catch (InterruptedException e) {
                System.err.println("interupted while waiting for connection to shut down.");
            } catch (IOException e) {
                System.err.println("Could not close the pipe.");
            }
        }
        connection = null;
        connectionThread = null;
        sshConnectionMock = null;
        pipedReader = null;
        pipedWriter = null;
    }

    /**
     * Test skelton.
     */
    @Test
    public void test() {
        System.out.println("Not yet implemented.");
    }

}
