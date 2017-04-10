package com.sonyericsson.hudson.plugins.gerrit.trigger.spec;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerParameters;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.FilePath;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Topic;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginChangeAbandonedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginChangeRestoredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginCommentAddedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginRefUpdatedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.MockGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Account;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeRestored;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.RefUpdated;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

/**
 * Tests various aspects of {@link GerritTriggerParameters.ParameterMode}.
 *
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 */
public class ParameterModeJenkinsTest {
    /**
     * An instance of Jenkins Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public JenkinsRule j = new JenkinsRule();
    private FreeStyleProject job;
    private GerritTrigger trigger;

    /**
     * Shared setup for all tests.
     *
     * @throws IOException if so.
     */
    @Before
    public void setup() throws IOException {

        MockGerritHudsonTriggerConfig config = Setup.createConfig();
        GerritServer server = new MockGerritServer("gerrit version 2.11.4");
        server.setConfig(config);
        PluginImpl plugin = PluginImpl.getInstance();
        assertNotNull(plugin);
        plugin.setServers(Arrays.asList(server));

        job = j.createFreeStyleProject();
        job.getBuildersList().add(new ParametersBuilder());
        trigger = Setup.createDefaultTrigger(job);
        trigger.setGerritProjects(Collections.singletonList(new GerritProject(CompareType.ANT, "**",
                Collections.singletonList(new Branch(CompareType.ANT, "**")),
                Collections.<Topic>emptyList(), Collections.<FilePath>emptyList(),
                Collections.<FilePath>emptyList(), false)));
        trigger.setEscapeQuotes(false);
    }

    /**
     * Mock Gerrit server with a version.
     */
    public static class MockGerritServer extends GerritServer {

        private String version;

        /**
         * Create Gerrit Server with Version.
         * @param gerritVersion mock version for Gerrit.
         */
        public MockGerritServer(String gerritVersion) {
            super(PluginImpl.DEFAULT_SERVER_NAME);
            version = gerritVersion;
        }

        @Override
        public String getGerritVersion() {
            return version;
        }

        /**
         * Create server instance.
         */
        public MockGerritServer() {
            super(PluginImpl.DEFAULT_SERVER_NAME);
        }
    }
    /**
     * Tests the default {@link GerritTriggerParameters.ParameterMode#PLAIN}
     * when the build is triggered by a
     * {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated} event.
     *
     * @throws Exception if so
     */
    @Test
    public void testNameAndEmailParameterModeDefault() throws Exception {
        assertSame(GerritTriggerParameters.ParameterMode.PLAIN, trigger.getNameAndEmailParameterMode());
        Account ac = new Account("Bobby", "rsandell@cloudbees.com");
        PluginImpl.getHandler_().triggerEvent(Setup.createPatchsetCreatedWithAccounts(ac, ac, ac));
        j.waitUntilNoActivity();
        FreeStyleBuild build = job.getLastBuild();
        List<GerritTriggerParameters> params = Arrays.asList(
                GerritTriggerParameters.GERRIT_CHANGE_OWNER,
                GerritTriggerParameters.GERRIT_EVENT_ACCOUNT);
        String expected = ac.getNameAndEmail();
        for (GerritTriggerParameters param : params) {
            j.assertLogContains(param.name() + "=" + expected, build);
        }
    }

    /**
     * Tests the default {@link GerritTriggerParameters.ParameterMode#PLAIN}
     * when the build is triggered by a {@link ChangeAbandoned} event.
     *
     * @throws Exception if so
     */
    @Test
    public void testNameAndEmailParameterModeDefaultChangeAbandoned() throws Exception {
        assertSame(GerritTriggerParameters.ParameterMode.PLAIN, trigger.getNameAndEmailParameterMode());
        trigger.getTriggerOnEvents().add(new PluginChangeAbandonedEvent());
        Account ac = new Account("Bobby", "rsandell@cloudbees.com");
        ChangeAbandoned changeAbandoned = Setup.createChangeAbandoned();
        changeAbandoned.setAccount(ac);
        changeAbandoned.getChange().setOwner(ac);
        changeAbandoned.setAbandoner(ac);
        PluginImpl.getHandler_().triggerEvent(changeAbandoned);
        j.waitUntilNoActivity();
        FreeStyleBuild build = job.getLastBuild();
        List<GerritTriggerParameters> params = Arrays.asList(
                GerritTriggerParameters.GERRIT_CHANGE_OWNER,
                GerritTriggerParameters.GERRIT_EVENT_ACCOUNT,
                GerritTriggerParameters.GERRIT_CHANGE_ABANDONER);
        String expected = ac.getNameAndEmail();
        for (GerritTriggerParameters param : params) {
            j.assertLogContains(param.name() + "=" + expected, build);
        }
    }

    /**
     * Tests the default {@link GerritTriggerParameters.ParameterMode#PLAIN}
     * when the build is triggered by a {@link ChangeRestored} event.
     *
     * @throws Exception if so
     */
    @Test
    public void testNameAndEmailParameterModeDefaultChangeRestored() throws Exception {
        assertSame(GerritTriggerParameters.ParameterMode.PLAIN, trigger.getNameAndEmailParameterMode());
        trigger.getTriggerOnEvents().add(new PluginChangeRestoredEvent());
        Account ac = new Account("Bobby", "rsandell@cloudbees.com");
        ChangeRestored change = Setup.createChangeRestored();
        change.setAccount(ac);
        change.getChange().setOwner(ac);
        change.setRestorer(ac);
        PluginImpl.getHandler_().triggerEvent(change);
        j.waitUntilNoActivity();
        FreeStyleBuild build = job.getLastBuild();
        List<GerritTriggerParameters> params = Arrays.asList(
                GerritTriggerParameters.GERRIT_CHANGE_OWNER,
                GerritTriggerParameters.GERRIT_EVENT_ACCOUNT,
                GerritTriggerParameters.GERRIT_CHANGE_RESTORER);
        String expected = ac.getNameAndEmail();
        for (GerritTriggerParameters param : params) {
            j.assertLogContains(param.name() + "=" + expected, build);
        }
    }

    /**
     * Tests the default {@link GerritTriggerParameters.ParameterMode#PLAIN}
     * when the build is triggered by a {@link RefUpdated} event.
     *
     * @throws Exception if so
     */
    @Test
    public void testNameAndEmailParameterModeDefaultRefUpdated() throws Exception {
        assertSame(GerritTriggerParameters.ParameterMode.PLAIN, trigger.getNameAndEmailParameterMode());
        trigger.getTriggerOnEvents().add(new PluginRefUpdatedEvent());
        Account ac = new Account("Bobby", "rsandell@cloudbees.com");
        RefUpdated change = Setup.createRefUpdated(PluginImpl.DEFAULT_SERVER_NAME, "olle", "abc123");
        change.setAccount(ac);
        PluginImpl.getHandler_().triggerEvent(change);
        j.waitUntilNoActivity();
        FreeStyleBuild build = job.getLastBuild();
        List<GerritTriggerParameters> params = Arrays.asList(GerritTriggerParameters.GERRIT_EVENT_ACCOUNT);
        //TODO According to the doc GerritTriggerParameters.GERRIT_SUBMITTER should be set as well but its not?
        String expected = ac.getNameAndEmail();
        for (GerritTriggerParameters param : params) {
            j.assertLogContains(param.name() + "=" + expected, build);
        }
    }

    /**
     * Tests the {@link GerritTriggerParameters.ParameterMode#BASE64} setting
     * for {@link GerritTrigger#nameAndEmailParameterMode} when the build is triggered by a
     * {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated} event.
     *
     * @throws Exception if so
     */
    @Test
    public void testNameAndEmailParameterModeBase64() throws Exception {
        trigger.setNameAndEmailParameterMode(GerritTriggerParameters.ParameterMode.BASE64);
        Account ac = new Account("Bobby", "rsandell@cloudbees.com");
        PluginImpl.getHandler_().triggerEvent(Setup.createPatchsetCreatedWithAccounts(ac, ac, ac));
        j.waitUntilNoActivity();
        FreeStyleBuild build = job.getLastBuild();
        List<GerritTriggerParameters> params = Arrays.asList(
                GerritTriggerParameters.GERRIT_CHANGE_OWNER,
                GerritTriggerParameters.GERRIT_EVENT_ACCOUNT);
        String expected = GerritTriggerParameters.ParameterMode.encodeBase64(ac.getNameAndEmail());
        for (GerritTriggerParameters param : params) {
            j.assertLogContains(param.name() + "=" + expected, build);
        }
    }

    /**
     * Tests the {@link GerritTriggerParameters.ParameterMode#NONE} setting
     * for {@link GerritTrigger#nameAndEmailParameterMode} when the build is triggered by a
     * {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated} event.
     *
     * @throws Exception if so
     */
    @Test
    public void testNameAndEmailParameterModeNone() throws Exception {
        trigger.setNameAndEmailParameterMode(GerritTriggerParameters.ParameterMode.NONE);
        Account ac = new Account("Bobby", "rsandell@cloudbees.com");
        PluginImpl.getHandler_().triggerEvent(Setup.createPatchsetCreatedWithAccounts(ac, ac, ac));
        j.waitUntilNoActivity();
        FreeStyleBuild build = job.getLastBuild();
        List<GerritTriggerParameters> params = Arrays.asList(
                GerritTriggerParameters.GERRIT_CHANGE_OWNER,
                GerritTriggerParameters.GERRIT_EVENT_ACCOUNT);
        String expected = ac.getNameAndEmail();
        for (GerritTriggerParameters param : params) {
            j.assertLogNotContains(param.name() + "=", build);
        }
        j.assertLogNotContains(expected, build);
    }

    /**
     * Tests the {@link GerritTriggerParameters.ParameterMode#BASE64} (default) setting
     * for {@link GerritTrigger#commitMessageParameterMode} when the build is triggered by a
     * {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated} event.
     *
     * @throws Exception if so
     */
    @Test
    public void testCommitMessageParameterModeDefault() throws Exception {
        assertSame(GerritTriggerParameters.ParameterMode.BASE64, trigger.getCommitMessageParameterMode());
        String expected = "A new commit has arrived!";
        PatchsetCreated event = Setup.createPatchsetCreated();
        event.getChange().setCommitMessage(expected);
        PluginImpl.getHandler_().triggerEvent(event);
        j.waitUntilNoActivity();
        FreeStyleBuild build = job.getLastBuild();
        j.assertLogContains(GerritTriggerParameters.GERRIT_CHANGE_COMMIT_MESSAGE.name()
                + "="
                + GerritTriggerParameters.ParameterMode.encodeBase64(expected), build);
    }

    /**
     * Tests the {@link GerritTriggerParameters.ParameterMode#PLAIN} setting
     * for {@link GerritTrigger#commitMessageParameterMode} when the build is triggered by a
     * {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated} event.
     *
     * @throws Exception if so
     */
    @Test
    public void testCommitMessageParameterModePlain() throws Exception {
        trigger.setCommitMessageParameterMode(GerritTriggerParameters.ParameterMode.PLAIN);
        String expected = "A new commit has arrived!";
        PatchsetCreated event = Setup.createPatchsetCreated();
        event.getChange().setCommitMessage(expected);
        PluginImpl.getHandler_().triggerEvent(event);
        j.waitUntilNoActivity();
        FreeStyleBuild build = job.getLastBuild();
        j.assertLogContains(GerritTriggerParameters.GERRIT_CHANGE_COMMIT_MESSAGE.name()
                + "="
                + expected, build);
    }

    /**
     * Tests the {@link GerritTriggerParameters.ParameterMode#NONE} setting
     * for {@link GerritTrigger#commitMessageParameterMode} when the build is triggered by a
     * {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated} event.
     *
     * @throws Exception if so
     */
    @Test
    public void testCommitMessageParameterModeNone() throws Exception {
        trigger.setCommitMessageParameterMode(GerritTriggerParameters.ParameterMode.NONE);
        String expected = "A new commit has arrived!";
        PatchsetCreated event = Setup.createPatchsetCreated();
        event.getChange().setCommitMessage(expected);
        PluginImpl.getHandler_().triggerEvent(event);
        j.waitUntilNoActivity();
        FreeStyleBuild build = job.getLastBuild();
        j.assertLogNotContains(GerritTriggerParameters.GERRIT_CHANGE_COMMIT_MESSAGE.name(), build);
    }

    /**
     * Tests the {@link GerritTriggerParameters.ParameterMode#BASE64} (default) setting
     * for {@link GerritTrigger#commentTextParameterMode} when the build is triggered by a
     * {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded} event.
     *
     * @throws Exception if so
     */
    @Test
    public void testCommentTextParameterModeDefault() throws Exception {
        assertSame(GerritTriggerParameters.ParameterMode.BASE64, trigger.getCommentTextParameterMode());
        trigger.getTriggerOnEvents().add(new PluginCommentAddedEvent("Code-Review", "1"));
        String expected = "Triggering comment";
        CommentAdded event = Setup.createCommentAdded();
        event.setComment(expected);
        PluginImpl.getHandler_().triggerEvent(event);
        j.waitUntilNoActivity();
        FreeStyleBuild build = job.getLastBuild();
        j.assertLogContains(GerritTriggerParameters.GERRIT_EVENT_COMMENT_TEXT.name()
                + "="
                + GerritTriggerParameters.ParameterMode.encodeBase64(expected), build);
    }

    /**
     * Tests the {@link GerritTriggerParameters.ParameterMode#PLAIN} setting
     * for {@link GerritTrigger#commentTextParameterMode} when the build is triggered by a
     * {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded} event.
     *
     * @throws Exception if so
     */
    @Test
    public void testCommentTextParameterModePlain() throws Exception {
        trigger.setCommentTextParameterMode(GerritTriggerParameters.ParameterMode.PLAIN);
        trigger.getTriggerOnEvents().add(new PluginCommentAddedEvent("Code-Review", "1"));
        String expected = "Triggering comment";
        CommentAdded event = Setup.createCommentAdded();
        event.setComment(expected);
        PluginImpl.getHandler_().triggerEvent(event);
        j.waitUntilNoActivity();
        FreeStyleBuild build = job.getLastBuild();
        j.assertLogContains(GerritTriggerParameters.GERRIT_EVENT_COMMENT_TEXT.name()
                + "="
                + expected, build);
    }

    /**
     * Tests the {@link GerritTriggerParameters.ParameterMode#NONE} setting
     * for {@link GerritTrigger#commentTextParameterMode} when the build is triggered by a
     * {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded} event.
     *
     * @throws Exception if so
     */
    @Test
    public void testCommentTextParameterModeNone() throws Exception {
        trigger.setCommentTextParameterMode(GerritTriggerParameters.ParameterMode.NONE);
        trigger.getTriggerOnEvents().add(new PluginCommentAddedEvent("Code-Review", "1"));
        String expected = "Triggering comment";
        CommentAdded event = Setup.createCommentAdded();
        event.setComment(expected);
        PluginImpl.getHandler_().triggerEvent(event);
        j.waitUntilNoActivity();
        FreeStyleBuild build = job.getLastBuild();
        j.assertLogNotContains(GerritTriggerParameters.GERRIT_EVENT_COMMENT_TEXT.name(), build);
    }

    /**
     * Tests the {@link GerritTriggerParameters.ParameterMode#NONE} setting
     * for {@link GerritTrigger#changeSubjectParameterMode} when the build is triggered by a
     * {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated} event.
     *
     * @throws Exception if so
     */
    @Test
    public void testChangeSubjectParameterModeNone() throws Exception {
        trigger.setChangeSubjectParameterMode(GerritTriggerParameters.ParameterMode.NONE);
        String expected = "A new commit has arrived!";
        PatchsetCreated event = Setup.createPatchsetCreated();
        event.getChange().setSubject(expected);
        PluginImpl.getHandler_().triggerEvent(event);
        j.waitUntilNoActivity();
        FreeStyleBuild build = job.getLastBuild();
        j.assertLogNotContains(GerritTriggerParameters.GERRIT_CHANGE_SUBJECT.name(), build);
    }

    /**
     * Tests the {@link GerritTriggerParameters.ParameterMode#PLAIN} (default) setting
     * for {@link GerritTrigger#changeSubjectParameterMode} when the build is triggered by a
     * {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated} event.
     *
     * @throws Exception if so
     */
    @Test
    public void testChangeSubjectParameterModeDefault() throws Exception {
        assertSame(GerritTriggerParameters.ParameterMode.PLAIN, trigger.getChangeSubjectParameterMode());
        String expected = "A new commit has arrived!";
        PatchsetCreated event = Setup.createPatchsetCreated();
        event.getChange().setSubject(expected);
        PluginImpl.getHandler_().triggerEvent(event);
        j.waitUntilNoActivity();
        FreeStyleBuild build = job.getLastBuild();
        j.assertLogContains(GerritTriggerParameters.GERRIT_CHANGE_SUBJECT.name()
                + "="
                + expected, build);
    }

    /**
     * Tests the {@link GerritTriggerParameters.ParameterMode#BASE64} setting
     * for {@link GerritTrigger#changeSubjectParameterMode} when the build is triggered by a
     * {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated} event.
     *
     * @throws Exception if so
     */
    @Test
    public void testChangeSubjectParameterModeBase64() throws Exception {
        trigger.setChangeSubjectParameterMode(GerritTriggerParameters.ParameterMode.BASE64);
        String expected = "A new commit has arrived!";
        PatchsetCreated event = Setup.createPatchsetCreated();
        event.getChange().setSubject(expected);
        PluginImpl.getHandler_().triggerEvent(event);
        j.waitUntilNoActivity();
        FreeStyleBuild build = job.getLastBuild();
        j.assertLogContains(GerritTriggerParameters.GERRIT_CHANGE_SUBJECT.name()
                + "="
                + GerritTriggerParameters.ParameterMode.encodeBase64(expected), build);
    }


    /**
     * Test builder that prints out all env vars to the build log.
     */
    public static class ParametersBuilder extends Builder {

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            EnvVars vars = new EnvVars();
            ParametersAction parametersAction = build.getAction(ParametersAction.class);
            if (parametersAction != null) {
                parametersAction.buildEnvVars(build, vars);
            } else {
                listener.error("Build was scheduled without parameters!");
                return false;
            }

            for (Map.Entry<String, String> entry : vars.entrySet()) {
                listener.getLogger().println(entry.getKey() + "=" + entry.getValue());
            }
            return true;
        }

        /**
         * TestDescriptor.
         */
        @TestExtension
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }

            @Override
            public String getDisplayName() {
                return "Print Env vars";
            }
        }
    }
}
