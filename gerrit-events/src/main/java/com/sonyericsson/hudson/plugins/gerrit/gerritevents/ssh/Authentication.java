/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh;

import java.io.File;

/**
 * Represents authentication information to an SSH server connection.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 * @see SshConnection
 */
public class Authentication {
    private File privateKeyFile;
    private String username;
    private String privateKeyFilePassword;

    /**
     * Constructor.
     * @param privateKeyFile the key.
     * @param username the username.
     * @param privateKeyFilePassword password for the key file, or null if there is none.
     */
    public Authentication(File privateKeyFile, String username, String privateKeyFilePassword) {
        this.privateKeyFile = privateKeyFile;
        this.username = username;
        this.privateKeyFilePassword = privateKeyFilePassword;
    }

    /**
     * Constructor.
     * With null as privateKeyFilePassword.
     * @param privateKeyFile the key.
     * @param username the username.
     */
    public Authentication(File privateKeyFile, String username) {
        this.privateKeyFile = privateKeyFile;
        this.username = username;
        this.privateKeyFilePassword = null;
    }

    /**
     * The file path to the private key.
     * @return the path.
     */
    public File getPrivateKeyFile() {
        return privateKeyFile;
    }

    /**
     * The password for the private key file.
     * @return the password.
     */
    public String getPrivateKeyFilePassword() {
        return privateKeyFilePassword;
    }

    /**
     * The username to authenticate as.
     * @return the username.
     */
    public String getUsername() {
        return username;
    }
}
