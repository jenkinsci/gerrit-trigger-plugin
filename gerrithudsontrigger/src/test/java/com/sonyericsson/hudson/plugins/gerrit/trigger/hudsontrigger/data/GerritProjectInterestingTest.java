/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
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
 * Testing different scenarios if they are interesting.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(Parameterized.class)
public class GerritProjectInterestingTest {

    private final InterestingScenario scenario;

    /**
     * Constructor.
     * @param scenario scenario
     */
    public GerritProjectInterestingTest(InterestingScenario scenario) {
        this.scenario = scenario;
    }

    /**
     * Tests {@link GerritProject#isInteresting(java.lang.String, java.lang.String)}.
     */
    @Test
    public void testInteresting() {
        assertEquals(scenario.expected, scenario.config.isInteresting(scenario.project, scenario.branch));
    }

    /**
     * The parameters.
     * @return parameters
     */
    @Parameters
    public static Collection getParameters() {

        List<InterestingScenario[]> parameters = new LinkedList<InterestingScenario[]>();

        List<Branch> branches = new LinkedList<Branch>();
        Branch branch = new Branch(CompareType.PLAIN, "master", false);
        branches.add(branch);
        GerritProject config = new GerritProject(CompareType.PLAIN, "project", branches, null);
        parameters.add(new InterestingScenario[]{new InterestingScenario(config, "project", "master", true)});

        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.ANT, "**/master", false);
        branches.add(branch);
        config = new GerritProject(CompareType.PLAIN, "project", branches, null);
        parameters.add(new InterestingScenario[]{new InterestingScenario(config, "project", "origin/master", true)});

        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.ANT, "**/master", false);
        branches.add(branch);
        config = new GerritProject(CompareType.PLAIN, "project", branches, null);
        parameters.add(new InterestingScenario[]{new InterestingScenario(config, "project", "master", true)});

        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.ANT, "**/master", false);
        branches.add(branch);
        branch = new Branch(CompareType.REG_EXP, "feature/.*master", false);
        branches.add(branch);
        config = new GerritProject(CompareType.PLAIN, "project", branches, null);
        parameters.add(new InterestingScenario[]{new InterestingScenario(config, "project", "master", true)});

        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.PLAIN, "olstorp", false);
        branches.add(branch);
        branch = new Branch(CompareType.REG_EXP, "feature/.*master", false);
        branches.add(branch);
        config = new GerritProject(CompareType.PLAIN, "project", branches, null);
        parameters.add(new InterestingScenario[]{new InterestingScenario(config, "project", "feature/mymaster", true)});

        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.ANT, "**/master", false);
        branches.add(branch);
        config = new GerritProject(CompareType.ANT, "vendor/**/project", branches, null);
        parameters.add(new InterestingScenario[]{new InterestingScenario(config, "vendor/semc/master/project",
                                                            "origin/master", true), });

        return parameters;
    }

    /**
     * A parameter to a test scenario.
     */
    public static class InterestingScenario {

        GerritProject config;
        String project;
        String branch;
        boolean expected;

        /**
         * Constructor.
         * @param config config
         * @param project project
         * @param branch branch
         * @param expected expected
         */
        public InterestingScenario(GerritProject config, String project, String branch, boolean expected) {
            this.config = config;
            this.project = project;
            this.branch = branch;
            this.expected = expected;
        }

        /**
         * Constructor.
         */
        public InterestingScenario() {
        }
    }
}
