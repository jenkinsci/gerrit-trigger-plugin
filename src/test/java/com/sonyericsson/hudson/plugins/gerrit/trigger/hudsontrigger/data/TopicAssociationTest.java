/*
 *  The MIT License
 *
 *  Copyright 2022 Christoph Kreisl. All rights reserved.
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

import com.sonymobile.tools.gerrit.gerritevents.dto.GerritChangeStatus;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the TopicAssociation method.
 * @author Christoph Kreisl
 */
public class TopicAssociationTest {

    /**
     * Create a custom change for testing.
     *
     * @param status The GerritChangeStatus
     * @return new Change object based on status parameter.
     */
    private Change createChange(GerritChangeStatus status) {
        Change change = new Change();
        change.setStatus(status);
        return change;
    }

    /**
     * Test if change with status is interesting.
     */
    @Test
    public void testIsInterestingChange() {
        TopicAssociation topicAssociation = new TopicAssociation();

        Change c = createChange(GerritChangeStatus.NEW);
        assertTrue(topicAssociation.isInterestingChangeStatus(c));

        topicAssociation.setIgnoreNewChangeStatus(true);
        assertFalse(topicAssociation.isInterestingChangeStatus(c));

        c = createChange(GerritChangeStatus.MERGED);
        assertTrue(topicAssociation.isInterestingChangeStatus(c));

        topicAssociation.setIgnoreMergedChangeStatus(true);
        assertFalse(topicAssociation.isInterestingChangeStatus(c));

        c = createChange(GerritChangeStatus.ABANDONED);
        assertTrue(topicAssociation.isInterestingChangeStatus(c));

        topicAssociation.setIgnoreAbandonedChangeStatus(true);
        assertFalse(topicAssociation.isInterestingChangeStatus(c));
    }

}
