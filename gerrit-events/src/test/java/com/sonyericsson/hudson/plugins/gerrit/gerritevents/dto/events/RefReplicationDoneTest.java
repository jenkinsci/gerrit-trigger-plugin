/*
 *  The MIT License
 *
 *  Copyright 2013 Ericsson.
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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.sf.json.JSONObject;

import org.junit.Test;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventType;

/**
 * Tests {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.RefReplicationDone}.
 * @author Hugo Arès &lt;hugo.ares@ericsson.com&gt;
 */
public class RefReplicationDoneTest {

    /**
     * Tests {@link RefReplicationDone#getEventType()}.
     */
    @Test
    public void shouldBeRefReplicationDoneAsEventType() {
        RefReplicationDone refReplicationDone = new RefReplicationDone();
        assertEquals(GerritEventType.REF_REPLICATION_DONE, refReplicationDone.getEventType());
    }

    /**
     * Tests {@link RefReplicationDone#isScorable()}.
     */
    @Test
    public void shouldNotBeScorable() {
        RefReplicationDone refReplicationDone = new RefReplicationDone();
        assertEquals(false, refReplicationDone.isScorable());
    }

    /**
     * Tests {@link RefReplicationDone#fromJson(net.sf.json.JSONObject)}.
     */
    @Test
    public void testFromJSON() {
        JSONObject json = new JSONObject();
        json.put(GerritEventKeys.PROJECT, "someProject");
        json.put(GerritEventKeys.REF, "refs/changes/00/100/2");
        //CS IGNORE MagicNumber FOR NEXT 1 LINES. REASON: test code.
        json.put(GerritEventKeys.NODES_COUNT, "5");

        RefReplicationDone refReplicationDone = new RefReplicationDone();
        refReplicationDone.fromJson(json);

        assertEquals("someProject", refReplicationDone.getProject());
        assertEquals("refs/changes/00/100/2", refReplicationDone.getRef());
        //CS IGNORE MagicNumber FOR NEXT 1 LINES. REASON: test code.
        assertEquals(5, refReplicationDone.getNodesCount());
    }

    /**
     * Tests {@link RefReplicationDone#equals()}.
     */
    @Test
    public void shouldBeEqualWhenSameInstance() {
        RefReplicationDone refReplicationDone = new RefReplicationDone();
        assertTrue(refReplicationDone.equals(refReplicationDone));
    }

    /**
     * Tests {@link RefReplicationDone#equals().
     */
    @Test
    public void shouldNotBeEqualWhenObjectIsNull() {
        RefReplicationDone refReplicationDone = new RefReplicationDone();

        assertFalse(refReplicationDone.equals(null));
    }

    /**
     * Tests {@link RefReplicationDone#equals().
     */
    @Test
    public void shouldNotBeEqualWhenObjectIsNotSameType() {
        RefReplicationDone refReplicationDone = new RefReplicationDone();

        assertFalse(refReplicationDone.equals(new CommentAdded()));
    }

    /**
     * Tests {@link RefReplicationDone#equals() and {@link RefReplicationDone#hashCode()}}.
     */
    @Test
    public void shouldBeEqualWhenAllFieldsAreNull() {
        RefReplicationDone refReplicationDone1 = new RefReplicationDone();
        RefReplicationDone refReplicationDone2 = new RefReplicationDone();

        assertTrue(refReplicationDone1.equals(refReplicationDone2));
        assertTrue(refReplicationDone1.hashCode() == refReplicationDone2.hashCode());
    }

    /**
     * Tests {@link RefReplicationDone#equals() and {@link RefReplicationDone#hashCode()}}.
     */
    @Test
    public void shouldBeEqualWhenProjectIsTheSameAndOtherFieldsAreNull() {
        RefReplicationDone refReplicationDone1 = new RefReplicationDone();
        refReplicationDone1.setProject("someProject");
        RefReplicationDone refReplicationDone2 = new RefReplicationDone();
        refReplicationDone2.setProject("someProject");

        assertTrue(refReplicationDone1.equals(refReplicationDone1));
        assertEquals(refReplicationDone1.hashCode(), refReplicationDone2.hashCode());
    }

    /**
     * Tests {@link RefReplicationDone#equals() and {@link RefReplicationDone#hashCode()}}.
     */
    @Test
    public void shouldNoBeEqualWhenProjectIsNotTheSameAndOtherFieldsAreNull() {
        RefReplicationDone refReplicationDone1 = new RefReplicationDone();
        refReplicationDone1.setProject("someProject");
        RefReplicationDone refReplicationDone2 = new RefReplicationDone();

        assertFalse(refReplicationDone1.equals(refReplicationDone2));
        assertFalse(refReplicationDone1.hashCode() == refReplicationDone2.hashCode());

        refReplicationDone2.setProject("someOtherProject");

        assertFalse(refReplicationDone1.equals(refReplicationDone2));
        assertFalse(refReplicationDone1.hashCode() == refReplicationDone2.hashCode());

        refReplicationDone1.setProject(null);

        assertFalse(refReplicationDone1.equals(refReplicationDone2));
        assertFalse(refReplicationDone1.hashCode() == refReplicationDone2.hashCode());
    }

    /**
     * Tests {@link RefReplicationDone#equals() and {@link RefReplicationDone#hashCode()}}.
     */
    @Test
    public void shouldBeEqualWhenRefIsTheSameAndOtherFieldsAreNull() {
        RefReplicationDone refReplicationDone1 = new RefReplicationDone();
        refReplicationDone1.setRef("someRef");
        RefReplicationDone refReplicationDone2 = new RefReplicationDone();
        refReplicationDone2.setRef("someRef");

        assertTrue(refReplicationDone1.equals(refReplicationDone1));
        assertEquals(refReplicationDone1.hashCode(), refReplicationDone2.hashCode());
    }

    /**
     * Tests {@link RefReplicationDone#equals() and {@link RefReplicationDone#hashCode()}}.
     */
    @Test
    public void shouldNoBeEqualWhenRefIsNotTheSameAndOtherFieldsAreNull() {
        RefReplicationDone refReplicationDone1 = new RefReplicationDone();
        refReplicationDone1.setRef("someRef");
        RefReplicationDone refReplicationDone2 = new RefReplicationDone();

        assertFalse(refReplicationDone1.equals(refReplicationDone2));
        assertFalse(refReplicationDone1.hashCode() == refReplicationDone2.hashCode());

        refReplicationDone2.setRef("someOtherRef");

        assertFalse(refReplicationDone1.equals(refReplicationDone2));
        assertFalse(refReplicationDone1.hashCode() == refReplicationDone2.hashCode());

        refReplicationDone1.setRef(null);

        assertFalse(refReplicationDone1.equals(refReplicationDone2));
        assertFalse(refReplicationDone1.hashCode() == refReplicationDone2.hashCode());
    }

    /**
     * Tests {@link RefReplicationDone#equals() and {@link RefReplicationDone#hashCode()}}.
     */
    @Test
    public void shouldBeEqualWhenNodesCountIsTheSameAndOtherFieldsAreNull() {
        RefReplicationDone refReplicationDone1 = new RefReplicationDone();
        //CS IGNORE MagicNumber FOR NEXT 1 LINES. REASON: test code.
        refReplicationDone1.setNodesCount(4);
        RefReplicationDone refReplicationDone2 = new RefReplicationDone();
        //CS IGNORE MagicNumber FOR NEXT 1 LINES. REASON: test code.
        refReplicationDone2.setNodesCount(4);

        assertTrue(refReplicationDone1.equals(refReplicationDone1));
        assertEquals(refReplicationDone1.hashCode(), refReplicationDone2.hashCode());
    }

    /**
     * Tests {@link RefReplicationDone#equals() and {@link RefReplicationDone#hashCode()}}.
     */
    @Test
    public void shouldNoBeEqualWhenNodesCountIsNotTheSameAndOtherFieldsAreNull() {
        RefReplicationDone refReplicationDone1 = new RefReplicationDone();
        //CS IGNORE MagicNumber FOR NEXT 1 LINES. REASON: test code.
        refReplicationDone1.setNodesCount(3);
        RefReplicationDone refReplicationDone2 = new RefReplicationDone();
        //CS IGNORE MagicNumber FOR NEXT 1 LINES. REASON: test code.
        refReplicationDone2.setNodesCount(5);

        assertFalse(refReplicationDone1.equals(refReplicationDone2));
        assertFalse(refReplicationDone1.hashCode() == refReplicationDone2.hashCode());
    }

    /**
     * Tests {@link RefReplicationDone#equals() and {@link RefReplicationDone#hashCode()}}.
     */
    @Test
    public void shouldBeEqualWhenAllFieldsAreTheSame() {
        RefReplicationDone refReplicationDone1 = new RefReplicationDone();
        refReplicationDone1.setProject("someProject");
        refReplicationDone1.setRef("someRef");
        //CS IGNORE MagicNumber FOR NEXT 1 LINES. REASON: test code.
        refReplicationDone1.setNodesCount(5);
        RefReplicationDone refReplicationDone2 = new RefReplicationDone();
        refReplicationDone2.setProject("someProject");
        refReplicationDone2.setRef("someRef");
        //CS IGNORE MagicNumber FOR NEXT 1 LINES. REASON: test code.
        refReplicationDone2.setNodesCount(5);

        assertTrue(refReplicationDone1.equals(refReplicationDone2));
        assertTrue(refReplicationDone1.hashCode() == refReplicationDone2.hashCode());
    }
}
