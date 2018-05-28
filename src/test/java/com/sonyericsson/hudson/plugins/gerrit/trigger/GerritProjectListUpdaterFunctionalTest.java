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

import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.time.StopWatch;
import org.apache.sshd.SshServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock.GERRIT_STREAM_EVENTS;
//CS IGNORE AvoidStarImport FOR NEXT 1 LINES. REASON: UnitTest.

/**
 * Functional Test for Project List Updater.
 *
 */
public class GerritProjectListUpdaterFunctionalTest {

    /**
     * An instance of Jenkins Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule j = new JenkinsRule();

    private static final int SLEEPTIME = 1;
    private static final int LONGSLEEPTIME = 10;

    private static final long MAXSLEEPTIME = 10;

    private final int port = 29418;

    private SshdServerMock server;
    private SshServer sshd;
    private SshdServerMock.KeyPairFiles sshKey;

    /**
     * Runs before test method.
     *
     * @throws Exception throw if so.
     */
    @Before
    public void setUp() throws Exception {
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
    @After
    public void tearDown() throws Exception {
        sshd.stop(true);
        sshd = null;
    }

    /**
     * Test that Project List updates are active for Gerrit Version 2.11 upon
     * connection startup.
     * @throws Exception if occurs.
     */
    @Test
    public void testProjectListUpdateActiveOnStartup() throws Exception {
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

        StopWatch watch = new StopWatch();
        watch.start();
        while (!gerritServer.isConnected()) {
            TimeUnit.SECONDS.sleep(SLEEPTIME);
            if (TimeUnit.MILLISECONDS.toSeconds(watch.getTime()) > MAXSLEEPTIME) {
                break;
            }
        }
        watch.stop();

        watch.reset();
        watch.start();
        while (gerritServer.getGerritProjects().size() == 0) {
            TimeUnit.SECONDS.sleep(SLEEPTIME);
            if (TimeUnit.MILLISECONDS.toSeconds(watch.getTime()) > MAXSLEEPTIME) {
                break;
            }
        }
        watch.stop();
        assertEquals(1, gerritServer.getGerritProjects().size());

        server.returnCommandFor("gerrit ls-projects", SshdServerMock.SendTwoProjectsCommand.class);

        TimeUnit.SECONDS.sleep(LONGSLEEPTIME);
        assertEquals(2, gerritServer.getGerritProjects().size());

    }
}
