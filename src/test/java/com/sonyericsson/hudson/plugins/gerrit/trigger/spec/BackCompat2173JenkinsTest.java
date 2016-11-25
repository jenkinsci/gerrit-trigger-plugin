/*
 * The MIT License
 *
 * Copyright 2015 CloudBees Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.spec;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerParameters;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginChangeRestoredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginPatchsetCreatedEvent;
import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.powermock.reflect.Whitebox;

import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.Is.isA;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 */
public class BackCompat2173JenkinsTest {

    /**
     * The rule which we follow.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Tests a job called AllOff that has most advanced boolean settings set to false.
     * Specifically interested in {@link GerritTrigger#getNameAndEmailParameterMode()}
     * and {@link GerritTrigger#getCommitMessageParameterMode()}.
     *
     * @throws Exception if so
     */
    @Test
    @LocalData
    public void testAllOff() throws Exception {
        FreeStyleProject job = j.jenkins.getItemByFullName("AllOff", FreeStyleProject.class);
        GerritTrigger trigger = GerritTrigger.getTrigger(job);
        verifyAllOff(trigger);
        job = j.configRoundtrip(job);
        trigger = GerritTrigger.getTrigger(job);
        verifyAllOff(trigger);
    }

    /**
     * Assertions for {@link #testAllOff()}.
     *
     * @param trigger the trigger instance to test.
     */
    private void verifyAllOff(GerritTrigger trigger) {
        assertNotNull(trigger);
        assertNotNull(trigger.getSkipVote());
        assertFalse("Skip not built", trigger.getSkipVote().isOnNotBuilt());
        assertNotNull(trigger.getGerritBuildFailedCodeReviewValue());
        assertEquals("Build Failed Code Review value", Integer.valueOf(0),
                trigger.getGerritBuildFailedCodeReviewValue());
        assertFalse("Silent Mode", trigger.isSilentMode());
        assertEquals("Notification level", "ALL", trigger.getNotificationLevel());
        assertFalse("Silent start", trigger.isSilentStartMode());
        assertFalse("Escape quotes", trigger.isEscapeQuotes());
        assertFalse("No name and email", trigger.isNoNameAndEmailParameters());
        assertSame("Name and email mode == PLAIN", GerritTriggerParameters.ParameterMode.PLAIN,
                trigger.getNameAndEmailParameterMode());
        assertFalse("Readable message", trigger.isReadableMessage());
        assertSame("Commit message mode == BASE64", GerritTriggerParameters.ParameterMode.BASE64,
                trigger.getCommitMessageParameterMode());
        //Setting introduced after the version under test, so it should have the default value
        assertSame("Change subject mode == PLAIN", GerritTriggerParameters.ParameterMode.PLAIN,
                trigger.getChangeSubjectParameterMode());
        assertSame("Comment text mode == PLAIN", GerritTriggerParameters.ParameterMode.PLAIN,
                trigger.getCommentTextParameterMode());
        assertEquals(GerritServer.ANY_SERVER, trigger.getServerName());

        assertThat(trigger.getGerritProjects(), hasItem(
                allOf(
                        isA(GerritProject.class),
                        hasProperty("compareType", is(CompareType.ANT)),
                        hasProperty("pattern", equalTo("**")),
                        hasProperty("branches", hasItem(
                                allOf(
                                        isA(Branch.class),
                                        hasProperty("compareType", is(CompareType.ANT)),
                                        hasProperty("pattern", equalTo("**"))
                                )
                        ))
                )
        ));

        assertThat(trigger.getTriggerOnEvents(), hasItem(
                allOf(
                        isA(PluginPatchsetCreatedEvent.class),
                        hasProperty("excludeDrafts", is(false)),
                        hasProperty("excludeTrivialRebase", is(false)),
                        hasProperty("excludeNoCodeChange", is(false))
                )
        ));

        assertNotNull(Whitebox.getInternalState(trigger, "triggerInformationAction"));
    }

    /**
     * Tests a job called AllOn that has a couple of atypical advanced settings set to true.
     * Specifically interested in {@link GerritTrigger#getNameAndEmailParameterMode()}
     * and {@link GerritTrigger#getCommitMessageParameterMode()}.
     *
     * @throws Exception if so
     */
    @Test
    @LocalData
    public void testAllOn() throws Exception {
        FreeStyleProject job = j.jenkins.getItemByFullName("AllOn", FreeStyleProject.class);
        GerritTrigger trigger = GerritTrigger.getTrigger(job);
        verifyAllOn(trigger);
        job = j.configRoundtrip(job);
        trigger = GerritTrigger.getTrigger(job);
        verifyAllOn(trigger);
    }

    /**
     * All the asserts for {@link #testAllOn()}.
     *
     * @param trigger the trigger instance to test.
     */
    private void verifyAllOn(GerritTrigger trigger) {
        assertNotNull(trigger);
        assertNotNull(trigger.getSkipVote());
        assertTrue("Skip not built", trigger.getSkipVote().isOnNotBuilt());
        assertNotNull(trigger.getGerritBuildNotBuiltCodeReviewValue());
        assertEquals("Build Failed Code Review value", Integer.valueOf(0),
                trigger.getGerritBuildNotBuiltCodeReviewValue());
        assertFalse("Silent Mode", trigger.isSilentMode());
        assertEquals("Notification level", "OWNER", trigger.getNotificationLevel());
        assertTrue("Silent start", trigger.isSilentStartMode());
        assertTrue("Escape quotes", trigger.isEscapeQuotes());
        assertTrue("No name and email", trigger.isNoNameAndEmailParameters());
        assertSame("Name and email mode == NONE", GerritTriggerParameters.ParameterMode.NONE,
                trigger.getNameAndEmailParameterMode());
        assertTrue("Readable message", trigger.isReadableMessage());
        assertSame("Commit message mode == PLAIN", GerritTriggerParameters.ParameterMode.PLAIN,
                trigger.getCommitMessageParameterMode());
        //Setting introduced after the version under test, so it should have the default value
        assertSame("Change subject mode == PLAIN", GerritTriggerParameters.ParameterMode.PLAIN,
                trigger.getChangeSubjectParameterMode());
        assertEquals(GerritServer.ANY_SERVER, trigger.getServerName());

        assertThat(trigger.getGerritProjects(), hasItem(
                allOf(
                        isA(GerritProject.class),
                        hasProperty("compareType", is(CompareType.ANT)),
                        hasProperty("pattern", equalTo("something/projects/*")),
                        hasProperty("branches", hasItem(
                                allOf(
                                        isA(Branch.class),
                                        hasProperty("compareType", is(CompareType.ANT)),
                                        hasProperty("pattern", equalTo("**"))
                                )
                        ))
                )
        ));

        assertThat(trigger.getTriggerOnEvents(), hasItem(
                allOf(
                        isA(PluginPatchsetCreatedEvent.class),
                        hasProperty("excludeDrafts", is(false)),
                        hasProperty("excludeTrivialRebase", is(true)),
                        hasProperty("excludeNoCodeChange", is(false))
                )
        ));
        assertThat(trigger.getTriggerOnEvents(), hasItem(isA(PluginChangeRestoredEvent.class)));

        assertNotNull(Whitebox.getInternalState(trigger, "triggerInformationAction"));
    }
}
