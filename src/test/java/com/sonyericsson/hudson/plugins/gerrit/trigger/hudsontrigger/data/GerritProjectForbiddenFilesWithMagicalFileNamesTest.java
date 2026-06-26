/*
 *  The MIT License
 *
 *  Copyright 2021 Liberty Global B.V.
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

import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;

import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import java.util.List;

/**
 * Testing if magical file names are ignored with Forbidden File Paths and do not make change interesting.
 * @author Adam Romanek&lt;romanek.adam@gmail.com&gt;
 */
class GerritProjectForbiddenFilesWithMagicalFileNamesTest {

    /**
     * Tests.
     */
    @Test
    void testMagicalFileNamesDoNotMakeChangeInteresting() {
        List<Branch> branches = new LinkedList<>();
        branches.add(new Branch(CompareType.PLAIN, "master"));
        List<Topic> topics = new LinkedList<>();
        List<FilePath> filePaths = new LinkedList<>();
        List<FilePath> forbiddenFilePaths = new LinkedList<>();
        forbiddenFilePaths.add(new FilePath(CompareType.ANT, "README.md"));
        List<String> files = new LinkedList<>();
        files.add("/COMMIT_MSG");
        files.add("/MERGE_LIST");
        files.add("/PATCHSET_LEVEL");
        files.add("README.md");
        GerritProject project = new GerritProject(
                CompareType.PLAIN, "project1", branches, topics, filePaths, forbiddenFilePaths, true);
        Change change = new Change();
        change.setProject("project1");
        change.setBranch("master");
        assertFalse(project.isInteresting(change, () -> files));
    }
}
