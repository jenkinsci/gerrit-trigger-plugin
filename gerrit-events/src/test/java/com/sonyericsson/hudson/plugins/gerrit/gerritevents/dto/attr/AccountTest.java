/*
 *  The MIT License
 *
 *  Copyright 2011 Sony Ericsson Mobile Communications. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys;
import net.sf.json.JSONObject;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * Tests for {@link Account}.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class AccountTest {

    /**
     * Tests {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account#getNameAndEmail()}.
     * @throws Exception if so.
     */
    @Test
    public void testGetNameAndEmail() throws Exception {
        Account account = new Account();
        account.setEmail("someone@somewhere.com");
        account.setName("someone");
        String expected = "\"someone\" <someone@somewhere.com>";
        assertEquals(expected, account.getNameAndEmail());
    }

    /**
     * Tests {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account#getNameAndEmail()}.
     * With a dot "." in the name and email.
     * @throws Exception if so.
     */
    @Test
    public void testGetNameAndEmailDots() throws Exception {
        Account account = new Account();
        account.setEmail("robert.sandell@somewhere.com");
        account.setName("robert.sandell");
        String expected = "\"robert.sandell\" <robert.sandell@somewhere.com>";
        assertEquals(expected, account.getNameAndEmail());
    }

    /**
     * Tests {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account#getNameAndEmail()}.
     * With a space in the name.
     * @throws Exception if so.
     */
    @Test
    public void testGetNameAndEmailSpace() throws Exception {
        Account account = new Account();
        account.setEmail("robert.sandell@somewhere.com");
        account.setName("Robert Sandell");
        String expected = "\"Robert Sandell\" <robert.sandell@somewhere.com>";
        assertEquals(expected, account.getNameAndEmail());
    }

    /**
     * Tests {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account#getNameAndEmail()}.
     * With a space in the name and the data retrieved from a JSONObject.
     * @throws Exception if so.
     */
    @Test
    public void testGetNameAndEmailSpaceFromJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put(GerritEventKeys.NAME, "Robert Sandell");
        json.put(GerritEventKeys.EMAIL, "robert.sandell@somewhere.com");
        Account account = new Account(json);
        String expected = "\"Robert Sandell\" <robert.sandell@somewhere.com>";
        assertEquals(expected, account.getNameAndEmail());
    }

    /**
     * Tests {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account#getNameAndEmail()}.
     * With null values.
     * @throws Exception if so.
     */
    @Test
    public void testGetNameAndEmailNull() throws Exception {
        Account account = new Account();
        account.setEmail(null);
        account.setName(null);
        String expected = null;
        assertEquals(expected, account.getNameAndEmail());
    }

    /**
     * Tests {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account#getNameAndEmail()}.
     * With empty string values.
     * @throws Exception if so.
     */
    @Test
    public void testGetNameAndEmailEmpty() throws Exception {
        Account account = new Account();
        account.setEmail("");
        account.setName("");
        String expected = "";
        assertEquals(expected, account.getNameAndEmail());
    }
}
