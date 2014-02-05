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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.SkipVote;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil;
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
import java.util.LinkedList;
import java.util.List;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//CS IGNORE MagicNumber FOR NEXT 450 LINES. REASON: Mocks tests.

/**
 * Tests for {@link ParameterExpander}.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ Hudson.class, GerritMessageProvider.class })
public class ParameterExpanderTest {

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
        AbstractProject project = mock(AbstractProject.class);
        when(project.getTrigger(GerritTrigger.class)).thenReturn(trigger);

        AbstractBuild r = Setup.createBuild(project, taskListener, Setup.createEnvVars());

        PatchsetCreated event = Setup.createPatchsetCreated();
        BuildsStartedStats stats = Setup.createBuildStartedStats(event);
        IGerritHudsonTriggerConfig config = Setup.createConfig();

        Hudson hudson = PowerMockito.mock(Hudson.class);
        when(hudson.getRootUrl()).thenReturn("http://localhost/");

        PowerMockito.mockStatic(GerritMessageProvider.class);
        List<GerritMessageProvider> messageProviderExtensionList = new LinkedList<GerritMessageProvider>();
        messageProviderExtensionList.add(new GerritMessageProviderExtension());
        messageProviderExtensionList.add(new GerritMessageProviderExtensionReturnNull());
        when(GerritMessageProvider.all()).thenReturn(messageProviderExtensionList);

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
        assertTrue("Missing CUSTOM_MESSAGE", result.indexOf("CUSTOM_MESSAGE_BUILD_STARTED") >= 0);
    }

    /**
     * test.
     */
    @Test
    public void testGetMinimumVerifiedValue() {
        IGerritHudsonTriggerConfig config = Setup.createConfig();

        ParameterExpander instance = new ParameterExpander(config);
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

        ParameterExpander instance = new ParameterExpander(config);
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
        int expResult = -1;
        int result = instance.getMinimumCodeReviewValue(memoryImprint, true);
        assertEquals(expResult, result);

        // Otherwise, we should use NOT_BUILT.
        expResult = -4;
        result = instance.getMinimumCodeReviewValue(memoryImprint, false);
        assertEquals(expResult, result);
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

        ParameterExpander instance = new ParameterExpander(config);
        MemoryImprint memoryImprint = mock(MemoryImprint.class);
        MemoryImprint.Entry[] entries = new MemoryImprint.Entry[3];

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(1);
        entries[0] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.SUCCESS);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildUnstableCodeReviewValue()).thenReturn(-1);
        SkipVote skipVote = new SkipVote(false, false, true, false);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        entries[1] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.UNSTABLE);

        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(2);
        entries[2] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.SUCCESS);


        when(memoryImprint.getEntries()).thenReturn(entries);

        int expResult = 1;
        int result = instance.getMinimumCodeReviewValue(memoryImprint, true);
        assertEquals(expResult, result);
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

        ParameterExpander instance = new ParameterExpander(config);
        MemoryImprint memoryImprint = mock(MemoryImprint.class);
        MemoryImprint.Entry[] entries = new MemoryImprint.Entry[1];

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(1);
        SkipVote skipVote = new SkipVote(true, false, false, false);
        when(trigger.getSkipVote()).thenReturn(skipVote);
        entries[0] = Setup.createAndSetupMemoryImprintEntry(trigger, Result.SUCCESS);

        when(memoryImprint.getEntries()).thenReturn(entries);

        int expResult = 0;
        int result = instance.getMinimumCodeReviewValue(memoryImprint, true);
        assertEquals(expResult, result);
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
        tryGetBuildCompletedCommandSuccessfulEvent(customUrl, expectedBuildsStats, Setup.createChangeAbandoned(), 0, 0);
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
        tryGetBuildCompletedCommandSuccessfulEvent(customUrl, expectedBuildsStats, Setup.createChangeMerged(), 0, 0);
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
        tryGetBuildCompletedCommandSuccessfulEvent(customUrl, expectedBuildsStats, Setup.createChangeRestored(), 0, 0);
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
                                                           GerritTriggeredEvent event, int expectedVerifiedVote,
                                                           int expectedCodeReviewVote)
            throws IOException, InterruptedException {

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

        EnvVars env = Setup.createEnvVars();
        AbstractBuild r = Setup.createBuild(project, taskListener, env);
        env.put("BUILD_URL", hudson.getRootUrl() + r.getUrl());

        when(r.getResult()).thenReturn(Result.SUCCESS);

        MemoryImprint memoryImprint = mock(MemoryImprint.class);
        when(memoryImprint.getEvent()).thenReturn(event);

        when(memoryImprint.wereAllBuildsSuccessful()).thenReturn(true);
        when(memoryImprint.wereAnyBuildsFailed()).thenReturn(false);
        when(memoryImprint.wereAnyBuildsUnstable()).thenReturn(false);

        MemoryImprint.Entry[] entries = { Setup.createImprintEntry(project, r) };
        when(memoryImprint.getEntries()).thenReturn(entries);

        assertThat("Event should be a ChangeBasedEvent", event, instanceOf(ChangeBasedEvent.class));
        final String expectedRefSpec = StringUtil.makeRefSpec((ChangeBasedEvent)event);

        PowerMockito.mockStatic(GerritMessageProvider.class);
        List<GerritMessageProvider> messageProviderExtensionList = new LinkedList<GerritMessageProvider>();
        messageProviderExtensionList.add(new GerritMessageProviderExtension());
        messageProviderExtensionList.add(new GerritMessageProviderExtensionReturnNull());
        when(GerritMessageProvider.all()).thenReturn(messageProviderExtensionList);

        ParameterExpander instance = new ParameterExpander(config, hudson);

        String result = instance.getBuildCompletedCommand(memoryImprint, taskListener);
        System.out.println("Result: " + result);

        assertTrue("Missing OK message", result.indexOf(" MSG='Your friendly butler says OK.") >= 0);
        assertTrue("Missing BS", result.indexOf(" BS=" + expectedBuildsStats) >= 0);
        assertTrue("Missing CHANGE_ID", result.indexOf("CHANGE_ID=Iddaaddaa123456789") >= 0);
        assertTrue("Missing PATCHSET", result.indexOf("PATCHSET=1") >= 0);
        assertTrue("Missing VERIFIED", result.indexOf("VERIFIED=" + expectedVerifiedVote) >= 0);
        assertTrue("Missing CODEREVIEW", result.indexOf("CODEREVIEW=" + expectedCodeReviewVote) >= 0);
        assertTrue("Missing REFSPEC", result.indexOf("REFSPEC=" + expectedRefSpec) >= 0);
        assertTrue("Missing ENV_BRANCH", result.indexOf("ENV_BRANCH=branch") >= 0);
        assertTrue("Missing ENV_CHANGE", result.indexOf("ENV_CHANGE=1000") >= 0);
        assertTrue("Missing ENV_REFSPEC", result.indexOf("ENV_REFSPEC=" + expectedRefSpec) >= 0);
        assertTrue("Missing ENV_CHANGEURL", result.indexOf("ENV_CHANGEURL=http://gerrit/1000") >= 0);
        assertTrue("Missing CUSTOM_MESSAGES", result.indexOf("CUSTOM_MESSAGE_BUILD_COMPLETED") >= 0);
    }


    /**
     * Test.
     * @throws Exception if so
     */
    @Test
    public void testBuildStatsWithFailureMessage() throws Exception {
        tryBuildStatsFailureCommand("This was a failure message. ",
                "\n\nhttp://localhost/test/ : FAILURE <<<\nThis was a failure message.\n>>>");
        tryBuildStatsFailureCommand(null, "\n\nhttp://localhost/test/ : FAILURE");
        tryBuildStatsFailureCommand("", "\n\nhttp://localhost/test/ : FAILURE");
    }

    /**
     * Sub test for {@link #testBuildStatsWithFailureMessage()}.
     *
     * @param failureMessage Build failure message
     * @param expectedBuildStats Expected build stats string
     * @throws Exception if so
     */
    public void tryBuildStatsFailureCommand(String failureMessage, String expectedBuildStats) throws Exception {
        IGerritHudsonTriggerConfig config = Setup.createConfig();

        Hudson hudson = PowerMockito.mock(Hudson.class);
        when(hudson.getRootUrl()).thenReturn("http://localhost/");

        TaskListener taskListener = mock(TaskListener.class);

        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulVerifiedValue()).thenReturn(null);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(32);
        AbstractProject project = mock(AbstractProject.class);
        when(project.getTrigger(GerritTrigger.class)).thenReturn(trigger);

        EnvVars env = Setup.createEnvVars();
        AbstractBuild r = Setup.createBuild(project, taskListener, env);
        env.put("BUILD_URL", hudson.getRootUrl() + r.getUrl());

        when(r.getResult()).thenReturn(Result.FAILURE);

        PatchsetCreated event = Setup.createPatchsetCreated();

        MemoryImprint memoryImprint = mock(MemoryImprint.class);
        when(memoryImprint.getEvent()).thenReturn(event);

        when(memoryImprint.wereAllBuildsSuccessful()).thenReturn(true);
        when(memoryImprint.wereAnyBuildsFailed()).thenReturn(false);
        when(memoryImprint.wereAnyBuildsUnstable()).thenReturn(false);

        MemoryImprint.Entry[] entries = { Setup.createImprintEntry(project, r) };

        if (failureMessage != null && !failureMessage.isEmpty()) {
            when(entries[0].getMessage()).thenReturn(failureMessage.trim());
        } else {
            when(entries[0].getMessage()).thenReturn(null);
        }

        when(memoryImprint.getEntries()).thenReturn(entries);

        PowerMockito.mockStatic(GerritMessageProvider.class);
        List<GerritMessageProvider> messageProviderExtensionList = new LinkedList<GerritMessageProvider>();
        messageProviderExtensionList.add(new GerritMessageProviderExtension());
        messageProviderExtensionList.add(new GerritMessageProviderExtensionReturnNull());
        when(GerritMessageProvider.all()).thenReturn(messageProviderExtensionList);

        ParameterExpander instance = new ParameterExpander(config, hudson);

        String result = instance.getBuildCompletedCommand(memoryImprint, taskListener);
        System.out.println("Result: " + result);

        assertTrue("Missing BS", result.indexOf(" BS=" + expectedBuildStats) >= 0);
    }

    /**
     * Extension implementing GerritMessageProvider to provide a custom build message.
     */
    public static class GerritMessageProviderExtension extends GerritMessageProvider {
        private static final long serialVersionUID = -7565217057927807166L;

        @Override
        public String getBuildStartedMessage(AbstractBuild build) {
            return "CUSTOM_MESSAGE_BUILD_STARTED";
        }

        @Override
        public String getBuildCompletedMessage(AbstractBuild build) {
            return "CUSTOM_MESSAGE_BUILD_COMPLETED";
        }
    }

    /**
     * Extension implementing GerritMessageProvider to provide a custom build message (null).
     */
    public static class GerritMessageProviderExtensionReturnNull extends GerritMessageProvider {
        private static final long serialVersionUID = -3479376646924947609L;

        @Override
        public String getBuildStartedMessage(AbstractBuild build) {
            return null;
        }

        @Override
        public String getBuildCompletedMessage(AbstractBuild build) {
            return null;
        }
    }
}
