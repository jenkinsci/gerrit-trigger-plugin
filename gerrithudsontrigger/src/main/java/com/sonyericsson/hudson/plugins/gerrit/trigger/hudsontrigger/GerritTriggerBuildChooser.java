package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;


import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.Result;
import hudson.plugins.git.IGitAPI;
import hudson.plugins.git.GitException;
import hudson.plugins.git.Revision;
import hudson.plugins.git.Branch;
import hudson.plugins.git.util.*;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
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
     * Default constructor.
     */
    @DataBoundConstructor
    public GerritTriggerBuildChooser() {
    }

    //CS IGNORE RedundantThrows FOR NEXT 30 LINES. REASON: Informative, and could happen.
    /**
     * Determines which Revisions to build.
     *
     * Doesn't care about branches.
     * @param isPollCall whether this is being called from Git polling
     * @param singleBranch The branch
     * @param git The GitAPI object
     * @param listener TaskListener for logging, etc
     * @param data the historical BuildData object
     * @return A Collection containing the new revision.
     * @throws GitException in case of error
     * @throws IOException In case of error
     */
    @Override
    public Collection<Revision> getCandidateRevisions(boolean isPollCall, String singleBranch,
                                                      IGitAPI git, TaskListener listener,
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
    public Build prevBuildForChangelog(String singleBranch, BuildData data, IGitAPI git, BuildChooserContext context) {
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

        Build newLastBuild = new Build(parentRev, prevBuildNum, r);

        return newLastBuild;
    }

    //CS IGNORE RedundantThrows FOR NEXT 30 LINES. REASON: Informative, and could happen.
    /**
     * Gets the top parent of the given revision.
     *
     * @param id Revision
     * @param git GitAPI object
     * @return object id of Revision's parent, or of Revision itself if there is no parent
     * @throws GitException In case of error in git call
     */
    private ObjectId getFirstParent(ObjectId id, IGitAPI git) throws GitException {
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
                walk.dispose();
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
