/*
 *  The MIT License
 *
 *  Copyright 2013 Sony Mobile Communications AB. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.MockGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.model.AbstractProject;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link GerritTriggerParameters}.
 *
 * @author <a href="robert.sandell@sonymobile.com">Robert Sandell</a>
 */
public class GerritTriggerParametersTest {

    /**
     * Jenkins rule instance.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 3 LINES. REASON: Mocks tests.
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private MockGerritHudsonTriggerConfig config;

    /**
     * Run before every test to setup some mocks.
     */
    @Before
    public void setup() {
        config = Setup.createConfig();
        GerritServer server = new GerritServer(PluginImpl.DEFAULT_SERVER_NAME);
        server.setConfig(config);
        PluginImpl plugin = PluginImpl.getInstance();
        assertNotNull(plugin);
        plugin.setServers(Arrays.asList(server));
    }

    // CS IGNORE LineLength FOR NEXT 3 LINES. REASON: JavaDoc.
    /**
     * Tests {@link GerritTriggerParameters#setOrCreateParameters(com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent, hudson.model.Job, List)}.
     * The {@link GerritTriggerParameters#GERRIT_CHANGE_URL} should contain the base url from the event provider.
     *
     * @throws Exception if so
     */
    @Test
    public void setOrCreateParametersProviderUrl() throws Exception {
        PatchsetCreated created = Setup.createPatchsetCreated();
        AbstractProject project = j.createFreeStyleProject();
        LinkedList<ParameterValue> parameters = new LinkedList<ParameterValue>();
        GerritTriggerParameters.setOrCreateParameters(created, project, parameters);
        StringParameterValue param = findParameter(GerritTriggerParameters.GERRIT_CHANGE_URL, parameters);
        assertNotNull(param);
        assertTrue(param.value.startsWith(created.getProvider().getUrl()));
    }

    // CS IGNORE LineLength FOR NEXT 3 LINES. REASON: JavaDoc.
    /**
     * Tests {@link GerritTriggerParameters#setOrCreateParameters(com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent, hudson.model.Job, List)}.
     * The {@link GerritTriggerParameters#GERRIT_CHANGE_URL} should contain the base url from the project trigger.
     *
     * @throws Exception if so
     */
    @Test
    public void setOrCreateParametersUrlNoProvider() throws Exception {
        PatchsetCreated created = Setup.createPatchsetCreated();
        created.setProvider(null);
        AbstractProject project = j.createFreeStyleProject();
        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        LinkedList<ParameterValue> parameters = new LinkedList<ParameterValue>();
        GerritTriggerParameters.setOrCreateParameters(created, project, parameters);
        StringParameterValue param = findParameter(GerritTriggerParameters.GERRIT_CHANGE_URL, parameters);
        assertNotNull(param);
        assertTrue(param.value.startsWith(config.getGerritFrontEndUrl()));
    }

    // CS IGNORE LineLength FOR NEXT 3 LINES. REASON: JavaDoc.
    /**
     * Tests {@link GerritTriggerParameters#setOrCreateParameters(com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent, hudson.model.Job, List)}.
     * The {@link GerritTriggerParameters#GERRIT_CHANGE_URL} should contain the base url from the first server.
     *
     * @throws Exception if so
     */
    @Test
    public void setOrCreateParametersUrlNoProviderAnyServer() throws Exception {
        PatchsetCreated created = Setup.createPatchsetCreated();
        created.setProvider(null);
        AbstractProject project = j.createFreeStyleProject();
        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        trigger.setServerName(GerritServer.ANY_SERVER);
        LinkedList<ParameterValue> parameters = new LinkedList<ParameterValue>();
        GerritTriggerParameters.setOrCreateParameters(created, project, parameters);
        StringParameterValue param = findParameter(GerritTriggerParameters.GERRIT_CHANGE_URL, parameters);
        assertNotNull(param);
        assertTrue(param.value.startsWith(config.getGerritFrontEndUrl()));
    }

    /**
     * Finds the given parameter in the list.
     *
     * @param name       the parameter to find.
     * @param parameters the list
     * @return the value or null if none was found
     */
    private StringParameterValue findParameter(GerritTriggerParameters name, List<ParameterValue> parameters) {
        for (ParameterValue val : parameters) {
            if (name.name().equals(val.getName())) {
                return (StringParameterValue)val;
            }
        }
        return null;
    }
}
