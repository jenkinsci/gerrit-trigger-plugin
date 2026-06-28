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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data;

import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedList;
import java.util.List;

/**
 * Testing different scenarios if they are interesting.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
class GerritProjectInterestingTest {

    /**
     * Tests {@link GerritProject#isInteresting(Change change)}.
     *
     * @param config
     * @param change
     * @param expected
     */
    @ParameterizedTest
    @MethodSource("parameters")
    void testInteresting(GerritProject config, Change change, boolean expected) {
        assertEquals(expected, config.isInteresting(
                change));
    }

    // CS IGNORE MethodLength FOR NEXT 300 LINES. REASON: Test data.
    /**
     * The parameters.
     * @return parameters
     */
    static List<Arguments> parameters() {
        List<Arguments> parameters = new LinkedList<>();

        List<Branch> branches = new LinkedList<>();
        List<Topic> topics = new LinkedList<>();
        List<Hashtag> hashtags = new LinkedList<>();
        Branch branch = new Branch(CompareType.PLAIN, "master");
        branches.add(branch);
        GerritProject config = new GerritProject(CompareType.PLAIN, "project", branches, topics,
                null, null, false);
        Change change = new Change();
        change.setProject("project");
        change.setBranch("master");
        change.setTopic(null);
        change.setHashtags(null);
        parameters.add(Arguments.of(config, change, true));

        branches = new LinkedList<>();
        branch = new Branch(CompareType.ANT, "**/master");
        branches.add(branch);
        config = new GerritProject(CompareType.PLAIN, "project", branches, topics,
                null, null, false);
        change = new Change();
        change.setProject("project");
        change.setBranch("origin/master");
        change.setTopic(null);
        change.setHashtags(null);
        parameters.add(Arguments.of(config, change, true));

        branches = new LinkedList<>();
        branch = new Branch(CompareType.ANT, "**/master");
        branches.add(branch);
        config = new GerritProject(CompareType.PLAIN, "project", branches, topics,
                null, null, false);
        change = new Change();
        change.setProject("project");
        change.setBranch("master");
        change.setTopic(null);
        change.setHashtags(null);
        parameters.add(Arguments.of(config, change, true));

        branches = new LinkedList<>();
        branch = new Branch(CompareType.ANT, "**/master");
        branches.add(branch);
        branch = new Branch(CompareType.REG_EXP, "feature/.*master");
        branches.add(branch);
        config = new GerritProject(CompareType.PLAIN, "project", branches, topics,
                null, null, false);
        change = new Change();
        change.setProject("project");
        change.setBranch("master");
        change.setTopic(null);
        change.setHashtags(null);
        parameters.add(Arguments.of(config, change, true));

        branches = new LinkedList<>();
        branch = new Branch(CompareType.PLAIN, "olstorp");
        branches.add(branch);
        branch = new Branch(CompareType.REG_EXP, "feature/.*master");
        branches.add(branch);
        config = new GerritProject(CompareType.PLAIN, "project", branches, topics,
                null, null, false);
        change = new Change();
        change.setProject("project");
        change.setBranch("feature/mymaster");
        change.setTopic(null);
        change.setHashtags(null);
        parameters.add(Arguments.of(config, change, true));
        change = new Change();
        change.setProject("project");
        change.setBranch("Olstorp");
        change.setTopic(null);
        change.setHashtags(null);
        parameters.add(Arguments.of(config, change, true));

        branches = new LinkedList<>();
        branch = new Branch(CompareType.ANT, "**/master");
        branches.add(branch);
        config = new GerritProject(CompareType.ANT, "vendor/**/project", branches, topics,
                null, null, false);
        change = new Change();
        change.setProject("vendor/semc/master/project");
        change.setBranch("origin/master");
        change.setTopic(null);
        change.setHashtags(null);
        parameters.add(Arguments.of(config, change, true));

        branches = new LinkedList<>();
        branch = new Branch(CompareType.PLAIN, "master");
        branches.add(branch);
        topics = new LinkedList<>();
        Topic topic = new Topic(CompareType.PLAIN, "topic");
        topics.add(topic);
        config = new GerritProject(CompareType.PLAIN, "project", branches, topics,
                null, null, false);
        change = new Change();
        change.setProject("project");
        change.setBranch("master");
        change.setTopic("topic");
        change.setHashtags(null);
        parameters.add(Arguments.of(config, change, true));

        topics = new LinkedList<>();
        topic = new Topic(CompareType.ANT, "**/topic");
        topics.add(topic);
        config = new GerritProject(CompareType.PLAIN, "project", branches, topics,
                null, null, false);
        change = new Change();
        change.setProject("project");
        change.setBranch("master");
        change.setTopic("team/topic");
        change.setHashtags(null);
        parameters.add(Arguments.of(config, change, true));

        topics = new LinkedList<>();
        topic = new Topic(CompareType.REG_EXP, ".*_topic");
        topics.add(topic);
        config = new GerritProject(CompareType.PLAIN, "project", branches, topics,
                null, null, false);
        change = new Change();
        change.setProject("project");
        change.setBranch("master");
        change.setTopic("team-wolf_topic");
        change.setHashtags(null);
        parameters.add(Arguments.of(config, change, true));

        hashtags = new LinkedList<>();
        Hashtag hashtag = new Hashtag(CompareType.PLAIN, "hashtag");
        hashtags.add(hashtag);
        config = new GerritProject(CompareType.PLAIN, "project", branches, null,
                null, null, false);
        config.setHashtags(hashtags);
        change = new Change();
        change.setProject("project");
        change.setBranch("master");
        change.setTopic(null);
        change.setHashtags(List.of("hashtag"));
        parameters.add(Arguments.of(config, change, true));

        hashtags = new LinkedList<>();
        hashtag = new Hashtag(CompareType.ANT, "**/hashtag");
        hashtags.add(hashtag);
        config = new GerritProject(CompareType.PLAIN, "project", branches, null,
                null, null, false);
        config.setHashtags(hashtags);
        change = new Change();
        change.setProject("project");
        change.setBranch("master");
        change.setTopic(null);
        change.setHashtags(List.of("test/hashtag"));
        parameters.add(Arguments.of(config, change, true));

        hashtags = new LinkedList<>();
        hashtag = new Hashtag(CompareType.REG_EXP, ".*_hashtag");
        hashtags.add(hashtag);
        config = new GerritProject(CompareType.PLAIN, "project", branches, null,
                null, null, false);
        config.setHashtags(hashtags);
        change = new Change();
        change.setProject("project");
        change.setBranch("master");
        change.setTopic(null);
        change.setHashtags(List.of("test_hashtag"));
        parameters.add(Arguments.of(config, change, true));

        hashtags = new LinkedList<>();
        hashtag = new Hashtag(CompareType.REG_EXP, "hash.*tag");
        hashtags.add(hashtag);
        config = new GerritProject(CompareType.PLAIN, "project", branches, null,
                null, null, false);
        config.setHashtags(hashtags);
        change = new Change();
        change.setProject("project");
        change.setBranch("master");
        change.setTopic(null);
        change.setHashtags(List.of("test_hashtag"));
        parameters.add(Arguments.of(config, change, false));

        hashtags = new LinkedList<>();
        hashtag = new Hashtag(CompareType.ANT, "hash.*tag");
        hashtags.add(hashtag);
        config = new GerritProject(CompareType.PLAIN, "project", branches, null,
                null, null, false);
        config.setHashtags(hashtags);
        change = new Change();
        change.setProject("project");
        change.setBranch("master");
        change.setTopic(null);
        change.setHashtags(List.of("test_hashtag"));
        parameters.add(Arguments.of(config, change, false));

        hashtags = new LinkedList<>();
        hashtag = new Hashtag(CompareType.PLAIN, "hash.*tag");
        hashtags.add(hashtag);
        config = new GerritProject(CompareType.PLAIN, "project", branches, null,
                null, null, false);
        config.setHashtags(hashtags);
        change = new Change();
        change.setProject("project");
        change.setBranch("master");
        change.setTopic(null);
        change.setHashtags(List.of("test_hashtag"));
        parameters.add(Arguments.of(config, change, false));

        return parameters;
    }
}
