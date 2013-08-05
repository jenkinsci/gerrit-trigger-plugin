/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents;

import java.io.File;

/**
 * Contains the default values for Gerrit communication.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public final class GerritDefaultValues {

    /**
     * Default private constructor to hinder instantiation.
     */
    private GerritDefaultValues() {

    }
    /**
     * The default gerrit name.
     */
    public static final String DEFAULT_GERRIT_NAME = "";
    /**
     * The default gerrit hostname.
     */
    public static final String DEFAULT_GERRIT_HOSTNAME = "";
    /**
     * The default ssh port for the gerrit server.
     */
    public static final int DEFAULT_GERRIT_SSH_PORT = 29418;
    /**
     * The default gerrit proxy.
     */
    public static final String DEFAULT_GERRIT_PROXY = "";

    /**
     * The default key-file to use when authenticating to the gerrit server.
     */
    public static final File DEFAULT_GERRIT_AUTH_KEY_FILE = new File(new File(System.getProperty("user.home"), ".ssh"),
                                                              "id_rsa");
    /**
     * The default password for the private key-file.
     */
    public static final String DEFAULT_GERRIT_AUTH_KEY_FILE_PASSWORD = null;
    /**
     * The default username to use when authenticating to the gerrit server.
     */
    public static final String DEFAULT_GERRIT_USERNAME = "";
    /**
     * The default nr of event worker threads.
     */
    public static final int DEFAULT_NR_OF_RECEIVING_WORKER_THREADS = 3;
    /**
     * The default nr of worker threads that sends approvals/review commands.
     */
    public static final int DEFAULT_NR_OF_SENDING_WORKER_THREADS = 1;
    /**
     * The default build schedule delay.
     */
    public static final int DEFAULT_BUILD_SCHEDULE_DELAY = 3;
    /**
     * The default refresh interval for the Dynamic Trigger Configuration.
     */
    public static final int DEFAULT_DYNAMIC_CONFIG_REFRESH_INTERVAL = 30;

    /**
     * The minimum refresh interval for dynamic configuration.
     */
    public static final int MINIMUM_DYNAMIC_CONFIG_REFRESH_INTERVAL = 5;
}
