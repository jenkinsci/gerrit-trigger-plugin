/*
 * The MIT License
 *
 * Copyright 2015 Ericsson.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger;

import hudson.security.ACL;
import hudson.security.ACLContext;

import com.sonymobile.tools.gerrit.gerritevents.workers.Coordinator;
import com.sonymobile.tools.gerrit.gerritevents.workers.EventThread;

/**
 * EventThread that impersonates the System user in Jenkins.
 * @author scott.hebert@ericsson.com
 *
 */
public class SystemEventThread extends EventThread {

    /**
     * Constructs an Event thread worker with name.
     * @param coordinator the master.
     * @param name The Thread Name
     */
    public SystemEventThread(Coordinator coordinator, String name) {
        super(coordinator, name);
    }

    /**
     * Constructs an Event thread worker.
     * @param coordinator the master.
     */
    public SystemEventThread(Coordinator coordinator) {
        super(coordinator);
    }

    /**
     * We perform the impersonation of the System user
     * prior to execution.
     */
    @Override
    public void run() {
        try (ACLContext ctx = ACL.as(ACL.SYSTEM)) {
            super.run();
        }
    }
}
