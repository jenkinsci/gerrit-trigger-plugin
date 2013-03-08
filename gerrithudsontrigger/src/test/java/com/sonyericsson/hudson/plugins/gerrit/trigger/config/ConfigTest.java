/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
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

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class ConfigTest {

    //CS IGNORE MagicNumber FOR NEXT 100 LINES. REASON: Mocks tests.

    /**
     * test.
     */
    @Test
    public void testSetValues() {
        String formString = "{\"gerritVerifiedCmdBuildFailed\":\"gerrit approve <CHANGE>,<PATCHSET> "
                + "--message 'Failed misserably <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>\","
                + "\"gerritVerifiedCmdBuildStarted\":\"gerrit approve <CHANGE>,<PATCHSET> "
                + "--message 'Started yay!! <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>\","
                + "\"gerritVerifiedCmdBuildSuccessful\":\"gerrit approve <CHANGE>,<PATCHSET>"
                + " --message 'Successful wonderful <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>\","
                + "\"gerritVerifiedCmdBuildUnstable\":\"gerrit approve <CHANGE>,<PATCHSET> "
                + "--message 'Unstable and you are to <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>\","
                + "\"gerritVerifiedCmdBuildNotBuilt\":\"gerrit approve <CHANGE>,<PATCHSET> "
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
                + "\"numberOfSendingWorkerThreads\":\"4\","
                + "\"numberOfReceivingWorkerThreads\":\"6\"}";
        JSONObject form = (JSONObject)JSONSerializer.toJSON(formString);
        Config config = new Config(form);
        assertEquals("gerrit approve <CHANGE>,<PATCHSET> "
                + "--message 'Failed misserably <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>",
                     config.getGerritCmdBuildFailed());
        assertEquals("gerrit approve <CHANGE>,<PATCHSET> "
                + "--message 'Started yay!! <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>",
                     config.getGerritCmdBuildStarted());
        assertEquals("gerrit approve <CHANGE>,<PATCHSET>"
                + " --message 'Successful wonderful <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>",
                     config.getGerritCmdBuildSuccessful());
        assertEquals("gerrit approve <CHANGE>,<PATCHSET> "
                + "--message 'Unstable and you are to <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>",
                     config.getGerritCmdBuildUnstable());
        assertEquals("gerrit approve <CHANGE>,<PATCHSET> "
                + "--message 'You are not built for it <BUILDURL>' --verified <VERIFIED> --code-review <CODE_REVIEW>",
                     config.getGerritCmdBuildNotBuilt());
        assertEquals(new File("/home/local/gerrit/.ssh/id_rsa").getPath(),
                     config.getGerritAuthKeyFile().getPath());
        assertEquals("passis", config.getGerritAuthKeyFilePassword());
        assertEquals(1, config.getGerritBuildFailedCodeReviewValue());
        assertEquals(-1, config.getGerritBuildFailedVerifiedValue());
        assertEquals(2, config.getGerritBuildStartedCodeReviewValue());
        assertEquals(-2, config.getGerritBuildStartedVerifiedValue());
        assertEquals(3, config.getGerritBuildSuccessfulCodeReviewValue());
        assertEquals(-3, config.getGerritBuildSuccessfulVerifiedValue());
        assertEquals(4, config.getGerritBuildUnstableCodeReviewValue());
        assertEquals(-4, config.getGerritBuildUnstableVerifiedValue());
        assertEquals(5, config.getGerritBuildNotBuiltCodeReviewValue());
        assertEquals(-5, config.getGerritBuildNotBuiltVerifiedValue());
        assertEquals("http://gerrit:8088/", config.getGerritFrontEndUrl());
        assertEquals("gerrit", config.getGerritHostName());
        assertEquals(1337, config.getGerritSshPort());
        assertEquals("", config.getGerritProxy());
        assertEquals("gerrit", config.getGerritUserName());
        assertEquals(6, config.getNumberOfReceivingWorkerThreads());
        assertEquals(4, config.getNumberOfSendingWorkerThreads());
    }
}
