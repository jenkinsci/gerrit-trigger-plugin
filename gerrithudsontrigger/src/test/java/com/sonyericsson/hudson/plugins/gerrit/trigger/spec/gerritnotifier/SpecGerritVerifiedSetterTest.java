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
package com.sonyericsson.hudson.plugins.gerrit.trigger.spec.gerritnotifier;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritCmdRunner;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.GerritNotifier;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.TaskListener;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;

import static org.mockito.Mockito.*;

/**
 * Scenario tests.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({Hudson.class })
public class SpecGerritVerifiedSetterTest {

    /**
     * A test.
     *
     * @throws IOException          IOException
     * @throws InterruptedException InterruptedException
     */
    @Test
    public void shouldCallGerritWithVerifiedOkFlagWhenBuildWasSuccessful()
            throws IOException, InterruptedException {

        Hudson hudson = PowerMockito.mock(Hudson.class);
        when(hudson.getRootUrl()).thenReturn("http://localhost/");

        TaskListener taskListener = mock(TaskListener.class);

        GerritCmdRunner mockGerritCmdRunner = mock(GerritCmdRunner.class);

        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getResult()).thenReturn(Result.SUCCESS);
        EnvVars env = Setup.createEnvVars();
        when(build.getEnvironment(taskListener)).thenReturn(env);
        AbstractProject project = mock(AbstractProject.class);
        when(build.getProject()).thenReturn(project);

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(null);
        when(trigger.getGerritBuildSuccessfulVerifiedValue()).thenReturn(null);
        when(project.getTrigger(GerritTrigger.class)).thenReturn(trigger);

        PatchsetCreated event = Setup.createPatchsetCreated();

        BuildMemory memory = new BuildMemory();
        memory.completed(event, build);

        IGerritHudsonTriggerConfig config = mock(IGerritHudsonTriggerConfig.class);

        String parameterString = "gerrit approve MSG=OK VERIFIED=<VERIFIED> CODEREVIEW=<CODE_REVIEW>";
        when(config.getGerritCmdBuildSuccessful()).thenReturn(parameterString);
        when(config.getGerritBuildSuccessfulVerifiedValue()).thenReturn(1);
        when(config.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(1);

        GerritNotifier notifier = new GerritNotifier(config, mockGerritCmdRunner, hudson);
        notifier.buildCompleted(memory.getMemoryImprint(event), taskListener);
        String parameterStringExpected = "gerrit approve MSG=OK VERIFIED=1 CODEREVIEW=1";

        verify(mockGerritCmdRunner).sendCommand(parameterStringExpected);
    }

    /**
     * A test.
     *
     * @throws IOException          IOException
     * @throws InterruptedException InterruptedException
     */
    @Test
    public void shouldCallGerritWithVerifiedRejectFlagWhenBuildWasNotSuccessful()
            throws IOException, InterruptedException {

        Hudson hudson = PowerMockito.mock(Hudson.class);
        when(hudson.getRootUrl()).thenReturn("http://localhost/");

        TaskListener taskListener = mock(TaskListener.class);

        GerritCmdRunner mockGerritCmdRunner = mock(GerritCmdRunner.class);

        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getResult()).thenReturn(Result.FAILURE);
        EnvVars env = Setup.createEnvVars();
        when(build.getEnvironment(taskListener)).thenReturn(env);
        AbstractProject project = mock(AbstractProject.class);
        when(build.getProject()).thenReturn(project);

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildFailedCodeReviewValue()).thenReturn(null);
        when(trigger.getGerritBuildFailedVerifiedValue()).thenReturn(null);
        when(project.getTrigger(GerritTrigger.class)).thenReturn(trigger);

        PatchsetCreated event = Setup.createPatchsetCreated();

        BuildMemory memory = new BuildMemory();
        memory.completed(event, build);

        IGerritHudsonTriggerConfig config = mock(IGerritHudsonTriggerConfig.class);

        String parameterString = "gerrit approve MSG=Failed VERIFIED=<VERIFIED> CODEREVIEW=<CODE_REVIEW>";
        when(config.getGerritCmdBuildFailed()).thenReturn(parameterString);
        when(config.getGerritBuildFailedVerifiedValue()).thenReturn(-1);
        when(config.getGerritBuildFailedCodeReviewValue()).thenReturn(-1);

        GerritNotifier notifier = new GerritNotifier(config, mockGerritCmdRunner, hudson);
        notifier.buildCompleted(memory.getMemoryImprint(event), taskListener);
        String parameterStringExpected = "gerrit approve MSG=Failed VERIFIED=-1 CODEREVIEW=-1";

        verify(mockGerritCmdRunner).sendCommand(parameterStringExpected);
    }

    /**
     * A test.
     *
     * @throws IOException          IOException
     * @throws InterruptedException InterruptedException
     */
    @Test
    public void shouldCallGerritWithVerifiedFailedFlagWhenBuildOneBuildFailedAndAnotherSuccessful()
            throws IOException, InterruptedException {
        Hudson hudson = PowerMockito.mock(Hudson.class);
        when(hudson.getRootUrl()).thenReturn("http://localhost/");

        TaskListener taskListener = mock(TaskListener.class);

        GerritCmdRunner mockGerritCmdRunner = mock(GerritCmdRunner.class);

        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getResult()).thenReturn(Result.SUCCESS);
        EnvVars env = Setup.createEnvVars();
        when(build.getEnvironment(taskListener)).thenReturn(env);
        AbstractProject project = mock(AbstractProject.class);
        when(build.getProject()).thenReturn(project);

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(null);
        when(trigger.getGerritBuildSuccessfulVerifiedValue()).thenReturn(null);
        when(project.getTrigger(GerritTrigger.class)).thenReturn(trigger);

        PatchsetCreated event = Setup.createPatchsetCreated();

        BuildMemory memory = new BuildMemory();
        memory.completed(event, build);

        build = mock(AbstractBuild.class);
        when(build.getResult()).thenReturn(Result.FAILURE);
        env = Setup.createEnvVars();
        when(build.getEnvironment(taskListener)).thenReturn(env);
        project = mock(AbstractProject.class);
        when(build.getProject()).thenReturn(project);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildFailedCodeReviewValue()).thenReturn(null);
        when(trigger.getGerritBuildFailedVerifiedValue()).thenReturn(null);
        when(project.getTrigger(GerritTrigger.class)).thenReturn(trigger);

        memory.completed(event, build);

        IGerritHudsonTriggerConfig config = mock(IGerritHudsonTriggerConfig.class);

        String parameterString = "gerrit approve MSG=FAILED VERIFIED=<VERIFIED> CODEREVIEW=<CODE_REVIEW>";
        when(config.getGerritCmdBuildFailed()).thenReturn(parameterString);
        when(config.getGerritBuildSuccessfulVerifiedValue()).thenReturn(1);
        when(config.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(1);
        when(config.getGerritBuildFailedCodeReviewValue()).thenReturn(-1);
        when(config.getGerritBuildFailedVerifiedValue()).thenReturn(-1);

        GerritNotifier notifier = new GerritNotifier(config, mockGerritCmdRunner, hudson);
        notifier.buildCompleted(memory.getMemoryImprint(event), taskListener);
        String parameterStringExpected = "gerrit approve MSG=FAILED VERIFIED=-1 CODEREVIEW=-1";

        verify(mockGerritCmdRunner).sendCommand(parameterStringExpected);
    }
}
