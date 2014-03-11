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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritSlave;

//CS IGNORE MagicNumber FOR NEXT 50 LINES. REASON: testdata.

/**
 * Tests {@link com.sonyericsson.hudson.plugins.gerrit.trigger.replication.WaitingForReplication}.
 * @author Hugo Arès &lt;hugo.ares@ericsson.com&gt;
 */
public class WaitingForReplicationTest {

    /**
     * Test that {@link WaitingForReplication#getShortDescription()} return a
     * message including all the slaves display name.
     */
    @Test
    public void shouldReturnADescriptionWithAllSlaves() {
        List<GerritSlave> slaves = new ArrayList<GerritSlave>();
        slaves.add(new GerritSlave("slaveA", null, 1234));

        WaitingForReplication waitingForReplication = new WaitingForReplication(slaves);
        assertEquals(Messages.WaitingForReplication("slaveA"), waitingForReplication.getShortDescription());

        slaves.add(new GerritSlave("slaveB", null, 1234));
        assertEquals(Messages.WaitingForReplication("slaveA, slaveB"), waitingForReplication.getShortDescription());
    }

}
