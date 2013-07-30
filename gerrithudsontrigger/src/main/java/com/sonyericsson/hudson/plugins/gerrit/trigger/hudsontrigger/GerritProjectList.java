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
 * TODO: RemoveProject (So disabling the property would be possible without restarting Jenkins)
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
    private Map<String, ArrayList<GerritProject>> projectList = new HashMap<String, ArrayList<GerritProject>>();

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
     *  Adds project to project list.
     *  @param project the GerritProject
     */
    public static void addProject(GerritProject project) {
        GerritProjectList inst = getInstance();
        String key = inst.createKeyString(project);
        if (key != null) {
            if (inst.projectList.get(key) == null) {
                inst.projectList.put(key, new ArrayList<GerritProject>());
                logger.info("Gerrit project location " + project.getPattern() + " added to Gerrit project list.");
            }
            inst.projectList.get(key).add(project);
        }
    }

    /**
     *  Returns project list.
     *  @return gerrit projects that are stored into map.
     */
    public static Map<String, ArrayList<GerritProject>> getGerritProjects() {
        return getInstance().projectList;
    }
}