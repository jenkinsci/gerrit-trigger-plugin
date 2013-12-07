/*
 * The MIT License
 *
 * Copyright 2013 Jyrki Puttonen. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.rest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test for proper encoding.
 */
public class ChangeIdTest {

    private static final String PROJECT_NAME = "project";
    private static final String PROJECT_NAME_WITH_SLASH = "project/subproject";
    private static final String ENCODED_PROJECT_NAME_WITH_SLASH = "project%2Fsubproject";
    private static final String BRANCH_NAME = "branch";
    private static final String BRANCH_NAME_WITH_SLASH = "branch/other";
    private static final String ENCODED_BRANCH_NAME_WITH_SLASH = "branch%2Fother";
    private static final String CHANGE_ID = "Id4d4a864c09e80115e35d55d36694d877130226e";

    /**
     * test {@link ChangeId#asUrlPart()} with simple names without any special characters.
     */
    @Test
    public void testEncodeSimpleChangeId() {
        ChangeId changeId = new ChangeId(PROJECT_NAME, BRANCH_NAME, CHANGE_ID);
        String asUrlPart = changeId.asUrlPart();
        assertEquals(PROJECT_NAME + "~" + BRANCH_NAME + "~" + CHANGE_ID, asUrlPart);
    }

    /**
     * test {@link ChangeId#asUrlPart()} slash in project name.
     */

    @Test
    public void testProjectNameHasSlash() {
        ChangeId changeId = new ChangeId(PROJECT_NAME_WITH_SLASH, BRANCH_NAME, CHANGE_ID);
        String asUrlPart = changeId.asUrlPart();
        assertEquals(ENCODED_PROJECT_NAME_WITH_SLASH + "~" + BRANCH_NAME + "~" + CHANGE_ID, asUrlPart);
    }

    /**
     * test {@link ChangeId#asUrlPart()} slash in branch name.
     */
    @Test
    public void testBranchHasSlash() {
        ChangeId changeId = new ChangeId(PROJECT_NAME, BRANCH_NAME_WITH_SLASH, CHANGE_ID);
        String asUrlPart = changeId.asUrlPart();
        assertEquals(PROJECT_NAME + "~" + ENCODED_BRANCH_NAME_WITH_SLASH + "~" + CHANGE_ID, asUrlPart);
    }
}
