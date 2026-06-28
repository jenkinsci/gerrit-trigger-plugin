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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

//CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: TestData.

/**
 * {@link ParameterizedTest} Tests for {@link ParameterExpander} with
 * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger#getSkipVote()} configured.
 *
 * @author Robert Sandell &lt;robert.sandell@sonymobile.com&gt;
 */
class ParameterExpanderSkipVoteParameterTest {

    private MockedStatic<Jenkins> jenkinsMockedStatic;
    private final BuildMemory.MemoryImprint memoryImprint = mock(BuildMemory.MemoryImprint.class);

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
     * Tests that {@link ParameterExpander#getMinimumCodeReviewValue(BuildMemory.MemoryImprint, boolean)}
     * returns {@param expectedCodeReview}.
     *
     * @param expectedCodeReview
     * @param expectedVerified
     * @param entries
     */
    @ParameterizedTest
    @MethodSource("parameters")
    void testCodeReview(Integer expectedCodeReview, Integer expectedVerified,
                        BuildMemory.MemoryImprint.Entry[] entries) {
        when(memoryImprint.getEntries()).thenReturn(entries);

        IGerritHudsonTriggerConfig config = Setup.createConfig();
        ParameterExpander instance = new ParameterExpander(config);
        Integer result = instance.getMinimumCodeReviewValue(memoryImprint, true);
        if (expectedCodeReview == null) {
            assertNull(result);
        } else {
            assertEquals(expectedCodeReview, result);
        }
    }

    /**
     * Tests that {@link ParameterExpander#getMinimumVerifiedValue(BuildMemory.MemoryImprint, boolean, Integer)}
     * returns {@param expectedVerified}.
     *
     * @param expectedCodeReview
     * @param expectedVerified
     * @param entries
     */
    @ParameterizedTest
    @MethodSource("parameters")
    void testVerified(Integer expectedCodeReview, Integer expectedVerified,
                      BuildMemory.MemoryImprint.Entry[] entries) {
        when(memoryImprint.getEntries()).thenReturn(entries);

        IGerritHudsonTriggerConfig config = Setup.createConfig();
        ParameterExpander instance = new ParameterExpander(config);
        Integer result = instance.getMinimumVerifiedValue(memoryImprint, true, Integer.MAX_VALUE);
        if (expectedVerified == null) {
            assertNull(result);
        } else {
            assertEquals(expectedVerified, result);
        }
    }

    /**
     * Returns the parameters for the JUnit Runner to pass to the Constructor.
     * @return parameters.
     */
    static List<Arguments> parameters() {
        List<Arguments> parameters = new LinkedList<>();

        parameters.add(Arguments.of(
                +1,
                +2,
                new BuildMemory.MemoryImprint.Entry[]{
                        Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, false),
                        Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, false),
                        Setup.createAndSetupMemoryImprintEntry(Result.UNSTABLE, -1, -2, true),
                }));
        parameters.add(Arguments.of(
                        1,
                +2,
                new BuildMemory.MemoryImprint.Entry[]{
                    Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, false),
                    Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, false),
                    Setup.createAndSetupMemoryImprintEntry(Result.FAILURE, -1, -2, true),
                }));
        parameters.add(Arguments.of(
                +1,
                +2,
                new BuildMemory.MemoryImprint.Entry[]{
                    Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, false),
                    Setup.createAndSetupMemoryImprintEntry(Result.UNSTABLE, -1, -2, true),
                    Setup.createAndSetupMemoryImprintEntry(Result.FAILURE, -1, -2, true),
                }));
        parameters.add(Arguments.of(
                -1,
                -2,
                new BuildMemory.MemoryImprint.Entry[]{
                    Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, true),
                    Setup.createAndSetupMemoryImprintEntry(Result.UNSTABLE, -1, -2, false),
                    Setup.createAndSetupMemoryImprintEntry(Result.UNSTABLE, -1, -2, false),
                }));
        parameters.add(Arguments.of(
                -1,
               -2,
               new BuildMemory.MemoryImprint.Entry[]{
                    Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, true),
                    Setup.createAndSetupMemoryImprintEntry(Result.UNSTABLE, -1, -2, false),
                    Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, false),
               }));
        parameters.add(Arguments.of(
                +1,
                +2,
                new BuildMemory.MemoryImprint.Entry[]{
                    Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, false),
                }));
        parameters.add(Arguments.of(
                -1,
                -2,
                new BuildMemory.MemoryImprint.Entry[]{
                    Setup.createAndSetupMemoryImprintEntry(Result.UNSTABLE, -1, -2, false),
                }));
        parameters.add(Arguments.of(
                -5,
                -6,
                new BuildMemory.MemoryImprint.Entry[]{
                    Setup.createAndSetupMemoryImprintEntry(Result.ABORTED, -5, -6, false),
                }));
        parameters.add(Arguments.of(
                null,
               null,
               new BuildMemory.MemoryImprint.Entry[]{
                    Setup.createAndSetupMemoryImprintEntry(Result.ABORTED, -5, -6, true),
               }));
        parameters.add(Arguments.of(
                null,
                null,
                new BuildMemory.MemoryImprint.Entry[]{
                    Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, true),
                }));
        parameters.add(Arguments.of(
                null,
                null,
                new BuildMemory.MemoryImprint.Entry[]{
                    Setup.createAndSetupMemoryImprintEntry(Result.UNSTABLE, -1, -2, true),
                }));
        parameters.add(Arguments.of(
                null,
                null,
                new BuildMemory.MemoryImprint.Entry[]{
                    Setup.createAndSetupMemoryImprintEntry(Result.UNSTABLE, -1, -2, true),
                    Setup.createAndSetupMemoryImprintEntry(Result.UNSTABLE, -1, -2, true),
                }));
        parameters.add(Arguments.of(
                null,
                null,
                new BuildMemory.MemoryImprint.Entry[]{
                    Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, true),
                    Setup.createAndSetupMemoryImprintEntry(Result.SUCCESS, +1, +2, true),
                }));

        return parameters;
    }
}
