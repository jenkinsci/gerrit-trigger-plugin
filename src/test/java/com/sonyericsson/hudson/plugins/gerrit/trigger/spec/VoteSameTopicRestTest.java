package com.sonyericsson.hudson.plugins.gerrit.trigger.spec;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginCommentAddedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;

import hudson.model.FreeStyleProject;

import org.apache.sshd.server.SshServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.Collections;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests that {@link Config#setVoteSameTopic} also works when notifications are sent with the REST API.
 */
@WithJenkins
class VoteSameTopicRestTest {

    private static final String PROJECT = "project";
    private static final String CHANGE_URL = "/a/changes/project~branch~I2343434344/revisions/.*/review";
    private static final String TOPIC_CHANGE_URL = "/a/changes/project2~branch~I2343434345/revisions/.*/review";
    private static final int HTTPOK = 200;
    private static final long TIMEOUT = 30000;
    private static final long SLEEPTIME = 100;

    /**
     * An instance of WireMock Rule.
     */
    @RegisterExtension
    private static final WireMockExtension WIRE_MOCK = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private JenkinsRule j;
    private SshdServerMock server;
    private SshServer sshd;
    private GerritServer gerritServer;

    /**
     * Shared setup for all tests.
     *
     * @param rule the jenkins rule
     *
     * @throws Exception if so.
     */
    @BeforeEach
    void setup(JenkinsRule rule) throws Exception {
        j = rule;
        WIRE_MOCK.stubFor(post(urlMatching("/a/changes/.*/review")).willReturn(aResponse().withStatus(HTTPOK)));

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
        config.setGerritFrontEndURL("http://localhost:" + WIRE_MOCK.getPort());
        config.setUseRestApi(true);
        config.setGerritHttpUserName("user");
        config.setGerritHttpPassword("passwd");
        config.setRestVerified(true);
        config.setVoteSameTopic(true);
        gerritServer.setConfig(config);
        gerritServer.start();
        gerritServer.startConnection();
    }

    /**
     * Runs after test method.
     *
     * @throws Exception throw if so.
     */
    @AfterEach
    void tearDown() throws Exception {
        server.stopServer(sshd);
        sshd = null;
    }

    /**
     * A build triggered by a change in a topic should send its started and completed reviews
     * to the other change in the topic as well. Only a job for the triggering project exists,
     * so the reviews on the other change can only come from same topic voting.
     *
     * @throws Exception if so
     */
    @Test
    void testVoteSameTopicWithRestApi() throws Exception {
        FreeStyleProject job = j.createFreeStyleProject();
        GerritTrigger trigger = Setup.createDefaultTrigger(job);
        trigger.getTriggerOnEvents().add(new PluginCommentAddedEvent("Code-Review", "1"));
        trigger.setGerritProjects(Collections.singletonList(new GerritProject(CompareType.PLAIN, PROJECT,
                Collections.singletonList(new Branch(CompareType.ANT, "**")),
                Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), false)));
        trigger.setSilentMode(false);

        //CS IGNORE MagicNumber FOR NEXT 1 LINES. REASON: Testdata.
        server.waitForCommand("gerrit stream-events", 2000);
        CommentAdded event = Setup.createCommentAdded();
        event.setComment("Triggering comment");
        event.getChange().setProject(PROJECT);
        event.getChange().setTopic("topic");
        event.getChange().setNumber("100");
        event.getChange().setBranch("branch");
        event.getChange().setId("I2343434344");
        event.getChange().setUrl("http://localhost:8080");
        gerritServer.triggerEvent(event);
        j.waitUntilNoActivity();

        waitForReview(CHANGE_URL, "Build Started");
        waitForReview(TOPIC_CHANGE_URL, "Build Started");
        waitForReview(CHANGE_URL, "Build Successful");
        waitForReview(TOPIC_CHANGE_URL, "Build Successful");
    }

    /**
     * Waits until a review with the given message has been posted to the given url.
     *
     * @param url     the review url pattern.
     * @param message the expected start of the review message.
     * @throws InterruptedException if interrupted.
     */
    private void waitForReview(String url, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT;
        while (System.currentTimeMillis() < deadline) {
            List<LoggedRequest> requests = WIRE_MOCK.findAll(postRequestedFor(urlMatching(url)));
            for (LoggedRequest request : requests) {
                if (request.getBodyAsString().contains("\"message\":\"" + message)) {
                    return;
                }
            }
            Thread.sleep(SLEEPTIME);
        }
        fail("No '" + message + "' review posted to " + url);
    }
}
