/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;

import java.util.LinkedList;
import java.util.List;

/**
 * This bean contains information to the
 * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause}
 * about what other builds were involved in the same event.
 *
 * For backwards compatibility reasons this class is serialized by the help of the
 * XStream converter {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContextConverter}
 * so any future additions to this class need to be handled in that class as well or it won't be serialized correctly.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class TriggerContext {

    private GerritTriggeredEvent event;
    private TriggeredItemEntity thisBuild;
    private List<TriggeredItemEntity> others;

    /**
     * standard constructor.
     *
     * @param thisBuild this build.
     * @param event     the event for this context.
     * @param others    the other building and untriggered builds.
     */
    public TriggerContext(AbstractBuild thisBuild,
                          GerritTriggeredEvent event,
                          List<TriggeredItemEntity> others) {
        this.thisBuild = new TriggeredItemEntity(thisBuild);
        this.event = event;
        this.others = others;
    }

    /**
     * Standard constructor.
     *
     * @param event the event for this context.
     */
    public TriggerContext(GerritTriggeredEvent event) {
        this.event = event;
    }

    /**
     * Default constructor.
     */
    public TriggerContext() {
    }

    /**
     * A list of builds that were triggered by the same event as "this" build.
     *
     * @return the builds.
     */
    public synchronized List<TriggeredItemEntity> getOthers() {
        return others;
    }

    /**
     * A list of builds that were triggered by the same event as "this" build.
     * Could contain non-triggered builds represented by
     * {@link TriggeredItemEntity#getBuild()} == null.
     * <strong>
     * Do not use this method unless you are a serializer,
     * use {@link #addOtherBuild(hudson.model.AbstractBuild)} for adding builds.
     * </strong>
     *
     * @param otherBuilds the builds.
     */
    public synchronized void setOthers(List<TriggeredItemEntity> otherBuilds) {
        this.others = otherBuilds;
    }

    /**
     * The build that this context represents.
     *
     * @return the build.
     */
    public synchronized TriggeredItemEntity getThisBuild() {
        return thisBuild;
    }

    /**
     * The build that this context represents.
     *
     * @param thisBuild the build.
     */
    public synchronized void setThisBuild(TriggeredItemEntity thisBuild) {
        this.thisBuild = thisBuild;
    }

    /**
     * The build that this context represents.
     *
     * @param thisBuild the build.
     */
    public synchronized void setThisBuild(AbstractBuild thisBuild) {
        this.thisBuild = new TriggeredItemEntity(thisBuild);
    }

    /**
     * The event for this context.
     *
     * @return the event.
     */
    public GerritTriggeredEvent getEvent() {
        return event;
    }

    /**
     * The event for this context.
     *
     * @param event the event.
     */
    void setEvent(GerritTriggeredEvent event) {
        this.event = event;
    }

    /**
     * Adds a build to the list of other builds if it doesn't exist in the list.
     * Also if the build's project exists in the list of other projects,
     * the project will be removed from that list.
     *
     * @param build the build to add.
     * @see #getOtherBuilds()
     */
    public synchronized void addOtherBuild(AbstractBuild build) {
        if (others == null) {
            others = new LinkedList<TriggeredItemEntity>();
        }
        TriggeredItemEntity other = findOtherBuild(build);
        if (other == null) {
            other = findOtherProject(build.getProject());
            if (other != null) {
                other.setBuild(build);
            } else {
                others.add(new TriggeredItemEntity(build));
            }
        }
    }

    /**
     * Adds a project to the list of other projects if it doesn't exist in the list.
     *
     * @param project the project to add.
     * @see #getOtherProjects()
     */
    public synchronized void addOtherProject(AbstractProject project) {
        if (others == null) {
            others = new LinkedList<TriggeredItemEntity>();
        }
        if (findOtherProject(project) == null) {
            others.add(new TriggeredItemEntity(project));
        }
    }

    /**
     * Tells if there are any other builds or projects in this context.
     *
     * @return true if it is so.
     * @see #getOtherBuilds()
     * @see #getOtherProjects()
     */
    public synchronized boolean hasOthers() {
        return (others != null && !others.isEmpty());
    }

    /**
     * Finds the other object for the specified build, or null if the build does not exist.
     *
     * @param build a build.
     * @return the other object if there is one, null if there is none.
     */
    private synchronized TriggeredItemEntity findOtherBuild(AbstractBuild build) {
        for (TriggeredItemEntity other : others) {
            if (other.equals(build)) {
                return other;
            }
        }
        return null;
    }

    /**
     * Finds the object for the specified project, or null if the project does not exist in the list.
     *
     * @param project the project.
     * @return the other object, or null if none.
     */
    private synchronized TriggeredItemEntity findOtherProject(AbstractProject project) {
        for (TriggeredItemEntity other : others) {
            if (other.equals(project)) {
                return other;
            }
        }
        return null;
    }

    /**
     * Gets all the other builds in this context.
     * If some project hasn't started a build yet, that project will be unrepresented in this list.
     *
     * @return a list of builds from this context.
     */
    public synchronized List<AbstractBuild> getOtherBuilds() {
        List<AbstractBuild> list = new LinkedList<AbstractBuild>();
        if (others != null) {
            for (TriggeredItemEntity entity : others) {
                if (entity.getBuild() != null) {
                    list.add(entity.getBuild());
                }
            }
        }
        return list;
    }

    /**
     * Gets all the other projects in this context.
     *
     * @return a list of projects from this context.
     */
    public synchronized List<AbstractProject> getOtherProjects() {
        List<AbstractProject> list = new LinkedList<AbstractProject>();
        if (others != null) {
            for (TriggeredItemEntity entity : others) {
                if (entity.getProject() != null) {
                    list.add(entity.getProject());
                }
            }
        }
        return list;
    }
}
