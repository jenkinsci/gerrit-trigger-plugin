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


import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.RefUpdated;

import hudson.Extension;
import hudson.model.Run;
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
import hudson.remoting.VirtualChannel;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;
import org.kohsuke.stapler.DataBoundConstructor;
import org.eclipse.jgit.lib.ObjectId;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;

/**
 * Used by the git plugin to determine the revision to build.
 * @author Andrew Bayer
 */
public class GerritTriggerBuildChooser extends BuildChooser {
    private static final long serialVersionUID = 2003462680723330645L;

    /**
     * Used by XStream for something.
     */
    @SuppressWarnings("unused")
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
     *
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
     * @throws InterruptedException  In case of error
     */
    @Override
    public Collection<Revision> getCandidateRevisions(boolean isPollCall, String singleBranch,
                                                      GitClient git, TaskListener listener,
                                                      BuildData data, BuildChooserContext context)
            throws GitException, IOException, InterruptedException {

        try {
            String rev = context.actOnBuild(new GetGerritEventRevision());
            if (rev == null) {
                rev = "FETCH_HEAD";
            }

            String refspec = context.actOnBuild(new GetGerritEventRefspec());
            if (refspec == null) {
                refspec = singleBranch;
            }

            ObjectId sha1 = git.revParse(rev);

            Revision revision = new Revision(sha1);
            revision.getBranches().add(new Branch(refspec, sha1));

            return Collections.singletonList(revision);
        } catch (GitException e) {
            // branch does not exist, there is nothing to build
            return Collections.<Revision>emptyList();
        }
    }

    @Override
    public Build prevBuildForChangelog(String singleBranch, BuildData data, GitClient git,
                                       BuildChooserContext context) throws InterruptedException, IOException {
        if (data != null) {
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
        } else {
            //Hmm no sure what to do here, but the git plugin can handle us returning null here
            return null;
        }
    }

    /**
     * Close walk through method name found by reflection.
     * @param walk the RevWalk object to be closed
     */
    private static void closeByReflection(@NonNull RevWalk walk) {
        java.lang.reflect.Method closeMethod;
        try {
            closeMethod = walk.getClass().getDeclaredMethod("close");
        } catch (NoSuchMethodException ex) {
            LOGGER.log(Level.SEVERE, "Exception finding walker close method: {0}", ex);
            return;
        } catch (SecurityException ex) {
            LOGGER.log(Level.SEVERE, "Exception finding walker close method: {0}", ex);
            return;
        }
        try {
            closeMethod.invoke(walk);
        } catch (IllegalAccessException ex) {
            LOGGER.log(Level.SEVERE, "Exception calling walker close method: {0}", ex);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.SEVERE, "Exception calling walker close method: {0}", ex);
        } catch (InvocationTargetException ex) {
            LOGGER.log(Level.SEVERE, "Exception calling walker close method: {0}", ex);
        }
    }

    /**
     * Call release method on walk.  JGit 3 uses release(), JGit 4 uses close() to
     * release resources.
     *
     * This method should be removed once the code depends on git client 2.0.0.
     * @param walk object whose close or release method will be called
     * @throws IOException on IO error
     */
    private static void releaseOrClose(RevWalk walk) throws IOException {
        if (walk == null) {
            return;
        }
        try {
            walk.release(); // JGit 3
        } catch (NoSuchMethodError noMethod) {
            closeByReflection(walk);
        }
    }

    //CS IGNORE RedundantThrows FOR NEXT 30 LINES. REASON: Informative, and could happen.
    /**
     * Gets the top parent of the given revision.
     *
     * @param id Revision
     * @param git GitClient API object
     * @return object id of Revision's parent, or of Revision itself if there is no parent
     * @throws GitException In case of error in git call
     * @throws InterruptedException if the repository handling gets interrupted
     * @throws IOException in case of communication errors.
     */
    @SuppressWarnings("serial")
    private ObjectId getFirstParent(final ObjectId id, GitClient git)
            throws GitException, IOException, InterruptedException {
        return git.withRepository(new RepositoryCallback<ObjectId>() {
            @Override
            public ObjectId invoke(Repository repository, VirtualChannel virtualChannel)
                    throws IOException, InterruptedException {
                RevWalk walk = null;
                ObjectId result = null;
                try {
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
                    releaseOrClose(walk);
                }
                return result;
            }
        });
    }

    /**
     * Descriptor for GerritTriggerBuildChooser.
     */
    @Extension(optional = true)
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

    /**
     * Retrieve the Gerrit event revision
     */
    private static class GetGerritEventRevision
            implements BuildChooserContext.ContextCallable<Run<?, ?>, String> {
        static final long serialVersionUID = 0L;
        @Override
        public String invoke(Run<?, ?> build, VirtualChannel channel) {
            GerritCause cause = build.getCause(GerritCause.class);
            if (cause != null) {
                GerritTriggeredEvent event = cause.getEvent();
                if (event instanceof ChangeBasedEvent) {
                    return ((ChangeBasedEvent)event).getPatchSet().getRevision();
                }
                if (event instanceof RefUpdated) {
                    return ((RefUpdated)event).getRefUpdate().getNewRev();
                }
            }
            return null;
        }
    }

    /**
     * Retrieve the Gerrit refspec
     */
    private static class GetGerritEventRefspec
            implements BuildChooserContext.ContextCallable<Run<?, ?>, String> {
        static final long serialVersionUID = 0L;
        @Override
        public String invoke(Run<?, ?> build, VirtualChannel channel) {
            GerritCause cause = build.getCause(GerritCause.class);
            if (cause != null) {
                GerritTriggeredEvent event = cause.getEvent();
                if (event instanceof ChangeBasedEvent) {
                    return ((ChangeBasedEvent)event).getPatchSet().getRef();
                }
                if (event instanceof RefUpdated) {
                    return ((RefUpdated)event).getRefUpdate().getRefName();
                }
            }
            return null;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(GerritTriggerBuildChooser.class.getName());
}
