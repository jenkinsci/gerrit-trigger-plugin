package com.sonyericsson.hudson.plugins.gerrit.trigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.TestUtils;
import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.apache.sshd.server.SshServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock.GERRIT_STREAM_EVENTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class GerritServerSshServerTest {

    // CS IGNORE MagicNumber FOR NEXT 400 LINES. REASON: Test data.

    /**
     * An instance of Jenkins Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule j = new JenkinsRule();

    private final String gerritServerOneName = "testServer1";
    private final String gerritServerTwoName = "testServer2";
    private final String projectOneName = "testProject1";
    private final String projectTwoName = "testProject2";

    private SshdServerMock serverOne;
    private SshdServerMock serverTwo;
    private SshServer sshdOne;
    private SshServer sshdTwo;
    private SshdServerMock.KeyPairFiles sshKey;
    private GerritServer gerritServerOne;
    private GerritServer gerritServerTwo;

    /**
     * Runs before test method.
     *
     * @throws Exception throw if so.
     */
    @Before
    public void setUp() throws Exception {
        sshKey = SshdServerMock.generateKeyPair();

        serverOne = new SshdServerMock();
        serverTwo = new SshdServerMock();
        sshdOne = SshdServerMock.startServer(serverOne);
        sshdTwo = SshdServerMock.startServer(serverTwo);
        serverOne.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        serverOne.returnCommandFor(GERRIT_STREAM_EVENTS, SshdServerMock.CommandMock.class);
        serverOne.returnCommandFor("gerrit review.*", SshdServerMock.EofCommandMock.class);
        serverOne.returnCommandFor("gerrit version", SshdServerMock.EofCommandMock.class);
        serverTwo.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        serverTwo.returnCommandFor(GERRIT_STREAM_EVENTS, SshdServerMock.CommandMock.class);
        serverTwo.returnCommandFor("gerrit review.*", SshdServerMock.EofCommandMock.class);
        serverTwo.returnCommandFor("gerrit version", SshdServerMock.EofCommandMock.class);
    }

    /**
     * Runs after test method.
     *
     * @throws Exception throw if so.
     */
    @After
    public void tearDown() throws Exception {
        gerritServerOne.stop();
        gerritServerTwo.stop();
        serverOne.stopServer(sshdOne);
        //sshdOne.stop(true);
        serverTwo.stopServer(sshdTwo);
        //sshdTwo.stop(true);
        sshdOne = null;
        sshdTwo = null;
    }

    /**
     * Test triggering two jobs with events from two different Gerrit servers.
     * @throws Exception Error creating job.
     */
    @Test
    public void testTriggeringFromMultipleGerritServers() throws Exception {
        gerritServerOne = new GerritServer(gerritServerOneName);
        gerritServerTwo = new GerritServer(gerritServerTwoName);
        SshdServerMock.configureFor(sshdOne, sshKey, gerritServerOne);
        SshdServerMock.configureFor(sshdTwo, sshKey, gerritServerTwo);
        PluginImpl.getInstance().addServer(gerritServerOne);
        PluginImpl.getInstance().addServer(gerritServerTwo);
        gerritServerOne.start();
        gerritServerOne.startConnection();
        gerritServerTwo.start();
        gerritServerTwo.startConnection();
        FreeStyleProject projectOne = DuplicatesUtil.createGerritTriggeredJob(j, projectOneName, gerritServerOneName);
        FreeStyleProject projectTwo = DuplicatesUtil.createGerritTriggeredJob(j, projectTwoName, gerritServerTwoName);
        serverOne.waitForCommand(GERRIT_STREAM_EVENTS, 20000);
        serverTwo.waitForCommand(GERRIT_STREAM_EVENTS, 20000);
        gerritServerOne.triggerEvent(Setup.createPatchsetCreated(gerritServerOneName));
        gerritServerTwo.triggerEvent(Setup.createPatchsetCreated(gerritServerTwoName));
        TestUtils.waitForBuilds(projectOne, 1, 20000);
        TestUtils.waitForBuilds(projectTwo, 1, 20000);

        FreeStyleBuild buildOne = projectOne.getLastCompletedBuild();
        assertSame(Result.SUCCESS, buildOne.getResult());
        assertEquals(1, projectOne.getLastCompletedBuild().getNumber());
        assertSame(gerritServerOneName, buildOne.getCause(GerritCause.class).getEvent().getProvider().getName());

        FreeStyleBuild buildTwo = projectTwo.getLastCompletedBuild();
        assertSame(Result.SUCCESS, buildTwo.getResult());
        assertEquals(1, projectTwo.getLastCompletedBuild().getNumber());
        assertSame(gerritServerTwoName, buildTwo.getCause(GerritCause.class).getEvent().getProvider().getName());

        j.waitUntilNoActivity();
    }
}
