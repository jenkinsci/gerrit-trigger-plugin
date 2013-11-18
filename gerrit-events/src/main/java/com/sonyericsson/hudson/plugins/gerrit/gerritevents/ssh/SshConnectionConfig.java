/*
 * The MIT License
 *
 * Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 * Copyright 2012 Sony Mobile Communications AB. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh;

import java.io.File;

/**
 * Connection information related to the Gerrit ssh interface.
 */
public interface SshConnectionConfig {

    /**
     * The path to the private key.
     * @return the path.
     */
    File getGerritAuthKeyFile();

    /**
     * The password for the private key, or null if there is none.
     * @return the password
     */
    String getGerritAuthKeyFilePassword();

    /**
     * The hostname for gerrit where it is listening to ssh commands.
     * @return the hostname.
     */
    String getGerritHostName();

    /**
     * The port to connect with ssh to.
     * @return the port.
     */
    int getGerritSshPort();

    /**
     * The username to authenticate to gerrit with.
     * @return the username.
     */
    String getGerritUserName();

    /**
     * The the Gerrit authentication credentials.
     * Containing
     * {@link #getGerritAuthKeyFile() },
     * {@link #getGerritUserName() } and
     * {@link #getGerritAuthKeyFilePassword() }.
     * @return the credentials.
     */
    Authentication getGerritAuthentication();

    /**
     * The http or socks5 proxy url.
     *
     * @return the proxy url.
     */
    String getGerritProxy();
}
