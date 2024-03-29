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

import hudson.model.queue.CauseOfBlockage;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritSlave;

/**
 * Build is blocked because replication is not completed to slave(s).
 * @author Hugo Arès &lt;hugo.ares@ericsson.com&gt;
 */
public class WaitingForReplication extends CauseOfBlockage {

    private Collection<GerritSlave> gerritSlaves;

    /**
     * Standard constructor.
     * @param gerritSlaves The slaves(s) that replication needs to complete before the build can start.
     */
    public WaitingForReplication(Collection<GerritSlave> gerritSlaves) {
        this.gerritSlaves = gerritSlaves;
    }

    @Override
    public String getShortDescription() {
        return Messages.WaitingForReplication(
                gerritSlaves.stream()
                        .filter(Objects::nonNull)
                        .map(GerritSlave::getName)
                        .collect(Collectors.joining(", ")));
    }
}
