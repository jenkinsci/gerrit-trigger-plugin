/*
 * The MIT License
 *
 * Copyright 2014 Ericsson.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.replication;

import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.AbstractBuild;
import hudson.model.listeners.RunListener;

import java.io.IOException;

/**
 * Look for ReplicationFailedAction in the build and fail the build if found.
 * @author Hugo Arès &lt;hugo.ares@ericsson.com&gt;
 */
@Extension
public class ReplicationFailedHandler extends RunListener<AbstractBuild<?, ?>> {

    @Override
    public Environment setUpEnvironment(AbstractBuild build, Launcher launcher, BuildListener listener)
        throws IOException, InterruptedException {
        //doing this in the method onStarted would make more sense but you are not allowed to fail a build
        //at that point. This method is the first one called that we can fail the build.
        ReplicationFailedAction replicationFailedAction = build.getAction(ReplicationFailedAction.class);
        if (replicationFailedAction != null) {
            throw new AbortException(replicationFailedAction.getReason());
        }
        return super.setUpEnvironment(build, launcher, listener);
    }
}
