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
import java.util.Collections;
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
     * Tests {@link GerritProject#isInteresting(String, String, String, java.util.function.Supplier)}.
     */
    @Test
    public void testInteresting() {
        assertEquals(scenarioWithFiles.expected, scenarioWithFiles.config.isInteresting(
                scenarioWithFiles.project, scenarioWithFiles.branch, scenarioWithFiles.topic,
                () -> scenarioWithFiles.getFiles()));
        assertEquals(scenarioWithFiles.fileCheckOccurred, scenarioWithFiles.fileCheckNeeded);
    }

    /**
     * The parameters.
     * @return parameters
     */
    @Parameters
    public static Collection getParameters() {
        LinkedList<InterestingScenarioWithFiles[]> parameters = new LinkedList<>();
        addParametersForFileMatchTests(parameters);
        addParametersForBypassFileMatchTests(parameters);
        return parameters;
    }

    /**
     * Test parameters for checking file path include/exclude filters.
     * @param parameters Test parameter list to append to.
     */
    private static void addParametersForFileMatchTests(LinkedList<InterestingScenarioWithFiles[]> parameters) {
        List<Branch> branches = new LinkedList<Branch>();
        branches.add(new Branch(CompareType.PLAIN, "master"));
        List<Topic> topics = new LinkedList<Topic>();
        List<FilePath> filePaths = new LinkedList<FilePath>();
        filePaths.add(new FilePath(CompareType.PLAIN, "test.txt"));
        GerritProject config = new GerritProject(
                CompareType.PLAIN, "project", branches, topics, filePaths, null, false);
        List<String> files = new LinkedList<String>();
        files.add("test.txt");
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "project", "master", null, files, true, true), });

        branches = new LinkedList<Branch>();
        branches.add(new Branch(CompareType.REG_EXP, "feature/.*master"));
        filePaths = new LinkedList<FilePath>();
        filePaths.add(new FilePath(CompareType.REG_EXP, "tests/.*"));
        files = new LinkedList<String>();
        files.add("tests/test.txt");
        config = new GerritProject(CompareType.REG_EXP, "project.*5", branches, topics, filePaths, null, false);
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "projectNumber5", "feature/mymaster", null, files, true, true), });

        branches = new LinkedList<Branch>();
        branches.add(new Branch(CompareType.ANT, "**/master"));
        filePaths = new LinkedList<FilePath>();
        filePaths.add(new FilePath(CompareType.ANT, "**/*test*"));
        config = new GerritProject(
                CompareType.ANT, "vendor/**/project", branches, topics, filePaths, null, false);
        files = new LinkedList<String>();
        files.add("resources/test.xml");
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "vendor/semc/master/project", "origin/master", null, files, true, true), });

        branches = new LinkedList<Branch>();
        branches.add(new Branch(CompareType.REG_EXP, "feature/.*master"));
        filePaths = new LinkedList<FilePath>();
        filePaths.add(new FilePath(CompareType.REG_EXP, "tests/.*"));
        files = new LinkedList<String>();
        files.add("notintests/test.txt");
        config = new GerritProject(CompareType.REG_EXP, "project.*5", branches, topics, filePaths, null, false);
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "projectNumber5", "feature/mymaster", null, files, true, false), });

        //Testing with Forbidden File Paths now
        branches = new LinkedList<Branch>();
        branches.add(new Branch(CompareType.PLAIN, "master"));
        topics = new LinkedList<Topic>();
        filePaths = new LinkedList<FilePath>();
        filePaths.add(new FilePath(CompareType.PLAIN, "test.txt"));
        List<FilePath> forbiddenFilePaths = new LinkedList<FilePath>();
        forbiddenFilePaths.add(new FilePath(CompareType.PLAIN, "test2.txt"));
        config = new GerritProject(CompareType.PLAIN, "project", branches, topics, filePaths, forbiddenFilePaths, false);
        files = new LinkedList<String>();
        files.add("test.txt");
        files.add("test2.txt");
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "project", "master", null, files, true, false), });

        //Testing with Forbidden File Paths now BUT disableStrictForbiddenFileVerification
        // is true
        branches = new LinkedList<Branch>();
        branches.add(new Branch(CompareType.PLAIN, "master"));
        topics = new LinkedList<Topic>();
        filePaths = new LinkedList<FilePath>();
        filePaths.add(new FilePath(CompareType.PLAIN, "test.txt"));
        forbiddenFilePaths = new LinkedList<FilePath>();
        forbiddenFilePaths.add(new FilePath(CompareType.PLAIN, "test2.txt"));
        config = new GerritProject(CompareType.PLAIN, "project", branches, topics, filePaths, forbiddenFilePaths, true);
        files = new LinkedList<String>();
        files.add("test.txt");
        files.add("test2.txt");
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "project", "master", null, files, true, true), });

        //Testing with Forbidden File Paths now BUT no filepaths defined
        branches = new LinkedList<Branch>();
        branches.add(new Branch(CompareType.PLAIN, "master"));
        topics = new LinkedList<Topic>();
        forbiddenFilePaths = new LinkedList<FilePath>();
        forbiddenFilePaths.add(new FilePath(CompareType.PLAIN, "test2.txt"));
        config = new GerritProject(CompareType.PLAIN, "project", branches, topics, null, forbiddenFilePaths, false);
        files = new LinkedList<String>();
        files.add("test.txt");
        files.add("test2.txt");
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "project", "master", null, files, true, false), });

        //Testing with Forbidden File Paths now BUT no filepaths defined AND
        //disableStrictForbiddenFileVerification is true, with both forbidden & allowed files
        branches = new LinkedList<Branch>();
        branches.add(new Branch(CompareType.PLAIN, "master"));
        topics = new LinkedList<Topic>();
        forbiddenFilePaths = new LinkedList<FilePath>();
        forbiddenFilePaths.add(new FilePath(CompareType.PLAIN, "test2.txt"));
        config = new GerritProject(CompareType.PLAIN, "project", branches, topics, null, forbiddenFilePaths, true);
        files = new LinkedList<String>();
        files.add("test.txt");
        files.add("test2.txt");
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "project", "master", null, files, true, true), });

        //Testing with Forbidden File Paths BUT no filepaths defined AND
        //disableStrictForbiddenFileVerification is true, with only forbidden files
        branches = new LinkedList<Branch>();
        branches.add(new Branch(CompareType.PLAIN, "master"));
        topics = new LinkedList<Topic>();
        forbiddenFilePaths = new LinkedList<FilePath>();
        forbiddenFilePaths.add(new FilePath(CompareType.PLAIN, "test2.txt"));
        config = new GerritProject(CompareType.PLAIN, "project", branches, topics, null, forbiddenFilePaths, true);
        files = new LinkedList<String>();
        files.add("test2.txt");
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "project", "master", null, files, true, false), });

        branches = new LinkedList<Branch>();
        branches.add(new Branch(CompareType.REG_EXP, "feature/.*master"));
        filePaths = new LinkedList<FilePath>();
        filePaths.add(new FilePath(CompareType.REG_EXP, "tests/.*"));
        forbiddenFilePaths = new LinkedList<FilePath>();
        forbiddenFilePaths.add(new FilePath(CompareType.REG_EXP, "tests/.*2.*"));
        files = new LinkedList<String>();
        files.add("tests/test.txt");
        files.add("tests/test2.txt");
        config = new GerritProject(CompareType.REG_EXP, "project.*5", branches, topics, filePaths, forbiddenFilePaths,
                false);
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "projectNumber5", "feature/mymaster", null, files, true, false), });

        branches = new LinkedList<Branch>();
        branches.add(new Branch(CompareType.ANT, "**/master"));
        filePaths = new LinkedList<FilePath>();
        filePaths.add(new FilePath(CompareType.ANT, "**/*test*"));
        forbiddenFilePaths = new LinkedList<FilePath>();
        forbiddenFilePaths.add(new FilePath(CompareType.ANT, "**/*skip*"));
        config = new GerritProject(
                CompareType.ANT, "vendor/**/project", branches, topics, filePaths, forbiddenFilePaths, false);
        files = new LinkedList<String>();
        files.add("resources/test.xml");
        files.add("files/skip.txt");
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "vendor/semc/master/project", "origin/master", null, files, true, false), });
    }

    /**
     * Test parameters for checking that project/branch/topic mismatches cause
     * file path filters to be ignored, even though they would have matched.
     * These also verify that the list of file paths aren't queried unnecessarily.
     * @param parameters Test parameter list to append to.
     */
    private static void addParametersForBypassFileMatchTests(LinkedList<InterestingScenarioWithFiles[]> parameters) {
        // Mismatched project.
        List<Branch> branches = Collections.singletonList(new Branch(CompareType.PLAIN, "branch"));
        List<Topic> topics = Collections.singletonList(new Topic(CompareType.PLAIN, "topic"));
        List<FilePath> filePaths = Collections.singletonList(new FilePath(CompareType.PLAIN, "test.txt"));
        List<FilePath> forbiddenFilePaths = Collections.singletonList(new FilePath(CompareType.PLAIN, "nomatch.txt"));
        GerritProject config = new GerritProject(
                CompareType.PLAIN, "project", branches, topics, filePaths, forbiddenFilePaths, false);
        List<String> files = Collections.singletonList("test.txt");
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "another-project", "branch", "topic", files, false, false), });

        // Same project, different branch.
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "project", "another-branch", "topic", files, false, false), });

        // Same project and branch, different topic.
        parameters.add(new InterestingScenarioWithFiles[]{new InterestingScenarioWithFiles(
                config, "project", "branch", "another-topic", files, false, false), });
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
        boolean fileCheckNeeded;
        boolean fileCheckOccurred;

        /**
         * Constructor.
         * @param config config
         * @param project the project of this scenario.
         * @param branch the branch of this scenario.
         * @param topic the topic of this scenario.
         * @param files the files in this scenario.
         * @param fileCheckNeeded whether list of files is expected to be referenced.
         * @param expected the expected outcome, true if interesting, false if not.
         */
        public InterestingScenarioWithFiles(GerritProject config,
                String project,
                String branch,
                String topic,
                List<String> files,
                boolean fileCheckNeeded,
                boolean expected) {
            this.config = config;
            this.project = project;
            this.branch = branch;
            this.topic = topic;
            this.files = files;
            this.fileCheckNeeded = fileCheckNeeded;
            this.fileCheckOccurred = false;
            this.expected = expected;
        }

        /**
         * Constructor.
         */
        public InterestingScenarioWithFiles() {
        }

        /**
         * List of files to be matched.
         * @return the list
         */
        public List<String> getFiles() {
            this.fileCheckOccurred = true;
            return files;
        }
    }
}
