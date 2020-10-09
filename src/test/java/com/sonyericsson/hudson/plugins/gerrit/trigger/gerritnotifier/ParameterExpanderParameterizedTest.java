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

import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: Mocks tests.

/**
 * Tests a bunch of different scenarios.
 * For {@link ParameterExpander#getCodeReviewValue(hudson.model.Result, GerritTrigger)}
 * and {@link ParameterExpander#getVerifiedValue(hudson.model.Result, GerritTrigger)}
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(Parameterized.class)
@PrepareForTest(Jenkins.class)
public class ParameterExpanderParameterizedTest {

    private TestParameters parameters;

    /**
     * Constructor.
     * @param parameters parameters.
     */
    public ParameterExpanderParameterizedTest(TestParameters parameters) {
        this.parameters = parameters;
    }

    /**
     * Mock Jenkins.
     */
    @Before
    public void setup() {
        PowerMockito.mockStatic(Jenkins.class);
        Jenkins jenkins = PowerMockito.mock(Jenkins.class);
        PowerMockito.when(Jenkins.getInstance()).thenReturn(jenkins);
    }

    /**
     * test.
     */
    @Test
    public void testGetVerifiedValue() {
        ParameterExpander instance = new ParameterExpander(parameters.config);
        assertEquals(Integer.valueOf(parameters.expectedVerified),
                instance.getVerifiedValue(parameters.result, parameters.trigger));
    }

    /**
     * test.
     */
    @Test
    public void testGetCodeReviewValue() {
        ParameterExpander instance = new ParameterExpander(parameters.config);
        assertEquals(Integer.valueOf(parameters.expectedCodeReview),
                instance.getCodeReviewValue(parameters.result, parameters.trigger));
    }

    /**
     * Parameters.
     * @return parameters
     */
    @Parameters
    public static Collection getParameters() {
        List<TestParameters[]> list = new LinkedList<TestParameters[]>();

        IGerritHudsonTriggerConfig config = Setup.createConfig();

        //SUCCESS, FAILURE, ABORTED, UNSTABLE, other
        //not overridden, overridden

        //SUCCESS Not overridden
        GerritTrigger trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(null);
        when(trigger.getGerritBuildSuccessfulVerifiedValue()).thenReturn(null);
        list.add(new TestParameters[]{new TestParameters(config, Result.SUCCESS, trigger, 4, 3)});
        //SUCCESS overridden
        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(21);
        when(trigger.getGerritBuildSuccessfulVerifiedValue()).thenReturn(22);
        list.add(new TestParameters[]{new TestParameters(config, Result.SUCCESS, trigger, 21, 22)});
        //FAILURE Not overridden
        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildFailedCodeReviewValue()).thenReturn(null);
        when(trigger.getGerritBuildFailedVerifiedValue()).thenReturn(null);
        list.add(new TestParameters[]{new TestParameters(config, Result.FAILURE, trigger, -2, -1)});
        //FAILURE overridden
        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildFailedCodeReviewValue()).thenReturn(31);
        when(trigger.getGerritBuildFailedVerifiedValue()).thenReturn(32);
        list.add(new TestParameters[]{new TestParameters(config, Result.FAILURE, trigger, 31, 32)});
        //UNSTABLE overridden
        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildUnstableCodeReviewValue()).thenReturn(-21);
        when(trigger.getGerritBuildUnstableVerifiedValue()).thenReturn(-22);
        list.add(new TestParameters[]{new TestParameters(config, Result.UNSTABLE, trigger, -21, -22)});
        //OTHER Not overridden
        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildNotBuiltCodeReviewValue()).thenReturn(null);
        when(trigger.getGerritBuildNotBuiltVerifiedValue()).thenReturn(null);
        list.add(new TestParameters[]{new TestParameters(config, Result.NOT_BUILT, trigger, -6, -5)});
        //OTHER overridden
        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildNotBuiltCodeReviewValue()).thenReturn(-51);
        when(trigger.getGerritBuildNotBuiltVerifiedValue()).thenReturn(-52);
        list.add(new TestParameters[]{new TestParameters(config, Result.NOT_BUILT, trigger, -51, -52)});
        //ABORTED Not overridden
        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildAbortedCodeReviewValue()).thenReturn(null);
        when(trigger.getGerritBuildAbortedVerifiedValue()).thenReturn(null);
        list.add(new TestParameters[]{new TestParameters(config, Result.ABORTED, trigger, 3, -2)});
        //ABORTED overridden
        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildAbortedCodeReviewValue()).thenReturn(41);
        when(trigger.getGerritBuildAbortedVerifiedValue()).thenReturn(42);
        list.add(new TestParameters[]{new TestParameters(config, Result.ABORTED, trigger, 41, 42)});
        //UNSTABLE Not overridden
        trigger = mock(GerritTrigger.class);
        when(trigger.getGerritBuildUnstableCodeReviewValue()).thenReturn(null);
        when(trigger.getGerritBuildUnstableVerifiedValue()).thenReturn(null);
        list.add(new TestParameters[]{new TestParameters(config, Result.UNSTABLE, trigger, -4, -3)});

        return list;
    }

    /**
     * Parameters for the test.
     */
    public static class TestParameters {
        IGerritHudsonTriggerConfig config;
        Result result;
        GerritTrigger trigger;
        int expectedCodeReview;
        int expectedVerified;

        /**
         * Constructor.
         * @param config config
         * @param result result
         * @param trigger trigger
         * @param expectedCodeReview expectedCodeReview
         * @param expectedVerified expectedVerified
         */
        public TestParameters(IGerritHudsonTriggerConfig config, Result result, GerritTrigger trigger,
                int expectedCodeReview, int expectedVerified) {
            this.config = config;
            this.result = result;
            this.trigger = trigger;
            this.expectedCodeReview = expectedCodeReview;
            this.expectedVerified = expectedVerified;
        }

        /**
         * Constructor.
         */
        public TestParameters() {
        }
    }
}
