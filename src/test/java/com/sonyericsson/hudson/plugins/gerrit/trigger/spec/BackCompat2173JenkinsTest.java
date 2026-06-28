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
import com.sonyericsson.jenkins.plugins.bfa.test.utils.Whitebox;
import hudson.model.FreeStyleProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 */
@WithJenkins
class BackCompat2173JenkinsTest {

    /**
     * The rule which we follow.
     */
    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Tests a job called AllOff that has most advanced boolean settings set to false.
     * Specifically interested in {@link GerritTrigger#getNameAndEmailParameterMode()}
     * and {@link GerritTrigger#getCommitMessageParameterMode()}.
     *
     * @throws Exception if so
     */
    @Test
    @LocalData
    void testAllOff() throws Exception {
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
        assertFalse(trigger.getSkipVote().isOnNotBuilt(), "Skip not built");
        assertNotNull(trigger.getGerritBuildFailedCodeReviewValue());
        assertEquals(Integer.valueOf(0), trigger.getGerritBuildFailedCodeReviewValue(),
                "Build Failed Code Review value");
        assertFalse(trigger.isSilentMode(), "Silent Mode");
        assertEquals("ALL", trigger.getNotificationLevel(), "Notification level");
        assertFalse(trigger.isSilentStartMode(), "Silent start");
        assertFalse(trigger.isEscapeQuotes(), "Escape quotes");
        assertFalse(trigger.isNoNameAndEmailParameters(), "No name and email");
        assertSame(GerritTriggerParameters.ParameterMode.PLAIN, trigger.getNameAndEmailParameterMode(),
                "Name and email mode == PLAIN");
        assertFalse(trigger.isReadableMessage(), "Readable message");
        assertSame(GerritTriggerParameters.ParameterMode.BASE64, trigger.getCommitMessageParameterMode(),
                "Commit message mode == BASE64");
        //Setting introduced after the version under test, so it should have the default value
        assertSame(GerritTriggerParameters.ParameterMode.PLAIN, trigger.getChangeSubjectParameterMode(),
                "Change subject mode == PLAIN");
        assertSame(GerritTriggerParameters.ParameterMode.PLAIN, trigger.getCommentTextParameterMode(),
                "Comment text mode == PLAIN");
        assertEquals(GerritServer.ANY_SERVER, trigger.getServerName());

        assertThat(trigger.getGerritProjects(), hasItem(
                allOf(
                        instanceOf(GerritProject.class),
                        hasProperty("compareType", is(CompareType.ANT)),
                        hasProperty("pattern", equalTo("**")),
                        hasProperty("branches", hasItem(
                                allOf(
                                        instanceOf(Branch.class),
                                        hasProperty("compareType", is(CompareType.ANT)),
                                        hasProperty("pattern", equalTo("**"))
                                )
                        ))
                )
        ));

        assertThat(trigger.getTriggerOnEvents(), hasItem(
                allOf(
                        instanceOf(PluginPatchsetCreatedEvent.class),
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
    void testAllOn() throws Exception {
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
        assertTrue(trigger.getSkipVote().isOnNotBuilt(), "Skip not built");
        assertNotNull(trigger.getGerritBuildNotBuiltCodeReviewValue());
        assertEquals(Integer.valueOf(0), trigger.getGerritBuildNotBuiltCodeReviewValue(),
                "Build Failed Code Review value");
        assertFalse(trigger.isSilentMode(), "Silent Mode");
        assertEquals("OWNER", trigger.getNotificationLevel(), "Notification level");
        assertTrue(trigger.isSilentStartMode(), "Silent start");
        assertTrue(trigger.isEscapeQuotes(), "Escape quotes");
        assertTrue(trigger.isNoNameAndEmailParameters(), "No name and email");
        assertSame(GerritTriggerParameters.ParameterMode.NONE, trigger.getNameAndEmailParameterMode(),
                "Name and email mode == NONE");
        assertTrue(trigger.isReadableMessage(), "Readable message");
        assertSame(GerritTriggerParameters.ParameterMode.PLAIN, trigger.getCommitMessageParameterMode(),
                "Commit message mode == PLAIN");
        //Setting introduced after the version under test, so it should have the default value
        assertSame(GerritTriggerParameters.ParameterMode.PLAIN, trigger.getChangeSubjectParameterMode(),
                "Change subject mode == PLAIN");
        assertEquals(GerritServer.ANY_SERVER, trigger.getServerName());

        assertThat(trigger.getGerritProjects(), hasItem(
                allOf(
                        instanceOf(GerritProject.class),
                        hasProperty("compareType", is(CompareType.ANT)),
                        hasProperty("pattern", equalTo("something/projects/*")),
                        hasProperty("branches", hasItem(
                                allOf(
                                        instanceOf(Branch.class),
                                        hasProperty("compareType", is(CompareType.ANT)),
                                        hasProperty("pattern", equalTo("**"))
                                )
                        ))
                )
        ));

        assertThat(trigger.getTriggerOnEvents(), hasItem(
                allOf(
                        instanceOf(PluginPatchsetCreatedEvent.class),
                        hasProperty("excludeDrafts", is(false)),
                        hasProperty("excludeTrivialRebase", is(true)),
                        hasProperty("excludeNoCodeChange", is(false))
                )
        ));
        assertThat(trigger.getTriggerOnEvents(), hasItem(instanceOf(PluginChangeRestoredEvent.class)));

        assertNotNull(Whitebox.getInternalState(trigger, "triggerInformationAction"));
    }
}
