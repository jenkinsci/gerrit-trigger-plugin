/*
 *  The MIT License
 *
 *  Copyright 2013 Joel Huttunen. All rights reserved.
 *  Copyright (c) 2014 Sony Mobile Communications Inc. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;

import jenkins.model.Jenkins;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;

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
@PrepareForTest(Jenkins.class)
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
        GerritProject config = new GerritProject(CompareType.PLAIN, pattern, branches, null, null, null, false);
        return config;
    }

    /**
     * Creates GerritTrigger.
     * @param gerritProjects the list of Gerrit projects.
     * @param silentMode is silentMode allowed.
     * @return GerritTrigger the created trigger.
     */
    private GerritTrigger createGerritTrigger(
            List<GerritProject> gerritProjects, boolean silentMode) {
        GerritTrigger trigger = Setup.createDefaultTrigger(null);
        trigger.setGerritProjects(gerritProjects);
        trigger.setSilentMode(silentMode);
        trigger.setSilentStartMode(false);
        return trigger;
    }

    /**
     * Set up the projects and triggers needed by tests.
     * @throws Exception if so
     */
    @Before
    public void createProjectsAndTriggers() throws Exception {

        PowerMockito.mockStatic(Jenkins.class);
        Jenkins jenkins = PowerMockito.mock(Jenkins.class);
        PowerMockito.when(Jenkins.getInstance()).thenReturn(jenkins);

        GerritProject gP1 = createGerritProject("test/project1", CompareType.PLAIN);
        GerritProject gP2 = createGerritProject("test/project2", CompareType.PLAIN);
        GerritProject gP3 = createGerritProject("test/project3", CompareType.PLAIN);
        GerritProject gP4 = createGerritProject("test/project4", CompareType.PLAIN);
        GerritProject gP5 = createGerritProject("test/project5", CompareType.PLAIN);

        GerritTrigger trigger1 = createGerritTrigger(Arrays.asList(gP1, gP2, gP1), false);
        GerritTrigger trigger2 = createGerritTrigger(Arrays.asList(gP2, gP1, gP5), false);
        GerritTrigger trigger3 = createGerritTrigger(Arrays.asList(gP1, gP3, gP2, gP5), false);

        for (GerritProject p : trigger1.getGerritProjects()) {
            GerritProjectList.addProject(p, trigger1);
        }
        for (GerritProject p : trigger2.getGerritProjects()) {
            GerritProjectList.addProject(p, trigger2);
        }
        for (GerritProject p : trigger3.getGerritProjects()) {
            GerritProjectList.addProject(p, trigger3);
        }
        gerritTriggers = Arrays.asList(trigger1, trigger2, trigger3);
    }

    /**
     * Remove all the projects and triggers created in the test set up.
     */
    @After
    public void clearProjectsAndTriggers() {
        for (GerritTrigger gerritTrigger : gerritTriggers) {
          GerritProjectList.removeTriggerFromProjectList(gerritTrigger);
        }
        gerritTriggers = null;
    }

    /**
     * Tests {@link GerritProjectListTest#testAddProject()} if adds projects correctly.
     *
     * @throws Exception if so.
     */
    @Test
    public void testAddProject() throws Exception {

        //CS IGNORE MagicNumberCheck FOR NEXT 15 LINES. REASON: test input
        @SuppressWarnings("serial")
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
        GerritProjectList.removeTriggerFromProjectList(gerritTriggers.get(1));
        Map<String, ArrayList<GerritTrigger>> projects = GerritProjectList.getGerritProjects();

        //CS IGNORE MagicNumberCheck FOR NEXT 10 LINES. REASON: test input
        assertEquals(projects.size(), 4);
        GerritProjectList.removeTriggerFromProjectList(gerritTriggers.get(2));
        projects = GerritProjectList.getGerritProjects();
        assertEquals(projects.size(), 2);

        @SuppressWarnings("serial")
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
        Map<String, ArrayList<GerritTrigger>> projects = GerritProjectList.getGerritProjects();
        //CS IGNORE MagicNumberCheck FOR NEXT 20 LINES. REASON: Test input
        assertEquals(4, projects.size());

        GerritProjectList.removeTriggerFromProjectList(gerritTriggers.get(0));
        GerritProjectList.removeTriggerFromProjectList(gerritTriggers.get(1));
        GerritProjectList.removeTriggerFromProjectList(gerritTriggers.get(2));
        projects = GerritProjectList.getGerritProjects();
        assertEquals(0, projects.size());


        createProjectsAndTriggers();
        GerritProjectList.removeTriggerFromProjectList(gerritTriggers.get(2));
        projects = GerritProjectList.getGerritProjects();
        assertEquals(3, projects.size());
        @SuppressWarnings("serial")
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
