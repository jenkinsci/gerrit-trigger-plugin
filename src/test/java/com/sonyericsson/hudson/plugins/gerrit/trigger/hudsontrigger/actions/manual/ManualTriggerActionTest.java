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

package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.manual;

import static org.mockito.Mockito.any;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

//CS IGNORE LineLength FOR NEXT 1 LINES. REASON: static import
import static com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.manual.ManualTriggerAction.ID_SEPARATOR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

//CS IGNORE LineLength FOR NEXT 1 LINES. REASON: static java import.

/**
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(PluginImpl.class)
public class ManualTriggerActionTest {

    /**
     * Tests {@link ManualTriggerAction#getCodeReview(net.sf.json.JSONObject, int)}.
     * With Code-Review patchset info.
     * @throws Exception if so.
     */
    @Test
    public void testGetCodeReview() throws Exception {
        JSONObject json = new JSONObject();
        JSONObject currentPatchSet = new JSONObject();

        JSONArray approvals = new JSONArray();
        JSONObject crw = new JSONObject();
        crw.put("type", "Code-Review");
        crw.put("value", "2");
        approvals.add(crw);
        crw = new JSONObject();
        crw.put("type", "Code-Review");
        crw.put("value", "1");
        approvals.add(crw);
        crw = new JSONObject();
        crw.put("type", "Verified");
        crw.put("value", "1");
        approvals.add(crw);
        crw = new JSONObject();
        crw.put("type", "Code-Review");
        crw.put("value", "-1");
        approvals.add(crw);
        currentPatchSet.put("approvals", approvals);
        currentPatchSet.put("number", 2);

        json.put("currentPatchSet", currentPatchSet);

        ManualTriggerAction action = new ManualTriggerAction();

        ManualTriggerAction.HighLow highLow = action.getCodeReview(json, 2);

        assertEquals(2, highLow.getHigh());
        assertEquals(-1, highLow.getLow());

        highLow = action.getCodeReview(json, 1);

        assertEquals(0, highLow.getHigh());
        assertEquals(0, highLow.getLow());
    }

    /**
     * Tests {@link ManualTriggerAction#getCodeReview(net.sf.json.JSONObject)}.
     * With an empty JSON object.
     * @throws Exception if so.
     */
    @Test
    public void testGetCodeReviewNoPatchSet() throws Exception {
        JSONObject json = new JSONObject();

        ManualTriggerAction action = new ManualTriggerAction();

        ManualTriggerAction.HighLow highLow = action.getCodeReview(json, 2);

        assertEquals(0, highLow.getHigh());
        assertEquals(0, highLow.getLow());
    }

    /**
     * Tests {@link ManualTriggerAction#getVerified(net.sf.json.JSONObject, int)}.
     * With Verified patchset info.
     * @throws Exception if so.
     */
    @Test
    public void testGetVerified() throws Exception {
        JSONObject json = new JSONObject();
        JSONObject currentPatchSet = new JSONObject();

        JSONArray approvals = new JSONArray();
        JSONObject crw = new JSONObject();
        crw.put("type", "Verified");
        crw.put("value", "2");
        approvals.add(crw);
        crw = new JSONObject();
        crw.put("type", "Verified");
        crw.put("value", "1");
        approvals.add(crw);
        crw = new JSONObject();
        crw.put("type", "Code-Review");
        crw.put("value", "1");
        approvals.add(crw);
        crw = new JSONObject();
        crw.put("type", "Verified");
        crw.put("value", "-1");
        approvals.add(crw);
        currentPatchSet.put("approvals", approvals);
        currentPatchSet.put("number", 2);

        json.put("currentPatchSet", currentPatchSet);

        ManualTriggerAction action = new ManualTriggerAction();

        ManualTriggerAction.HighLow highLow = action.getVerified(json, 2);

        assertEquals(2, highLow.getHigh());
        assertEquals(-1, highLow.getLow());

        highLow = action.getVerified(json, 1);

        assertEquals(0, highLow.getHigh());
        assertEquals(0, highLow.getLow());
    }

    /**
     * Tests {@link ManualTriggerAction#getVerified(net.sf.json.JSONObject)}.
     * With an empty JSON object.
     * @throws Exception if so.
     */
    @Test
    public void testGetVerifiedNoPatchSet() throws Exception {
        JSONObject json = new JSONObject();

        ManualTriggerAction action = new ManualTriggerAction();

        ManualTriggerAction.HighLow highLow = action.getVerified(json, 2);

        assertEquals(0, highLow.getHigh());
        assertEquals(0, highLow.getLow());
    }

    /**
     * Tests {@link ManualTriggerAction#generateTheId(net.sf.json.JSONObject, net.sf.json.JSONObject)}.
     * With patch info.
     */
    @Test
    public void testGenerateTheId() {
        JSONObject change = new JSONObject();
        change.put("id", "I10abc01");
        change.put("number", "100");

        JSONObject patch = new JSONObject();
        patch.put("revision", "10abc01");
        patch.put("number", "1");

        ManualTriggerAction action = new ManualTriggerAction();

        String id = action.generateTheId(change, patch);

        assertEquals("I10abc01" + ID_SEPARATOR + "10abc01" + ID_SEPARATOR + "100" + ID_SEPARATOR + "1", id);
    }

    /**
     * Tests {@link ManualTriggerAction#generateTheId(net.sf.json.JSONObject, net.sf.json.JSONObject)}.
     * With no patch info.
     */
    @Test
    public void testGenerateTheIdNoPatch() {
        JSONObject change = new JSONObject();
        change.put("id", "I10abc01");
        change.put("number", "100");

        ManualTriggerAction action = new ManualTriggerAction();

        String id = action.generateTheId(change, null);

        assertEquals("I10abc01" + ID_SEPARATOR + "100", id);
    }

    /**
     * Tests {@link ManualTriggerAction#indexResult(java.util.List)}.
     * @throws Exception if so.
     */
    @Test
    public void testIndexResult() throws Exception {
        JSONObject change = new JSONObject();
        change.put("id", "I10abc01");
        change.put("number", "100");

        JSONArray patchSets = new JSONArray();

        JSONObject patch = new JSONObject();
        patch.put("revision", "10abc01");
        patch.put("number", "1");
        patchSets.add(patch);

        change.put("patchSets", patchSets);

        List<JSONObject> result = new LinkedList<JSONObject>();
        result.add(change);

        JSONObject type = new JSONObject();
        type.put("type", "result");
        result.add(type);

        ManualTriggerAction action = new ManualTriggerAction();

        HashMap<String, JSONObject> map = Whitebox.invokeMethod(action, "indexResult", result);

        assertSame(change, map.get("I10abc01" + ID_SEPARATOR + "100"));
        assertEquals(patch.toString(),
                map.get("I10abc01" + ID_SEPARATOR + "10abc01" + ID_SEPARATOR + "100" + ID_SEPARATOR + "1").toString());
    }

    /**
     * Tests {@link ManualTriggerAction#getGerritUrl(net.sf.json.JSONObject, String)} with a URL in the event info.
     */
    @Test
    public void testGetGerritUrlJson() {
        JSONObject change = new JSONObject();
        change.put("url", "http://gerrit/test");
        ManualTriggerAction action = new ManualTriggerAction();
        String url = action.getGerritUrl(change, null);

        assertEquals("http://gerrit/test", url);
    }

    /**
     * Tests {@link ManualTriggerAction#getGerritUrl(net.sf.json.JSONObject, String)} without a URL in the event info.
     */
    @Test
    public void testGetGerritUrlJsonNoUrl() {
        JSONObject change = new JSONObject();
        change.put("number", "100");
        PowerMockito.mockStatic(PluginImpl.class);
        PluginImpl plugin = mock(PluginImpl.class);
        GerritServer server = mock(GerritServer.class);
        when(plugin.getServer(any(String.class))).thenReturn(server);
        when(PluginImpl.getServer_(any(String.class))).thenReturn(server);
        when(server.getConfig()).thenReturn(Setup.createConfig());
        PowerMockito.when(PluginImpl.getInstance()).thenReturn(plugin);

        ManualTriggerAction action = new ManualTriggerAction();
        String url = action.getGerritUrl(change, null);

        assertEquals("http://gerrit/100", url);
    }
}
