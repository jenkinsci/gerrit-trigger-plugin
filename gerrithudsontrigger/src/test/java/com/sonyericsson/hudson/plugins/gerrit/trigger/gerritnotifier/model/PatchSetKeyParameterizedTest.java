/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.PatchSetKey;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;

//CS IGNORE LineLength FOR NEXT 200 LINES. REASON: Readability for the parameters.
//CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: Mock tests.

/**
 * Tests the patchset key for BuildMemory.
 * @author Gustaf Lund
 */
@RunWith(Parameterized.class)
public class PatchSetKeyParameterizedTest {
    private PatchSetKey key1;
    private PatchSetKey key2;
    private Boolean res;

    /**
     * Constructor.
     * @param key1 key1
     * @param key2 key2
     * @param res res
     */
    public PatchSetKeyParameterizedTest(PatchSetKey key1, PatchSetKey key2, Boolean res) {
        this.key1 = key1;
        this.key2 = key2;
        this.res = res;
    }

    /**
     * Parameters.
     * @return parameters
     */
    @Parameters
    public static Collection getParameters() {
        List<Object[]> list = new LinkedList<Object[]>();

        //Equals
        list.add(new Object[] {new PatchSetKey("1234", "5678"), new PatchSetKey("1234", "5678"), true});
        list.add(new Object[] {new PatchSetKey("1234", "1"), new PatchSetKey("1234", "1"), true});
        list.add(new Object[] {new PatchSetKey("1", "1"), new PatchSetKey("1", "1"), true});
        list.add(new Object[] {new PatchSetKey("0", "0"), new PatchSetKey("0", "0"), true});
        list.add(new Object[] {new PatchSetKey("-1", "-1"), new PatchSetKey("-1", "-1"), true});
        list.add(new Object[] {new PatchSetKey(null, Integer.valueOf(5678)), new PatchSetKey(null, Integer.valueOf(5678)), true});
        list.add(new Object[] {new PatchSetKey(Integer.valueOf(1), null), new PatchSetKey(Integer.valueOf(1), null), true});

        Integer i = null;
        list.add(new Object[] {new PatchSetKey(i, i), new PatchSetKey(i, i), true});

        list.add(new Object[] {new PatchSetKey(Integer.valueOf(1234), Integer.valueOf(5678)), new PatchSetKey("1234", "5678"), true});
        list.add(new Object[] {new PatchSetKey(Integer.valueOf(1234), Integer.valueOf(1)), new PatchSetKey("1234", "1"), true});
        list.add(new Object[] {new PatchSetKey(Integer.valueOf(1), Integer.valueOf(1)), new PatchSetKey("1", "1"), true});
        list.add(new Object[] {new PatchSetKey(Integer.valueOf(0), Integer.valueOf(0)), new PatchSetKey("0", "0"), true});
        list.add(new Object[] {new PatchSetKey(Integer.valueOf(-1), Integer.valueOf(-1)), new PatchSetKey("-1", "-1"), true});

        list.add(new Object[] {new PatchSetKey(Integer.valueOf(1234), Integer.valueOf(5678)), new PatchSetKey(Integer.valueOf(1234), Integer.valueOf(5678)), true});
        list.add(new Object[] {new PatchSetKey(Integer.valueOf(1234), Integer.valueOf(1)), new PatchSetKey(Integer.valueOf(1234), Integer.valueOf(1)), true});
        list.add(new Object[] {new PatchSetKey(Integer.valueOf(1), Integer.valueOf(1)), new PatchSetKey(Integer.valueOf(1), Integer.valueOf(1)), true});
        list.add(new Object[] {new PatchSetKey(Integer.valueOf(0), Integer.valueOf(0)), new PatchSetKey(Integer.valueOf(0), Integer.valueOf(0)), true});
        list.add(new Object[] {new PatchSetKey(Integer.valueOf(-1), Integer.valueOf(-1)), new PatchSetKey(Integer.valueOf(-1), Integer.valueOf(-1)), true});

        list.add(new Object[] {new PatchSetKey(new Integer(1234), new Integer(1)), new PatchSetKey(new Integer(1234), new Integer(1)), true});
        list.add(new Object[] {new PatchSetKey(new Integer(1), new Integer(1)), new PatchSetKey(new Integer(1), new Integer(1)), true});
        list.add(new Object[] {new PatchSetKey(createPatchsetCreated("1234", "5678")), new PatchSetKey(createPatchsetCreated("1234", "5678")), true});
        list.add(new Object[] {new PatchSetKey(createPatchsetCreated("1234", "1")), new PatchSetKey(createPatchsetCreated("1234", "1")), true});
        list.add(new Object[] {new PatchSetKey(createPatchsetCreated("1", "1")), new PatchSetKey(createPatchsetCreated("1", "1")), true});

        list.add(new Object[] {new PatchSetKey(createPatchsetCreated("0", "0")), new PatchSetKey(createPatchsetCreated("0", "0")), true});
        list.add(new Object[] {new PatchSetKey(createPatchsetCreated("-1", "-1")), new PatchSetKey(createPatchsetCreated("-1", "-1")), true});

        // Not Equals
        list.add(new Object[] {new PatchSetKey("1234", "5679"), new PatchSetKey("1234", "5678"), false});
        list.add(new Object[] {new PatchSetKey("1234", "2"), new PatchSetKey("1234", "1"), false});
        list.add(new Object[] {new PatchSetKey("1", "0"), new PatchSetKey("1", "1"), false});
        list.add(new Object[] {new PatchSetKey("0", "1"), new PatchSetKey("0", "0"), false});
        list.add(new Object[] {new PatchSetKey("-1", "0"), new PatchSetKey("-1", "-1"), false});

        list.add(new Object[] {new PatchSetKey(null, Integer.valueOf(5679)), new PatchSetKey(null, Integer.valueOf(5678)), false});
        list.add(new Object[] {new PatchSetKey(Integer.valueOf(2), null), new PatchSetKey(Integer.valueOf(1), null), false});

        list.add(new Object[] {new PatchSetKey("1235", "5678"), new PatchSetKey("1234", "5678"), false});
        list.add(new Object[] {new PatchSetKey("1235", "1"), new PatchSetKey("1234", "1"), false});
        list.add(new Object[] {new PatchSetKey("2", "1"), new PatchSetKey("1", "1"), false});
        list.add(new Object[] {new PatchSetKey("1", "0"), new PatchSetKey("0", "0"), false});
        list.add(new Object[] {new PatchSetKey("0", "-1"), new PatchSetKey("-1", "-1"), false});

        list.add(new Object[] {new PatchSetKey(Integer.valueOf(1235), Integer.valueOf(5679)), new PatchSetKey("1234", "5678"), false});
        list.add(new Object[] {new PatchSetKey(Integer.valueOf(1234), Integer.valueOf(2)), new PatchSetKey("1234", "1"), false});
        list.add(new Object[] {new PatchSetKey(Integer.valueOf(1), Integer.valueOf(0)), new PatchSetKey("1", "1"), false});
        list.add(new Object[] {new PatchSetKey(Integer.valueOf(0), Integer.valueOf(1)), new PatchSetKey("0", "0"), false});
        list.add(new Object[] {new PatchSetKey(Integer.valueOf(-1), Integer.valueOf(0)), new PatchSetKey("-1", "-1"), false});

        list.add(new Object[] {new PatchSetKey(Integer.valueOf(1235), Integer.valueOf(5678)), new PatchSetKey(Integer.valueOf(1234), Integer.valueOf(5678)), false});
        list.add(new Object[] {new PatchSetKey(Integer.valueOf(1235), Integer.valueOf(1)), new PatchSetKey(Integer.valueOf(1234), Integer.valueOf(1)), false});
        list.add(new Object[] {new PatchSetKey(Integer.valueOf(0), Integer.valueOf(1)), new PatchSetKey(Integer.valueOf(1), Integer.valueOf(1)), false});
        list.add(new Object[] {new PatchSetKey(Integer.valueOf(1), Integer.valueOf(0)), new PatchSetKey(Integer.valueOf(0), Integer.valueOf(0)), false});
        list.add(new Object[] {new PatchSetKey(Integer.valueOf(0), Integer.valueOf(-1)), new PatchSetKey(Integer.valueOf(-1), Integer.valueOf(-1)), false});

        list.add(new Object[] {new PatchSetKey(new Integer(123), new Integer(1)), new PatchSetKey(new Integer(1234), new Integer(1)), false});
        list.add(new Object[] {new PatchSetKey(new Integer(0), new Integer(1)), new PatchSetKey(new Integer(1), new Integer(1)), false});

        list.add(new Object[] {new PatchSetKey(createPatchsetCreated("123", "5678")), new PatchSetKey(createPatchsetCreated("1234", "5678")), false});
        list.add(new Object[] {new PatchSetKey(createPatchsetCreated("123", "1")), new PatchSetKey(createPatchsetCreated("1234", "1")), false});
        list.add(new Object[] {new PatchSetKey(createPatchsetCreated("0", "1")), new PatchSetKey(createPatchsetCreated("1", "1")), false});
        list.add(new Object[] {new PatchSetKey(createPatchsetCreated("1", "0")), new PatchSetKey(createPatchsetCreated("0", "0")), false});
        list.add(new Object[] {new PatchSetKey(createPatchsetCreated("-1", "0")), new PatchSetKey(createPatchsetCreated("-1", "-1")), false});

        return list;
    }

    /**
     * Utility method.
     * @param changeNr changeNr
     * @param patchNr patchNr
     * @return PatchsetCreated mock.
     */
    public static PatchsetCreated createPatchsetCreated(String changeNr, String patchNr) {
        PatchsetCreated event = new PatchsetCreated();
        Change change = new Change();
        change.setBranch("branch");
        change.setId("Iddaaddaa123456789");
        change.setNumber(changeNr);
        Account account = new Account();
        account.setEmail("email@domain.com");
        account.setName("Name");
        change.setOwner(account);
        change.setProject("project");
        change.setSubject("subject");
        change.setUrl("http://gerrit/" + changeNr);
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber(patchNr);
        patch.setRevision("9999");
        event.setPatchset(patch);
        return event;
    }

    /**
     * test.
     */
    @Test
    public void testPatchSetKeyEquals() {
        assertEquals(res, key1.equals(key2));
    }

    /**
     * test.
     */
    @Test
    public void testPatchSetKeyHashCode() {
        assertEquals(res, key1.hashCode() == key2.hashCode());
    }

    /**
     * test.
     */
    @Test
    public void testPatchSetKeyCompareTo() {
        assertEquals(res, key1.compareTo(key2) == 0);
    }
}
