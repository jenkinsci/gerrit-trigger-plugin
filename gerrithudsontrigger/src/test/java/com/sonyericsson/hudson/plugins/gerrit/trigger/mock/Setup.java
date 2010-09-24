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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil;
import hudson.EnvVars;


/**
 * Utility class for standard data during testing.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public final class Setup {

    /**
     * Utility class.
     */
    private Setup() {

    }


    /**
     * Utility method.
     * @param verifiedValue verifiedValue
     * @return comment.
     */
    private static String getCommentFormVerifiedValue(int verifiedValue) {
        switch (verifiedValue) {
            case 0:
                return "Hudson started build.";
            case 1:
                return "Hudson says ok.http://buildresultURL";
            case -1:
                return "Hudson says not ok.http://buildresultURL";
            default:
                return null;
        }
    }

    /**
     * Gives you a Config mock.
     * @return IGerritHudsonTriggerConfig mock.
     */
    public static IGerritHudsonTriggerConfig createConfig() {
        return new MockGerritHudsonTriggerConfig();
    }

    /**
     * Gives you a PatchsetCreated mock.
     * @return PatchsetCreated mock.
     */
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

    /**
     * Gives you a ManualPatchsetCreated mock.
     * @return PatchsetCreated mock.
     */
    public static ManualPatchsetCreated createManualPatchsetCreated() {
        ManualPatchsetCreated event = new ManualPatchsetCreated();
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
        event.setUserName("Bobby");
        return event;
    }

    /**
     * Gives you a BuildsStartedStats mock object for the given event.
     * @param event the event.
     * @return BuildsStartedStats mock.
     */
    public static BuildsStartedStats createBuildStartedStats(PatchsetCreated event) {
        //CS IGNORE MagicNumber FOR NEXT 2 LINES. REASON: mock.
        return new BuildsStartedStats(event, 3, 1);
    }

    /**
     * EnvVars mock.
     * @return EnvVars mock
     */
    public static EnvVars createEnvVars() {
        EnvVars env = new EnvVars();
        env.put("BRANCH", "branch");
        env.put("CHANGE", "1000");
        env.put("PATCHSET", "1");
        env.put("REFSPEC", StringUtil.REFSPEC_PREFIX + "00/1000/1");
        env.put("CHANGE_URL", "http://gerrit/1000");
        return env;
    }
}
