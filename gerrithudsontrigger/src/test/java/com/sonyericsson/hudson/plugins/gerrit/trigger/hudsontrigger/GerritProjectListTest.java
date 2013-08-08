
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;
import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

/**
 * Tests for {@link GerritProjectListTest}.
 */
@RunWith(PowerMockRunner.class)
public class GerritProjectListTest {

    /**
     * Keeps data of created triggers.
     */
    private static List<GerritTrigger> gerritTriggers;

    /**
     * Creates GerritProject.
     * @param pattern the Gerrit project pattern.
     * @param compareType the type of the pattern.
     * @return GerritProject the created project.
     */
    private GerritProject createGerritProject(String pattern, CompareType compareType) {
        List<Branch> branches = new LinkedList<Branch>();
        Branch branch = new Branch(compareType, "master");
        branches.add(branch);
        GerritProject config = new GerritProject(CompareType.PLAIN, pattern, branches, null);
        return config;
    }

    /**
     * Creates GerritTrigger.
     * @param gerritProjects the list of Gerrit projects.
     * @param unreviewed is check unreviewed patches property allowed.
     * @param silentMode is silentMode allowed.
     * @return GerritTrigger the created trigger.
     */
    private GerritTrigger createGerritTrigger(
            List<GerritProject> gerritProjects, boolean unreviewed, boolean silentMode) {
        GerritTrigger trigger = Setup.createDefaultTrigger(null);
        trigger.setGerritProjects(gerritProjects);
        trigger.setAllowTriggeringUnreviewedPatches(unreviewed);
        trigger.setSilentMode(silentMode);
        return trigger;
    }

    /**
     * Creates empty GerritTrigger.
     * @param unreviewed is check unreviewed patches property allowed.
     * @param silentMode is silentMode allowed.
     * @return GerritTrigger the created trigger.
     */
    private GerritTrigger createGerritTrigger(boolean unreviewed, boolean silentMode) {
        return createGerritTrigger(Collections.EMPTY_LIST, unreviewed, silentMode);
    }

    /**
     * Set up project list.
     *
     * @return list of Gerrit triggers.
     * @throws Exception if so.
     */
    private List<GerritTrigger> createProjects() throws Exception {
        GerritProject gP1 = createGerritProject("test/project1", CompareType.PLAIN);
        GerritProject gP2 = createGerritProject("test/project2", CompareType.PLAIN);
        GerritProject gP3 = createGerritProject("test/project3", CompareType.PLAIN);
        GerritProject gP4 = createGerritProject("test/project4", CompareType.PLAIN);
        GerritProject gP5 = createGerritProject("test/project5", CompareType.PLAIN);

        GerritTrigger trigger1 = createGerritTrigger(Arrays.asList(gP1, gP2, gP1), true, false);
        GerritTrigger trigger2 = createGerritTrigger(Arrays.asList(gP2, gP1, gP5), true, false);
        GerritTrigger trigger3 = createGerritTrigger(Arrays.asList(gP1, gP3, gP2, gP5), true, false);

        for (GerritProject p : trigger1.getGerritProjects()) {
            GerritProjectList.addProject(p, trigger1);
        }
        for (GerritProject p : trigger2.getGerritProjects()) {
            GerritProjectList.addProject(p, trigger2);
        }
        for (GerritProject p : trigger3.getGerritProjects()) {
            GerritProjectList.addProject(p, trigger3);
        }
        return Arrays.asList(trigger1, trigger2, trigger3);
    }

    /**
     * Tests {@link GerritProjectListTest#testAddProject()} if adds projects correctly.
     *
     * @throws Exception if so.
     */
    @Test
    public void testAddProject() throws Exception {
        this.gerritTriggers = createProjects();

        //CS IGNORE MagicNumberCheck FOR NEXT 15 LINES. REASON: test input
        Map<String, Integer> projectNumbers = new HashMap<String, Integer>() {
            {
               put("test/project1", 4);
               put("test/project2", 3);
               put("test/project3", 1);
               put("test/project4", 0);
               put("test/project5", 2);
            }
        };

        Map<String, ArrayList<GerritTrigger>> projects = GerritProjectList.getGerritProjects();
        assertEquals(4, projects.size());
        for (Map.Entry<String, ArrayList<GerritTrigger>> entry : projects.entrySet()) {
            String gerritPattern = entry.getKey();
            ArrayList<GerritTrigger> triggers = entry.getValue();
            assertEquals(projectNumbers.get(gerritPattern), (Integer)triggers.size());
        }
    }

    /**
     * Tests {@link GerritProjectListTest#testClearProjects()} if removes projects correctly.
     *
     * @throws Exception if so.
     */
    @Test
    public void testClearProjects() throws Exception {
        GerritProjectList.removeTriggerFromProjectList(this.gerritTriggers.get(1));
        Map<String, ArrayList<GerritTrigger>> projects = GerritProjectList.getGerritProjects();

        //CS IGNORE MagicNumberCheck FOR NEXT 10 LINES. REASON: test input
        assertEquals(projects.size(), 4);
        GerritProjectList.removeTriggerFromProjectList(gerritTriggers.get(2));
        projects = GerritProjectList.getGerritProjects();
        assertEquals(projects.size(), 2);

        Map<String, Integer> projectNumbers = new HashMap<String, Integer>() {
            {
               put("test/project1", 2);
               put("test/project2", 1);
            }
        };
        for (Map.Entry<String, ArrayList<GerritTrigger>> entry : projects.entrySet()) {
            String gerritPattern = entry.getKey();
            ArrayList<GerritTrigger> trigs = entry.getValue();
            assertEquals(projectNumbers.get(gerritPattern), (Integer)trigs.size());
        }
    }

    /**
     * Tests {@link GerritProjectListTest#testClearProjects()} if adds and removes projects correctly.
     *
     * @throws Exception if so.
     */
    @Test
    public void testClearAndAddProjects() throws Exception {
        GerritProjectList.removeTriggerFromProjectList(this.gerritTriggers.get(0));
        Map<String, ArrayList<GerritTrigger>> projects = GerritProjectList.getGerritProjects();
        //CS IGNORE MagicNumberCheck FOR NEXT 1 LINES. REASON: Test input
        assertEquals(0, projects.size());

        this.gerritTriggers = createProjects();
        GerritProjectList.removeTriggerFromProjectList(this.gerritTriggers.get(2));
        projects = GerritProjectList.getGerritProjects();

        //CS IGNORE MagicNumberCheck FOR NEXT 6 LINES. REASON: test input
        assertEquals(3, projects.size());
        Map<String, Integer> projectNumbers = new HashMap<String, Integer>() {
            {
                put("test/project1", 3);
                put("test/project2", 2);
                put("test/project5", 1);
            }
        };
        for (Map.Entry<String, ArrayList<GerritTrigger>> entry : projects.entrySet()) {
            String gerritPattern = entry.getKey();
            ArrayList<GerritTrigger> trigs = entry.getValue();
            assertEquals(projectNumbers.get(gerritPattern), (Integer)trigs.size());
        }
    }
}
