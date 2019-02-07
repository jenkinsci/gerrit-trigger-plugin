package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import hudson.plugins.git.util.BuildChooserContext;
import hudson.plugins.git.Revision;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import java.util.Collection;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.FreeStyleProject;
import hudson.model.FreeStyleBuild;
import hudson.EnvVars;
import java.io.IOException;
import java.io.Serializable;
import hudson.model.Hudson;
import org.jenkinsci.plugins.gitclient.GitClient;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import org.eclipse.jgit.lib.ObjectId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GerritTriggerBuildChooser}.
 * @author Jacob Keller
 */
public class GerritTriggerBuildChooserTest {
    /**
     * Copied from the Git plugin because the real implementation is private.
     *
     * Ideally we can find a way to have this work somehow without needing to have this copy...
     */
    static class BuildChooserContextImpl implements BuildChooserContext, Serializable {
        private static final long serialVersionUID = 1L;

        final Job project;
        final Run build;
        final EnvVars environment;

        /**
         * Provides context for running closures while determining what to build
         * @param project the Jenkins project
         * @param build the Jenkins build
         * @param environment the environment
         */
        BuildChooserContextImpl(Job project, Run build, EnvVars environment) {
            this.project = project;
            this.build = build;
            this.environment = environment;
        }

        /**
         * Perform some closure, executing on the build
         * @param <T> The return type from the closure
         * @param callable the closure to run
         * @throws IOException if IO cannot be performed
         * @throws InterruptedException if the process is interrupted
         * @return closure return value
         */
        public <T> T actOnBuild(ContextCallable<Run<?, ?>, T> callable)
            throws IOException, InterruptedException {
            return callable.invoke(build, Hudson.MasterComputer.localChannel);
        }

        /**
         * Perform some closure, executing on the project
         * @param <T> The return type from the closure
         * @param callable the closure to run
         * @throws IOException if IO cannot be performed
         * @throws InterruptedException if the process is interrupted
         * @return closure return value
         */
        public <T> T actOnProject(ContextCallable<Job<?, ?>, T> callable)
            throws IOException, InterruptedException {
            return callable.invoke(project, Hudson.MasterComputer.localChannel);
        }

        /**
         * Get the build
         * @return Jenkins build
         */
        public Run<?, ?> getBuild() {
            return build;
        }

        /**
         * Get the project
         * @return environment variables
         */
        public EnvVars getEnvironment() {
            return environment;
        }
    }

    /**
     * Tests {@link GerritTriggerBuildChooser} if it correctly determines revision.
     *
     * @throws Exception if so.
     */
    @Test
    public void testGerritTriggerBuildChooser() throws Exception {
        GerritTriggerBuildChooser chooser = new GerritTriggerBuildChooser();
        final ObjectId fetchHead = ObjectId.fromString("7f3547c6d55946e25e99a847b5160d69e59994ba");
        final ObjectId patchsetRevision = ObjectId.fromString("38b0940738376ee1b66c332a2cb6d4d37bafa4e4");
        final String singleBranch = "origin/master";
        final String patchsetRefspec = "refs/changes/98/99498/2";

        // Mock the necessary objects we will need to make this work
        FreeStyleProject p = mock(FreeStyleProject.class);
        FreeStyleBuild b = mock(FreeStyleBuild.class);
        GitClient git = mock(GitClient.class);
        when(git.revParse("FETCH_HEAD")).thenReturn(fetchHead);

        BuildChooserContextImpl context = new BuildChooserContextImpl(p, b, null);

        // get the candidate revision(s)
        Collection<Revision> revs = chooser.getCandidateRevisions(true, singleBranch, git, null, null, context);

        // Check that we correctly use branch when no gerrit revision is used
        assertEquals(1, revs.size());
        assertEquals(1, revs.iterator().next().getBranches().size());
        assertEquals(singleBranch, revs.iterator().next().getBranches().iterator().next().getName());
        assertEquals(fetchHead, revs.iterator().next().getBranches().iterator().next().getSHA1());

        // Mock the objects to report a gerrit revision was built
        // build.getCause returns some object which reports the event correctly
        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated();
        patchsetCreated.getPatchSet().setRef(patchsetRefspec);
        patchsetCreated.getPatchSet().setRevision(patchsetRevision.toString());

        GerritCause gerritCause = new GerritCause();
        gerritCause.setEvent(patchsetCreated);
        when(b.getCause(GerritCause.class)).thenReturn(gerritCause);
        when(git.revParse(patchsetRefspec)).thenReturn(patchsetRevision);
        when(git.revParse(patchsetRevision.toString())).thenReturn(patchsetRevision);

        // get the candidate revision(s)
        revs = chooser.getCandidateRevisions(true, singleBranch, git, null, null, context);

        // Check that we correctly use branch when a gerrit revision is used
        assertEquals(1, revs.size());
        assertEquals(1, revs.iterator().next().getBranches().size());
        assertEquals(patchsetRefspec, revs.iterator().next().getBranches().iterator().next().getName());
        assertEquals(patchsetRevision, revs.iterator().next().getBranches().iterator().next().getSHA1());
    }
}
