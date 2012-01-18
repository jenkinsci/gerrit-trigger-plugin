/*
 * The MIT License
 *
 * Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr;

import net.sf.json.JSONObject;
import org.junit.Test;

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.NUMBER;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.REF;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.REVISION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


/**
 * Tests {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.PatchSet}.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class PatchSetTest {

    /**
     * Tests {@link PatchSet#fromJson(net.sf.json.JSONObject)}.
     * @throws Exception if so.
     */
    @Test
    public void testFromJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put(NUMBER, "2");
        json.put(REVISION, "ad123456789");
        json.put(REF, "refs/changes/00/100/2");
        PatchSet patchSet = new PatchSet();
        patchSet.fromJson(json);

        assertEquals("2", patchSet.getNumber());
        assertEquals("ad123456789", patchSet.getRevision());
        assertEquals("refs/changes/00/100/2", patchSet.getRef());
    }

    /**
     * Tests {@link PatchSet#fromJson(net.sf.json.JSONObject)}.
     * Without "ref" in the JSON data.
     * @throws Exception if so.
     */
    @Test
    public void testFromJsonNoRef() throws Exception {
        JSONObject json = new JSONObject();
        json.put(NUMBER, "2");
        json.put(REVISION, "ad123456789");
        PatchSet patchSet = new PatchSet();
        patchSet.fromJson(json);

        assertEquals("2", patchSet.getNumber());
        assertEquals("ad123456789", patchSet.getRevision());
        assertNull(patchSet.getRef());
    }

    /**
     * Test {@link PatchSet#PatchSet(net.sf.json.JSONObject)}.
     * @throws Exception if so.
     */
    @Test
    public void testInitFromJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put(NUMBER, "2");
        json.put(REVISION, "ad123456789");
        json.put(REF, "refs/changes/00/100/2");
        PatchSet patchSet = new PatchSet(json);

        assertEquals("2", patchSet.getNumber());
        assertEquals("ad123456789", patchSet.getRevision());
        assertEquals("refs/changes/00/100/2", patchSet.getRef());
    }

    /**
     * Tests {@link PatchSet#equals(Object)}.
     * @throws Exception if so.
     */
    @Test
    public void testEquals() throws Exception {
        JSONObject json = new JSONObject();
        json.put(NUMBER, "2");
        json.put(REVISION, "ad123456789");
        json.put(REF, "refs/changes/00/100/2");
        PatchSet patchSet = new PatchSet(json);

        PatchSet patchSet2 = new PatchSet();
        patchSet2.setNumber("2");
        patchSet2.setRevision("ad123456789");
        patchSet2.setRef("refs/changes/00/100/2");
        assertTrue(patchSet.equals(patchSet2));
    }
}
