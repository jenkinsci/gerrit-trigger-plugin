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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog.WatchTimeExceptionData;

/**
 * Interface for an object that has information about how to connect to the Gerrit server.
 * Additions to the GerritConnectionConfig for the connection watchdog.
 *
 * @author Tomas Westling &lt;tomas.westling@sonymobile.com&gt;
 */
public interface GerritConnectionConfig2 extends GerritConnectionConfig {

    /**
     * Gets the time in minutes before the watchdog times out.
     *
     * @return the timeout setting in minutes for the watchdog.
     */
    int getWatchdogTimeoutSeconds();

    /**
     * Gets the exception data for when the watchdog shouldn't try to reconnect to Gerrit.
     *
     * @return the WatchTimeExceptionData.
     */
    WatchTimeExceptionData getExceptionData();

    /**
     * The http or socks5 proxy url.
     *
     * @return the proxy url.
     */
    String getGerritProxy();
}
