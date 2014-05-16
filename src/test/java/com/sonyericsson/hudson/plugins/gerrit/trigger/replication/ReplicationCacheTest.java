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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.sonymobile.tools.gerrit.gerritevents.dto.events.RefReplicated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;

//CS IGNORE MagicNumber FOR NEXT 100 LINES. REASON: testdata.

/**
 * Tests {@link com.sonyericsson.hudson.plugins.gerrit.trigger.replication.ReplicationCache}.
 * @author Hugo Arès &lt;hugo.ares@ericsson.com&gt;
 */
public class ReplicationCacheTest {

    /**
     * Test that it should return cached event.
     */
    @Test
    public void shouldReturnCachedEvent() {
        ReplicationCache replicationCache = ReplicationCache.Factory.createCache();
        RefReplicated refReplicated = Setup.createRefReplicatedEvent("someProject", "refs/changes/1/1/1", "someServer",
            "someSlave", null);
        replicationCache.put(refReplicated);

        assertEquals(refReplicated,
            replicationCache.getIfPresent("someServer", "someProject", "refs/changes/1/1/1", "someSlave"));
    }

    /**
     * Test that it should return null when no cached event found.
     */
    @Test
    public void shouldReturnNullWhenNoCachedEventFound() {
        ReplicationCache replicationCache = ReplicationCache.Factory.createCache();
        assertNull(replicationCache.getIfPresent("someServer", "someProject", "refs/changes/1/1/1", "someSlave"));
    }

    /**
     * Test that it should evict expired event.
     * @throws InterruptedException if something goes wrong
     */
    @Test
    public void shouldEvictExpiredEvent() throws InterruptedException {
        ReplicationCache replicationCache = ReplicationCache.Factory.createCache(100, TimeUnit.MILLISECONDS);
        RefReplicated refReplicated = Setup.createRefReplicatedEvent("someProject", "refs/changes/1/1/1", "someServer",
            "someSlave", null);
        replicationCache.put(refReplicated);

        // event is in the cache
        assertEquals(refReplicated,
            replicationCache.getIfPresent("someServer", "someProject", "refs/changes/1/1/1", "someSlave"));

        //event is no longer in the cache
        Thread.sleep(100);
        assertNull("Event should have been evicted from the cache",
            replicationCache.getIfPresent("someServer", "someProject", "refs/changes/1/1/1", "someSlave"));
    }

    /**
     * Test that it should return proper isExpired boolean, if not expired and if it is.
     */
    @Test
    public void shouldReturnIsExpired() {
        ReplicationCache replicationCache = ReplicationCache.Factory.createCache(100, TimeUnit.MILLISECONDS);
        assertFalse(replicationCache.isExpired(System.currentTimeMillis()));
        assertTrue(replicationCache.isExpired(System.currentTimeMillis() - 200));
    }
}
