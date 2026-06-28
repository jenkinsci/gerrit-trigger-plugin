/*
 * The MIT License
 *
 * Copyright (c) 2024 Amarula Solutions. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.workflow;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.sql.Timestamp;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link CheckInput}.
 */
public class CheckInputTest {

    private static final Gson GSON = new Gson();

    /**
     * Tests the default constructor creates an object with all null fields.
     */
    @Test
    public void testDefaultConstructor() {
        CheckInput input = new CheckInput();
        assertNull(input.getCheckerUuid());
        assertNull(input.getState());
        assertNull(input.getMessage());
        assertNull(input.getUrl());
        assertNull(input.getStarted());
        assertNull(input.getFinished());
    }

    /**
     * Tests the constructor with required fields.
     */
    @Test
    public void testConstructorWithRequiredFields() {
        CheckInput input = new CheckInput("checker:uuid", "SUCCESSFUL", "All good", "http://example.com");
        assertEquals("checker:uuid", input.getCheckerUuid());
        assertEquals("SUCCESSFUL", input.getState());
        assertEquals("All good", input.getMessage());
        assertEquals("http://example.com", input.getUrl());
    }

    /**
     * Tests getters and setters for all fields.
     */
    @Test
    public void testGettersAndSetters() {
        CheckInput input = new CheckInput();
        input.setCheckerUuid("uuid-123");
        input.setState("FAILED");
        input.setMessage("Build failed");
        input.setUrl("http://jenkins.example.com/job/test/1/console");

        // CS IGNORE MagicNumber FOR NEXT 2 LINES. REASON: Test data.
        Timestamp started = new Timestamp(1000000L);
        Timestamp finished = new Timestamp(2000000L);
        input.setStarted(started);
        input.setFinished(finished);

        assertEquals("uuid-123", input.getCheckerUuid());
        assertEquals("FAILED", input.getState());
        assertEquals("Build failed", input.getMessage());
        assertEquals("http://jenkins.example.com/job/test/1/console", input.getUrl());
        assertEquals(started, input.getStarted());
        assertEquals(finished, input.getFinished());
    }

    /**
     * Tests Gson serialization produces valid JSON with expected field names.
     */
    @Test
    public void testGsonSerialization() {
        CheckInput input = new CheckInput("checker:test", "RUNNING", "In progress", "http://example.com");
        String json = GSON.toJson(input);
        JsonObject obj = GSON.fromJson(json, JsonObject.class);

        assertEquals("checker:test", obj.get("checkerUuid").getAsString());
        assertEquals("RUNNING", obj.get("state").getAsString());
        assertEquals("In progress", obj.get("message").getAsString());
        assertEquals("http://example.com", obj.get("url").getAsString());
    }

    /**
     * Tests Gson deserialization preserves all fields.
     */
    @Test
    public void testGsonDeserialization() {
        String json = "{\"checkerUuid\":\"checker:a\",\"state\":\"SUCCESSFUL\","
                + "\"message\":\"OK\",\"url\":\"http://example.com\"}";
        CheckInput input = GSON.fromJson(json, CheckInput.class);

        assertEquals("checker:a", input.getCheckerUuid());
        assertEquals("SUCCESSFUL", input.getState());
        assertEquals("OK", input.getMessage());
        assertEquals("http://example.com", input.getUrl());
    }

    /**
     * Tests Gson roundtrip: serialize then deserialize preserves all fields.
     */
    @Test
    public void testGsonRoundtrip() {
        CheckInput original = new CheckInput("uuid", "FAILED", "Error message", "http://console.example.com");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        original.setStarted(now);

        String json = GSON.toJson(original);
        CheckInput restored = GSON.fromJson(json, CheckInput.class);

        assertEquals(original.getCheckerUuid(), restored.getCheckerUuid());
        assertEquals(original.getState(), restored.getState());
        assertEquals(original.getMessage(), restored.getMessage());
        assertEquals(original.getUrl(), restored.getUrl());
        assertNotNull(restored.getStarted());
    }

    /**
     * Tests that timestamp fields serialize correctly to JSON.
     */
    @Test
    public void testTimestampSerialization() {
        CheckInput input = new CheckInput();
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        input.setStarted(ts);

        String json = GSON.toJson(input);
        // Gson serializes Timestamp; verify the "started" field is present
        JsonObject obj = GSON.fromJson(json, JsonObject.class);
        assertTrue("JSON should have started field",
                obj.has("started"));
        assertNotNull("started field should not be null",
                obj.get("started").getAsString());
    }
}
