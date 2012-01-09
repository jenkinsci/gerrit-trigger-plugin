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

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import java.io.File;
import net.sf.json.JSONObject;

/**
 * Mock class of a Config.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class MockGerritHudsonTriggerConfig implements
        IGerritHudsonTriggerConfig {

    @Override
    public String getGerritCmdBuildStarted() {
        return "CHANGE=<CHANGE>"
                + " CHANGE_ID=<CHANGE_ID>"
                + " PATCHSET=<PATCHSET>"
                + " VERIFIED=<VERIFIED>"
                + " CODEREVIEW=<CODE_REVIEW>"
                + " REFSPEC=<REFSPEC> MSG=I started a build."
                + " BUILDURL=<BUILDURL>"
                + " STARTED_STATS=<STARTED_STATS>"
                + " ENV_BRANCH=$BRANCH"
                + " ENV_CHANGE=$CHANGE"
                + " ENV_PATCHSET=$PATCHSET"
                + " ENV_REFSPEC=$REFSPEC"
                + " ENV_CHANGEURL=$CHANGE_URL";
    }

    @Override
    public String getGerritCmdBuildSuccessful() {
        return "CHANGE=<CHANGE>"
                + " CHANGE_ID=<CHANGE_ID>"
                + " PATCHSET=<PATCHSET>"
                + " VERIFIED=<VERIFIED>"
                + " CODEREVIEW=<CODE_REVIEW>"
                + " REFSPEC=<REFSPEC> MSG='Your friendly butler says OK. BS=<BUILDS_STATS>'"
                + " BUILDURL=<BUILDURL>"
                + " STARTED_STATS=<STARTED_STATS>"
                + " ENV_BRANCH=$BRANCH"
                + " ENV_CHANGE=$CHANGE"
                + " ENV_PATCHSET=$PATCHSET"
                + " ENV_REFSPEC=$REFSPEC"
                + " ENV_CHANGEURL=$CHANGE_URL";
    }

    @Override
    public String getGerritCmdBuildFailed() {
        return "CHANGE=<CHANGE> PATCHSET=<PATCHSET> VERIFIED=-1 MSG=A disappointed butler says not OK.<BUILDURL>";
    }

    @Override
    public String getGerritCmdBuildUnstable() {
        // TODO Auto-generated method stub
        return "CHANGE=<CHANGE> PATCHSET=<PATCHSET> VERIFIED=0 MSG=The build is Unstable";
    }

    @Override
    public File getGerritAuthKeyFile() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getGerritAuthKeyFilePassword() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getGerritFrontEndUrl() {
        return "http://gerrit/";
    }

    @Override
    public String getGerritHostName() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getGerritSshPort() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getGerritUserName() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getNumberOfReceivingWorkerThreads() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getNumberOfSendingWorkerThreads() {
        return 1;
    }

    @Override
    public void setValues(JSONObject form) {
        //Empty
    }

    //CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: Mock object.
    @Override
    public int getGerritBuildStartedVerifiedValue() {
        return 1;
    }

    @Override
    public int getGerritBuildStartedCodeReviewValue() {
        return 2;
    }

    @Override
    public int getGerritBuildSuccessfulVerifiedValue() {
        return 3;
    }

    @Override
    public int getGerritBuildSuccessfulCodeReviewValue() {
        return 4;
    }

    @Override
    public int getGerritBuildFailedVerifiedValue() {
        return -1;
    }

    @Override
    public int getGerritBuildFailedCodeReviewValue() {
        return -2;
    }

    @Override
    public int getGerritBuildUnstableVerifiedValue() {
        return -3;
    }

    @Override
    public int getGerritBuildUnstableCodeReviewValue() {
        return -4;
    }

    @Override
    public String getGerritFrontEndUrlFor(String number, String revision) {
        return "http://gerrit/" + number;
    }

    @Override
    public boolean isEnableManualTrigger() {
        return true;
    }

    @Override
    public Authentication getGerritAuthentication() {
        return new Authentication(getGerritAuthKeyFile(), getGerritUserName(),
                getGerritAuthKeyFilePassword());
    }

    @Override
    public int getBuildScheduleDelay() {
        return 3;
    }

    @Override
    public boolean hasDefaultValues() {
        return false;
    }

    public boolean isGerritBuildCurrentPatchesOnly() {
        return true;
    }
}
