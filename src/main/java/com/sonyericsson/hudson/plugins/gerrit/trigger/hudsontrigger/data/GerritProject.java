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
import hudson.model.Item;
import hudson.util.ComboBoxModel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;

/**
 * Base settings for one matcher rule of a Gerrit project.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritProject implements Describable<GerritProject> {
    /** Magical file name which represents the commit message. */
    private static final String MAGIC_FILE_NAME_COMMIT_MSG = "/COMMIT_MSG";
    /** Magical file name which represents the merge list of a merge commit. */
    private static final String MAGIC_FILE_NAME_MERGE_LIST = "/MERGE_LIST";
    /** Magical file name which doesn't represent a file. Used specifically for patchset-level comments. */
    private static final String MAGIC_FILE_NAME_PATCHSET_LEVEL = "/PATCHSET_LEVEL";

    private CompareType compareType;
    private String pattern;
    private List<Branch> branches;
    private List<FilePath> filePaths;
    private List<Topic> topics;
    private List<Hashtag> hashtags;
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
     *
     * @return the hashtags-rules
     */
    public List<Hashtag> getHashtags() {
        return hashtags;
    }

    /**
     * The list of the hashtags-rules.
     * @param hashtags the hashtags-rules
     */
    @DataBoundSetter
    public void setHashtags(List<Hashtag> hashtags) {
        this.hashtags = hashtags;
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
     * @param files a closure which returns the list of files in the change.
     * @return true is the rules match.
     */
    @Deprecated
    public boolean isInteresting(String project, String branch, String topic, Supplier<List<String>> files) {
        if (isInteresting(project, branch, topic)) {
            return isInterestingFile(files.get());
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
    @Deprecated
    public boolean isInteresting(String project, String branch, String topic) {
        Change change = new Change();
        change.setProject(project);
        change.setBranch(branch);
        change.setTopic(topic);
        return isInteresting(change);
    }

    /**
     * Compares the project, branch and files to see if the rules specified is a match.
     * @param change gerrit change info.
     * @param files a closure which returns the list of files in the change.
     * @return true is the rules match.
     */
    public boolean isInteresting(Change change, Supplier<List<String>> files) {
        if (isInteresting(change)) {
            return isInterestingFile(files.get());
        }
        return false;
    }

    /**
     * Compares the project and branch to see if the rules specified is a match.
     * @param change gerrit change info.
     * @return true is the rules match.
     */
    public boolean isInteresting(Change change) {
        if (compareType.matches(pattern, change.getProject())) {
            for (Branch b : branches) {
                if (b.isInteresting(change.getBranch())) {
                    return isInterestingTopic(change.getTopic()) && isInterestingHashtags(change.getHashtags());
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
        if (topics != null && !topics.isEmpty()) {
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
     * Compare tags to see if the rules specified is a match.
     *
     * @param tags the tags in change.
     * @return true if the rules match or no rules.
     */
    private boolean isInterestingHashtags(List<String> tags) {
        if (this.hashtags != null && !this.hashtags.isEmpty()) {
            return this.hashtags.stream().anyMatch(h-> h.isInteresting(tags));
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
        List<String> tmpFiles = new ArrayList<>(files);
        tmpFiles.remove(MAGIC_FILE_NAME_COMMIT_MSG);
        tmpFiles.remove(MAGIC_FILE_NAME_MERGE_LIST);
        tmpFiles.remove(MAGIC_FILE_NAME_PATCHSET_LEVEL);

        boolean foundInterestingForbidden = false;
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

        if (filePaths != null && !filePaths.isEmpty()) {
            for (FilePath f : filePaths) {
                if (f.isInteresting(tmpFiles)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    @Override
    public Descriptor<GerritProject> getDescriptor() {
        return Jenkins.get().getDescriptor(getClass());
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
         * @param project the current project.
         * @param serverName the name of the server that the user has chosen.
         * @return ComboBoxModels containing a list of all Gerrit Projects found on that server.
         */
        public ComboBoxModel doFillPatternItems(@AncestorInPath Item project, @QueryParameter("serverName")
                @RelativePath("..") final String serverName) {
            if (project == null) {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            } else {
                project.checkPermission(Item.CONFIGURE);
            }
            Collection<String> projects = new HashSet<>();

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
            return "";
        }
    }
}
