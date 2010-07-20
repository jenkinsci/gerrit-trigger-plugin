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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import java.util.LinkedList;
import java.util.List;

/**
 * This bean contains information to the
 * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause}
 * about what other builds was involved in the same event.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class TriggerContext {

    private PatchsetCreated event;
    private TriggeredItemEntity thisBuild;
    private List<TriggeredItemEntity> others;

    /**
     * standard constructor.
     * @param thisBuild this build.
     * @param event the event for this context.
     * @param others the other building and untriggered builds.
     */
    public TriggerContext(AbstractBuild thisBuild,
            PatchsetCreated event,
            List<TriggeredItemEntity> others) {
        this.thisBuild = new TriggeredItemEntity(thisBuild);
        this.event = event;
        this.others = others;
    }

    /**
     * Standard constructor.
     * @param event the event for this context.
     */
    public TriggerContext(PatchsetCreated event) {
        this.event = event;
    }

    /**
     * Default constructor.
     */
    public TriggerContext() {
    }

    /**
     * A list of builds that was triggered by the same event as "this" build.
     * @return the builds.
     */
    public synchronized List<TriggeredItemEntity> getOthers() {
        return others;
    }

    /**
     * A list of builds that was triggered by the same event as "this" build.
     * Could contain non triggered builds represented by {@link Other#getBuild()} == null.
     * <strong>
     * Do not use this method unless you are a serializer,
     * use {@link #addOtherBuild(hudson.model.AbstractBuild)} for adding builds.
     * </strong>
     * @param otherBuilds the builds.
     */
    public synchronized void setOthers(List<TriggeredItemEntity> otherBuilds) {
        this.others = otherBuilds;
    }

    /**
     * The build that this context represents.
     * @return the build.
     */
    public TriggeredItemEntity getThisBuild() {
        return thisBuild;
    }

    /**
     * The build that this context represents.
     * @param thisBuild the build.
     */
    public synchronized void setThisBuild(TriggeredItemEntity thisBuild) {
        this.thisBuild = thisBuild;
    }

    /**
     * The build that this context represents.
     * @param thisBuild the build.
     */
    public synchronized void setThisBuild(AbstractBuild thisBuild) {
        this.thisBuild = new TriggeredItemEntity(thisBuild);
    }

    /**
     * The event for this context.
     * @return the event.
     */
    public PatchsetCreated getEvent() {
        return event;
    }

    /**
     * Adds a build to the list of other builds if it doesn't exist in the list.
     * Also if the build's project exists in the list of other projects,
     * the project will be removed from that list.
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
     * @return true if it is so.
     * @see #getOtherBuilds()
     * @see #getOtherProjects()
     */
    public synchronized boolean hasOthers() {
        return (others != null && !others.isEmpty());
    }

    /**
     * finds the orther object for the specified build, or null if the build does not exist.
     * @param build a build.
     * @return the other object if there is some, null if there is none.
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
     * Finds the object for the specified project, or null if the project does not exists in the list.
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

    /**
     * Wrapper class for smoother serialization of {@link AbstractBuild } and {@link AbstractProject }.
     */
    public static class TriggeredItemEntity {

        private Integer buildNumber;
        private String projectId;
        private transient AbstractProject project;
        private transient AbstractBuild build;

        /**
         * Standard constructor.
         * @param buildNumber a buildNumber
         * @param projectId a project's full name.
         */
        public TriggeredItemEntity(Integer buildNumber, String projectId) {
            this.buildNumber = buildNumber;
            this.projectId = projectId;
        }

        /**
         * Standard Constructor.
         * @param project a project.
         * @param build a build.
         */
        public TriggeredItemEntity(AbstractProject project, AbstractBuild build) {
            setProject(project);
            setBuild(build);
        }

        /**
         * Easy Constructor.
         * The project will be set from {@link AbstractBuild#getProject() }.
         * @param build a build.
         */
        public TriggeredItemEntity(AbstractBuild build) {
            setProject(build.getProject());
            setBuild(build);
        }

        /**
         * Easy Constructor.
         * @param project a project.
         */
        public TriggeredItemEntity(AbstractProject project) {
            setProject(project);
            this.buildNumber = null;
        }

        /**
         * Default constructor.
         */
        public TriggeredItemEntity() {
        }

        /**
         * If this object contains a build.
         * @return true if so.
         */
        public boolean hasBuild() {
            return buildNumber != null;
        }

        /**
         * The build.
         * If this object is newly deserialized, the build will be looked up via {@link #getBuildNumber() }.
         * @return the build.
         */
        public AbstractBuild getBuild() {
            if (build == null) {
                if (buildNumber != null) {
                    getProject();
                    if (project != null) {
                        build = (AbstractBuild)project.getBuildByNumber(buildNumber);
                    }
                }
            }
            return build;
        }

        /**
         * The build.
         * @param build the build.
         */
        public void setBuild(AbstractBuild build) {
            this.build = build;
            buildNumber = build.getNumber();
        }

        /**
         * The project.
         * If this object is newly deserialized, the project will be looked up from {@link #getProjectId() }
         * @return the project.
         */
        public AbstractProject getProject() {
            if (project == null) {
                project = Hudson.getInstance().getItemByFullName(projectId, AbstractProject.class);
            }
            return project;
        }

        /**
         * The project.
         * @param project the project.
         */
        public void setProject(AbstractProject project) {
            this.project = project;
            this.projectId = project.getFullName();
        }

        /**
         * The buildnumber if any yet.
         * @return the build number.
         */
        public Integer getBuildNumber() {
            return buildNumber;
        }

        /**
         * The buildnumber if any yet.
         * <strong>Do not use this method unless you are a serializer!</strong>
         * @param buildNumber the build number.
         */
        public void setBuildNumber(Integer buildNumber) {
            this.buildNumber = buildNumber;
        }

        /**
         * The project's id.
         * @return the id.
         * @see AbstractProject#getFullName()
         */
        public String getProjectId() {
            return projectId;
        }

        /**
         * The project's id.
         * <strong>Do not use this method unless you are a serializer!</strong>
         * @param projectId the id.
         * @see AbstractProject#getFullName()
         */
        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        @Override
        public boolean equals(Object obj) {
            //CS IGNORE InlineConditionals FOR NEXT 17 LINES. REASON: Autogenerated.
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final TriggeredItemEntity other = (TriggeredItemEntity)obj;
            if (this.buildNumber != other.buildNumber && (this.buildNumber == null
                    || !this.buildNumber.equals(other.buildNumber))) {
                return false;
            }
            if ((this.projectId == null) ? (other.projectId != null) : !this.projectId.equals(other.projectId)) {
                return false;
            }
            return true;
        }

        /**
         * If this object represents the same build as aBuild.
         * @param aBuild the build.
         * @return true if it is so.
         */
        public boolean equals(AbstractBuild aBuild) {
            if (this.buildNumber != null) {
                return projectId.equals(aBuild.getProject().getFullName())
                        && this.buildNumber.equals(aBuild.getNumber());
            }
            return false;
        }

        /**
         * If this object represents the same project as aProject.
         * @param aProject the project to compare.
         * @return true if it is so.
         */
        public boolean equals(AbstractProject aProject) {
            return projectId.equals(aProject.getFullName());
        }

        @Override
        public int hashCode() {
            //CS IGNORE InlineConditionals FOR NEXT 5 LINES. REASON: Autogenerated code.
            //CS IGNORE MagicNumber FOR NEXT 5 LINES. REASON: Autogenerated code.
            int hash = 5;
            hash = 47 * hash + (this.buildNumber != null ? this.buildNumber.hashCode() : 0);
            hash = 47 * hash + (this.projectId != null ? this.projectId.hashCode() : 0);
            return hash;
        }
    }
}
