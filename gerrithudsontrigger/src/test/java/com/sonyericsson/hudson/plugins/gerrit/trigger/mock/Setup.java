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
package com.sonyericsson.hudson.plugins.gerrit.trigger.mock;

import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.EnvVars;
import org.easymock.EasyMock;

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.GerritSSHCmdRunner;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.PatchSet;


public class Setup {


    public static GerritSSHCmdRunner GerritCmdRunner(
            IGerritHudsonTriggerConfig config, int verifiedValue, int change,
            int patchset) {
        return GerritCmdRunner(config, verifiedValue,
                getCommentFormVerifiedValue(verifiedValue), change, patchset);
    }

    private static String getCommentFormVerifiedValue(int verifiedValue) {
        switch (verifiedValue) {
            case 0:
                return "Hudson started build.";
            case 1:
                return "Hudson says ok.http://buildresultURL";
            case -1:
                return "Hudson says not ok.http://buildresultURL";
        }
        return null;
    }

    public static GerritSSHCmdRunner GerritCmdRunner(
            IGerritHudsonTriggerConfig config, int verifiedValue) {
        return GerritCmdRunner(config, verifiedValue,
                getCommentFormVerifiedValue(verifiedValue));
    }

    public static GerritSSHCmdRunner GerritCmdRunner(
            IGerritHudsonTriggerConfig config, int verifiedValue, String comment) {
        return GerritCmdRunner(config, verifiedValue, comment, 2, 1);
    }

    public static GerritSSHCmdRunner GerritCmdRunner(
            IGerritHudsonTriggerConfig config, int verifiedValue,
            String comment, int change, int patchset) {
        GerritSSHCmdRunner verifiedHandler = EasyMock.createMock(GerritSSHCmdRunner.class);
        verifiedHandler.runCmd("CHANGE=" + change + " PATCHSET=" + patchset
                + " VERIFIED=" + verifiedValue + " MSG=" + comment);
        EasyMock.replay(verifiedHandler);
        return verifiedHandler;
    }

    public static MockServer HudsonServerMock() {
        return HudsonServerMock(2, 1, "refs/changes/02/2/1");
    }

    public static MockServer HudsonServerMock(int change, int patchset,
            String refspec) {
        final MockServer mockHudsonServer = MockServer.create().expectHTTPGet(
                "/hudson/job/PROJECTNAME/build?PROJECT=new/project&CHANGE="
                + change + "&BRANCH=master&PATCHSET=" + patchset
                + "&REFSPEC=" + refspec).sendHTTP("");
        return mockHudsonServer;
    }

    public static IGerritHudsonTriggerConfig createConfig() {
        return new MockGerritHudsonTriggerConfig();
    }

    public static GerritSSHCmdRunner GerritCmdRunner() {
        GerritSSHCmdRunner verifiedHandler = EasyMock.createMock(GerritSSHCmdRunner.class);
        EasyMock.replay(verifiedHandler);
        return verifiedHandler;
    }

    public static void VerifyWithTimeout(Object obj) {
        for (int j = 0;j < 10;j++) {
            try {
                EasyMock.verify(obj);
                return;
            } catch (Throwable e) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e1) {
                }
            }
        }
        EasyMock.verify(obj);

    }

    public static PatchsetCreated createPatchsetCreated() {
        PatchsetCreated event = new PatchsetCreated();
        Change change = new Change();
        change.setBranch("branch");
        change.setId("Iddaaddaa123456789");
        change.setNumber("1000");
        Account account = new Account();
        account.setEmail("email@domain.com");
        account.setName("Name");
        change.setOwner(account);
        change.setProject("project");
        change.setSubject("subject");
        change.setUrl("http://gerrit/1000");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        patch.setRevision("9999");
        event.setPatchset(patch);
        return event;
    }

    public static BuildsStartedStats createBuildStartedStats(PatchsetCreated event) {
        return new BuildsStartedStats(event, 3, 1);
    }

    public static EnvVars createEnvVars() {
        EnvVars env = new EnvVars();
        env.put("BRANCH", "branch");
        env.put("CHANGE", "1000");
        env.put("PATCHSET", "1");
        env.put("REFSPEC", StringUtil.REFSPEC_PREFIX + "00/1000/1");
        env.put("CHANGE_URL", "http://gerrit/1000");
        return env;
    }

    public static class EmailMessage {

        String to;
        String subject;
        String body;
        String cc;

        public EmailMessage(String to, String subject, String body, String cc) {
            this.to = to;
            this.subject = subject;
            this.body = body;
            this.cc = cc;
        }
    }
}
