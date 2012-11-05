/*
 * The MIT License
 *
 * Copyright 2011 Sony Ericsson Mobile Communications. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh;

import com.jcraft.jsch.ChannelExec;

import java.io.IOException;
import java.io.Reader;

/**
 * A simple ssh client connection with private key.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public interface SshConnection {

    /**
     * Is the connection connected.
     *
     * @return true if it is so.
     */
    boolean isConnected();

    /**
     * Is the connection authenticated.
     *
     * @return true if it is so.
     */
    boolean isAuthenticated();

    /**
     * Execute an ssh command on the server.
     * After the command is sent the used channel is disconnected.
     *
     * @param command the command to execute.
     * @return a String containing the output from the command.
     * @throws SshException if so.
     */
    String executeCommand(String command) throws SshException;

    //CS IGNORE RedundantThrows FOR NEXT 12 LINES. REASON: Informative

    /**
     * Execute an ssh command on the server, without closing the session
     * so that a Reader can be returned with streaming data from the server.
     *
     * @param command the command to execute.
     * @return a Reader with streaming data from the server.
     * @throws IOException  if it is so.
     * @throws SshException if there are any ssh problems.
     */
    Reader executeCommandReader(String command) throws SshException, IOException;

    //CS IGNORE RedundantThrows FOR NEXT 13 LINES. REASON: Informative.

    /**
     * Execute an ssh command on the server, without closing the session
     * so that the caller can get access to all the Channel attributes from
     * the server.
     *
     * @param command the command to execute.
     * @return a Channel with access to all streams and the exit code.
     * @throws IOException  if it is so.
     * @throws SshException if there are any ssh problems.
     * @see #executeCommandReader(String)
     */
    ChannelExec executeCommandChannel(String command) throws SshException, IOException;

    /**
     * Disconnects the connection.
     */
    void disconnect();
}
