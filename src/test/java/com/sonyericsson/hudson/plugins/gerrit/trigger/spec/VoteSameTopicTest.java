package com.sonyericsson.hudson.plugins.gerrit.trigger.spec;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TopicAssociation;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Topic;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.FilePath;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginCommentAddedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import org.apache.sshd.server.SshServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test JENKINS-65349.
 *
 * @author Jack Mo &lt;jack.mo@dji.com&gt;.
 */
public class VoteSameTopicTest {
    /**
     * An instance of Jenkins Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public JenkinsRule j = new JenkinsRule();
    private List<FreeStyleProject> jobs = new ArrayList<>();

    private SshdServerMock server;
    private SshServer sshd;
    private GerritServer gerritServer;
    private final String[] projects = {"project", "project2"};

    /**
     * Create a new job.
     * @param pattern Gerrit project pattern.
     * @return A FreeStyleProject.
     * @throws IOException if so.
     */
    private FreeStyleProject createJob(String pattern) throws IOException {
        FreeStyleProject job = j.createFreeStyleProject();
        job.getBuildersList().add(new ParametersBuilder());

        GerritTrigger trigger = Setup.createDefaultTrigger(job);
        trigger.setTopicAssociation(new TopicAssociation());
        trigger.getTriggerOnEvents().add(new PluginCommentAddedEvent("Code-Review", "1"));
        trigger.setGerritProjects(Collections.singletonList(new GerritProject(CompareType.ANT, pattern,
                Collections.singletonList(new Branch(CompareType.ANT, "**")),
                Collections.<Topic>emptyList(), Collections.<FilePath>emptyList(),
                Collections.<FilePath>emptyList(), false)));
        trigger.setEscapeQuotes(false);
        trigger.setSilentMode(false);

        return job;
    }

    /**
     * Shared setup for all tests.
     *
     * @throws Exception if so.
     */
    @Before
    public void setup() throws Exception {
        final SshdServerMock.KeyPairFiles sshKey = SshdServerMock.generateKeyPair();

        server = new SshdServerMock();
        sshd = SshdServerMock.startServer(server);
        server.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor("gerrit version", SshdServerMock.SendVersionCommand.class);
        server.returnCommandFor("gerrit stream-events", SshdServerMock.CommandMock.class);
        server.returnCommandFor("gerrit query.*", SshdServerMock.SendQueryTopic.class);
        server.returnCommandFor("gerrit review.*", SshdServerMock.EofCommandMock.class);

        gerritServer = new GerritServer(PluginImpl.DEFAULT_SERVER_NAME);
        SshdServerMock.configureFor(sshd, gerritServer);
        PluginImpl.getInstance().addServer(gerritServer);
        Config config = (Config)gerritServer.getConfig();
        config.setGerritAuthKeyFile(sshKey.getPrivateKey());
        config.setEnableProjectAutoCompletion(false);
        config.setVoteSameTopic(true);
        gerritServer.setConfig(config);
        gerritServer.start();
        gerritServer.startConnection();

        for (String project : projects) {
            jobs.add(createJob(project));
        }
    }

    /**
     * Runs after test method.
     *
     * @throws Exception throw if so.
     */
    @After
    public void tearDown() throws Exception {
        sshd.stop(true);
        sshd = null;
    }

    /**
     * Trigger a
     * {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.createCommentAdded} event and wait until no activity.
     * @param project The gerrit project name.
     * @throws Exception if so
     */
    private void triggerAndWait(String project) throws Exception {
        System.out.println("trigger " + project);
        String expected = "Triggering comment";
        CommentAdded event = Setup.createCommentAdded();
        event.setComment(expected);
        event.getChange().setProject(project);
        event.getChange().setTopic("topic");
        event.getChange().setNumber("100");
        event.getChange().setBranch("branch");
        event.getChange().setId("I2343434344");
        event.getChange().setUrl("http://localhost:8080");
        gerritServer.triggerEvent(event);
        j.waitUntilNoActivity();
    }

    /**
     * Check command received by the server.
     *
     * @param pattern the command pattern
     */
    private void checkCommand(String pattern) {
        //CS IGNORE MagicNumber FOR NEXT 1 LINES. REASON: Testdata.
        server.waitForCommand(pattern, 3000);
    }

    /**
     * Tests the {@link Config#setVoteSameTopic} setting
     * for {@link GerritTrigger#commitMessageParameterMode} when the build is triggered by a
     * {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.createCommentAdded} event.
     *
     * @throws Exception if so
     */
    @Test
    public void testVoteSameTopic() throws Exception {
        //CS IGNORE MagicNumber FOR NEXT 4 LINES. REASON: Testdata.
        server.waitForCommand("gerrit stream-events", 2000);
        triggerAndWait(projects[0]);
        checkCommand("gerrit review --project " + projects[0] + " 100,1 --message 'Build Started[\\s\\S.]*");
        checkCommand("gerrit review --project " + projects[1] + " 101,1 --message 'Build Started[\\s\\S.]*");
        checkCommand("gerrit review --project " + projects[0] + " 100,1 --message 'Build Successful[\\s\\S.]*");
        checkCommand("gerrit review --project " + projects[1] + " 101,1 --message 'Build Successful[\\s\\S.]*");
    }

    /**
     * Test builder that prints out all env vars to the build log.
     */
    public static class ParametersBuilder extends Builder {

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
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
                return "Test";
            }
        }
    }
}
