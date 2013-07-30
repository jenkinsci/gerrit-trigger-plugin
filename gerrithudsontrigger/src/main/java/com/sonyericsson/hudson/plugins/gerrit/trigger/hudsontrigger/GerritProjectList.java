package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
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
     * projectList[Gerrit project pattern] = GerritProject data
     */
    private List<GerritProject> projectList = new ArrayList<GerritProject>();

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
     *  Adds project to project list.
     *  @param project the GerritProject
     *  @param silentMode is silent mode enabled.
     */
    public static void addProject(GerritProject project, boolean silentMode) {
        GerritProjectList inst = getInstance();
        inst.projectList.add(project);
        logger.info("Project " + project.getPattern() + " added to Gerrit project list. SilentMode: " + silentMode);
    }

    /**
     *  Returns project list.
     *  @return gerrit projects that are stored into map.
     */
    public static List<GerritProject> getGerritProjects() {
        return getInstance().projectList;
    }
}
