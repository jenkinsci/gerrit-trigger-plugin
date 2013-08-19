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
public class GerritProjectAutorebuildTest {

    private final InterestingScenario scenario;

    /**
     * Constructor.
     * @param scenario scenario
     */
    public GerritProjectAutorebuildTest(InterestingScenario scenario) {
        this.scenario = scenario;
    }

    /**
     * Tests {@link GerritProject#isInteresting(java.lang.String, java.lang.String)}.
     */
    @Test
    public void testAutorebuild() {
        assertEquals(scenario.expected, scenario.config.isAutoRebuildEnabled(scenario.project, scenario.branch));
    }

    /**
     * The parameters.
     * @return parameters
     */
    @Parameters
    public static Collection getParameters() {

        List<InterestingScenario[]> parameters = new LinkedList<InterestingScenario[]>();

        List<Branch> branches = new LinkedList<Branch>();
        Branch branch = new Branch(CompareType.PLAIN, "master", true);
        branches.add(branch);
        GerritProject config = new GerritProject(CompareType.PLAIN, "project", branches, null);
        parameters.add(new InterestingScenario[]{new InterestingScenario(config, "project", "master", true)});

        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.PLAIN, "master", false);
        branches.add(branch);
        config = new GerritProject(CompareType.PLAIN, "project", branches, null);
        parameters.add(new InterestingScenario[]{new InterestingScenario(config, "project", "master", false)});

        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.ANT, "**/master", true);
        branches.add(branch);
        config = new GerritProject(CompareType.PLAIN, "project", branches, null);
        parameters.add(new InterestingScenario[]{new InterestingScenario(config, "project", "origin/master", true)});

        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.ANT, "**/master", false);
        branches.add(branch);
        config = new GerritProject(CompareType.PLAIN, "project", branches, null);
        parameters.add(new InterestingScenario[]{new InterestingScenario(config, "project", "origin/master", false)});

        branches = new LinkedList<Branch>();
        branch = new Branch(CompareType.ANT, "**/master", true);
        branches.add(branch);
        branch = new Branch(CompareType.PLAIN, "feature", false);
        branches.add(branch);
        config = new GerritProject(CompareType.PLAIN, "project", branches, null);
        parameters.add(new InterestingScenario[]{new InterestingScenario(config, "project", "master", true)});
        parameters.add(new InterestingScenario[]{new InterestingScenario(config, "project", "feature/master", true)});
        parameters.add(new InterestingScenario[]{new InterestingScenario(config, "project", "feature", false)});

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
