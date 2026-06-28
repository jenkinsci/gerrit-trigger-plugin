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
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;

import hudson.model.Result;
import org.junit.jupiter.api.AfterEach;

import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

//CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: Mocks tests.

/**
 * Tests a bunch of different scenarios.
 * For {@link ParameterExpander#getCodeReviewValue(hudson.model.Result, GerritTrigger)}
 * and {@link ParameterExpander#getVerifiedValue(hudson.model.Result, GerritTrigger)}
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
class ParameterExpanderParameterizedTest {

    private MockedStatic<Jenkins> jenkinsMockedStatic;

    /**
     * Mock Jenkins.
     */
    @BeforeEach
    void setup() {
        jenkinsMockedStatic = mockStatic(Jenkins.class);
        Jenkins jenkins = mock(Jenkins.class);
        jenkinsMockedStatic.when(Jenkins::get).thenReturn(jenkins);
    }

    @AfterEach
    void tearDown() {
        jenkinsMockedStatic.close();
    }

    /**
     * test.
     *
     * @param config
     * @param result
     * @param trigger
     * @param expectedCodeReview
     * @param expectedVerified
     */
    @ParameterizedTest
    @MethodSource("parameters")
    void testGetVerifiedValue(IGerritHudsonTriggerConfig config, Result result, GerritTrigger trigger,
                              int expectedCodeReview, int expectedVerified) {
        ParameterExpander instance = new ParameterExpander(config);
        assertEquals(Integer.valueOf(expectedVerified),
                instance.getVerifiedValue(result, trigger));
    }

    /**
     * test.
     *
     * @param config
     * @param result
     * @param trigger
     * @param expectedCodeReview
     * @param expectedVerified
     */
    @ParameterizedTest
    @MethodSource("parameters")
    void testGetCodeReviewValue(IGerritHudsonTriggerConfig config, Result result, GerritTrigger trigger,
                                int expectedCodeReview, int expectedVerified) {
        ParameterExpander instance = new ParameterExpander(config);
        assertEquals(Integer.valueOf(expectedCodeReview),
                instance.getCodeReviewValue(result, trigger));
    }

    /**
     * Parameters.
     * @return parameters
     */
    static List<Arguments> parameters() {
        List<Arguments> list = new LinkedList<>();

        IGerritHudsonTriggerConfig config = Setup.createConfig();

        //SUCCESS, FAILURE, ABORTED, UNSTABLE, other
        //not overridden, overridden

        //SUCCESS Not overridden
        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(null);
        when(trigger.getGerritBuildSuccessfulVerifiedValue()).thenReturn(null);
        list.add(Arguments.of(config, Result.SUCCESS, trigger, 4, 3));
        //SUCCESS overridden
        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(21);
        when(trigger.getGerritBuildSuccessfulVerifiedValue()).thenReturn(22);
        list.add(Arguments.of(config, Result.SUCCESS, trigger, 21, 22));
        //FAILURE Not overridden
        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildFailedCodeReviewValue()).thenReturn(null);
        when(trigger.getGerritBuildFailedVerifiedValue()).thenReturn(null);
        list.add(Arguments.of(config, Result.FAILURE, trigger, -2, -1));
        //FAILURE overridden
        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildFailedCodeReviewValue()).thenReturn(31);
        when(trigger.getGerritBuildFailedVerifiedValue()).thenReturn(32);
        list.add(Arguments.of(config, Result.FAILURE, trigger, 31, 32));
        //UNSTABLE overridden
        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildUnstableCodeReviewValue()).thenReturn(-21);
        when(trigger.getGerritBuildUnstableVerifiedValue()).thenReturn(-22);
        list.add(Arguments.of(config, Result.UNSTABLE, trigger, -21, -22));
        //OTHER Not overridden
        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildNotBuiltCodeReviewValue()).thenReturn(null);
        when(trigger.getGerritBuildNotBuiltVerifiedValue()).thenReturn(null);
        list.add(Arguments.of(config, Result.NOT_BUILT, trigger, -6, -5));
        //OTHER overridden
        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildNotBuiltCodeReviewValue()).thenReturn(-51);
        when(trigger.getGerritBuildNotBuiltVerifiedValue()).thenReturn(-52);
        list.add(Arguments.of(config, Result.NOT_BUILT, trigger, -51, -52));
        //ABORTED Not overridden
        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildAbortedCodeReviewValue()).thenReturn(null);
        when(trigger.getGerritBuildAbortedVerifiedValue()).thenReturn(null);
        list.add(Arguments.of(config, Result.ABORTED, trigger, 3, -2));
        //ABORTED overridden
        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildAbortedCodeReviewValue()).thenReturn(41);
        when(trigger.getGerritBuildAbortedVerifiedValue()).thenReturn(42);
        list.add(Arguments.of(config, Result.ABORTED, trigger, 41, 42));
        //UNSTABLE Not overridden
        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildUnstableCodeReviewValue()).thenReturn(null);
        when(trigger.getGerritBuildUnstableVerifiedValue()).thenReturn(null);
        list.add(Arguments.of(config, Result.UNSTABLE, trigger, -4, -3));

        return list;
    }
}
