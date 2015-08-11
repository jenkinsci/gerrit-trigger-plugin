/*
 * The MIT License
 *
 * Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data;

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Run;
import jenkins.model.Jenkins;

/**
 * Wrapper class for smoother serialization of {@link Run } and {@link Job }.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class TriggeredItemEntity {

    private Integer buildNumber;
        private String projectId;
        private transient Job project;
        private transient Run build;

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
        public TriggeredItemEntity(Job project, Run build) {
            setProject(project);
            setBuild(build);
        }

        /**
         * Easy Constructor.
         * The project will be set from {@link hudson.model.Run#getParent()}.
         * @param build a build.
         */
        public TriggeredItemEntity(Run build) {
            setProject(build.getParent());
            setBuild(build);
        }

        /**
         * Easy Constructor.
         * @param project a project.
         */
        public TriggeredItemEntity(Job project) {
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
        @WithBridgeMethods(AbstractBuild.class)
        public Run getBuild() {
            if (build == null) {
                if (buildNumber != null) {
                    getProject();
                    if (project != null) {
                        build = project.getBuildByNumber(buildNumber);
                    }
                }
            }
            return build;
        }

        /**
         * The build.
         * @param build the build.
         */
        public void setBuild(Run build) {
            this.build = build;
            buildNumber = build.getNumber();
        }

        /**
         * The project.
         * If this object is newly deserialized, the project will be looked up from {@link #getProjectId() }
         * @return the project.
         */
        @WithBridgeMethods(AbstractProject.class)
        public Job getProject() {
            if (project == null) {
                project = Jenkins.getInstance().getItemByFullName(projectId, Job.class);
            }
            return project;
        }

        /**
         * The project.
         * @param project the project.
         */
        public void setProject(Job project) {
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
         * @see Job#getFullName()
         */
        public String getProjectId() {
            return projectId;
        }

        /**
         * The project's id.
         * <strong>Do not use this method unless you are a serializer!</strong>
         * @param projectId the id.
         * @see Job#getFullName()
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
        public boolean equals(Run aBuild) {
            if (this.buildNumber != null) {
                return projectId.equals(aBuild.getParent().getFullName())
                        && this.buildNumber.equals(aBuild.getNumber());
            }
            return false;
        }

        /**
         * If this object represents the same project as aProject.
         * @param aProject the project to compare.
         * @return true if it is so.
         */
        public boolean equals(Job aProject) {
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
