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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Testing different scenarios with file paths to see if they are interesting.
 * @author Tomas Westling &lt;thomas.westling@sonyericsson.com&gt;
 */
@RunWith(Parameterized.class)
public class GerritProjectWithFilesInterestingTest {

    private final InterestingScenarioWithFiles scenarioWithFiles;

    /**
     * Constructor.
     * @param scenarioWithFiles scenarioWithFiles
     */
    public GerritProjectWithFilesInterestingTest(InterestingScenarioWithFiles scenarioWithFiles) {
        this.scenarioWithFiles = scenarioWithFiles;
    }

    /**
     * Tests {@link GerritProject#isInteresting(String, String, List<String>)}.
     */
    @Test
    public void testInteresting() {
        assertEquals(scenarioWithFiles.expected, scenarioWithFiles.config.isInteresting(
                scenarioWithFiles.project, scenarioWithFiles.branch, scenarioWithFiles.files));
    }

    /**
     * The parameters.
     * @return parameters
     */
    @Parameters
    public static Collection getParameters() {

        List<InterestingScenarioWithFiles[]> parameters = new LinkedList<InterestingScenarioWithFiles[]>();

        List<Branch> branches = new LinkedList<Branch>();
        Branch branch = new Branch(CompareType.PLAIN, "master", false);
        branches.add(branch);
        List<FilePath> filePaths = new LinkedList<FilePath>();
        FilePath filePath = new FilePath(CompareType.PLAIN, "test.txt");
        filePaths.add(filePath);
        GerritProject config = new GerritProject(CompareType.PLAIN, "project", branches, filePaths);
        List<String> files = new LinkedList<String>();
        files.add("test.txt");
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "project", "master", files, true), });

        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.REG_EXP, "feature/.*master", false);
        branches.add(branch);
        filePaths = new LinkedList<FilePath>();
        filePath = new FilePath(CompareType.REG_EXP, "tests/.*");
        filePaths.add(filePath);
        files = new LinkedList<String>();
        files.add("tests/test.txt");
        config = new GerritProject(CompareType.REG_EXP, "project.*5", branches, filePaths);
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "projectNumber5", "feature/mymaster", files, true), });

        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.ANT, "**/master", false);
        branches.add(branch);
        filePaths = new LinkedList<FilePath>();
        filePath = new FilePath(CompareType.ANT, "**/*test*");
        filePaths.add(filePath);
        config = new GerritProject(CompareType.ANT, "vendor/**/project", branches, filePaths);
        files = new LinkedList<String>();
        files.add("resources/test.xml");
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "vendor/semc/master/project", "origin/master", files, true), });

        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.REG_EXP, "feature/.*master", false);
        branches.add(branch);
        filePaths = new LinkedList<FilePath>();
        filePath = new FilePath(CompareType.REG_EXP, "tests/.*");
        filePaths.add(filePath);
        files = new LinkedList<String>();
        files.add("notintests/test.txt");
        config = new GerritProject(CompareType.REG_EXP, "project.*5", branches, filePaths);
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "projectNumber5", "feature/mymaster", files, false), });

        return parameters;
    }

    /**
     * A parameter to a test scenario.
     */
    public static class InterestingScenarioWithFiles {

        GerritProject config;
        String project;
        String branch;
        boolean expected;
        List<String> files;

        /**
         * Constructor.
         * @param config config
         * @param project the project of this scenario.
         * @param branch the branch of this scenario.
         * @param files the files in this scenario.
         * @param expected the expected outcome, true if interesting, false if not.
         */
        public InterestingScenarioWithFiles(
                GerritProject config, String project, String branch, List<String> files, boolean expected) {
            this.config = config;
            this.project = project;
            this.branch = branch;
            this.files = files;
            this.expected = expected;
        }

        /**
         * Constructor.
         */
        public InterestingScenarioWithFiles() {
        }
    }
}
