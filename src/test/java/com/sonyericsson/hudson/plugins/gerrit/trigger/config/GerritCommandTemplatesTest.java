/*
 *  The MIT License
 *
 *  Copyright 2026 Amarula Solutions. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.config;

import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

//CS IGNORE MagicNumber FOR NEXT 100 LINES. REASON: Mocks tests.

/**
 * Tests for {@link GerritCommandTemplates}.
 */
class GerritCommandTemplatesTest {

    /**
     * Test that default constructor sets non-null defaults.
     */
    @Test
    void testDefaultConstructor() {
        GerritCommandTemplates c = new GerritCommandTemplates();
        assertNotNull(c.getCmdBuildStarted());
        assertNotNull(c.getCmdBuildSuccessful());
        assertNotNull(c.getCmdBuildFailed());
        assertNotNull(c.getCmdBuildUnstable());
        assertNotNull(c.getCmdBuildNotBuilt());
        assertNotNull(c.getCmdBuildAborted());
        assertTrue(c.getCmdBuildStarted().contains("Build Started"));
        assertTrue(c.getCmdBuildSuccessful().contains("Build Successful"));
        assertTrue(c.getCmdBuildFailed().contains("Build Failed"));
        assertTrue(c.getCmdBuildUnstable().contains("Build Unstable"));
        assertTrue(c.getCmdBuildNotBuilt().contains("No Builds Executed"));
        assertTrue(c.getCmdBuildAborted().contains("Build Aborted"));
    }

    /**
     * Test that JSON constructor parses values correctly.
     */
    @Test
    void testJsonConstructor() {
        String jsonStr = "{"
                + "\"gerritVerifiedCmdBuildStarted\":\"cmdStarted\","
                + "\"gerritVerifiedCmdBuildSuccessful\":\"cmdSuccess\","
                + "\"gerritVerifiedCmdBuildFailed\":\"cmdFailed\","
                + "\"gerritVerifiedCmdBuildUnstable\":\"cmdUnstable\","
                + "\"gerritVerifiedCmdBuildNotBuilt\":\"cmdNotBuilt\","
                + "\"gerritVerifiedCmdBuildAborted\":\"cmdAborted\""
                + "}";
        JSONObject formData = (JSONObject)JSONSerializer.toJSON(jsonStr);
        GerritCommandTemplates c = new GerritCommandTemplates(formData);

        assertEquals("cmdStarted", c.getCmdBuildStarted());
        assertEquals("cmdSuccess", c.getCmdBuildSuccessful());
        assertEquals("cmdFailed", c.getCmdBuildFailed());
        assertEquals("cmdUnstable", c.getCmdBuildUnstable());
        assertEquals("cmdNotBuilt", c.getCmdBuildNotBuilt());
        assertEquals("cmdAborted", c.getCmdBuildAborted());
    }

    /**
     * Test copy constructor.
     */
    @Test
    void testCopyConstructor() {
        GerritCommandTemplates source = new GerritCommandTemplates();
        source.setCmdBuildStarted("customStarted");
        source.setCmdBuildSuccessful("customSuccess");

        GerritCommandTemplates copy = new GerritCommandTemplates(source);
        assertEquals("customStarted", copy.getCmdBuildStarted());
        assertEquals("customSuccess", copy.getCmdBuildSuccessful());
        // Other fields should have their defaults from source
        assertNotNull(copy.getCmdBuildFailed());
    }

    /**
     * Test fromConfig factory method.
     */
    @Test
    void testFromConfig() {
        Config config = new Config();
        config.setGerritCmdBuildStarted("startedCmd");
        config.setGerritCmdBuildSuccessful("successCmd");

        GerritCommandTemplates c = GerritCommandTemplates.fromConfig(config);
        assertEquals("startedCmd", c.getCmdBuildStarted());
        assertEquals("successCmd", c.getCmdBuildSuccessful());
        assertNotNull(c.getCmdBuildFailed());
    }

    /**
     * Test readResolve backward compat for aborted command.
     */
    @Test
    void testReadResolve() {
        GerritCommandTemplates c = new GerritCommandTemplates();
        // Simulate old data where aborted is null but failed is set
        c.setCmdBuildAborted(null);
        c.setCmdBuildFailed("fallbackCommand");

        Object resolved = c.readResolve();
        assertNotNull(resolved);
        assertEquals("fallbackCommand", c.getCmdBuildAborted(),
                "Aborted command should fall back to failed command");
    }
}
