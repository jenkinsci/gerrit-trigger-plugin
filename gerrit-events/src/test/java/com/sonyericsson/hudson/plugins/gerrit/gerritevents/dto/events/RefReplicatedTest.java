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
 * Tests {@linkcom.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.RefReplicated}.
 * @author Hugo Arès &lt;hugo.ares@ericsson.com&gt;
 */
public class RefReplicatedTest {

    /**
     * Tests {@link RefReplicated#getEventType()}.
     */
    @Test
    public void shouldBeRefReplicatedAsEventType() {
        RefReplicated refReplicated = new RefReplicated();
        assertEquals(GerritEventType.REF_REPLICATED, refReplicated.getEventType());
    }

    /**
     * Tests {@link RefReplicated#isScorable()}.
     */
    @Test
    public void shouldNotBeScorable() {
        RefReplicated refReplicated = new RefReplicated();
        assertEquals(false, refReplicated.isScorable());
    }

    /**
     * Tests {@link RefReplicated#fromJson(net.sf.json.JSONObject)}.
     */
    @Test
    public void testFromJSON() {
        JSONObject json = new JSONObject();
        json.put(GerritEventKeys.PROJECT, "someProject");
        json.put(GerritEventKeys.REF, "refs/changes/00/100/2");
        json.put(GerritEventKeys.STATUS, "Success");
        json.put(GerritEventKeys.TARGET_NODE, "someNode");

        RefReplicated refReplicated = new RefReplicated();
        refReplicated.fromJson(json);

        assertEquals("someProject", refReplicated.getProject());
        assertEquals("refs/changes/00/100/2", refReplicated.getRef());
        assertEquals("Success", refReplicated.getStatus());
        assertEquals("someNode", refReplicated.getTargetNode());
    }

    /**
     * Tests {@link RefReplicated#equals()}.
     */
    @Test
    public void shouldBeEqualWhenSameInstance() {
        RefReplicated refReplicated = new RefReplicated();
        assertTrue(refReplicated.equals(refReplicated));
    }

    /**
     * Tests {@link RefReplicated#equals().
     */
    @Test
    public void shouldNotBeEqualWhenObjectIsNull() {
        RefReplicated refReplicated = new RefReplicated();

        assertFalse(refReplicated.equals(null));
    }

    /**
     * Tests {@link RefReplicated#equals().
     */
    @Test
    public void shouldNotBeEqualWhenObjectIsNotSameType() {
        RefReplicated refReplicated = new RefReplicated();

        assertFalse(refReplicated.equals(new CommentAdded()));
    }

    /**
     * Tests {@link RefReplicated#equals() and {@link RefReplicated#hashCode()}}.
     */
    @Test
    public void shouldBeEqualWhenAllFieldsAreNull() {
        RefReplicated refReplicated1 = new RefReplicated();
        RefReplicated refReplicated2 = new RefReplicated();

        assertTrue(refReplicated1.equals(refReplicated2));
        assertTrue(refReplicated1.hashCode() == refReplicated2.hashCode());
    }

    /**
     * Tests {@link RefReplicated#equals() and {@link RefReplicated#hashCode()}}.
     */
    @Test
    public void shouldBeEqualWhenProjectIsTheSameAndOtherFieldsAreNull() {
        RefReplicated refReplicated1 = new RefReplicated();
        refReplicated1.setProject("someProject");
        RefReplicated refReplicated2 = new RefReplicated();
        refReplicated2.setProject("someProject");

        assertTrue(refReplicated1.equals(refReplicated1));
        assertEquals(refReplicated1.hashCode(), refReplicated2.hashCode());
    }

    /**
     * Tests {@link RefReplicated#equals() and {@link RefReplicated#hashCode()}}.
     */
    @Test
    public void shouldNoBeEqualWhenProjectIsNotTheSameAndOtherFieldsAreNull() {
        RefReplicated refReplicated1 = new RefReplicated();
        refReplicated1.setProject("someProject");
        RefReplicated refReplicated2 = new RefReplicated();

        assertFalse(refReplicated1.equals(refReplicated2));
        assertFalse(refReplicated1.hashCode() == refReplicated2.hashCode());

        refReplicated2.setProject("someOtherProject");

        assertFalse(refReplicated1.equals(refReplicated2));
        assertFalse(refReplicated1.hashCode() == refReplicated2.hashCode());

        refReplicated1.setProject(null);

        assertFalse(refReplicated1.equals(refReplicated2));
        assertFalse(refReplicated1.hashCode() == refReplicated2.hashCode());
    }

    /**
     * Tests {@link RefReplicated#equals() and {@link RefReplicated#hashCode()}}.
     */
    @Test
    public void shouldBeEqualWhenRefIsTheSameAndOtherFieldsAreNull() {
        RefReplicated refReplicated1 = new RefReplicated();
        refReplicated1.setRef("someRef");
        RefReplicated refReplicated2 = new RefReplicated();
        refReplicated2.setRef("someRef");

        assertTrue(refReplicated1.equals(refReplicated1));
        assertEquals(refReplicated1.hashCode(), refReplicated2.hashCode());
    }

    /**
     * Tests {@link RefReplicated#equals() and {@link RefReplicated#hashCode()}}.
     */
    @Test
    public void shouldNoBeEqualWhenRefIsNotTheSameAndOtherFieldsAreNull() {
        RefReplicated refReplicated1 = new RefReplicated();
        refReplicated1.setRef("someRef");
        RefReplicated refReplicated2 = new RefReplicated();

        assertFalse(refReplicated1.equals(refReplicated2));
        assertFalse(refReplicated1.hashCode() == refReplicated2.hashCode());

        refReplicated2.setRef("someOtherRef");

        assertFalse(refReplicated1.equals(refReplicated2));
        assertFalse(refReplicated1.hashCode() == refReplicated2.hashCode());

        refReplicated1.setRef(null);

        assertFalse(refReplicated1.equals(refReplicated2));
        assertFalse(refReplicated1.hashCode() == refReplicated2.hashCode());
    }

    /**
     * Tests {@link RefReplicated#equals() and {@link RefReplicated#hashCode()}}.
     */
    @Test
    public void shouldBeEqualWhenStatusIsTheSameAndOtherFieldsAreNull() {
        RefReplicated refReplicated1 = new RefReplicated();
        refReplicated1.setStatus("someStatus");
        RefReplicated refReplicated2 = new RefReplicated();
        refReplicated2.setStatus("someStatus");

        assertTrue(refReplicated1.equals(refReplicated1));
        assertEquals(refReplicated1.hashCode(), refReplicated2.hashCode());
    }

    /**
     * Tests {@link RefReplicated#equals() and {@link RefReplicated#hashCode()}}.
     */
    @Test
    public void shouldNoBeEqualWhenStatusIsNotTheSameAndOtherFieldsAreNull() {
        RefReplicated refReplicated1 = new RefReplicated();
        refReplicated1.setStatus("someStatus");
        RefReplicated refReplicated2 = new RefReplicated();

        assertFalse(refReplicated1.equals(refReplicated2));
        assertFalse(refReplicated1.hashCode() == refReplicated2.hashCode());

        refReplicated2.setStatus("someOtherStatus");

        assertFalse(refReplicated1.equals(refReplicated2));
        assertFalse(refReplicated1.hashCode() == refReplicated2.hashCode());

        refReplicated1.setStatus(null);

        assertFalse(refReplicated1.equals(refReplicated2));
        assertFalse(refReplicated1.hashCode() == refReplicated2.hashCode());
    }

    /**
     * Tests {@link RefReplicated#equals() and {@link RefReplicated#hashCode()}}.
     */
    @Test
    public void shouldBeEqualWhenTargetNodeIsTheSameAndOtherFieldsAreNull() {
        RefReplicated refReplicated1 = new RefReplicated();
        refReplicated1.setTargetNode("someTargetNode");
        RefReplicated refReplicated2 = new RefReplicated();
        refReplicated2.setTargetNode("someTargetNode");

        assertTrue(refReplicated1.equals(refReplicated1));
        assertEquals(refReplicated1.hashCode(), refReplicated2.hashCode());
    }

    /**
     * Tests {@link RefReplicated#equals() and {@link RefReplicated#hashCode()}}.
     */
    @Test
    public void shouldNoBeEqualWhenTargetNodeIsNotTheSameAndOtherFieldsAreNull() {
        RefReplicated refReplicated1 = new RefReplicated();
        refReplicated1.setTargetNode("someTargetNode");
        RefReplicated refReplicated2 = new RefReplicated();

        assertFalse(refReplicated1.equals(refReplicated2));
        assertFalse(refReplicated1.hashCode() == refReplicated2.hashCode());

        refReplicated2.setTargetNode("someOtherTargetNode");

        assertFalse(refReplicated1.equals(refReplicated2));
        assertFalse(refReplicated1.hashCode() == refReplicated2.hashCode());

        refReplicated1.setTargetNode(null);

        assertFalse(refReplicated1.equals(refReplicated2));
        assertFalse(refReplicated1.hashCode() == refReplicated2.hashCode());
    }

    /**
     * Tests {@link RefReplicated#equals() and {@link RefReplicated#hashCode()}}.
     */
    @Test
    public void shouldBeEqualWhenAllFieldsAreTheSame() {
        RefReplicated refReplicated1 = new RefReplicated();
        refReplicated1.setProject("someProject");
        refReplicated1.setRef("someRef");
        refReplicated1.setStatus("someStatus");
        refReplicated1.setTargetNode("someTargetNode");
        RefReplicated refReplicated2 = new RefReplicated();
        refReplicated2.setProject("someProject");
        refReplicated2.setRef("someRef");
        refReplicated2.setStatus("someStatus");
        refReplicated2.setTargetNode("someTargetNode");

        assertTrue(refReplicated1.equals(refReplicated2));
        assertTrue(refReplicated1.hashCode() == refReplicated2.hashCode());
    }
}
