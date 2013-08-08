/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;

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
     * and as content a ArrayList of Jenkins jobs related to that Gerrit project.
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
     *  Removes trigger from the projectList.
     *  @param trigger the GerritTrigger
     */
    public static void removeTriggerFromProjectList(GerritTrigger trigger) {
        GerritProjectList inst = getInstance();
        Iterator entries = inst.projectList.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry)entries.next();
            String projectName = (String)entry.getKey();
            ArrayList<GerritTrigger> triggers = (ArrayList<GerritTrigger>)entry.getValue();
            if (triggers == null || projectName == null || projectName.isEmpty()) {
                logger.warn("Invalid parameters: Triggers: " + triggers + " ProjectName: " + projectName);
                continue;
            }

            for (Iterator i = triggers.iterator(); i.hasNext();) {
                GerritTrigger trig = (GerritTrigger)i.next();
                if (trig == trigger) {
                   i.remove();
                }
            }
            if (triggers == null || triggers.isEmpty()) {
                entries.remove();
            }
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
