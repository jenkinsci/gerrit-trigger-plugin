/*
 *  The MIT License
 *
 *  Copyright 2012 Sony Ericsson Mobile Communications.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data;

import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Testing if Forbidden File Paths usage corrupts the list of files checked.
 * @author Peter Walls &lt;peter.walls@se.bosch.com&gt;
 */
public class GerritProjectForbiddenFilesTest {

    /**
     * Constructor.
     */
    public GerritProjectForbiddenFilesTest() {
    }

    /**
     * Tests.
     */
    @Test
    public void testFileRemoved() {
        List<Branch> branches = new LinkedList<Branch>();
        branches.add(new Branch(CompareType.PLAIN, "master"));
        List<Topic> topics = new LinkedList<Topic>();
        List<FilePath> filePaths = new LinkedList<FilePath>();
        List<FilePath> forbiddenFilePaths = new LinkedList<FilePath>();
        forbiddenFilePaths.add(new FilePath(CompareType.ANT, "t*.txt"));
        List<String> files = new LinkedList<String>();
        files.add("test.txt");
        files.add("hide.txt");
        GerritProject config = new GerritProject(
                CompareType.PLAIN, "project1", branches, topics, filePaths, forbiddenFilePaths, true);
        config.isInteresting("project1", "master", null, files);
        filePaths.add(new FilePath(CompareType.PLAIN, "test.txt"));
        config = new GerritProject(
                CompareType.PLAIN, "project2", branches, topics, filePaths, null, false);

        assertEquals(true, config.isInteresting("project2", "master", null, files));
    }
}
