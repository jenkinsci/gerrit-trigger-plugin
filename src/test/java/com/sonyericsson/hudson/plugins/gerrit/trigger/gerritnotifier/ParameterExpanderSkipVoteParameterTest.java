/*
 * The MIT License
 *
 * Copyright 2012 Sony Mobile Communications AB. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier;

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;

import hudson.model.Result;

import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: TestData.

/**
 * {@link Parameterized} Tests for {@link ParameterExpander} with
 * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger#getSkipVote()} configured.
 *
 * @author Robert Sandell &lt;robert.sandell@sonymobile.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(Parameterized.class)
@PrepareForTest(Jenkins.class)
public class ParameterExpanderSkipVoteParameterTest {

    private TestParameter parameter;

    /**
     * Parametrized test Constructor.
     *
     * @param parameter the test parameters.
     */
    public ParameterExpanderSkipVoteParameterTest(TestParameter parameter) {
        this.parameter = parameter;
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
     * Tests that {@link ParameterExpander#getMinimumCodeReviewValue(BuildMemory.MemoryImprint, boolean)}
     * returns {@link TestParameter#expectedCodeReview}.
     */
    @Test
    public void testCodeReview() {
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        ParameterExpander instance = new ParameterExpander(config);
        Integer result = instance.getMinimumCodeReviewValue(parameter.memoryImprint, true);
        if (parameter.expectedCodeReview == null) {
            assertNull(result);
        } else {
            assertEquals(Integer.valueOf(parameter.expectedCodeReview), result);
        }
    }

    /**
     * Tests that {@link ParameterExpander#getMinimumVerifiedValue(BuildMemory.MemoryImprint, boolean)}
     * returns {@link TestParameter#expectedVerified}.
     */
    @Test
    public void testVerified() {
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        ParameterExpander instance = new ParameterExpander(config);
        Integer result = instance.getMinimumVerifiedValue(parameter.memoryImprint, true);
        if (parameter.expectedVerified == null) {
            assertNull(result);
        } else {
            assertEquals(Integer.valueOf(parameter.expectedVerified), result);
        }
    }

    /**
     * Returns the parameters for the JUnit Runner to pass to the Constructor.
     * @return parameters.
     */
    @Parameterized.Parameters
    public static Collection getParameters() {
        List<TestParameter[]> parameters = new LinkedList<TestParameter[]>();

        parameters.add(createParameter(+1, +2,
                Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, false),
                Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, false),
                Setup.createAndSetupMemoryImprintEntry(Result.UNSTABLE, -1, -2, true)
                ));
        parameters.add(createParameter(+1, +2,
                Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, false),
                Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, false),
                Setup.createAndSetupMemoryImprintEntry(Result.FAILURE, -1, -2, true)
                ));
        parameters.add(createParameter(+1, +2,
                Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, false),
                Setup.createAndSetupMemoryImprintEntry(Result.UNSTABLE, -1, -2, true),
                Setup.createAndSetupMemoryImprintEntry(Result.FAILURE, -1, -2, true)
                ));
        parameters.add(createParameter(-1, -2,
                Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, true),
                Setup.createAndSetupMemoryImprintEntry(Result.UNSTABLE, -1, -2, false),
                Setup.createAndSetupMemoryImprintEntry(Result.UNSTABLE, -1, -2, false)
                ));
        parameters.add(createParameter(-1, -2,
                Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, true),
                Setup.createAndSetupMemoryImprintEntry(Result.UNSTABLE, -1, -2, false),
                Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, false)
                ));
        parameters.add(createParameter(+1, +2,
                Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, false)
                ));
        parameters.add(createParameter(-1, -2,
                Setup.createAndSetupMemoryImprintEntry(Result.UNSTABLE, -1, -2, false)
                ));
        parameters.add(createParameter(-5, -6,
                Setup.createAndSetupMemoryImprintEntry(Result.ABORTED, -5, -6, false)
        ));
        parameters.add(createParameter(null, null,
                Setup.createAndSetupMemoryImprintEntry(Result.ABORTED, -5, -6, true)
        ));
        parameters.add(createParameter(null, null,
                Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, true)
                ));
        parameters.add(createParameter(null, null,
                Setup.createAndSetupMemoryImprintEntry(Result.UNSTABLE, -1, -2, true)
                ));
        parameters.add(createParameter(null, null,
                Setup.createAndSetupMemoryImprintEntry(Result.UNSTABLE, -1, -2, true),
                Setup.createAndSetupMemoryImprintEntry(Result.UNSTABLE, -1, -2, true)
                ));
        parameters.add(createParameter(null, null,
                Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, true),
                Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, true)
                ));

        return parameters;
    }

    /**
     * Creates one parameter (in a one size array).
     * So that it is simple to add to the list of parameters to return from {@link #getParameters()}.
     *
     * @param expectedCodeReview the expected code review vote
     * @param expectedVerified the expected verified vote
     * @param entries the build memory entries
     * @return the created test parameter.
     */
    private static TestParameter[] createParameter(Integer expectedCodeReview, Integer expectedVerified,
                                                   BuildMemory.MemoryImprint.Entry... entries) {
        return new TestParameter[]{new TestParameter(expectedCodeReview, expectedVerified, entries)};
    }


    /**
     * Parameters for the tests.
     */
    public static class TestParameter {

        BuildMemory.MemoryImprint memoryImprint;
        private Integer expectedCodeReview;
        private Integer expectedVerified;

        /**
         * Convenience constructor.
         *
         * @param expectedCodeReview the expected code review vote
         * @param expectedVerified the expected verified vote.
         * @param entries the build memory entries.
         */
        public TestParameter(Integer expectedCodeReview, Integer expectedVerified,
                BuildMemory.MemoryImprint.Entry... entries) {
            this.expectedCodeReview = expectedCodeReview;
            this.expectedVerified = expectedVerified;
            memoryImprint = mock(BuildMemory.MemoryImprint.class);
            when(memoryImprint.getEntries()).thenReturn(entries);
        }
    }
}
