package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * A sigleton class that keeps list of Jenkin's Gerrit projects.
 *
 * TODO: Support to other project formats (At the moment only plain text is supported).
 *
 */
public final class GerritProjectList {

    private static GerritProjectList instance = new GerritProjectList();
    private static final Logger logger = LoggerFactory.getLogger(GerritProjectList.class);

    /**
     * Data structure, which holds Project data from jenkins.
     *
     * projectList data structure has Gerrit project's pattern as key value
     * and as content a ArrayList of Jenkins GerritProjects related to that Gerrit project.
     */
    private Map<String, ArrayList<GerritTrigger>> projectList = new HashMap<String, ArrayList<GerritTrigger>>();

    /**
     * A private Constructor prevents any other class from instantiating.
     */
    private GerritProjectList() {
    }

    /**
     *  Static 'instance' method.
     *  @return instance of the class.
     */
    public static GerritProjectList getInstance() {
       return instance;
    }

    /**
     * Function returns projects name.
     * @param project the GerritProject
     * @return null if parameter project is null or if its type is not PLAIN text.
     *         Otherwise method will return project's pattern.
     */
    private String createKeyString(GerritProject project) {
        if (project != null) {
            if (project.getCompareType() == CompareType.PLAIN) {
                return project.getPattern();
            }
        }
        return null;
    }

    /**
     *  Deletes trigger from projectList.
     *  @param trigger the GerritTrigger
     */
    public static void clearTriggerProjects(GerritTrigger trigger) {
        GerritProjectList inst = getInstance();
        for (Map.Entry<String, ArrayList<GerritTrigger>> entry : inst.projectList.entrySet()) {
            String projectName = entry.getKey();
            ArrayList<GerritTrigger> triggers = entry.getValue();
            ArrayList<GerritTrigger> newTriggers = new ArrayList<GerritTrigger>();

            for (GerritTrigger trig : triggers) {
                if (!trig.equals(trigger)) {
                    newTriggers.add(trig);
                }
            }
            inst.projectList.put(projectName, newTriggers);
        }
    }

    /**
     *  Adds project to project list.
     *  @param project the GerritProject
     *  @param trigger the GerritTrigger
     */
    public static void addProject(GerritProject project, GerritTrigger trigger) {
        GerritProjectList inst = getInstance();
        String key = inst.createKeyString(project);
        if (key != null) {
            if (inst.projectList.get(key) == null) {
                inst.projectList.put(key, new ArrayList<GerritTrigger>());
                logger.info("Gerrit project location " + project.getPattern() + " added to Gerrit project list.");
            }
            inst.projectList.get(key).add(trigger);
        }
    }

    /**
     *  Returns project list.
     *  @return gerrit projects that are stored into map.
     */
    public static Map<String, ArrayList<GerritTrigger>> getGerritProjects() {
        return getInstance().projectList;
    }
}
