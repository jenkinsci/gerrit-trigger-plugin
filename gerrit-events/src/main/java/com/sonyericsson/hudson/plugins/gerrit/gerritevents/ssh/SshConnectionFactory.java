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

import java.io.IOException;

/**
 * Factory class for {@link SshConnection}s.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public abstract class SshConnectionFactory {

    /**
     * Private constructor to hinder instansiation.
     */
    private SshConnectionFactory() {
        throw new UnsupportedOperationException("Cannot instansiate util classes.");
    }

    /**
     * Creates a {@link SshConnection}.
     *
     * @param host           the host name
     * @param port           the port
     * @param authentication the credentials
     * @return a new connection.
     *
     * @throws IOException if so.
     * @see SshConnection
     * @see SshConnectionImpl
     */
    public static SshConnection getConnection(String host, int port, Authentication authentication) throws IOException {
        return new SshConnectionImpl(host, port, authentication);
    }
}
