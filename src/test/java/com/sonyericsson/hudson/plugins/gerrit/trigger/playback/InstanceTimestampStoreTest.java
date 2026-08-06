/*
 *  The MIT License
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.playback;

import hudson.security.ACL;
import jenkins.model.Jenkins;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link InstanceTimestampStore}.
 */
public class InstanceTimestampStoreTest {

    // CS IGNORE MagicNumber FOR NEXT 150 LINES. REASON: Test data.
    private MockedStatic<Jenkins> jenkinsMockedStatic;

    /**
     * Points Jenkins' root directory at a fresh real temp directory per test.
     * @throws IOException if the temp directory cannot be created.
     */
    @Before
    public void setUp() throws IOException {
        Jenkins jenkinsMock = mock(Jenkins.class);
        jenkinsMockedStatic = mockStatic(Jenkins.class);
        jenkinsMockedStatic.when(Jenkins::getInstanceOrNull).thenReturn(jenkinsMock);
        jenkinsMockedStatic.when(Jenkins::getAuthentication).thenReturn(ACL.SYSTEM);
        jenkinsMockedStatic.when(Jenkins::getAuthentication2).thenReturn(ACL.SYSTEM2);

        File jenkinsRootDir = Files.createTempDirectory("jenkins-root").toFile();
        jenkinsRootDir.deleteOnExit();
        when(jenkinsMock.getRootDir()).thenReturn(jenkinsRootDir);
    }

    @After
    public void tearDown() {
        jenkinsMockedStatic.close();
    }

    /**
     * Given multiple JVM instances have each persisted their own timestamp for a server
     * When computing the max across instances
     * Then the highest value wins, regardless of which instance wrote it.
     * @throws IOException if it occurs.
     */
    @Test
    public void testComputeMaxAcrossMultipleInstanceFiles() throws IOException {
        InstanceTimestampStore storeA = new InstanceTimestampStore("server-x", "instance-a");
        InstanceTimestampStore storeB = new InstanceTimestampStore("server-x", "instance-b");
        storeA.writeTimestamp(new EventTimeSlice(1000L));
        storeB.writeTimestamp(new EventTimeSlice(5000L));

        OptionalLong max = storeA.computeMaxTimestampAcrossInstances();

        assertTrue(max.isPresent());
        assertEquals(5000L, max.getAsLong());
    }

    /**
     * Given no per-instance files exist yet, but a legacy single-file timestamp does (upgrade
     * from a pre-existing single-JVM deployment)
     * When computing the max across instances
     * Then the legacy file's value is used, not treated as "no known timestamp".
     * @throws IOException if it occurs.
     */
    @Test
    public void testFallsBackToLegacyFileWhenNoInstanceFilesExist() throws IOException {
        InstanceTimestampStore store = new InstanceTimestampStore("server-y", "instance-a");
        store.getLegacyConfigXml().write(new EventTimeSlice(7000L));

        OptionalLong max = store.computeMaxTimestampAcrossInstances();

        assertTrue(max.isPresent());
        assertEquals(7000L, max.getAsLong());
    }

    /**
     * Given neither instance files nor a legacy file exist
     * When computing the max across instances
     * Then the result is empty.
     */
    @Test
    public void testNoInstanceFilesAndNoLegacyFileReturnsEmpty() {
        InstanceTimestampStore store = new InstanceTimestampStore("server-z", "instance-a");

        assertFalse(store.computeMaxTimestampAcrossInstances().isPresent());
    }

    /**
     * Given one instance file is corrupt/unreadable and another is valid
     * When computing the max across instances
     * Then the corrupt file is skipped (logged, not fatal) and the valid file's value is used.
     * @throws IOException if it occurs.
     */
    @Test
    public void testCorruptInstanceFileIsSkippedNotFatal() throws IOException {
        InstanceTimestampStore storeA = new InstanceTimestampStore("server-w", "instance-a");
        InstanceTimestampStore storeB = new InstanceTimestampStore("server-w", "instance-b");
        storeA.writeTimestamp(new EventTimeSlice(3000L));
        File corruptFile = storeB.getInstanceConfigXml().getFile();
        try (PrintWriter out = new PrintWriter(corruptFile)) {
            out.println("not valid xml <<<");
        }

        OptionalLong max = storeA.computeMaxTimestampAcrossInstances();

        assertTrue(max.isPresent());
        assertEquals(3000L, max.getAsLong());
    }

    /**
     * Given this instance's own file is old enough to look stale
     * When pruning stale instance files
     * Then it is never deleted, regardless of its age.
     * @throws IOException if it occurs.
     */
    @Test
    public void testPruneStaleInstanceFilesNeverDeletesOwnFileRegardlessOfAge() throws IOException {
        InstanceTimestampStore store = new InstanceTimestampStore("server-v", "instance-a");
        store.writeTimestamp(new EventTimeSlice(1L));
        File ownFile = store.getInstanceConfigXml().getFile();
        assertTrue(ownFile.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365L)));

        int removed = store.pruneStaleInstanceFiles(TimeUnit.HOURS.toMillis(1));

        assertEquals(0, removed);
        assertTrue(ownFile.exists());
    }

    /**
     * Given another instance's file is older than the staleness threshold
     * When pruning stale instance files
     * Then it is deleted as an orphan from a decommissioned JVM.
     * @throws IOException if it occurs.
     */
    @Test
    public void testPruneStaleInstanceFilesDeletesOldOtherInstanceFiles() throws IOException {
        InstanceTimestampStore storeA = new InstanceTimestampStore("server-u", "instance-a");
        InstanceTimestampStore storeB = new InstanceTimestampStore("server-u", "instance-b");
        storeA.writeTimestamp(new EventTimeSlice(1L));
        storeB.writeTimestamp(new EventTimeSlice(2L));
        File otherFile = storeB.getInstanceConfigXml().getFile();
        assertTrue(otherFile.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365L)));

        int removed = storeA.pruneStaleInstanceFiles(TimeUnit.HOURS.toMillis(1));

        assertEquals(1, removed);
        assertFalse(otherFile.exists());
    }
}
