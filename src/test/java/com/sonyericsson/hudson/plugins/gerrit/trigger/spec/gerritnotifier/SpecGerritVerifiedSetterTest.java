/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012, 2013 Sony Mobile Communications AB. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.extensions.GerritTriggeredBuildListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.GerritMessageProvider;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.GerritNotifier;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonymobile.tools.gerrit.gerritevents.GerritCmdRunner;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Scenario tests.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({
        Hudson.class,
        Jenkins.class,
        GerritMessageProvider.class,
        AbstractProject.class,
        GerritTriggeredBuildListener.class })
public class SpecGerritVerifiedSetterTest {

    private TaskListener taskListener;
    private GerritCmdRunner mockGerritCmdRunner;
    private Hudson hudson;
    private AbstractBuild build;
    private EnvVars env;
    private AbstractProject project;
    private GerritTrigger trigger;
    private Jenkins jenkins;

    /**
     * Prepare all the mocks.
     *
     * @throws Exception if so
     */
    @Before
    public void setUp() throws Exception {
        hudson = PowerMockito.mock(Hudson.class);
        when(hudson.getRootUrl()).thenReturn("http://localhost/");

        PowerMockito.mockStatic(GerritMessageProvider.class);
        when(GerritMessageProvider.all()).thenReturn(null);

        taskListener = mock(TaskListener.class);

        mockGerritCmdRunner = mock(GerritCmdRunner.class);

        build = mock(AbstractBuild.class);

        env = Setup.createEnvVars();
        when(build.getEnvironment(taskListener)).thenReturn(env);
        when(build.getId()).thenReturn("1");
        project = mock(AbstractProject.class);
        doReturn("MockProject").when(project).getFullName();
        when(build.getProject()).thenReturn(project);
        when(build.getParent()).thenReturn(project);
        doReturn(build).when(project).getBuild(anyString());

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(null);
        when(trigger.getGerritBuildSuccessfulVerifiedValue()).thenReturn(null);
        when(trigger.getGerritBuildFailedCodeReviewValue()).thenReturn(null);
        when(trigger.getGerritBuildFailedVerifiedValue()).thenReturn(null);
        Setup.setTrigger(trigger, project);

        mockStatic(Jenkins.class);
        jenkins = mock(Jenkins.class);
        when(Jenkins.getInstance()).thenReturn(jenkins);
        when(jenkins.getItemByFullName(eq("MockProject"), same(AbstractProject.class))).thenReturn(project);
        when(jenkins.getItemByFullName(eq("MockProject"), same(Job.class))).thenReturn(project);

        mockStatic(GerritTriggeredBuildListener.class);
        when(GerritTriggeredBuildListener.all()).thenReturn(mock(ExtensionList.class));
    }

    /**
     * A test.
     *
     * @throws IOException          IOException
     * @throws InterruptedException InterruptedException
     */
    @Test
    public void shouldCallGerritWithVerifiedOkFlagWhenBuildWasSuccessful()
            throws IOException, InterruptedException {

        when(build.getResult()).thenReturn(Result.SUCCESS);

        PatchsetCreated event = Setup.createPatchsetCreated();

        BuildMemory memory = new BuildMemory();
        memory.completed(event, build);

        IGerritHudsonTriggerConfig config = mock(IGerritHudsonTriggerConfig.class);

        String parameterString = "gerrit review MSG=OK VERIFIED=<VERIFIED> CODEREVIEW=<CODE_REVIEW>";
        when(config.getGerritCmdBuildSuccessful()).thenReturn(parameterString);
        when(config.getGerritBuildSuccessfulVerifiedValue()).thenReturn(1);
        when(config.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(1);

        GerritNotifier notifier = new GerritNotifier(config, mockGerritCmdRunner, hudson);
        notifier.buildCompleted(memory.getMemoryImprint(event), taskListener);
        String parameterStringExpected = "gerrit review MSG=OK VERIFIED=1 CODEREVIEW=1";

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

        when(build.getResult()).thenReturn(Result.FAILURE);
        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildMemory memory = new BuildMemory();

        memory.completed(event, build);

        IGerritHudsonTriggerConfig config = mock(IGerritHudsonTriggerConfig.class);

        String parameterString = "gerrit review MSG=Failed VERIFIED=<VERIFIED> CODEREVIEW=<CODE_REVIEW>";
        when(config.getGerritCmdBuildFailed()).thenReturn(parameterString);
        when(config.getGerritBuildFailedVerifiedValue()).thenReturn(-1);
        when(config.getGerritBuildFailedCodeReviewValue()).thenReturn(-1);

        GerritNotifier notifier = new GerritNotifier(config, mockGerritCmdRunner, hudson);
        notifier.buildCompleted(memory.getMemoryImprint(event), taskListener);
        String parameterStringExpected = "gerrit review MSG=Failed VERIFIED=-1 CODEREVIEW=-1";

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

        when(build.getResult()).thenReturn(Result.SUCCESS);

        PatchsetCreated event = Setup.createPatchsetCreated();

        BuildMemory memory = new BuildMemory();
        memory.completed(event, build);

        build = mock(AbstractBuild.class);
        when(build.getResult()).thenReturn(Result.FAILURE);
        env = Setup.createEnvVars();
        when(build.getEnvironment(taskListener)).thenReturn(env);
        when(build.getId()).thenReturn("1");
        project = mock(AbstractProject.class);
        doReturn("MockProject2").when(project).getFullName();
        doReturn(build).when(project).getBuild(anyString());
        when(build.getProject()).thenReturn(project);
        when(build.getParent()).thenReturn(project);
        when(jenkins.getItemByFullName(eq("MockProject2"), same(AbstractProject.class))).thenReturn(project);
        when(jenkins.getItemByFullName(eq("MockProject2"), same(Job.class))).thenReturn(project);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildFailedCodeReviewValue()).thenReturn(null);
        when(trigger.getGerritBuildFailedVerifiedValue()).thenReturn(null);
        Setup.setTrigger(trigger, project);

        memory.completed(event, build);

        IGerritHudsonTriggerConfig config = mock(IGerritHudsonTriggerConfig.class);

        String parameterString = "gerrit review MSG=FAILED VERIFIED=<VERIFIED> CODEREVIEW=<CODE_REVIEW>";
        when(config.getGerritCmdBuildFailed()).thenReturn(parameterString);
        when(config.getGerritBuildSuccessfulVerifiedValue()).thenReturn(1);
        when(config.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(1);
        when(config.getGerritBuildFailedCodeReviewValue()).thenReturn(-1);
        when(config.getGerritBuildFailedVerifiedValue()).thenReturn(-1);

        GerritNotifier notifier = new GerritNotifier(config, mockGerritCmdRunner, hudson);
        notifier.buildCompleted(memory.getMemoryImprint(event), taskListener);
        String parameterStringExpected = "gerrit review MSG=FAILED VERIFIED=-1 CODEREVIEW=-1";

        verify(mockGerritCmdRunner).sendCommand(parameterStringExpected);
    }
}
