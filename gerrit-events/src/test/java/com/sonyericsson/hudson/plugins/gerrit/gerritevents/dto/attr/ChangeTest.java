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
import org.junit.Before;
import org.junit.Test;

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PROJECT;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.BRANCH;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.ID;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.NUMBER;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.SUBJECT;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.OWNER;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.URL;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.EMAIL;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * JUnit tests for {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change}.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class ChangeTest {
    private Account account;
    private JSONObject jsonAccount;

    /**
     * Sets up a dummy Account object and a JSON version before each test.
     */
    @Before
    public void setUp() {
        account = new Account();
        account.setEmail("robert.sandell@sonyericsson.com");
        account.setName("Bobby");
        jsonAccount = new JSONObject();
        jsonAccount.put(EMAIL, account.getEmail());
        jsonAccount.put(NAME, account.getName());
    }

    /**
     * Tests {@link Change#fromJson(net.sf.json.JSONObject)}.
     * @throws Exception if so.
     */
    @Test
    public void testFromJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put(PROJECT, "project");
        json.put(BRANCH, "branch");
        json.put(ID, "I2343434344");
        json.put(NUMBER, "100");
        json.put(SUBJECT, "subject");
        json.put(OWNER, jsonAccount);
        json.put(URL, "http://localhost:8080");
        Change change = new Change();
        change.fromJson(json);

        assertEquals(change.getProject(), "project");
        assertEquals(change.getBranch(), "branch");
        assertEquals(change.getId(), "I2343434344");
        assertEquals(change.getNumber(), "100");
        assertEquals(change.getSubject(), "subject");
        assertTrue(change.getOwner().equals(account));
        assertEquals(change.getUrl(), "http://localhost:8080");
    }

    /**
     * Tests {@link Change#fromJson(net.sf.json.JSONObject)}.
     * Without any JSON URL data.
     * @throws Exception if so.
     */
    @Test
    public void testFromJsonNoUrl() throws Exception {
        JSONObject json = new JSONObject();
        json.put(PROJECT, "project");
        json.put(BRANCH, "branch");
        json.put(ID, "I2343434344");
        json.put(NUMBER, "100");
        json.put(SUBJECT, "subject");
        json.put(OWNER, jsonAccount);
        Change change = new Change();
        change.fromJson(json);

        assertEquals(change.getProject(), "project");
        assertEquals(change.getBranch(), "branch");
        assertEquals(change.getId(), "I2343434344");
        assertEquals(change.getNumber(), "100");
        assertEquals(change.getSubject(), "subject");
        assertTrue(change.getOwner().equals(account));
        assertNull(change.getUrl());
    }

    /**
     * Tests {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change#Change(net.sf.json.JSONObject)}.
     * @throws Exception if so.
     */
    @Test
    public void testInitJson() throws Exception {
        JSONObject json = new JSONObject();
        json.put(PROJECT, "project");
        json.put(BRANCH, "branch");
        json.put(ID, "I2343434344");
        json.put(NUMBER, "100");
        json.put(SUBJECT, "subject");
        json.put(OWNER, jsonAccount);
        json.put(URL, "http://localhost:8080");
        Change change = new Change(json);

        assertEquals(change.getProject(), "project");
        assertEquals(change.getBranch(), "branch");
        assertEquals(change.getId(), "I2343434344");
        assertEquals(change.getNumber(), "100");
        assertEquals(change.getSubject(), "subject");
        assertTrue(change.getOwner().equals(account));
        assertEquals(change.getUrl(), "http://localhost:8080");
    }

    /**
     * Tests {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change#equals(Object)}.
     * @throws Exception if so.
     */
    @Test
    public void testEquals() throws Exception {
        JSONObject json = new JSONObject();
        json.put(PROJECT, "project");
        json.put(BRANCH, "branch");
        json.put(ID, "I2343434344");
        json.put(NUMBER, "100");
        json.put(SUBJECT, "subject");
        json.put(OWNER, jsonAccount);
        json.put(URL, "http://localhost:8080");
        Change change = new Change(json);

        Change change2 = new Change();
        change2.setProject("project");
        change2.setBranch("branch");
        change2.setId("I2343434344");
        change2.setNumber("100");
        change2.setSubject("subject");
        change2.setOwner(account);
        change2.setUrl("http://localhost:8080");

        assertTrue(change.equals(change2));
    }
}
