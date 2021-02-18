/*
 *
 *  * The MIT License
 *  *
 *  * Copyright (c) Red Hat, Inc.
 *  *
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in
 *  * all copies or substantial portions of the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  * THE SOFTWARE.
 *
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.PluginConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.ReplicationConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.BuildCancellationPolicy;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritSlave;
import com.sonymobile.tools.gerrit.gerritevents.watchdog.WatchTimeExceptionData;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.yaml.YamlSource;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.tools.ant.filters.StringInputStream;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import static com.sonymobile.tools.gerrit.gerritevents.dto.rest.Notify.OWNER_REVIEWERS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// CS IGNORE MagicNumber FOR NEXT 400 LINES. REASON: Test data.
// CS IGNORE Javadoc FOR NEXT 400 LINES. REASON: Test methods.
public class JcascTest {
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("casc.yaml")
    public void load() throws Exception {
        verifyDefaultSetup(true);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ConfigurationAsCode.get().export(baos);
            String exported = baos.toString();

            assertThat(exported, containsString("- \"saturday\""));
            assertThat(exported, containsString("- from: \"23:13\""));
            assertThat(exported, containsString("to: \"05:07\""));

            assertThat(exported, not(containsString("FAILED TO EXPORT")));

            ConfigurationAsCode.get().configureWith(YamlSource.of(new StringInputStream(exported)));

            verifyDefaultSetup(false);
        }
    }

    private void verifyDefaultSetup(boolean checkPasswords) {
        PluginImpl g = PluginImpl.getInstance();
        PluginConfig pc = g.getPluginConfig();
        assertEquals(4, pc.getNumberOfReceivingWorkerThreads());
        assertEquals(2, pc.getNumberOfSendingWorkerThreads());
        assertEquals(10, pc.getReplicationCacheExpirationInMinutes());
        assertEquals(Arrays.asList("ref-updated", "comment-added"), pc.getInterestingEvents());

        List<GerritServer> servers = g.getServers();
        assertThat(servers, iterableWithSize(1));
        GerritServer s = servers.get(0);
        assertEquals("foo", s.getName());
        assertFalse(s.isNoConnectionOnStartup());

        Config c = (Config)s.getConfig();
        assertEquals("example.com", c.getGerritHostName());
        assertEquals("https://example.com/", c.getGerritFrontEndUrl());
        assertEquals(66666, c.getGerritSshPort());
        assertEquals("http://localhost:8080", c.getGerritProxy());
        assertEquals("jenkins-user", c.getGerritUserName());
        assertEquals("gerrit@example.com", c.getGerritEMail());
        if (checkPasswords) {
            assertEquals("never_use_plaintext_password", c.getGerritHttpSecretPassword().getPlainText());
        }
        assertEquals("/key/file", c.getGerritAuthKeyFile().getAbsolutePath());
        if (checkPasswords) {
            assertEquals("never_use_plaintext_password_ever", c.getGerritAuthKeyFileSecretPassword().getPlainText());
        }

        BuildCancellationPolicy bcp = c.getBuildCurrentPatchesOnly();
        assertTrue(bcp.isAbortManualPatchsets());
        assertTrue(bcp.isAbortNewPatchsets());
        assertTrue(bcp.isAbortSameTopic());

        assertEquals(4, c.getGerritBuildStartedVerifiedValue().intValue());
        assertEquals(4, c.getGerritBuildSuccessfulVerifiedValue().intValue());
        assertEquals(4, c.getGerritBuildFailedVerifiedValue().intValue());
        assertEquals(4, c.getGerritBuildUnstableVerifiedValue().intValue());
        assertEquals(4, c.getGerritBuildNotBuiltVerifiedValue().intValue());
        assertEquals(4, c.getGerritBuildAbortedVerifiedValue().intValue());

        assertEquals(4, c.getGerritBuildStartedCodeReviewValue().intValue());
        assertEquals(4, c.getGerritBuildSuccessfulCodeReviewValue().intValue());
        assertEquals(4, c.getGerritBuildFailedCodeReviewValue().intValue());
        assertEquals(4, c.getGerritBuildUnstableCodeReviewValue().intValue());
        assertEquals(4, c.getGerritBuildNotBuiltCodeReviewValue().intValue());
        assertEquals(4, c.getGerritBuildAbortedCodeReviewValue().intValue());

        assertThat(c.getGerritCmdBuildStarted(), containsString("Build Started CMD"));
        assertThat(c.getGerritCmdBuildSuccessful(), containsString("Build Successful CMD"));
        assertThat(c.getGerritCmdBuildFailed(), containsString("Build Failed CMD"));
        assertThat(c.getGerritCmdBuildUnstable(), containsString("Build Unstable CMD"));
        assertThat(c.getGerritCmdBuildNotBuilt(), containsString("No Builds Executed CMD"));
        assertThat(c.getGerritCmdBuildAborted(), containsString("Build Aborted CMD"));

        assertEquals(4, c.getBuildScheduleDelay());
        assertEquals(31, c.getDynamicConfigRefreshInterval());
        assertFalse(c.isEnableManualTrigger());
        assertTrue(c.isTriggerOnAllComments());
        assertFalse(c.isEnableProjectAutoCompletion());
        assertEquals(1, c.getProjectListFetchDelay());
        assertEquals(3636, c.getProjectListRefreshInterval());
        assertEquals(OWNER_REVIEWERS, c.getNotificationLevel());
        List<VerdictCategory> cats = c.getCategories();
        assertThat(cats, iterableWithSize(2));
        VerdictCategory c1 = cats.get(0);
        VerdictCategory c2 = cats.get(1);
        assertEquals("foo", c1.getVerdictValue());
        assertEquals("bar", c1.getVerdictDescription());
        assertEquals("baz", c2.getVerdictValue());
        assertEquals("bax", c2.getVerdictDescription());

        UsernamePasswordCredentials httpCredentials = (UsernamePasswordCredentials)c.getHttpCredentials();
        if (checkPasswords) {
            assertEquals("never_use_plaintext_password", httpCredentials.getPassword());
        }
        assertEquals("Gerrit", httpCredentials.getUserName());

        assertTrue(c.isRestCodeReview());
        assertTrue(c.isRestVerified());
        assertTrue(c.isUseRestApi());

        ReplicationConfig rc = c.getReplicationConfig();
        assertTrue(rc.isEnableReplication());
        assertTrue(rc.isEnableReplicaSelectionInJobs());
        assertEquals("foobar", rc.getDefaultReplicaId());
        List<GerritSlave> replicas = rc.getReplicas();
        assertThat(replicas, iterableWithSize(1));
        GerritSlave replica = replicas.get(0);
        assertEquals("replica01", replica.getId());
        assertEquals("replica01", replica.getName());
        assertEquals("example.com", replica.getHost());
        assertEquals(42, replica.getTimeoutInSeconds());

        assertEquals(5, c.getWatchdogTimeoutMinutes());
        WatchTimeExceptionData ed = c.getExceptionData();
        assertArrayEquals(new int[] {7, 1}, ed.getDaysOfWeek());
        List<WatchTimeExceptionData.TimeSpan> tod = ed.getTimesOfDay();
        assertThat(tod, iterableWithSize(2));
        assertEquals(23, tod.get(0).getFrom().getHour());
        assertEquals(13, tod.get(0).getFrom().getMinute());
        assertEquals(23, tod.get(0).getTo().getHour());
        assertEquals(59, tod.get(0).getTo().getMinute());
        assertEquals(0, tod.get(1).getFrom().getHour());
        assertEquals(0, tod.get(1).getFrom().getMinute());
        assertEquals(5, tod.get(1).getTo().getHour());
        assertEquals(7, tod.get(1).getTo().getMinute());
    }
}
