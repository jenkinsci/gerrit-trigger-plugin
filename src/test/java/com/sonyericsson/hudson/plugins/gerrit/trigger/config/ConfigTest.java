/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2013 Sony Mobile Communications AB. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.config;

import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.Notify;
import com.sonymobile.tools.gerrit.gerritevents.GerritDefaultValues;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.util.Secret;

import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class ConfigTest {

    /**
     * Jenkins rule instance.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 3 LINES. REASON: Mocks tests.
    @Rule
    public JenkinsRule j = new JenkinsRule();

    //CS IGNORE MagicNumber FOR NEXT 100 LINES. REASON: Mocks tests.

    /**
     * test.
     */
    @Test
    public void testSetValues() {
        String formString = "{\"gerritVerifiedCmdBuildFailed\":\"gerrit review <CHANGE>,<PATCHSET> "
                + "--message 'Failed misserably <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>\","
                + "\"gerritVerifiedCmdBuildStarted\":\"gerrit review <CHANGE>,<PATCHSET> "
                + "--message 'Started yay!! <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>\","
                + "\"gerritVerifiedCmdBuildSuccessful\":\"gerrit review <CHANGE>,<PATCHSET>"
                + " --message 'Successful wonderful <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>\","
                + "\"gerritVerifiedCmdBuildUnstable\":\"gerrit review <CHANGE>,<PATCHSET> "
                + "--message 'Unstable and you are to <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>\","
                + "\"gerritVerifiedCmdBuildNotBuilt\":\"gerrit review <CHANGE>,<PATCHSET> "
                + "--message 'You are not built for it <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>\","
                + "\"gerritAuthKeyFile\":\"/home/local/gerrit/.ssh/id_rsa\","
                + "\"gerritAuthKeyFilePassword\":\"passis\","
                + "\"gerritBuildFailedCodeReviewValue\":\"1\","
                + "\"gerritBuildFailedVerifiedValue\":\"-1\","
                + "\"gerritBuildStartedCodeReviewValue\":\"2\","
                + "\"gerritBuildStartedVerifiedValue\":\"-2\","
                + "\"gerritBuildSuccessfulCodeReviewValue\":\"3\","
                + "\"gerritBuildSuccessfulVerifiedValue\":\"-3\","
                + "\"gerritBuildUnstableCodeReviewValue\":\"4\","
                + "\"gerritBuildUnstableVerifiedValue\":\"-4\","
                + "\"gerritBuildNotBuiltCodeReviewValue\":\"5\","
                + "\"gerritBuildNotBuiltVerifiedValue\":\"-5\","
                + "\"gerritFrontEndUrl\":\"http://gerrit:8088\","
                + "\"gerritHostName\":\"gerrit\","
                + "\"gerritSshPort\":\"1337\","
                + "\"gerritProxy\":\"\","
                + "\"gerritUserName\":\"gerrit\","
                + "\"useRestApi\":{\"gerritHttpUserName\":\"httpgerrit\",\"gerritHttpPassword\":\"httppass\"},"
                + "\"numberOfSendingWorkerThreads\":\"4\","
                + "\"numberOfReceivingWorkerThreads\":\"6\","
                + "\"notificationLevel\":\"OWNER\"}";
        JSONObject form = (JSONObject)JSONSerializer.toJSON(formString);
        Config config = new Config(form);
        assertEquals("gerrit review <CHANGE>,<PATCHSET> "
                + "--message 'Failed misserably <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>",
                     config.getGerritCmdBuildFailed());
        assertEquals("gerrit review <CHANGE>,<PATCHSET> "
                + "--message 'Started yay!! <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>",
                     config.getGerritCmdBuildStarted());
        assertEquals("gerrit review <CHANGE>,<PATCHSET>"
                + " --message 'Successful wonderful <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>",
                     config.getGerritCmdBuildSuccessful());
        assertEquals("gerrit review <CHANGE>,<PATCHSET> "
                + "--message 'Unstable and you are to <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>",
                     config.getGerritCmdBuildUnstable());
        assertEquals("gerrit review <CHANGE>,<PATCHSET> "
                + "--message 'You are not built for it <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>",
                     config.getGerritCmdBuildNotBuilt());
        assertEquals(new File("/home/local/gerrit/.ssh/id_rsa").getPath(),
                     config.getGerritAuthKeyFile().getPath());
        assertEquals("passis", config.getGerritAuthKeyFilePassword());
        assertEquals(Integer.valueOf(1), config.getGerritBuildFailedCodeReviewValue());
        assertEquals(Integer.valueOf(-1), config.getGerritBuildFailedVerifiedValue());
        assertEquals(Integer.valueOf(2), config.getGerritBuildStartedCodeReviewValue());
        assertEquals(Integer.valueOf(-2), config.getGerritBuildStartedVerifiedValue());
        assertEquals(Integer.valueOf(3), config.getGerritBuildSuccessfulCodeReviewValue());
        assertEquals(Integer.valueOf(-3), config.getGerritBuildSuccessfulVerifiedValue());
        assertEquals(Integer.valueOf(4), config.getGerritBuildUnstableCodeReviewValue());
        assertEquals(Integer.valueOf(-4), config.getGerritBuildUnstableVerifiedValue());
        assertEquals(Integer.valueOf(5), config.getGerritBuildNotBuiltCodeReviewValue());
        assertEquals(Integer.valueOf(-5), config.getGerritBuildNotBuiltVerifiedValue());
        assertEquals("http://gerrit:8088/", config.getGerritFrontEndUrl());
        assertEquals("gerrit", config.getGerritHostName());
        assertEquals(1337, config.getGerritSshPort());
        assertEquals("", config.getGerritProxy());
        assertEquals("gerrit", config.getGerritUserName());
        assertEquals(true, config.isUseRestApi());
        assertEquals("httpgerrit", config.getGerritHttpUserName());
        assertEquals("httppass", config.getGerritHttpPassword());
        assertEquals(6, config.getNumberOfReceivingWorkerThreads());
        assertEquals(4, config.getNumberOfSendingWorkerThreads());
        assertEquals(Notify.OWNER, config.getNotificationLevel());
        assertEquals(GerritDefaultValues.DEFAULT_BUILD_SCHEDULE_DELAY, config.getBuildScheduleDelay());
    }

    //CS IGNORE MagicNumber FOR NEXT 100 LINES. REASON: Mocks tests.

    /**
     * Test ProjectListRefreshInterval zero value after upgrade from gerrit-trigger version 2.13.0 to 2.14.0.
     */
    @Test
    public void testProjectListRefreshIntervalZeroValue() {
        String formString = "{\"projectListRefreshInterval\":\"0\"}";
        JSONObject form = (JSONObject)JSONSerializer.toJSON(formString);
        Config config = new Config(form);
        assertEquals(Config.DEFAULT_PROJECT_LIST_REFRESH_INTERVAL, config.getProjectListRefreshInterval());
    }

    //CS IGNORE MagicNumber FOR NEXT 100 LINES. REASON: Mocks tests.

    /**
     * Test creation of a config object from an existing one.
     */
    @Test
    public void testCopyConfig() {
        String formString = "{\"gerritVerifiedCmdBuildFailed\":\"gerrit review <CHANGE>,<PATCHSET> "
                + "--message 'Failed misserably <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>\","
                + "\"gerritVerifiedCmdBuildStarted\":\"gerrit review <CHANGE>,<PATCHSET> "
                + "--message 'Started yay!! <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>\","
                + "\"gerritVerifiedCmdBuildSuccessful\":\"gerrit review <CHANGE>,<PATCHSET>"
                + " --message 'Successful wonderful <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>\","
                + "\"gerritVerifiedCmdBuildUnstable\":\"gerrit review <CHANGE>,<PATCHSET> "
                + "--message 'Unstable and you are to <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>\","
                + "\"gerritVerifiedCmdBuildNotBuilt\":\"gerrit review <CHANGE>,<PATCHSET> "
                + "--message 'You are not built for it <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>\","
                + "\"gerritAuthKeyFile\":\"/home/local/gerrit/.ssh/id_rsa\","
                + "\"gerritAuthKeyFilePassword\":\"passis\","
                + "\"gerritBuildFailedCodeReviewValue\":\"1\","
                + "\"gerritBuildFailedVerifiedValue\":\"-1\","
                + "\"gerritBuildStartedCodeReviewValue\":\"2\","
                + "\"gerritBuildStartedVerifiedValue\":\"-2\","
                + "\"gerritBuildSuccessfulCodeReviewValue\":\"3\","
                + "\"gerritBuildSuccessfulVerifiedValue\":\"-3\","
                + "\"gerritBuildUnstableCodeReviewValue\":\"4\","
                + "\"gerritBuildUnstableVerifiedValue\":\"-4\","
                + "\"gerritBuildNotBuiltCodeReviewValue\":\"5\","
                + "\"gerritBuildNotBuiltVerifiedValue\":\"-5\","
                + "\"gerritFrontEndUrl\":\"http://gerrit:8088\","
                + "\"gerritHostName\":\"gerrit\","
                + "\"gerritSshPort\":\"1337\","
                + "\"gerritProxy\":\"\","
                + "\"gerritUserName\":\"gerrit\","
                + "\"useRestApi\":{\"gerritHttpUserName\":\"httpgerrit\",\"gerritHttpPassword\":\"httppass\"},"
                + "\"numberOfSendingWorkerThreads\":\"4\","
                + "\"buildScheduleDelay\":\"0\","
                + "\"numberOfReceivingWorkerThreads\":\"6\"}";
        JSONObject form = (JSONObject)JSONSerializer.toJSON(formString);
        Config initialConfig = new Config(form);
        Config config = new Config(initialConfig);
        assertEquals("gerrit review <CHANGE>,<PATCHSET> "
                + "--message 'Failed misserably <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>",
                config.getGerritCmdBuildFailed());
        assertEquals("gerrit review <CHANGE>,<PATCHSET> "
                + "--message 'Started yay!! <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>",
                config.getGerritCmdBuildStarted());
        assertEquals("gerrit review <CHANGE>,<PATCHSET>"
                + " --message 'Successful wonderful <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>",
                config.getGerritCmdBuildSuccessful());
        assertEquals("gerrit review <CHANGE>,<PATCHSET> "
                + "--message 'Unstable and you are to <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>",
                config.getGerritCmdBuildUnstable());
        assertEquals("gerrit review <CHANGE>,<PATCHSET> "
                + "--message 'You are not built for it <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>",
                config.getGerritCmdBuildNotBuilt());
        assertEquals(new File("/home/local/gerrit/.ssh/id_rsa").getPath(),
                config.getGerritAuthKeyFile().getPath());
        assertEquals("passis", config.getGerritAuthKeyFilePassword());
        assertEquals(Integer.valueOf(1), config.getGerritBuildFailedCodeReviewValue());
        assertEquals(Integer.valueOf(-1), config.getGerritBuildFailedVerifiedValue());
        assertEquals(Integer.valueOf(2), config.getGerritBuildStartedCodeReviewValue());
        assertEquals(Integer.valueOf(-2), config.getGerritBuildStartedVerifiedValue());
        assertEquals(Integer.valueOf(3), config.getGerritBuildSuccessfulCodeReviewValue());
        assertEquals(Integer.valueOf(-3), config.getGerritBuildSuccessfulVerifiedValue());
        assertEquals(Integer.valueOf(4), config.getGerritBuildUnstableCodeReviewValue());
        assertEquals(Integer.valueOf(-4), config.getGerritBuildUnstableVerifiedValue());
        assertEquals(Integer.valueOf(5), config.getGerritBuildNotBuiltCodeReviewValue());
        assertEquals(Integer.valueOf(-5), config.getGerritBuildNotBuiltVerifiedValue());
        assertEquals("http://gerrit:8088/", config.getGerritFrontEndUrl());
        assertEquals("gerrit", config.getGerritHostName());
        assertEquals(1337, config.getGerritSshPort());
        assertEquals("", config.getGerritProxy());
        assertEquals("gerrit", config.getGerritUserName());
        assertEquals(true, config.isUseRestApi());
        assertEquals("httpgerrit", config.getGerritHttpUserName());
        assertEquals("httppass", config.getGerritHttpPassword());
        assertEquals(6, config.getNumberOfReceivingWorkerThreads());
        assertEquals(4, config.getNumberOfSendingWorkerThreads());
        assertEquals(0, config.getBuildScheduleDelay());
    }

    /**
     * Tests {@link Config#getGerritFrontEndUrlFor(String, String)}.
     */
    @Test
    public void testGetGerritFrontEndUrlForStringString() {
        Config config = new Config();
        config.setGerritFrontEndURL("http://gerrit/");
        assertEquals("http://gerrit/1000", config.getGerritFrontEndUrlFor("1000", "1"));
    }

    //CS IGNORE LineLength FOR NEXT 17 LINES. REASON: JavaDoc

    /**
     * Tests {@link Config#getGerritFrontEndUrlFor(com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent)}.
     * With a standard PatchsetCreated event.
     */
    @Test
    public void testGetGerritFrontEndUrlForChangeBasedEvent() {
        Config config = new Config();
        config.setGerritFrontEndURL("http://gerrit/");
        PatchsetCreated event = Setup.createPatchsetCreated();
        assertEquals(event.getChange().getUrl(), config.getGerritFrontEndUrlFor(event));
    }

    /**
     * Tests {@link Config#getGerritFrontEndUrlFor(com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent)}.
     * With a standard PatchsetCreated event but missing url.
     */
    @Test
    public void testGetGerritFrontEndUrlForChangeBasedEventProvider() {
        Config config = new Config();
        config.setGerritFrontEndURL("http://gerrit/");
        PatchsetCreated event = Setup.createPatchsetCreated();
        event.getChange().setUrl(null);
        assertEquals("http://gerrit/1000", config.getGerritFrontEndUrlFor(event));
    }

    /**
     * Tests {@link Config#getGerritAuthKeyFilePassword()}.
     * With a encrypted string as password.
     */
    @Test
    public void testGetGerritAuthKeyFilePassword() {
        String formString;
        JSONObject form;
        Config config;

        // plain text
        formString = "{\"gerritAuthKeyFilePassword\":\"plainpass\"}";
        form = (JSONObject)JSONSerializer.toJSON(formString);
        config = new Config(form);
        assertEquals("plainpass", config.getGerritAuthKeyFilePassword());

        // encrypted string
        Secret pass = Secret.fromString("encryptpass");
        formString = "{\"gerritAuthKeyFilePassword\":\"" + pass.getEncryptedValue() + "\"}";
        form = (JSONObject)JSONSerializer.toJSON(formString);
        config = new Config(form);
        assertEquals("encryptpass", config.getGerritAuthKeyFilePassword());

        // empty
        formString = "{\"gerritAuthKeyFilePassword\":\"\"}";
        form = (JSONObject)JSONSerializer.toJSON(formString);
        config = new Config(form);
        assertEquals("Empty check", "", config.getGerritAuthKeyFilePassword());

        // null
        config = new Config();
        assertEquals("Null check", "", config.getGerritAuthKeyFilePassword());
    }

    /**
     * Tests {@link Config#getGerritAuthKeyFileSecretPassword()}.
     */
    @Test
    public void testGetGerritAuthkeyFileSecretPassword() {
        Config config = new Config();
        config.setGerritAuthKeyFilePassword("secretpass");
        assertEquals(Secret.fromString("secretpass"), config.getGerritAuthKeyFileSecretPassword());
    }

    /**
     * Tests {@link Config#getGerritHttpSecretPassword()}.
     */
    @Test
    public void testGetGerritHttpSecretPassword() {
        Config config = new Config();
        config.setGerritHttpPassword("secretpass");
        assertEquals(Secret.fromString("secretpass"), config.getGerritHttpSecretPassword());
    }
}
