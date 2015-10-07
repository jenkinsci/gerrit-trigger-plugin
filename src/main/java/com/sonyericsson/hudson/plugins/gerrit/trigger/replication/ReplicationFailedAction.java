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

import java.util.Iterator;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritSlave;
import com.sonyericsson.hudson.plugins.gerrit.trigger.replication.ReplicationQueueTaskDispatcher.BlockedItem;

import hudson.model.InvisibleAction;

/**
 * Action to mark a build that must fail because replication failed. ReplicationFailedHandler
 * will fail the build if tagged with this action.
 * @author Hugo Arès &lt;hugo.ares@ericsson.com&gt;
 */
public class ReplicationFailedAction extends InvisibleAction {

    private BlockedItem blockedItem;

    /**
     * Standard constructor.
     * @param blockedItem The reason of the Failed replication
     */
    public ReplicationFailedAction(BlockedItem blockedItem) {
        this.blockedItem = blockedItem;
    }

    /**
     * The reason of the Failed replication.
     * @return the reason
     */
    public String getReason() {
        StringBuffer slavesReason = new StringBuffer("\n\nReplication Status:");
        slavesReason.append("\n-------------------\n\n");

        if (blockedItem.getSlavesWaitingFor() != null && blockedItem.getSlavesWaitingFor().size() > 0) {
            Iterator<String> it = blockedItem.getSlavesWaitingFor().keySet().iterator();
            while (it.hasNext()) {
                GerritSlave slave = blockedItem.getSlavesWaitingFor().get(it.next());
                if (slave.getReplicationStatus() != null) {
                    slavesReason = slavesReason.append("** Replication to " + slave.getName() + " "
                            + slave.getReplicationStatus().getStatusMessage() + "!\n");
                }
            }
        }
        return blockedItem.getReplicationFailedMessage() + slavesReason.toString();
    }
}
