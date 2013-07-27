package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;

/**
 * A sigleton class that keeps list of Jenkin's Gerrit project's depedencies.
 * Basically this class makes TriggerMissedPatches-class work faster
 * by giving spesific locations of interesting gerrit projects.
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
     * projectList[Gerrit project pattern] = GerritProject data
     */
    private Map<String, GerritProject> projectList = new HashMap<String, GerritProject>();

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
                inst.projectList.put(key, project);
                logger.info("Project " + key + " added to Gerrit project list.");
            }
        }
    }

    /**
     *  Returns project list.
     *  @return gerrit projects that are stored into map.
     */
    public static Map<String, GerritProject> getGerritProjects() {
        return getInstance().projectList;
    }
}
