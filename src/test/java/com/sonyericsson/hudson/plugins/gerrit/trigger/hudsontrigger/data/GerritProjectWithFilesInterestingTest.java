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
     * Tests {@link GerritProject#isInteresting(String, String, String, java.util.List)}.
     */
    @Test
    public void testInteresting() {
        assertEquals(scenarioWithFiles.expected, scenarioWithFiles.config.isInteresting(
                scenarioWithFiles.project, scenarioWithFiles.branch, scenarioWithFiles.topic, scenarioWithFiles.files));
    }

    /**
     * The parameters.
     * @return parameters
     */
    @Parameters
    public static Collection getParameters() {

        List<InterestingScenarioWithFiles[]> parameters = new LinkedList<InterestingScenarioWithFiles[]>();

        List<Branch> branches = new LinkedList<Branch>();
        Branch branch = new Branch(CompareType.PLAIN, "master");
        branches.add(branch);
        List<Topic> topics = new LinkedList<Topic>();
        List<FilePath> filePaths = new LinkedList<FilePath>();
        FilePath filePath = new FilePath(CompareType.PLAIN, "test.txt");
        filePaths.add(filePath);
        List<FilePath> forbiddenFilePaths = null;
        GerritProject config = new GerritProject(
                CompareType.PLAIN, "project", branches, topics, filePaths, forbiddenFilePaths, false);
        List<String> files = new LinkedList<String>();
        files.add("test.txt");
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "project", "master", null, files, true), });

        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.REG_EXP, "feature/.*master");
        branches.add(branch);
        filePaths = new LinkedList<FilePath>();
        filePath = new FilePath(CompareType.REG_EXP, "tests/.*");
        filePaths.add(filePath);
        files = new LinkedList<String>();
        files.add("tests/test.txt");
        forbiddenFilePaths = null;
        config = new GerritProject(CompareType.REG_EXP, "project.*5", branches, topics, filePaths, forbiddenFilePaths,
                false);
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "projectNumber5", "feature/mymaster", null, files, true), });

        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.ANT, "**/master");
        branches.add(branch);
        filePaths = new LinkedList<FilePath>();
        filePath = new FilePath(CompareType.ANT, "**/*test*");
        filePaths.add(filePath);
        forbiddenFilePaths = null;
        config = new GerritProject(
                CompareType.ANT, "vendor/**/project", branches, topics, filePaths, forbiddenFilePaths, false);
        files = new LinkedList<String>();
        files.add("resources/test.xml");
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "vendor/semc/master/project", "origin/master", null, files, true), });

        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.REG_EXP, "feature/.*master");
        branches.add(branch);
        filePaths = new LinkedList<FilePath>();
        filePath = new FilePath(CompareType.REG_EXP, "tests/.*");
        filePaths.add(filePath);
        files = new LinkedList<String>();
        files.add("notintests/test.txt");
        forbiddenFilePaths = null;
        config = new GerritProject(CompareType.REG_EXP, "project.*5", branches, topics, filePaths, forbiddenFilePaths,
                false);
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "projectNumber5", "feature/mymaster", null, files, false), });

        //Testing with Forbidden File Paths now
        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.PLAIN, "master");
        branches.add(branch);
        topics = new LinkedList<Topic>();
        filePaths = new LinkedList<FilePath>();
        filePath = new FilePath(CompareType.PLAIN, "test.txt");
        filePaths.add(filePath);
        forbiddenFilePaths = new LinkedList<FilePath>();
        FilePath forbiddenFilePath = new FilePath(CompareType.PLAIN, "test2.txt");
        forbiddenFilePaths.add(forbiddenFilePath);
        config = new GerritProject(CompareType.PLAIN, "project", branches, topics, filePaths, forbiddenFilePaths, false);
        files = new LinkedList<String>();
        files.add("test.txt");
        files.add("test2.txt");
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "project", "master", null, files, false), });

        //Testing with Forbidden File Paths now BUT disableStrictForbiddenFileVerification
        // is true
        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.PLAIN, "master");
        branches.add(branch);
        topics = new LinkedList<Topic>();
        filePaths = new LinkedList<FilePath>();
        filePath = new FilePath(CompareType.PLAIN, "test.txt");
        filePaths.add(filePath);
        forbiddenFilePaths = new LinkedList<FilePath>();
        forbiddenFilePath = new FilePath(CompareType.PLAIN, "test2.txt");
        forbiddenFilePaths.add(forbiddenFilePath);
        config = new GerritProject(CompareType.PLAIN, "project", branches, topics, filePaths, forbiddenFilePaths, true);
        files = new LinkedList<String>();
        files.add("test.txt");
        files.add("test2.txt");
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "project", "master", null, files, true), });

        //Testing with Forbidden File Paths now BUT no filepaths defined
        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.PLAIN, "master");
        branches.add(branch);
        topics = new LinkedList<Topic>();
        filePaths = null;
        forbiddenFilePaths = new LinkedList<FilePath>();
        forbiddenFilePath = new FilePath(CompareType.PLAIN, "test2.txt");
        forbiddenFilePaths.add(forbiddenFilePath);
        config = new GerritProject(CompareType.PLAIN, "project", branches, topics, filePaths, forbiddenFilePaths, false);
        files = new LinkedList<String>();
        files.add("test.txt");
        files.add("test2.txt");
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "project", "master", null, files, false), });

        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.REG_EXP, "feature/.*master");
        branches.add(branch);
        filePaths = new LinkedList<FilePath>();
        filePath = new FilePath(CompareType.REG_EXP, "tests/.*");
        filePaths.add(filePath);
        forbiddenFilePaths = new LinkedList<FilePath>();
        forbiddenFilePath = new FilePath(CompareType.REG_EXP, "tests/.*2.*");
        forbiddenFilePaths.add(forbiddenFilePath);
        files = new LinkedList<String>();
        files.add("tests/test.txt");
        files.add("tests/test2.txt");
        config = new GerritProject(CompareType.REG_EXP, "project.*5", branches, topics, filePaths, forbiddenFilePaths,
                false);
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "projectNumber5", "feature/mymaster", null, files, false), });

        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.ANT, "**/master");
        branches.add(branch);
        filePaths = new LinkedList<FilePath>();
        filePath = new FilePath(CompareType.ANT, "**/*test*");
        filePaths.add(filePath);
        forbiddenFilePaths = new LinkedList<FilePath>();
        forbiddenFilePath = new FilePath(CompareType.ANT, "**/*skip*");
        forbiddenFilePaths.add(forbiddenFilePath);
        config = new GerritProject(
                CompareType.ANT, "vendor/**/project", branches, topics, filePaths, forbiddenFilePaths, false);
        files = new LinkedList<String>();
        files.add("resources/test.xml");
        files.add("files/skip.txt");
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "vendor/semc/master/project", "origin/master", null, files, false), });

        return parameters;
    }

    /**
     * A parameter to a test scenario.
     */
    public static class InterestingScenarioWithFiles {

        GerritProject config;
        String project;
        String branch;
        String topic;
        boolean expected;
        List<String> files;

        /**
         * Constructor.
         * @param config config
         * @param project the project of this scenario.
         * @param branch the branch of this scenario.
         * @param topic the topic of this scenario.
         * @param files the files in this scenario.
         * @param expected the expected outcome, true if interesting, false if not.
         */
        public InterestingScenarioWithFiles(GerritProject config,
                String project,
                String branch,
                String topic,
                List<String> files,
                boolean expected) {
            this.config = config;
            this.project = project;
            this.branch = branch;
            this.topic = topic;
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
