/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
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
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.SkipVote;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;

import hudson.EnvVars;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import jenkins.model.Jenkins;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//CS IGNORE MagicNumber FOR NEXT 700 LINES. REASON: Mocks tests.

/**
 * Tests for {@link ParameterExpander}.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ Jenkins.class, GerritMessageProvider.class })
public class ParameterExpanderTest {

    private Jenkins jenkins;

    /**
     * Mock Jenkins.
     */
    @Before
    public void setup() {
        PowerMockito.mockStatic(Jenkins.class);
        jenkins = PowerMockito.mock(Jenkins.class);
        PowerMockito.when(Jenkins.getInstance()).thenReturn(jenkins);
        when(jenkins.getRootUrl()).thenReturn("http://localhost/");
    }

    /**
     * test.
     * @throws Exception Exception
     */
    @Test
    public void testGetBuildStartedCommand() throws Exception {
        TaskListener taskListener = mock(TaskListener.class);

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildStartedVerifiedValue()).thenReturn(null);
        when(trigger.getGerritBuildStartedCodeReviewValue()).thenReturn(32);
        when(trigger.getBuildStartMessage()).thenReturn("${START_MESSAGE_VAR}");
        AbstractProject project = mock(AbstractProject.class);

        Setup.setTrigger(trigger, project);

        AbstractBuild r = Setup.createBuild(project, taskListener, Setup.createEnvVars());

        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildsStartedStats stats = Setup.createBuildStartedStats(event);
        IGerritHudsonTriggerConfig config = Setup.createConfig();


        PowerMockito.mockStatic(GerritMessageProvider.class);
        List<GerritMessageProvider> messageProviderExtensionList = new LinkedList<GerritMessageProvider>();
        messageProviderExtensionList.add(new GerritMessageProviderExtension());
        messageProviderExtensionList.add(new GerritMessageProviderExtensionReturnNull());
        when(GerritMessageProvider.all()).thenReturn(messageProviderExtensionList);

        ParameterExpander instance = new ParameterExpander(config, jenkins);

        final String expectedRefSpec = StringUtil.makeRefSpec(event);

        String result = instance.getBuildStartedCommand(r, taskListener, event, stats);
        System.out.println("result: " + result);
        assertTrue("Missing START_MESSAGE_VAL from getBuildStartMessage()",
                result.indexOf("START_MESSAGE_VAL") >= 0);
        assertTrue("Missing CHANGE_ID", result.indexOf("CHANGE_ID=Iddaaddaa123456789") >= 0);
        assertTrue("Missing PATCHSET", result.indexOf("PATCHSET=1") >= 0);
        assertTrue("Missing VERIFIED", result.indexOf("VERIFIED=1") >= 0);
        assertTrue("Missing CODEREVIEW", result.indexOf("CODEREVIEW=32") >= 0);
        assertTrue("Missing NOTIFICATION_LEVEL", result.indexOf("NOTIFICATION_LEVEL=ALL") >= 0);
        assertTrue("Missing REFSPEC", result.indexOf("REFSPEC=" + expectedRefSpec) >= 0);
        assertTrue("Missing ENV_BRANCH", result.indexOf("ENV_BRANCH=branch") >= 0);
        assertTrue("Missing ENV_CHANGE", result.indexOf("ENV_CHANGE=1000") >= 0);
        assertTrue("Missing ENV_REFSPEC", result.indexOf("ENV_REFSPEC=" + expectedRefSpec) >= 0);
        assertTrue("Missing ENV_CHANGEURL", result.indexOf("ENV_CHANGEURL=http://gerrit/1000") >= 0);
        assertTrue("Missing CUSTOM_MESSAGE", result.indexOf("CUSTOM_MESSAGE_BUILD_STARTED") >= 0);
        assertTrue("Newlines are stripped", result.indexOf("Message\nwith newline") >= 0);
    }

    /**
     * test.
     */
    @Test
    public void testGetMinimumVerifiedValue() {
        IGerritHudsonTriggerConfig config = Setup.createConfig();

        ParameterExpander instance = new ParameterExpander(config, jenkins);
        MemoryImprint memoryImprint = mock(MemoryImprint.class);
        MemoryImprint.Entry[] entries = new MemoryImprint.Entry[4];

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulVerifiedValue()).thenReturn(3);
        entries[0] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.SUCCESS);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildUnstableVerifiedValue()).thenReturn(1);
        entries[1] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.UNSTABLE);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildUnstableVerifiedValue()).thenReturn(-1);
        entries[2] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.UNSTABLE);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildNotBuiltVerifiedValue()).thenReturn(-4);
        entries[3] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.NOT_BUILT);

        when(memoryImprint.getEntries()).thenReturn(entries);

        // When not all results are NOT_BUILT, we should ignore NOT_BUILT.
        int expResult = -1;
        int result = instance.getMinimumVerifiedValue(memoryImprint, true);
        assertEquals(expResult, result);

        // Otherwise, we should use NOT_BUILT.
        expResult = -4;
        result = instance.getMinimumVerifiedValue(memoryImprint, false);
        assertEquals(expResult, result);
    }

    /**
     * test.
     */
    @Test
    public void testGetMinimumCodeReviewValue() {
        IGerritHudsonTriggerConfig config = Setup.createConfig();

        ParameterExpander instance = new ParameterExpander(config, jenkins);
        MemoryImprint memoryImprint = mock(MemoryImprint.class);
        MemoryImprint.Entry[] entries = new MemoryImprint.Entry[4];

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(3);
        entries[0] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.SUCCESS);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildUnstableCodeReviewValue()).thenReturn(1);
        entries[1] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.UNSTABLE);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildUnstableCodeReviewValue()).thenReturn(-1);
        entries[2] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.UNSTABLE);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildNotBuiltCodeReviewValue()).thenReturn(-4);
        entries[3] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.NOT_BUILT);

        when(memoryImprint.getEntries()).thenReturn(entries);

        // When not all results are NOT_BUILT, we should ignore NOT_BUILT.
        Integer result = instance.getMinimumCodeReviewValue(memoryImprint, true);
        assertEquals(Integer.valueOf(-1), result);

        // Otherwise, we should use NOT_BUILT.
        result = instance.getMinimumCodeReviewValue(memoryImprint, false);
        assertEquals(Integer.valueOf(-4), result);
    }

    /**
     * Tests {@link ParameterExpander#getMinimumCodeReviewValue(MemoryImprint, boolean)} with one
     * unstable build vote skipped.
     *
     * @see com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger#getSkipVote()
     */
    @Test
    public void testGetMinimumCodeReviewValueOneUnstableSkipped() {
        IGerritHudsonTriggerConfig config = Setup.createConfig();

        ParameterExpander instance = new ParameterExpander(config, jenkins);
        MemoryImprint memoryImprint = mock(MemoryImprint.class);
        MemoryImprint.Entry[] entries = new MemoryImprint.Entry[3];

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(1);
        entries[0] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.SUCCESS);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildUnstableCodeReviewValue()).thenReturn(-1);
        SkipVote skipVote = new SkipVote(false, false, true, false, false);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        entries[1] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.UNSTABLE);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(2);
        entries[2] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.SUCCESS);


        when(memoryImprint.getEntries()).thenReturn(entries);

        Integer result = instance.getMinimumCodeReviewValue(memoryImprint, true);
        assertEquals(Integer.valueOf(1), result);
    }

    /**
     * Tests {@link ParameterExpander#getMinimumCodeReviewValue(MemoryImprint, boolean)} with one
     * successful build vote skipped.
     *
     * @see com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger#getSkipVote()
     */
    @Test
    public void testGetMinimumCodeReviewValueOneSuccessfulSkipped() {
        IGerritHudsonTriggerConfig config = Setup.createConfig();

        ParameterExpander instance = new ParameterExpander(config, jenkins);
        MemoryImprint memoryImprint = mock(MemoryImprint.class);
        MemoryImprint.Entry[] entries = new MemoryImprint.Entry[1];

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(1);
        SkipVote skipVote = new SkipVote(true, false, false, false, false);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        entries[0] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.SUCCESS);

        when(memoryImprint.getEntries()).thenReturn(entries);

        Integer result = instance.getMinimumCodeReviewValue(memoryImprint, true);
        assertEquals(null, result);
    }

    /**
     * Tests {@link ParameterExpander#getMinimumCodeReviewValue(MemoryImprint, boolean)} with one
     * job that has override core review value on build successful.
     *
     * @see com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger#getSkipVote()
     */
    @Test
    public void testGetMinimumCodeReviewValueForOneJobOverridenBuildSuccessful() {
        IGerritHudsonTriggerConfig config = Setup.createConfigWithCodeReviewsNull();

        ParameterExpander instance = new ParameterExpander(config, jenkins);
        MemoryImprint memoryImprint = mock(MemoryImprint.class);
        MemoryImprint.Entry[] entries = new MemoryImprint.Entry[2];

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(null);
        entries[0] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.SUCCESS);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(Integer.valueOf(2));
        entries[1] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.SUCCESS);

        when(memoryImprint.getEntries()).thenReturn(entries);

        // Since one job has overriden CR value, it is the only one inspected
        // and therefore the only one that contributes.
        Integer result = instance.getMinimumCodeReviewValue(memoryImprint, false);
        assertEquals(Integer.valueOf(2), result);
    }

    /**
     * Tests {@link ParameterExpander#getMinimumCodeReviewValue(MemoryImprint, boolean)} with one
     * job that has override core review value on build successful.
     *
     * @see com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger#getSkipVote()
     */
    @Test
    public void testGetMinimumCodeReviewValueForOneJobOverridenBuildFailed() {
        IGerritHudsonTriggerConfig config = Setup.createConfigWithCodeReviewsNull();

        ParameterExpander instance = new ParameterExpander(config, jenkins);
        MemoryImprint memoryImprint = mock(MemoryImprint.class);
        MemoryImprint.Entry[] entries = new MemoryImprint.Entry[2];

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildFailedCodeReviewValue()).thenReturn(null);
        entries[0] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.FAILURE);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildFailedCodeReviewValue()).thenReturn(Integer.valueOf(-2));
        entries[1] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.FAILURE);

        when(memoryImprint.getEntries()).thenReturn(entries);

        // Since one job has overriden CR value, it is the only one inspected
        // and therefore the only one that contributes.
        Integer result = instance.getMinimumCodeReviewValue(memoryImprint, false);
        assertEquals(Integer.valueOf(-2), result);
    }

    /**
     * Tests {@link ParameterExpander#getMinimumCodeReviewValue(MemoryImprint, boolean)} with one
     * job that has override core review value on build successful.
     *
     * @see com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger#getSkipVote()
     */
    @Test
    public void testGetMinimumCodeReviewValueForOneJobOverridenMixed() {
        IGerritHudsonTriggerConfig config = Setup.createConfigWithCodeReviewsNull();

        ParameterExpander instance = new ParameterExpander(config, jenkins);
        MemoryImprint memoryImprint = mock(MemoryImprint.class);
        MemoryImprint.Entry[] entries = new MemoryImprint.Entry[2];

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildFailedCodeReviewValue()).thenReturn(null);
        entries[0] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.FAILURE);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(Integer.valueOf(2));
        entries[1] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.SUCCESS);

        when(memoryImprint.getEntries()).thenReturn(entries);

        // Since one job has overriden CR value, it is the only one inspected
        // and therefore the only one that contributes.
        Integer result = instance.getMinimumCodeReviewValue(memoryImprint, false);
        assertEquals(Integer.valueOf(2), result);
    }

    /**
     * test.
     * @throws IOException IOException
     * @throws InterruptedException InterruptedException
     */
    @Test
    public void testGetBuildCompletedCommandSuccessful() throws IOException, InterruptedException {
        tryGetBuildCompletedCommandSuccessful("",
                "\n\nhttp://localhost/test/ : SUCCESS");
        tryGetBuildCompletedCommandSuccessful("http://example.org/<CHANGE_ID>",
                "\n\nhttp://example.org/Iddaaddaa123456789 : SUCCESS");
        tryGetBuildCompletedCommandSuccessful("${BUILD_URL}console",
                "\n\nhttp://localhost/test/console : SUCCESS");
    }

    /**
     * Same test as {@link #testGetBuildCompletedCommandSuccessful()}, but with ChangeAbandoned event instead.
     *
     * @throws IOException IOException
     * @throws InterruptedException InterruptedException
     */
    @Test
    public void testGetBuildCompletedCommandSuccessfulChangeAbandoned() throws IOException, InterruptedException {
        tryGetBuildCompletedCommandSuccessfulChangeAbandoned("",
                "\n\nhttp://localhost/test/ : SUCCESS");
        tryGetBuildCompletedCommandSuccessfulChangeAbandoned("http://example.org/<CHANGE_ID>",
                "\n\nhttp://example.org/Iddaaddaa123456789 : SUCCESS");
        tryGetBuildCompletedCommandSuccessfulChangeAbandoned("${BUILD_URL}console",
                "\n\nhttp://localhost/test/console : SUCCESS");
    }
    /**
     * Same test as {@link #testGetBuildCompletedCommandSuccessful()}, but with ChangeMerged event instead.
     *
     * @throws IOException IOException
     * @throws InterruptedException InterruptedException
     */
    @Test
    public void testGetBuildCompletedCommandSuccessfulChangeMerged() throws IOException, InterruptedException {
        tryGetBuildCompletedCommandSuccessfulChangeMerged("",
                "\n\nhttp://localhost/test/ : SUCCESS");
        tryGetBuildCompletedCommandSuccessfulChangeMerged("http://example.org/<CHANGE_ID>",
                "\n\nhttp://example.org/Iddaaddaa123456789 : SUCCESS");
        tryGetBuildCompletedCommandSuccessfulChangeMerged("${BUILD_URL}console",
                "\n\nhttp://localhost/test/console : SUCCESS");
    }

    /**
     * Same test as {@link #testGetBuildCompletedCommandSuccessful()}, but with ChangeRestored event instead.
     *
     * @throws IOException IOException
     * @throws InterruptedException InterruptedException
     */
    @Test
    public void testGetBuildCompletedCommandSuccessfulChangeRestored() throws IOException, InterruptedException {
        tryGetBuildCompletedCommandSuccessfulChangeRestored("",
                "\n\nhttp://localhost/test/ : SUCCESS");
        tryGetBuildCompletedCommandSuccessfulChangeRestored("http://example.org/<CHANGE_ID>",
                "\n\nhttp://example.org/Iddaaddaa123456789 : SUCCESS");
        tryGetBuildCompletedCommandSuccessfulChangeRestored("${BUILD_URL}console",
                "\n\nhttp://localhost/test/console : SUCCESS");
    }

    /**
     * Test for message ordering in case of multiple build entries.
     *
     * @throws IOException IOException
     * @throws InterruptedException InterruptedException
     */
    @Test
    public void testGetBuildCompletedCommandMulipleBuildsMessageOrder() throws IOException, InterruptedException {
        tryGetBuildCompletedCommandEventWithResults("",
                new String[] { // messages must be in order
                    "\n\nhttp://localhost/test/ : FAILURE",
                    "\n\nhttp://localhost/test/ : UNSTABLE",
                    "\n\nhttp://localhost/test/ : SUCCESS", },
                new Result[] {Result.SUCCESS, Result.FAILURE, Result.UNSTABLE}, "'A disappointed butler says not OK",
                Setup.createPatchsetCreated(), -1, 0);
    }

    /**
     * Sub test for {@link #testGetBuildCompletedCommandSuccessful()}.
     *
     * @param customUrl the customUrl to return from {@link GerritTrigger#getCustomUrl()}
     * @param expectedBuildsStats the expected buildStats output.
     * @throws IOException if so.
     * @throws InterruptedException if so.
     */
    public void tryGetBuildCompletedCommandSuccessful(String customUrl, String expectedBuildsStats)
            throws IOException, InterruptedException {
        tryGetBuildCompletedCommandSuccessfulEvent(customUrl, expectedBuildsStats, Setup.createPatchsetCreated(), 3, 32);
    }

    /**
     * Sub test for {@link #testGetBuildCompletedCommandSuccessfulChangeAbandoned()}.
     *
     * @param customUrl the customUrl to return from {@link GerritTrigger#getCustomUrl()}
     * @param expectedBuildsStats the expected buildStats output.
     * @throws IOException if so.
     * @throws InterruptedException if so.
     */
    public void tryGetBuildCompletedCommandSuccessfulChangeAbandoned(String customUrl, String expectedBuildsStats)
            throws IOException, InterruptedException {
        tryGetBuildCompletedCommandSuccessfulEvent(customUrl, expectedBuildsStats,
                                                   Setup.createChangeAbandoned(), null, null);
    }

    /**
     * Sub test for {@link #testGetBuildCompletedCommandSuccessfulChangeMerged()}.
     *
     * @param customUrl the customUrl to return from {@link GerritTrigger#getCustomUrl()}
     * @param expectedBuildsStats the expected buildStats output.
     * @throws IOException if so.
     * @throws InterruptedException if so.
     */
    public void tryGetBuildCompletedCommandSuccessfulChangeMerged(String customUrl, String expectedBuildsStats)
            throws IOException, InterruptedException {
        tryGetBuildCompletedCommandSuccessfulEvent(customUrl, expectedBuildsStats,
                                                   Setup.createChangeMerged(), null, null);
    }

    /**
     * Sub test for {@link #testGetBuildCompletedCommandSuccessfulChangeRestored()}.
     *
     * @param customUrl the customUrl to return from {@link GerritTrigger#getCustomUrl()}
     * @param expectedBuildsStats the expected buildStats output.
     * @throws IOException if so.
     * @throws InterruptedException if so.
     */
    public void tryGetBuildCompletedCommandSuccessfulChangeRestored(String customUrl, String expectedBuildsStats)
            throws IOException, InterruptedException {
        tryGetBuildCompletedCommandSuccessfulEvent(customUrl, expectedBuildsStats,
                                                   Setup.createChangeRestored(), null, null);
    }

    /**
     * Sub test for {@link #testGetBuildCompletedCommandSuccessful()} and
     * {@link #testGetBuildCompletedCommandSuccessfulChangeMerged()}.
     *
     * @param customUrl the customUrl to return from {@link GerritTrigger#getCustomUrl()}
     * @param expectedBuildsStats the expected buildStats output.
     * @param event the event.
     * @param expectedVerifiedVote what to expect in the final verified vote even if 1 is calculated
     * @param expectedCodeReviewVote what to expect in the final code review vote even if 32 is calculated
     * @throws IOException if so.
     * @throws InterruptedException if so.
     */
    public void tryGetBuildCompletedCommandSuccessfulEvent(String customUrl, String expectedBuildsStats,
            GerritTriggeredEvent event, Integer expectedVerifiedVote, Integer expectedCodeReviewVote)
                    throws IOException, InterruptedException {
        tryGetBuildCompletedCommandEventWithResults(customUrl, new String[] {expectedBuildsStats},
                new Result[] {Result.SUCCESS}, "'Your friendly butler says OK.",
                Setup.createChangeRestored(), null, null);
    }

    /**
     * Sub test for {@link #testGetBuildCompletedCommandSuccessful()} and
     * {@link #testGetBuildCompletedCommandSuccessfulChangeMerged()}.
     *
     * @param customUrl the customUrl to return from {@link GerritTrigger#getCustomUrl()}
     * @param expectedBuildsStats the expected buildStats output.
     * @param expectedBuildResults the expected build outcomes
     * @param expectedMessage the expected message
     * @param event the event.
     * @param expectedVerifiedVote what to expect in the final verified vote even if 1 is calculated
     * @param expectedCodeReviewVote what to expect in the final code review vote even if 32 is calculated
     * @throws IOException if so.
     * @throws InterruptedException if so.
     */
    public void tryGetBuildCompletedCommandEventWithResults(String customUrl, String[] expectedBuildsStats,
            Result[] expectedBuildResults, String expectedMessage, GerritTriggeredEvent event,
            Integer expectedVerifiedVote, Integer expectedCodeReviewVote)
                    throws IOException, InterruptedException {

        IGerritHudsonTriggerConfig config = Setup.createConfig();

        TaskListener taskListener = mock(TaskListener.class);

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulVerifiedValue()).thenReturn(null);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(32);
        when(trigger.getCustomUrl()).thenReturn(customUrl);
        AbstractProject project = mock(AbstractProject.class);
        Setup.setTrigger(trigger, project);

        MemoryImprint.Entry[] entries = new MemoryImprint.Entry[expectedBuildResults.length];
        for (int i = 0; i < expectedBuildResults.length; i++) {
            EnvVars env = Setup.createEnvVars();
            AbstractBuild r = Setup.createBuild(project, taskListener, env);
            env.put("BUILD_URL", jenkins.getRootUrl() + r.getUrl());
            when(r.getResult()).thenReturn(expectedBuildResults[i]);
            entries[i] = Setup.createImprintEntry(project, r);
        }

        MemoryImprint memoryImprint = mock(MemoryImprint.class);
        when(memoryImprint.getEvent()).thenReturn(event);

        when(memoryImprint.wereAllBuildsSuccessful()).thenReturn(allAreOfType(Result.SUCCESS, expectedBuildResults));
        when(memoryImprint.wereAllBuildsNotBuilt()).thenReturn(allAreOfType(Result.NOT_BUILT, expectedBuildResults));
        when(memoryImprint.wereAnyBuildsFailed()).thenReturn(anyIsOfType(Result.FAILURE, expectedBuildResults));
        when(memoryImprint.wereAnyBuildsUnstable()).thenReturn(anyIsOfType(Result.UNSTABLE, expectedBuildResults));

        when(memoryImprint.getEntries()).thenReturn(entries);

        assertThat("Event should be a ChangeBasedEvent", event, instanceOf(ChangeBasedEvent.class));
        final String expectedRefSpec = StringUtil.makeRefSpec((ChangeBasedEvent)event);

        PowerMockito.mockStatic(GerritMessageProvider.class);
        List<GerritMessageProvider> messageProviderExtensionList = new LinkedList<GerritMessageProvider>();
        messageProviderExtensionList.add(new GerritMessageProviderExtension());
        messageProviderExtensionList.add(new GerritMessageProviderExtensionReturnNull());
        when(GerritMessageProvider.all()).thenReturn(messageProviderExtensionList);

        ParameterExpander instance = new ParameterExpander(config, jenkins);

        String result = instance.getBuildCompletedCommand(memoryImprint, taskListener);
        System.out.println("Result: " + result);

        assertThat("Missing message", result, containsString(" MSG=" + expectedMessage));
        assertThat("Missing BS", result, containsStrings(expectedBuildsStats));
        assertThat("Missing CHANGE_ID", result, containsString("CHANGE_ID=Iddaaddaa123456789"));
        assertThat("Missing PATCHSET", result, containsString("PATCHSET=1"));
        assertThat("Missing NOTIFICATION_LEVEL", result, containsString("NOTIFICATION_LEVEL=ALL"));
        assertThat("Missing REFSPEC", result, containsString("REFSPEC=" + expectedRefSpec));
        assertThat("Missing ENV_BRANCH", result, containsString("ENV_BRANCH=branch"));
        assertThat("Missing ENV_CHANGE", result, containsString("ENV_CHANGE=1000"));
        assertThat("Missing ENV_REFSPEC", result, containsString("ENV_REFSPEC=" + expectedRefSpec));
        assertThat("Missing ENV_CHANGEURL", result, containsString("ENV_CHANGEURL=http://gerrit/1000"));
        assertThat("Missing CUSTOM_MESSAGES", result, containsString("CUSTOM_MESSAGE_BUILD_COMPLETED"));
        assertThat("Missing VERIFIED", result, containsString("VERIFIED=" + expectedVerifiedVote));
        assertThat("Missing CODEREVIEW", result, containsString("CODEREVIEW=" + expectedCodeReviewVote));
    }


    /**
     * Test.
     * @throws Exception if so
     */
    @Test
    public void testBuildStatsWithUnsuccessfulMessage() throws Exception {
        tryBuildStatsFailureCommand("This was an unsuccessful message. ",
                "\n\nhttp://localhost/test/ : FAILURE <<<\nThis was an unsuccessful message.\n>>>");
        tryBuildStatsFailureCommand(null, "\n\nhttp://localhost/test/ : FAILURE");
        tryBuildStatsFailureCommand("", "\n\nhttp://localhost/test/ : FAILURE");
    }

    /**
     * Sub test for {@link #testBuildStatsWithUnsuccessfulMessage()}.
     *
     * @param unsuccessfulMessage Build unsuccessful message
     * @param expectedBuildStats Expected build stats string
     * @throws Exception if so
     */
    public void tryBuildStatsFailureCommand(String unsuccessfulMessage, String expectedBuildStats) throws Exception {
        IGerritHudsonTriggerConfig config = Setup.createConfig();

        TaskListener taskListener = mock(TaskListener.class);

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulVerifiedValue()).thenReturn(null);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(32);
        AbstractProject project = mock(AbstractProject.class);
        Setup.setTrigger(trigger, project);

        EnvVars env = Setup.createEnvVars();
        AbstractBuild r = Setup.createBuild(project, taskListener, env);
        env.put("BUILD_URL", jenkins.getRootUrl() + r.getUrl());

        when(r.getResult()).thenReturn(Result.FAILURE);

        PatchsetCreated event = Setup.createPatchsetCreated();

        MemoryImprint memoryImprint = mock(MemoryImprint.class);
        when(memoryImprint.getEvent()).thenReturn(event);

        when(memoryImprint.wereAllBuildsSuccessful()).thenReturn(true);
        when(memoryImprint.wereAnyBuildsFailed()).thenReturn(false);
        when(memoryImprint.wereAnyBuildsUnstable()).thenReturn(false);

        MemoryImprint.Entry[] entries = { Setup.createImprintEntry(project, r) };

        if (unsuccessfulMessage != null && !unsuccessfulMessage.isEmpty()) {
            when(entries[0].getUnsuccessfulMessage()).thenReturn(unsuccessfulMessage.trim());
        } else {
            when(entries[0].getUnsuccessfulMessage()).thenReturn(null);
        }

        when(memoryImprint.getEntries()).thenReturn(entries);

        PowerMockito.mockStatic(GerritMessageProvider.class);
        List<GerritMessageProvider> messageProviderExtensionList = new LinkedList<GerritMessageProvider>();
        messageProviderExtensionList.add(new GerritMessageProviderExtension());
        messageProviderExtensionList.add(new GerritMessageProviderExtensionReturnNull());
        when(GerritMessageProvider.all()).thenReturn(messageProviderExtensionList);

        ParameterExpander instance = new ParameterExpander(config, jenkins);

        String result = instance.getBuildCompletedCommand(memoryImprint, taskListener);
        System.out.println("Result: " + result);

        assertTrue("Missing BS", result.indexOf(" BS=" + expectedBuildStats) >= 0);
    }

    /**
     * Test to determine Build Stats when server Code Review Values are null.
     *
     * @throws Exception if so
     */
    @Test
    public void getBuildStatsFailureCommandWithNullsForCodeReviewValues() throws Exception {
        IGerritHudsonTriggerConfig config = Setup.createConfigWithCodeReviewsNull();

        TaskListener taskListener = mock(TaskListener.class);

        GerritTrigger trigger = mock(GerritTrigger.class);

        when(trigger.getGerritBuildFailedCodeReviewValue()).thenReturn(null);

        AbstractProject project = mock(AbstractProject.class);
        Setup.setTrigger(trigger, project);

        EnvVars env = Setup.createEnvVars();
        AbstractBuild r = Setup.createBuild(project, taskListener, env);
        env.put("BUILD_URL", jenkins.getRootUrl() + r.getUrl());

        when(r.getResult()).thenReturn(Result.FAILURE);

        PatchsetCreated event = Setup.createPatchsetCreated();

        MemoryImprint memoryImprint = mock(MemoryImprint.class);
        when(memoryImprint.getEvent()).thenReturn(event);

        when(memoryImprint.wereAllBuildsSuccessful()).thenReturn(true);
        when(memoryImprint.wereAnyBuildsFailed()).thenReturn(false);
        when(memoryImprint.wereAnyBuildsUnstable()).thenReturn(false);

        MemoryImprint.Entry[] entries = { Setup.createImprintEntry(project, r) };

        when(entries[0].getUnsuccessfulMessage()).thenReturn("This Build has Failed");

        when(memoryImprint.getEntries()).thenReturn(entries);

        PowerMockito.mockStatic(GerritMessageProvider.class);
        List<GerritMessageProvider> messageProviderExtensionList = new LinkedList<GerritMessageProvider>();
        messageProviderExtensionList.add(new GerritMessageProviderExtension());
        messageProviderExtensionList.add(new GerritMessageProviderExtensionReturnNull());
        when(GerritMessageProvider.all()).thenReturn(messageProviderExtensionList);

        ParameterExpander instance = new ParameterExpander(config, jenkins);

        String result = instance.getBuildCompletedCommand(memoryImprint, taskListener);
        System.out.println("Result: " + result);

        assertTrue("Missing Build has Failed", result.indexOf("This Build has Failed") >= 0);
    }

    /**
     * Whether all of the given results equal to the given result.
     *
     * @param query the result to check
     * @param all all results
     * @return true if all are the same
     */
    private static boolean allAreOfType(Result query, Result[] all) {
        for (Result result : all) {
            if (!result.equals(query)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether any of the given results equals to the given result.
     *
     * @param query the result to check
     * @param all all results
     * @return true if any is the same
     */
    private static boolean anyIsOfType(Result query, Result[] all) {
        return Arrays.asList(all).contains(query);
    }

    /**
     * Extension implementing GerritMessageProvider to provide a custom build message.
     */
    public static class GerritMessageProviderExtension extends GerritMessageProvider {
        private static final long serialVersionUID = -7565217057927807166L;

        @Override
        public String getBuildStartedMessage(Run build) {
            return "CUSTOM_MESSAGE_BUILD_STARTED";
        }

        @Override
        public String getBuildCompletedMessage(Run build) {
            return "CUSTOM_MESSAGE_BUILD_COMPLETED";
        }
    }

    /**
     * Extension implementing GerritMessageProvider to provide a custom build message (null).
     */
    public static class GerritMessageProviderExtensionReturnNull extends GerritMessageProvider {
        private static final long serialVersionUID = -3479376646924947609L;

        @Override
        public String getBuildStartedMessage(Run build) {
            return null;
        }

        @Override
        public String getBuildCompletedMessage(Run build) {
            return null;
        }
    }

    /**
     * Creates a matcher for multiple substring containment
     *
     * @param substrings the substrings
     * @return the matcher
     */
    private static Matcher<String> containsStrings(String... substrings) {
        return new SubstringMatcher(substrings);
    }

    /**
     * Checks containment of multiple strings in another string.
     */
    public static final class SubstringMatcher extends TypeSafeMatcher<String> {

        private final String[] substrings;

        /**
         * Creates a matcher.
         *
         * @param substrings the substrings to check
         */
        public SubstringMatcher(final String... substrings) {
            this.substrings = substrings;
        }

        @Override
        public boolean matchesSafely(String s) {
            int i = 0;
            for (String substring : substrings) {
                i = s.indexOf(substring, i);
                if (i < 0) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void describeMismatchSafely(String item, Description mismatchDescription) {
          mismatchDescription.appendText("was \"").appendText(item).appendText("\"");
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("a string containing strings ").appendValue(Arrays.toString(substrings));
        }
    }

}
