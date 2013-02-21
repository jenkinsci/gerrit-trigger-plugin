/*
 * The MIT License
 *
 * Copyright 2010 Andrew Bayer. All rights reserved.
 * Copyright 2013 Sony Mobile Communications AB. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;


import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.Result;
import hudson.plugins.git.GitException;
import hudson.plugins.git.Revision;
import hudson.plugins.git.Branch;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.git.util.BuildChooserContext;
import hudson.plugins.git.util.BuildChooserDescriptor;
import hudson.plugins.git.util.BuildData;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;

/**
 * Used by the git plugin to determine the revision to build.
 * @author Andrew Bayer
 */
public class GerritTriggerBuildChooser extends BuildChooser {
    /**
     * Used by XStream for something.
     */
    private final String separator = "#";

    /**
     * Default constructor.
     */
    @DataBoundConstructor
    public GerritTriggerBuildChooser() {
    }

    //CS IGNORE RedundantThrows FOR NEXT 30 LINES. REASON: Informative, and could happen.

    /**
     * Determines which Revisions to build.
     * <p/>
     * Doesn't care about branches.
     *
     * @param isPollCall   whether this is being called from Git polling
     * @param singleBranch The branch
     * @param git          The GitClient API object
     * @param listener     TaskListener for logging, etc
     * @param data         the historical BuildData object
     * @param context      the remote context
     * @return A Collection containing the new revision.
     *
     * @throws GitException in case of error
     * @throws IOException  In case of error
     */
    @Override
    public Collection<Revision> getCandidateRevisions(boolean isPollCall, String singleBranch,
                                                      GitClient git, TaskListener listener,
                                                      BuildData data, BuildChooserContext context)
        throws GitException, IOException {

        try {
            ObjectId sha1 = git.revParse("FETCH_HEAD");

            Revision revision = new Revision(sha1);
            revision.getBranches().add(new Branch(singleBranch, sha1));

            return Collections.singletonList(revision);
        } catch (GitException e) {
            // branch does not exist, there is nothing to build
            return Collections.<Revision>emptyList();
        }
    }

    @Override
    public Build prevBuildForChangelog(String singleBranch, BuildData data, GitClient git,
                                       BuildChooserContext context) {
        ObjectId sha1 = git.revParse("FETCH_HEAD");

        // Now we cheat and add the parent as the last build on the branch, so we can
        // get the changelog working properly-ish.
        ObjectId parentSha1 = getFirstParent(sha1, git);
        Revision parentRev = new Revision(parentSha1);
        parentRev.getBranches().add(new Branch(singleBranch, parentSha1));

        int prevBuildNum = 0;
        Result r = null;

        Build lastBuild = data.getLastBuildOfBranch(singleBranch);
        if (lastBuild != null) {
            prevBuildNum = lastBuild.getBuildNumber();
            r = lastBuild.getBuildResult();
        }

        return new Build(parentRev, prevBuildNum, r);
    }

    //CS IGNORE RedundantThrows FOR NEXT 30 LINES. REASON: Informative, and could happen.
    /**
     * Gets the top parent of the given revision.
     *
     * @param id Revision
     * @param git GitClient API object
     * @return object id of Revision's parent, or of Revision itself if there is no parent
     * @throws GitException In case of error in git call
     */
    private ObjectId getFirstParent(ObjectId id, GitClient git) throws GitException {
        RevWalk walk = null;
        ObjectId result = null;
        try {
            Repository repository = git.getRepository();
            walk = new RevWalk(repository);
            RevCommit commit = walk.parseCommit(id);
            if (commit.getParentCount() > 0) {
                result = commit.getParent(0);
            } else {
                // If this is the first commit in the git, there is no parent.
                result = id;
            }
        } catch (Exception e) {
            throw new GitException("Failed to find parent id. ", e);
        } finally {
            if (walk != null) {
                walk.release();
            }
        }
        return result;
    }

    /**
     * Descriptor for GerritTriggerBuildChooser.
     */
    @Extension
    public static final class DescriptorImpl extends BuildChooserDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.DisplayName();
        }

        @Override
        public String getLegacyId() {
            return Messages.DisplayName();
        }
    }

}
