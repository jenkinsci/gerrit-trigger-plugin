/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
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

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.util.ComboBoxModel;
import java.util.List;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Base settings for one matcher rule of a Gerrit project.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritProject implements Describable<GerritProject> {

    private CompareType compareType;
    private String pattern;
    private List<Branch> branches;
    private List<FilePath> filePaths;
    private String serverName;


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
     * @param filePaths the file-path rules.
     * @param serverName the name of the Gerrit server.
     */
    @DataBoundConstructor
    public GerritProject(
            CompareType compareType,
            String pattern,
            List<Branch> branches,
            List<FilePath> filePaths,
            String serverName) {

        this.compareType = compareType;
        this.pattern = pattern;
        this.branches = branches;
        this.filePaths = filePaths;
        this.serverName = serverName;
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
     * The list of FilePath-rules.
     * @return the branch-rules
     */
    public List<Branch> getBranches() {
        return branches;
    }

    /**
     * The list of FilePath-rules.
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
     * Compares the project, branch and files to see if the rules specified is a match.
     * @param project the gerrit project
     * @param branch the branch.
     * @param files the files.
     * @return true is the rules match.
     */
    public boolean isInteresting(String project, String branch, List<String> files) {
        if (compareType.matches(pattern, project)) {
            for (Branch b : branches) {
                if (b.isInteresting(branch)) {
                    for (FilePath f : filePaths) {
                        if (f.isInteresting(files)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } else {
            return false;
        }
    }

    /**
     * Compares the project and branch to see if the rules specified is a match.
     * @param project the gerrit project
     * @param branch the branch.
     * @return true is the rules match.
     */
    public boolean isInteresting(String project, String branch) {
        if (compareType.matches(pattern, project)) {
            for (Branch b : branches) {
                if (b.isInteresting(branch)) {
                    return true;
                }
            }
            return false;
        } else {
            return false;
        }
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
         *
         * @return ComboBoxModels containing a list of all Gerrit Projects
         */
        public ComboBoxModel doFillPatternItems() {
            return new ComboBoxModel();
        }
        @Override
        public String getDisplayName() {
            return null;
        }
    }
}
