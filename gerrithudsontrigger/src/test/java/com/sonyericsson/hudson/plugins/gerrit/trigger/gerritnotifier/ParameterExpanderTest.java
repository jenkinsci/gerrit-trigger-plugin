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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier;

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.TaskListener;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

//CS IGNORE MagicNumber FOR NEXT 250 LINES. REASON: Mocks tests.

/**
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ Hudson.class })
public class ParameterExpanderTest {

    /**
     * test.
     * @throws Exception Exception
     */
    @Test
    public void testGetBuildStartedCommand() throws Exception {
        System.out.println("getBuildStartedCommand");


        TaskListener taskListener = mock(TaskListener.class);

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildStartedVerifiedValue()).thenReturn(null);
        when(trigger.getGerritBuildStartedCodeReviewValue()).thenReturn(32);
        AbstractProject project = mock(AbstractProject.class);
        when(project.getTrigger(GerritTrigger.class)).thenReturn(trigger);

        AbstractBuild r = mock(AbstractBuild.class);
        when(r.getUrl()).thenReturn("test/");
        when(r.getProject()).thenReturn(project);
        EnvVars env = Setup.createEnvVars();
        when(r.getEnvironment(taskListener)).thenReturn(env);

        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildsStartedStats stats = Setup.createBuildStartedStats(event);
        IGerritHudsonTriggerConfig config = Setup.createConfig();

        Hudson hudson = PowerMockito.mock(Hudson.class);
        when(hudson.getRootUrl()).thenReturn("http://localhost/");

        ParameterExpander instance = new ParameterExpander(config, hudson);

        final String expectedRefSpec = StringUtil.makeRefSpec(event);

        String result = instance.getBuildStartedCommand(r, taskListener, event, stats);
        System.out.println("result: " + result);

        assertTrue("Missing CHANGE_ID", result.indexOf("CHANGE_ID=Iddaaddaa123456789") >= 0);
        assertTrue("Missing PATCHSET", result.indexOf("PATCHSET=1") >= 0);
        assertTrue("Missing VERIFIED", result.indexOf("VERIFIED=1") >= 0);
        assertTrue("Missing CODEREVIEW", result.indexOf("CODEREVIEW=32") >= 0);
        assertTrue("Missing REFSPEC", result.indexOf("REFSPEC=" + expectedRefSpec) >= 0);
        assertTrue("Missing ENV_BRANCH", result.indexOf("ENV_BRANCH=branch") >= 0);
        assertTrue("Missing ENV_CHANGE", result.indexOf("ENV_CHANGE=1000") >= 0);
        assertTrue("Missing ENV_REFSPEC", result.indexOf("ENV_REFSPEC=" + expectedRefSpec) >= 0);
        assertTrue("Missing ENV_CHANGEURL", result.indexOf("ENV_CHANGEURL=http://gerrit/1000") >= 0);
    }

    /**
     * test.
     */
    @Test
    public void testGetMinimumVerifiedValue() {
        System.out.println("getMinimumVerifiedValue");

        IGerritHudsonTriggerConfig config = Setup.createConfig();

        ParameterExpander instance = new ParameterExpander(config);
        MemoryImprint memoryImprint = mock(MemoryImprint.class);
        MemoryImprint.Entry[] entries = new MemoryImprint.Entry[3];

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulVerifiedValue()).thenReturn(3);
        AbstractProject project = mock(AbstractProject.class);
        when(project.getTrigger(GerritTrigger.class)).thenReturn(trigger);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getResult()).thenReturn(Result.SUCCESS);
        entries[0] = mock(MemoryImprint.Entry.class);
        when(entries[0].getBuild()).thenReturn(build);
        when(entries[0].getProject()).thenReturn(project);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildUnstableVerifiedValue()).thenReturn(1);
        project = mock(AbstractProject.class);
        when(project.getTrigger(GerritTrigger.class)).thenReturn(trigger);
        build = mock(AbstractBuild.class);
        when(build.getResult()).thenReturn(Result.UNSTABLE);
        entries[1] = mock(MemoryImprint.Entry.class);
        when(entries[1].getBuild()).thenReturn(build);
        when(entries[1].getProject()).thenReturn(project);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildUnstableVerifiedValue()).thenReturn(-1);
        project = mock(AbstractProject.class);
        when(project.getTrigger(GerritTrigger.class)).thenReturn(trigger);
        build = mock(AbstractBuild.class);
        when(build.getResult()).thenReturn(Result.UNSTABLE);
        entries[2] = mock(MemoryImprint.Entry.class);
        when(entries[2].getBuild()).thenReturn(build);
        when(entries[2].getProject()).thenReturn(project);

        when(memoryImprint.getEntries()).thenReturn(entries);

        int expResult = -1;
        int result = instance.getMinimumVerifiedValue(memoryImprint);
        assertEquals(expResult, result);
    }

    /**
     * test.
     */
    @Test
    public void testGetMinimumCodeReviewValue() {
        System.out.println("getMinimumCodeReviewValue");
        IGerritHudsonTriggerConfig config = Setup.createConfig();

        ParameterExpander instance = new ParameterExpander(config);
        MemoryImprint memoryImprint = mock(MemoryImprint.class);
        MemoryImprint.Entry[] entries = new MemoryImprint.Entry[3];

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(3);
        AbstractProject project = mock(AbstractProject.class);
        when(project.getTrigger(GerritTrigger.class)).thenReturn(trigger);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getResult()).thenReturn(Result.SUCCESS);
        entries[0] = mock(MemoryImprint.Entry.class);
        when(entries[0].getBuild()).thenReturn(build);
        when(entries[0].getProject()).thenReturn(project);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildUnstableCodeReviewValue()).thenReturn(1);
        project = mock(AbstractProject.class);
        when(project.getTrigger(GerritTrigger.class)).thenReturn(trigger);
        build = mock(AbstractBuild.class);
        when(build.getResult()).thenReturn(Result.UNSTABLE);
        entries[1] = mock(MemoryImprint.Entry.class);
        when(entries[1].getBuild()).thenReturn(build);
        when(entries[1].getProject()).thenReturn(project);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildUnstableCodeReviewValue()).thenReturn(-1);
        project = mock(AbstractProject.class);
        when(project.getTrigger(GerritTrigger.class)).thenReturn(trigger);
        build = mock(AbstractBuild.class);
        when(build.getResult()).thenReturn(Result.UNSTABLE);
        entries[2] = mock(MemoryImprint.Entry.class);
        when(entries[2].getBuild()).thenReturn(build);
        when(entries[2].getProject()).thenReturn(project);

        when(memoryImprint.getEntries()).thenReturn(entries);

        int expResult = -1;
        int result = instance.getMinimumCodeReviewValue(memoryImprint);
        assertEquals(expResult, result);
    }

    /**
     * test.
     * @throws IOException IOException
     * @throws InterruptedException InterruptedException
     */
    @Test
    public void testGetBuildCompletedCommandSuccessful() throws IOException, InterruptedException {
        tryGetBuildCompletedCommandSuccessful("", "\n \nhttp://localhost/test/ : SUCCESS");
        tryGetBuildCompletedCommandSuccessful("http://example.org/<CHANGE_ID>", "\n \nhttp://example.org/Iddaaddaa123456789 : SUCCESS");
        tryGetBuildCompletedCommandSuccessful("${BUILD_URL}console", "\n \nhttp://localhost/test/console : SUCCESS");
    }

    public void tryGetBuildCompletedCommandSuccessful(String customUrl, String expectedBuildsStats) throws IOException, InterruptedException {
        IGerritHudsonTriggerConfig config = Setup.createConfig();

        Hudson hudson = PowerMockito.mock(Hudson.class);
        when(hudson.getRootUrl()).thenReturn("http://localhost/");

        TaskListener taskListener = mock(TaskListener.class);

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulVerifiedValue()).thenReturn(null);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(32);
        when(trigger.getCustomUrl()).thenReturn(customUrl);
        AbstractProject project = mock(AbstractProject.class);
        when(project.getTrigger(GerritTrigger.class)).thenReturn(trigger);

        AbstractBuild r = mock(AbstractBuild.class);
        when(r.getUrl()).thenReturn("test/");
        when(r.getProject()).thenReturn(project);
        EnvVars env = Setup.createEnvVars();
        env.put("BUILD_URL", hudson.getRootUrl() + r.getUrl());
        when(r.getEnvironment(taskListener)).thenReturn(env);

        when(r.getResult()).thenReturn(Result.SUCCESS);

        PatchsetCreated event = Setup.createPatchsetCreated();

        MemoryImprint memoryImprint = mock(MemoryImprint.class);
        when(memoryImprint.getEvent()).thenReturn(event);

        when(memoryImprint.whereAllBuildsSuccessful()).thenReturn(true);
        when(memoryImprint.whereAnyBuildsFailed()).thenReturn(false);
        when(memoryImprint.whereAnyBuildsUnstable()).thenReturn(false);

        MemoryImprint.Entry[] entries = new MemoryImprint.Entry[1];
        entries[0] = mock(MemoryImprint.Entry.class);
        when(entries[0].getBuild()).thenReturn(r);
        when(entries[0].getProject()).thenReturn(project);

        when(memoryImprint.getEntries()).thenReturn(entries);

        final String expectedRefSpec = StringUtil.makeRefSpec(event);

        ParameterExpander instance = new ParameterExpander(config, hudson);

        String result = instance.getBuildCompletedCommand(memoryImprint, taskListener);
        System.out.println("Result: " + result);

        assertTrue("Missing OK message", result.indexOf(" MSG='Your friendly butler says OK.") >= 0);
        assertTrue("Missing BS", result.indexOf(" BS=" + expectedBuildsStats + "'") >= 0);
        assertTrue("Missing CHANGE_ID", result.indexOf("CHANGE_ID=Iddaaddaa123456789") >= 0);
        assertTrue("Missing PATCHSET", result.indexOf("PATCHSET=1") >= 0);
        assertTrue("Missing VERIFIED", result.indexOf("VERIFIED=3") >= 0);
        assertTrue("Missing CODEREVIEW", result.indexOf("CODEREVIEW=32") >= 0);
        assertTrue("Missing REFSPEC", result.indexOf("REFSPEC=" + expectedRefSpec) >= 0);
        assertTrue("Missing ENV_BRANCH", result.indexOf("ENV_BRANCH=branch") >= 0);
        assertTrue("Missing ENV_CHANGE", result.indexOf("ENV_CHANGE=1000") >= 0);
        assertTrue("Missing ENV_REFSPEC", result.indexOf("ENV_REFSPEC=" + expectedRefSpec) >= 0);
        assertTrue("Missing ENV_CHANGEURL", result.indexOf("ENV_CHANGEURL=http://gerrit/1000") >= 0);
    }
}
