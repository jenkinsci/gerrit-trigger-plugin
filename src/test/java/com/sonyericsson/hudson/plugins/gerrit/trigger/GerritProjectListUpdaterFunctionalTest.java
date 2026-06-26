/*
 *  The MIT License
 *
 *  Copyright 2015 Ericsson All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;

import java.util.concurrent.TimeUnit;

import org.apache.sshd.server.SshServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Timeout;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock.GERRIT_STREAM_EVENTS;
import static org.junit.jupiter.api.Assertions.assertEquals;

//CS IGNORE AvoidStarImport FOR NEXT 1 LINES. REASON: UnitTest.

/**
 * Functional Test for Project List Updater.
 *
 */
@WithJenkins
class GerritProjectListUpdaterFunctionalTest {

    /**
     * An instance of Jenkins Rule.
     */
    private JenkinsRule j;

    private static final int SLEEPTIME = 1;
    private static final int TIMEOUT = 500;

    /**
     * Projects update period used in this test, in minutes
     * The default value is 5 minutes.
     * This makes the test not take 5 minutes.
     */
    private static final int UPDATE_PERIOD = 1;
    private static final int SECONDS_IN_A_MINUTE = 60;

    private static final long MAXSLEEPTIME = 10;

    private final int port = 29418;

    private SshdServerMock server;
    private SshServer sshd;
    private SshdServerMock.KeyPairFiles sshKey;

    /**
     * Runs before test method.
     *
     * @param rule the jenkins rule
     *
     * @throws Exception throw if so.
     */
    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        sshKey = SshdServerMock.generateKeyPair();

        server = new SshdServerMock();
        sshd = SshdServerMock.startServer(server);
        // We need to do this so that subsequent calls will be served with another command define later
        server.returnCommandFor("gerrit ls-projects", SshdServerMock.SendOneProjectCommand.class,
                true, new Object[0], new Class<?>[0]);
        server.returnCommandFor(GERRIT_STREAM_EVENTS, SshdServerMock.CommandMock.class);
        server.returnCommandFor("gerrit review.*", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor("gerrit version", SshdServerMock.SendVersionCommand.class);
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
     * Test that Project List updates are active for Gerrit Version 2.11 upon
     * connection startup.
     * @throws Exception if occurs.
     */
    @Timeout(TIMEOUT)
    @Test
    void testProjectListUpdateActiveOnStartup() throws Exception {
        GerritServer gerritServer = new GerritServer("ABCDEF");
        Config config = (Config)gerritServer.getConfig();
        config.setGerritAuthKeyFile(sshKey.getPrivateKey());
        config.setProjectListFetchDelay(1);
        config.setProjectListRefreshInterval(1);
        config = SshdServerMock.getConfigFor(sshd, config);
        gerritServer.setConfig(config);
        PluginImpl.getInstance().addServer(gerritServer);

        gerritServer.start();
        gerritServer.startConnection();

        long startTime = System.currentTimeMillis();
        while (!gerritServer.isConnected()) {
            TimeUnit.SECONDS.sleep(SLEEPTIME);
            if (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTime) > MAXSLEEPTIME) {
                break;
            }
        }

        startTime = System.currentTimeMillis();
        while (gerritServer.getGerritProjects().isEmpty()) {
            TimeUnit.SECONDS.sleep(SLEEPTIME);
            if (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTime) > MAXSLEEPTIME) {
                break;
            }
        }

        assertEquals(1, gerritServer.getGerritProjects().size());

        //Sets the project update period to 1 minute.
        gerritServer.getProjectListUpdater().setTimerUpdatePeriod(UPDATE_PERIOD);
        server.returnCommandFor("gerrit ls-projects", SshdServerMock.SendTwoProjectsCommand.class);
        TimeUnit.SECONDS.sleep((UPDATE_PERIOD * SECONDS_IN_A_MINUTE) + SLEEPTIME);
        assertEquals(2, gerritServer.getGerritProjects().size());

    }
}
