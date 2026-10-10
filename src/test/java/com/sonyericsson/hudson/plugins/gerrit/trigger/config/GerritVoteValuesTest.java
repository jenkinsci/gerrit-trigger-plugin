/*
 *  The MIT License
 *
 *  Copyright 2026 Amarula Solutions. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.config;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

//CS IGNORE MagicNumber FOR NEXT 100 LINES. REASON: Mocks tests.

/**
 * Tests for {@link GerritVoteValues}.
 */
class GerritVoteValuesTest {

    private static final int TEST_OVERRIDE = 99;

    /**
     * Test that default constructor sets all defaults.
     */
    @Test
    void testDefaultConstructor() {
        GerritVoteValues v = new GerritVoteValues();
        assertEquals(Integer.valueOf(0), v.getBuildStartedVerifiedValue());
        assertEquals(Integer.valueOf(1), v.getBuildSuccessfulVerifiedValue());
        assertEquals(Integer.valueOf(-1), v.getBuildFailedVerifiedValue());
        assertEquals(Integer.valueOf(0), v.getBuildUnstableVerifiedValue());
        assertEquals(Integer.valueOf(0), v.getBuildNotBuiltVerifiedValue());
        assertEquals(Integer.valueOf(0), v.getBuildAbortedVerifiedValue());
        assertEquals(Integer.valueOf(0), v.getBuildStartedCodeReviewValue());
        assertEquals(Integer.valueOf(0), v.getBuildSuccessfulCodeReviewValue());
        assertEquals(Integer.valueOf(0), v.getBuildFailedCodeReviewValue());
        assertEquals(Integer.valueOf(-1), v.getBuildUnstableCodeReviewValue());
        assertEquals(Integer.valueOf(0), v.getBuildNotBuiltCodeReviewValue());
        assertEquals(Integer.valueOf(0), v.getBuildAbortedCodeReviewValue());
    }

    /**
     * Test that JSON constructor parses values correctly.
     */
    @Test
    void testJsonConstructor() {
        String jsonStr = "{"
                + "\"gerritBuildFailedCodeReviewValue\":\"1\","
                + "\"gerritBuildFailedVerifiedValue\":\"-1\","
                + "\"gerritBuildStartedCodeReviewValue\":\"2\","
                + "\"gerritBuildStartedVerifiedValue\":\"-2\","
                + "\"gerritBuildSuccessfulCodeReviewValue\":\"3\","
                + "\"gerritBuildSuccessfulVerifiedValue\":\"-3\","
                + "\"gerritBuildUnstableCodeReviewValue\":\"4\","
                + "\"gerritBuildUnstableVerifiedValue\":\"-4\","
                + "\"gerritBuildNotBuiltCodeReviewValue\":\"5\","
                + "\"gerritBuildNotBuiltVerifiedValue\":\"-5\","
                + "\"gerritBuildAbortedCodeReviewValue\":\"6\","
                + "\"gerritBuildAbortedVerifiedValue\":\"-6\""
                + "}";
        JSONObject formData = (JSONObject)JSONSerializer.toJSON(jsonStr);
        GerritVoteValues v = new GerritVoteValues(formData);

        assertEquals(Integer.valueOf(-2), v.getBuildStartedVerifiedValue());
        assertEquals(Integer.valueOf(2), v.getBuildStartedCodeReviewValue());
        assertEquals(Integer.valueOf(-3), v.getBuildSuccessfulVerifiedValue());
        assertEquals(Integer.valueOf(3), v.getBuildSuccessfulCodeReviewValue());
        assertEquals(Integer.valueOf(-1), v.getBuildFailedVerifiedValue());
        assertEquals(Integer.valueOf(1), v.getBuildFailedCodeReviewValue());
        assertEquals(Integer.valueOf(-4), v.getBuildUnstableVerifiedValue());
        assertEquals(Integer.valueOf(4), v.getBuildUnstableCodeReviewValue());
        assertEquals(Integer.valueOf(-5), v.getBuildNotBuiltVerifiedValue());
        assertEquals(Integer.valueOf(5), v.getBuildNotBuiltCodeReviewValue());
        assertEquals(Integer.valueOf(-6), v.getBuildAbortedVerifiedValue());
        assertEquals(Integer.valueOf(6), v.getBuildAbortedCodeReviewValue());
    }

    /**
     * Test copy constructor.
     */
    @Test
    void testCopyConstructor() {
        GerritVoteValues source = new GerritVoteValues();
        source.setBuildStartedVerifiedValue(10);
        source.setBuildFailedVerifiedValue(-10);

        GerritVoteValues copy = new GerritVoteValues(source);
        assertEquals(Integer.valueOf(10), copy.getBuildStartedVerifiedValue());
        assertEquals(Integer.valueOf(0), copy.getBuildStartedCodeReviewValue()); // default
        assertEquals(Integer.valueOf(-10), copy.getBuildFailedVerifiedValue());
    }

    /**
     * Test that fromConfig copies values correctly.
     */
    @Test
    void testFromConfig() {
        Config config = new Config();
        config.setGerritBuildStartedVerifiedValue(7);
        config.setGerritBuildSuccessfulVerifiedValue(8);
        config.setGerritBuildFailedVerifiedValue(-2);
        config.setGerritBuildUnstableVerifiedValue(null);

        GerritVoteValues v = GerritVoteValues.fromConfig(config);
        assertEquals(Integer.valueOf(7), v.getBuildStartedVerifiedValue());
        assertEquals(Integer.valueOf(8), v.getBuildSuccessfulVerifiedValue());
        assertEquals(Integer.valueOf(-2), v.getBuildFailedVerifiedValue());
        assertNull(v.getBuildUnstableVerifiedValue());
    }

    /**
     * Test setValues with empty JSON applies defaults.
     */
    @Test
    void testSetValuesEmptyJson() {
        GerritVoteValues v = new GerritVoteValues();
        // Override one value, then reset with empty JSON
        v.setBuildStartedVerifiedValue(TEST_OVERRIDE);
        v.setValues(new JSONObject(false));

        assertEquals(Integer.valueOf(0), v.getBuildStartedVerifiedValue(),
                "Should be reset to default");
    }

}
