/*
 *  The MIT License
 *
 *  Copyright 2013 rinrinne <rinrin.ne@gmail.com>
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

import java.util.Collection;

/**
 * A interface to handle listers for connection.
 *
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public interface ConnectionHandler {
    /**
     * Add a ConnectionListener to the list of listeners.
     *
     * @param listener the listener to add.
     */
    void addConnectionListener(ConnectionListener listener);

    /**
     * Adds all the provided listeners to the internal list of listeners.
     *
     * @param listeners the listeners to add.
     */
    void addConnectionListeners(Collection<? extends ConnectionListener> listeners);

    /**
     * Removes a ConnectionListener from the list of listeners.
     *
     * @param listener the listener to remove.
     */
    void removeConnectionListener(ConnectionListener listener);

    /**
     * Removes all event listeners and returns those that where removed.
     *
     * @return the former list of listeners.
     */
    Collection<ConnectionListener> removeAllConnectionListeners();

    /**
     * The number of added e{@link ConnectionListener}s.
     * @return the size.
     */
    int getConnectionListenersCount();

    /**
     * If connection is established or not.
     * @return true if connection is established.
     */
    boolean isConnected();

    /**
     * Sets shutdown flag.
     */
    void shutdown();
}
