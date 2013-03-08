/*
 * The MIT License
 *
 * Copyright 2013 Sony Mobile Communications AB. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ConnectionListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritHandler;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.trigger.test.SshdServerMock;
import org.apache.sshd.SshServer;
import org.apache.sshd.server.Environment;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static com.sonyericsson.hudson.plugins.gerrit.trigger.test.SshdServerMock.GERRIT_STREAM_EVENTS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

//CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: TestData

/**
 * Tests for {@link StreamWatchdog}.
 *
 * @author Robert Sandell &lt;robert.sandell@sonymobile.com&gt;
 */
public class StreamWatchdogTest {

    /**
     * Tests that the {@link StreamWatchdog} actually performs a restart of the connection.
     *
     * @throws IOException if so.
     * @throws InterruptedException if so.
     * @throws NoSuchMethodException if so.
     */
    @Test(timeout = 2 * 60 * 60 * 1000)
    public void testFullTimeoutFlow() throws IOException, InterruptedException, NoSuchMethodException {
        System.out.println("====This will be a long running test ca. 2 minutes=====");
        File sshKey = SshdServerMock.generateKeyPair();
        SshdServerMock server = new SshdServerMock();
        SshServer sshd = SshdServerMock.startServer(server);
        server.returnCommandFor("gerrit version", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor(GERRIT_STREAM_EVENTS, WaitLongTimeCommand.class, true,
                new Object[]{MINUTES.toMillis(5)}, new Class<?>[]{Long.class});
        server.returnCommandFor(GERRIT_STREAM_EVENTS, SshdServerMock.CommandMock.class);
        GerritHandler handler = new GerritHandler("localhost", SshdServerMock.GERRIT_SSH_PORT, "",
                new Authentication(sshKey, "jenkins"), 1, "jenkins@localhost", 20,
                new WatchTimeExceptionData(new int[0], Collections.<WatchTimeExceptionData.TimeSpan>emptyList()));
        Listen connectionListener = new Listen();
        handler.addListener(connectionListener);
        handler.start();
        server.waitForCommand(GERRIT_STREAM_EVENTS, 8000);
        Thread.sleep(2000);
        assertTrue(connectionListener.isConnectionEstablished());
        //wait for the connection to go down.
        connectionListener.waitForConnectionDown();
        server.waitForCommand(GERRIT_STREAM_EVENTS, 8000);
        Thread.sleep(1000);
        assertTrue(connectionListener.isConnectionEstablished());
        assertEquals(1, handler.getReconnectCallCount());
        System.out.println("====Shutting down GerritHandler=====");
        handler.shutdown(true);
        System.out.println("====Shutting down SSHD=====");
        sshd.stop(true);
        System.out.println("====Done=====");
    }


    /**
     * ConnectionListener to help with the testing to see that the connection actually goes down and up.
     */
    public static class Listen implements ConnectionListener {

        boolean connectionEstablished = false;
        boolean connectionDown = true;

        @Override
        public synchronized void connectionEstablished() {
            connectionEstablished = true;
            connectionDown = false;
            this.notifyAll();
        }

        @Override
        public synchronized void connectionDown() {
            connectionDown = true;
            connectionEstablished = false;
            this.notifyAll();
        }

        /**
         * If the connection is established. I.e. if {@link #connectionEstablished()} has just been called.
         *
         * @return true if so.
         */
        public synchronized boolean isConnectionEstablished() {
            return connectionEstablished;
        }

        /**
         * If the connection is down. I.e. if {@link #connectionDown()} has just been called.
         *
         * @return true if so.
         */
        public synchronized boolean isConnectionDown() {
            return connectionDown;
        }

        /**
         * Waits for the {@link #connectionDown()} signal and returns after that.
         *
         * @throws InterruptedException if so.
         */
        public void waitForConnectionDown() throws InterruptedException {
            System.out.println("Waiting for connection to go down...");
            if (isConnectionDown()) {
                System.out.println("Connection is down!");
                return;
            }
            while (!isConnectionDown()) {
                synchronized (this) {
                    this.wait(1000);
                }
            }
            System.out.println("Connection is down!");
        }
    }

    /**
     * A SSH command that sleeps for a specified period of time.
     */
    public static class WaitLongTimeCommand extends SshdServerMock.CommandMock implements Runnable {

        private long timeout;
        private Thread thread;

        /**
         * Standard constructor. Will sleep for two minutes.
         *
         * @param command the command to "execute".
         */
        public WaitLongTimeCommand(String command) {
            super(command);
            timeout = MINUTES.toMillis(2);
        }

        /**
         * Standard constructor.
         *
         * @param command the command to "execute".
         * @param timeout millis to sleep for.
         */
        public WaitLongTimeCommand(String command, Long timeout) {
            super(command);
            this.timeout = timeout;
        }

        @Override
        public void start(Environment environment) throws IOException {
            thread = new Thread(this, "WaitLongTimeCommand " + this.command);
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void run() {
            System.out.println("WaitLongTimeCommand starting...");
            try {
                Thread.sleep(timeout);
            } catch (InterruptedException e) {
                System.err.println("WaitLongTimeCommand interrupted!");
            }
            System.out.println("WaitLongTimeCommand finished.");
            stop(0);
        }
    }
}
