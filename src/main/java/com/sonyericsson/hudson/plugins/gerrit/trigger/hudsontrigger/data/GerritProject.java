/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.  All rights reserved.
 *  Copyright 2013 Sony Mobile Communications AB.  All rights reserved.
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

import static com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer.ANY_SERVER;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.util.ComboBoxModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;

/**
 * Base settings for one matcher rule of a Gerrit project.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritProject implements Describable<GerritProject> {

    private CompareType compareType;
    private String pattern;
    private List<Branch> branches;
    private List<FilePath> filePaths;
    private List<Topic> topics;
    private List<FilePath> forbiddenFilePaths;
    private boolean disableStrictForbiddenFileVerification;

    /**
     * Default empty constructor.
     */
    public GerritProject() {

    }

    /**
     * DataBound Constructor.
     * @param compareType the compareType
     * @param pattern the project-name pattern
     * @param branches the branch-rules
     * @param topics the topic-rules
     * @param filePaths the file-path rules.
     * @param forbiddenFilePaths the forbidden file-path rules.
     * @param disableStrictForbiddenFileVerification whether to be strict or not.
     */
    @DataBoundConstructor
    public GerritProject(
            CompareType compareType,
            String pattern,
            List<Branch> branches,
            List<Topic> topics,
            List<FilePath> filePaths,
            List<FilePath> forbiddenFilePaths,
            boolean disableStrictForbiddenFileVerification) {

        this.compareType = compareType;
        this.pattern = pattern;
        this.branches = branches;
        this.topics = topics;
        this.filePaths = filePaths;
        this.forbiddenFilePaths = forbiddenFilePaths;
        this.disableStrictForbiddenFileVerification = disableStrictForbiddenFileVerification;
    }

    /**
     * Whether to disable strict verification of forbidden files.
     * @return true if disabled.
     */
    public boolean isDisableStrictForbiddenFileVerification() {
        return disableStrictForbiddenFileVerification;
    }

    /**
     * Set whether to disable strict verification of forbidden files.
     * @param disableStrictForbiddenFileVerification true to disable.
     */
    public void setDisableStrictForbiddenFileVerification(boolean disableStrictForbiddenFileVerification) {
        this.disableStrictForbiddenFileVerification = disableStrictForbiddenFileVerification;
    }

    /**
     * Which algorithm-type to use with the pattern.
     * @return the compareType
     */
    public CompareType getCompareType() {
        return compareType;
    }

    /**
     * Which algorithm-type to use with the pattern.
     * @param compareType the compareType
     */
    public void setCompareType(CompareType compareType) {
        this.compareType = compareType;
    }

    /**
     * The pattern for the project-name to match on.
     * @return the pattern
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * The pattern for the project-name to match on.
     * @param pattern the pattern
     */
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    /**
     * The list of branch-rules.
     * @return the branch-rules
     */
    public List<Branch> getBranches() {
        return branches;
    }

    /**
     * The list of branch-rules.
     * @param branches the branch-rules
     */
    public void setBranches(List<Branch> branches) {
        this.branches = branches;
    }

    /**
     * The list of filepath-rules.
     * @return the filepath-rules
     */
    public List<FilePath> getFilePaths() {
        return filePaths;
    }

    /**
     * The list of filepath-rules.
     * @param filePaths the filepath-rules
     */
    public void setFilePaths(List<FilePath> filePaths) {
        this.filePaths = filePaths;
    }

    /**
     * The list of topic-rules.
     * @return the topic-rules
     */
    public List<Topic> getTopics() {
        return topics;
    }

    /**
     * The list of topic-rules.
     * @param topics the topic-rules
     */
    public void setTopics(List<Topic> topics) {
        this.topics = topics;
    }

    /**
     * The list of the forbidden file-path rules.
     * @return the forbidden file-path rules.
     */
    public List<FilePath> getForbiddenFilePaths() {
        return forbiddenFilePaths;
    }

    /**
     * The list of the forbidden file-path rules.
     * @param forbiddenFilePaths the forbidden file-path rules.
     */
    public void setForbiddenFilePaths(List<FilePath> forbiddenFilePaths) {
        this.forbiddenFilePaths = forbiddenFilePaths;
    }

    /**
     * Compares the project, branch and files to see if the rules specified is a match.
     * @param project the Gerrit project
     * @param branch the branch.
     * @param topic the topic.
     * @param files the files.
     * @return true is the rules match.
     */
    public boolean isInteresting(String project, String branch, String topic, List<String> files) {
        if (compareType.matches(pattern, project)) {
            List<String> tmpFiles = new ArrayList<String>(files);
            for (Branch b : branches) {
                boolean foundInterestingForbidden = false;
                if (b.isInteresting(branch)) {
                    if (forbiddenFilePaths != null) {
                        Iterator<String> i = tmpFiles.iterator();
                        while (i.hasNext()) {
                            String file = i.next();
                            for (FilePath ffp : forbiddenFilePaths) {
                                if (ffp.isInteresting(file)) {
                                    if (!disableStrictForbiddenFileVerification) {
                                        return false;
                                    } else {
                                        foundInterestingForbidden = true;
                                        i.remove();
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (foundInterestingForbidden && tmpFiles.isEmpty()) {
                        // All changed files are forbidden, so this is not interesting
                        return false;
                    }
                    return isInterestingTopic(topic) && isInterestingFile(tmpFiles);
                }
            }
        }
        return false;
    }

    /**
     * Compares the project and branch to see if the rules specified is a match.
     * @param project the Gerrit project
     * @param branch the branch.
     * @param topic the topic.
     * @return true is the rules match.
     */
    public boolean isInteresting(String project, String branch, String topic) {
        if (compareType.matches(pattern, project)) {
            for (Branch b : branches) {
                if (b.isInteresting(branch)) {
                    return isInterestingTopic(topic);
                }
            }
        }
        return false;
    }

    /**
     * Compare topics to see if the rules specified is a match.
     *
     * @param topic the topic.
     * @return true if the rules match or no rules.
     */
    private boolean isInterestingTopic(String topic) {
        if (topics != null && topics.size() > 0) {
            for (Topic t : topics) {
                if (t.isInteresting(topic)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    /**
     * Compare files to see if the rules specified is a match.
     *
     * @param files the files.
     * @return true if the rules match or no rules.
     */
    private boolean isInterestingFile(List<String> files) {
        if (filePaths != null && filePaths.size() > 0) {
            for (FilePath f : filePaths) {
                if (f.isInteresting(files)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    @Override
    public Descriptor<GerritProject> getDescriptor() {
        return Hudson.getInstance().getDescriptor(getClass());
    }

    /**
     * Descriptor allowing for communication within the Repeatable.
     * Necessary for editable combobox.
     */
    @Extension
    public static final class DescriptorImpl extends Descriptor<GerritProject> {

        /**
         * Used to fill the project pattern combobox with AJAX.
         * The filled values will depend on the server that the user has chosen from the dropdown.
         *
         * @param serverName the name of the server that the user has chosen.
         * @return ComboBoxModels containing a list of all Gerrit Projects found on that server.
         */
        public ComboBoxModel doFillPatternItems(@QueryParameter("serverName")
                @RelativePath("..") final String serverName) {
            Collection<String> projects = new HashSet<String>();

            if (serverName != null && !serverName.isEmpty()) {
                if (ANY_SERVER.equals(serverName)) {
                    for (GerritServer server : PluginImpl.getServers_()) {
                        projects.addAll(server.getGerritProjects());
                    }
                } else {
                    GerritServer server = PluginImpl.getServer_(serverName);
                    if (server != null) {
                        projects.addAll(server.getGerritProjects());
                    }
                }
            }
            return new ComboBoxModel(projects);
        }
        @Override
        public String getDisplayName() {
            return null;
        }
    }
}
