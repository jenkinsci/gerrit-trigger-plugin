/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventType;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.RetriggerAction;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.RetriggerAllAction;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import net.sf.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.*;
import static com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerParameters.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
//CS IGNORE MagicNumber FOR NEXT 720 LINES. REASON: testdata.

/**
 * Tests make ref spec.
 * TODO move testMakeRefSpec* to StringUtilTest
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({AbstractProject.class, ToGerritRunListener.class, PluginImpl.class })
public class GerritTriggerTest {
    /**
     * test.
     */
    @Test
    public void testMakeRefSpec1() {
        PatchsetCreated event = new PatchsetCreated();
        Change change = new Change();
        change.setNumber("1");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        event.setPatchset(patch);
        String expResult = StringUtil.REFSPEC_PREFIX + "01/1/1";
        String result = StringUtil.makeRefSpec(event);
        assertEquals(expResult, result);
    }

    /**
     * test.
     */
    @Test
    public void testMakeRefSpec2() {

        PatchsetCreated event = new PatchsetCreated();
        Change change = new Change();
        change.setNumber("12");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        event.setPatchset(patch);
        String expResult = StringUtil.REFSPEC_PREFIX + "12/12/1";
        String result = StringUtil.makeRefSpec(event);
        assertEquals(expResult, result);
    }

    /**
     * test.
     */
    @Test
    public void testMakeRefSpec3() {

        PatchsetCreated event = new PatchsetCreated();
        Change change = new Change();
        change.setNumber("123");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        event.setPatchset(patch);
        String expResult = StringUtil.REFSPEC_PREFIX + "23/123/1";
        String result = StringUtil.makeRefSpec(event);
        assertEquals(expResult, result);
    }

    /**
     * test.
     */
    @Test
    public void testMakeRefSpec4() {
        PatchsetCreated event = new PatchsetCreated();
        Change change = new Change();
        change.setNumber("2131");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        event.setPatchset(patch);
        String expResult = StringUtil.REFSPEC_PREFIX + "31/2131/1";
        String result = StringUtil.makeRefSpec(event);
        assertEquals(expResult, result);
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with an average buildScheduleDelay 20.
     */
    @Test
    public void testScheduleWithAverageBuildScheduleDelay() {
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullDisplayName()).thenReturn("MockedProject");
        PowerMockito.mockStatic(PluginImpl.class);
        PluginImpl plugin = PowerMockito.mock(PluginImpl.class);
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        config = spy(config);
        doReturn("http://mock.url").when(config).getGerritFrontEndUrlFor(any(String.class), any(String.class));
        when(plugin.getConfig()).thenReturn(config);
        PowerMockito.when(PluginImpl.getInstance()).thenReturn(plugin);
        when(config.getBuildScheduleDelay()).thenReturn(20);
        GerritTrigger trigger = new GerritTrigger(null, 0, 0, 0, 0, 0, 0, 0, 0, true, true, true, false, false, false,
                "", "", "", "", "", "", null);
        trigger.start(project, true);
        PatchsetCreated event = Setup.createPatchsetCreated();
        GerritCause gerritCause = new GerritCause(event, true);
        gerritCause = spy(gerritCause);
        doReturn("http://mock.url").when(gerritCause).getUrl();
        trigger.schedule(gerritCause, event);
        verify(project).scheduleBuild2(
                eq(20),
                same(gerritCause),
                isA(Action.class),
                isA(Action.class),
                isA(Action.class),
                isA(Action.class));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with an negative buildScheduleDelay -20.
     */
    @Test
    public void testScheduleWithNegativeBuildScheduleDelay() {
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullDisplayName()).thenReturn("MockedProject");
        PowerMockito.mockStatic(PluginImpl.class);
        PluginImpl plugin = PowerMockito.mock(PluginImpl.class);
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        config = spy(config);
        doReturn("http://mock.url").when(config).getGerritFrontEndUrlFor(any(String.class), any(String.class));
        when(plugin.getConfig()).thenReturn(config);
        PowerMockito.when(PluginImpl.getInstance()).thenReturn(plugin);
        when(config.getBuildScheduleDelay()).thenReturn(-20);
        GerritTrigger trigger = new GerritTrigger(null, 0, 0, 0, 0, 0, 0, 0, 0, true, true, true, false, false, false,
                "", "", "", "", "", "", null);
        trigger.start(project, true);
        PatchsetCreated event = Setup.createPatchsetCreated();
        GerritCause gerritCause = new GerritCause(event, true);
        gerritCause = spy(gerritCause);
        doReturn("http://mock.url").when(gerritCause).getUrl();
        trigger.schedule(gerritCause, event);
        verify(project).scheduleBuild2(
                //negative value will be reset into default value 3
                eq(3),
                same(gerritCause),
                isA(Action.class),
                isA(Action.class),
                isA(Action.class),
                isA(Action.class));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with an negative buildScheduleDelay 10000.
     */
    @Test
    public void testScheduleWithMaximumBuildScheduleDelay() {
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullDisplayName()).thenReturn("MockedProject");
        PowerMockito.mockStatic(PluginImpl.class);
        PluginImpl plugin = PowerMockito.mock(PluginImpl.class);
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        config = spy(config);
        doReturn("http://mock.url").when(config).getGerritFrontEndUrlFor(any(String.class), any(String.class));
        when(plugin.getConfig()).thenReturn(config);
        PowerMockito.when(PluginImpl.getInstance()).thenReturn(plugin);
        when(config.getBuildScheduleDelay()).thenReturn(10000);
        GerritTrigger trigger = new GerritTrigger(null, 0, 0, 0, 0, 0, 0, 0, 0, true, true, true, false, false, false,
                "", "", "", "", "", "", null);
        trigger.start(project, true);
        PatchsetCreated event = Setup.createPatchsetCreated();
        GerritCause gerritCause = new GerritCause(event, true);
        gerritCause = spy(gerritCause);
        doReturn("http://mock.url").when(gerritCause).getUrl();
        trigger.schedule(gerritCause, event);
        verify(project).scheduleBuild2(
                eq(10000),
                same(gerritCause),
                isA(Action.class),
                isA(Action.class),
                isA(Action.class),
                isA(Action.class));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that
     * {@link hudson.model.AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with correct parameters when there are some default parameters present.
     */
    @Test
    public void testScheduleWithDefaultParameters() {

        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullDisplayName()).thenReturn("MockedProject");
        ParametersDefinitionProperty parameters = mock(ParametersDefinitionProperty.class);
        List<ParameterDefinition> list = new LinkedList<ParameterDefinition>();
        list.add(new StringParameterDefinition("MOCK_PARAM", "mock_value"));
        when(parameters.getParameterDefinitions()).thenReturn(list);
        when(project.getProperty(ParametersDefinitionProperty.class)).thenReturn(parameters);

        GerritTrigger trigger = new GerritTrigger(null, 0, 0, 0, 0, 0, 0, 0, 0, true, true, true, false, false, false,
                "", "", "", "", "", "", null);
        trigger.start(project, true);
        PatchsetCreated event = Setup.createPatchsetCreated();
        GerritCause gerritCause = new GerritCause(event, true);
        gerritCause = spy(gerritCause);
        doReturn("http://mock.url").when(gerritCause).getUrl();
        PowerMockito.mockStatic(PluginImpl.class);
        PluginImpl plugin = PowerMockito.mock(PluginImpl.class);
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        config = spy(config);
        doReturn("http://mock.url").when(config).getGerritFrontEndUrlFor(any(String.class), any(String.class));
        when(plugin.getConfig()).thenReturn(config);
        PowerMockito.when(PluginImpl.getInstance()).thenReturn(plugin);
        trigger.schedule(gerritCause, event);

        verify(project).scheduleBuild2(
                anyInt(),
                same(gerritCause),
                isA(Action.class),
                isA(Action.class),
                isA(Action.class),
                isParameterActionWithStringParameterValue("MOCK_PARAM", "mock_value"));
        //Just to make sure the normal arguments are there as well.
        verify(project).scheduleBuild2(
                anyInt(),
                same(gerritCause),
                isA(Action.class),
                isA(Action.class),
                isA(Action.class),
                isParameterActionWithStringParameterValue(GERRIT_CHANGE_URL.name(), "http://mock.url"));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with correct parameters when there are no default parameters present.
     */
    @Test
    public void testScheduleWithNoDefaultParameters() {
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullDisplayName()).thenReturn("MockedProject");
        ParametersDefinitionProperty parameters = mock(ParametersDefinitionProperty.class);
        when(parameters.getParameterDefinitions()).thenReturn(Collections.EMPTY_LIST);
        when(project.getProperty(ParametersDefinitionProperty.class)).thenReturn(parameters);

        GerritTrigger trigger = new GerritTrigger(null, 0, 0, 0, 0, 0, 0, 0, 0, true, true, true, false, false, false,
                "", "", "", "", "", "", null);
        trigger.start(project, true);
        PatchsetCreated event = Setup.createPatchsetCreated();
        GerritCause gerritCause = new GerritCause(event, true);
        gerritCause = spy(gerritCause);
        doReturn("http://mock.url").when(gerritCause).getUrl();
        PowerMockito.mockStatic(PluginImpl.class);
        PluginImpl plugin = PowerMockito.mock(PluginImpl.class);
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        config = spy(config);
        doReturn("http://mock.url").when(config).getGerritFrontEndUrlFor(any(String.class), any(String.class));
        when(plugin.getConfig()).thenReturn(config);
        PowerMockito.when(PluginImpl.getInstance()).thenReturn(plugin);

        trigger.schedule(gerritCause, event);

        verify(project).scheduleBuild2(
                anyInt(),
                same(gerritCause),
                isA(Action.class),
                isA(Action.class),
                isA(Action.class),
                isParameterActionWithStringParameterValue(GERRIT_CHANGE_ID.name(), event.getChange().getId()));
        //Just to make sure one more normal arguments is there as well.
        verify(project).scheduleBuild2(
                anyInt(),
                same(gerritCause),
                isA(Action.class),
                isA(Action.class),
                isA(Action.class),
                isParameterActionWithStringParameterValue(GERRIT_CHANGE_URL.name(), "http://mock.url"));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with correct change owner and uploader parameters when there are no default parameters present.
     */
    @Test
    public void testScheduleWithOwnerAndUploader() {
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullDisplayName()).thenReturn("MockedProject");
        ParametersDefinitionProperty parameters = mock(ParametersDefinitionProperty.class);
        when(parameters.getParameterDefinitions()).thenReturn(Collections.EMPTY_LIST);
        when(project.getProperty(ParametersDefinitionProperty.class)).thenReturn(parameters);

        Account owner = new Account("Bobby", "bobby@somewhere.com");
        Account uploader = new Account("Nisse", "nisse@acme.org");

        GerritTrigger trigger = new GerritTrigger(null, 0, 0, 0, 0, 0, 0, 0, 0, true, false, true, false, false, false,
                "", "", "", "", "", "", null);
        trigger.start(project, true);
        PatchsetCreated event = Setup.createPatchsetCreated();
        event.getChange().setOwner(owner);
        event.getPatchSet().setUploader(uploader);
        event.setAccount(uploader);
        GerritCause gerritCause = new GerritCause(event, true);
        gerritCause = spy(gerritCause);
        doReturn("http://mock.url").when(gerritCause).getUrl();
        PowerMockito.mockStatic(PluginImpl.class);
        PluginImpl plugin = PowerMockito.mock(PluginImpl.class);
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        config = spy(config);
        doReturn("http://mock.url").when(config).getGerritFrontEndUrlFor(any(String.class), any(String.class));
        when(plugin.getConfig()).thenReturn(config);
        PowerMockito.when(PluginImpl.getInstance()).thenReturn(plugin);

        trigger.schedule(gerritCause, event);

        verify(project).scheduleBuild2(
                anyInt(),
                same(gerritCause),
                isA(Action.class),
                isA(Action.class),
                isA(Action.class),
                isParameterActionWithStringParameterValues(
                        nameVal(GERRIT_CHANGE_OWNER.name(), owner.getNameAndEmail()),
                        nameVal(GERRIT_CHANGE_OWNER_NAME.name(), owner.getName()),
                        nameVal(GERRIT_CHANGE_OWNER_EMAIL.name(), owner.getEmail()),
                        nameVal(GERRIT_PATCHSET_UPLOADER.name(), uploader.getNameAndEmail()),
                        nameVal(GERRIT_PATCHSET_UPLOADER_NAME.name(), uploader.getName()),
                        nameVal(GERRIT_PATCHSET_UPLOADER_EMAIL.name(), uploader.getEmail())));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with correct change owner and uploader parameters when there are no default parameters present.
     * And sets the event.uploader to null keeping event.patchSet.uploader.
     */
    @Test
    public void testScheduleWithOwnerAndOneUploaderNull() {
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullDisplayName()).thenReturn("MockedProject");
        ParametersDefinitionProperty parameters = mock(ParametersDefinitionProperty.class);
        when(parameters.getParameterDefinitions()).thenReturn(Collections.EMPTY_LIST);
        when(project.getProperty(ParametersDefinitionProperty.class)).thenReturn(parameters);

        Account owner = new Account("Bobby", "bobby@somewhere.com");
        Account uploader = new Account("Nisse", "nisse@acme.org");

        GerritTrigger trigger = new GerritTrigger(null, 0, 0, 0, 0, 0, 0, 0, 0, true, false, true, false, false, false,
                "", "", "", "", "", "", null);
        trigger.start(project, true);
        PatchsetCreated event = Setup.createPatchsetCreated();
        event.getChange().setOwner(owner);
        event.getPatchSet().setUploader(uploader);
        event.setAccount(null);
        GerritCause gerritCause = new GerritCause(event, true);
        gerritCause = spy(gerritCause);
        doReturn("http://mock.url").when(gerritCause).getUrl();
        PowerMockito.mockStatic(PluginImpl.class);
        PluginImpl plugin = PowerMockito.mock(PluginImpl.class);
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        config = spy(config);
        doReturn("http://mock.url").when(config).getGerritFrontEndUrlFor(any(String.class), any(String.class));
        when(plugin.getConfig()).thenReturn(config);
        PowerMockito.when(PluginImpl.getInstance()).thenReturn(plugin);

        trigger.schedule(gerritCause, event);

        verify(project).scheduleBuild2(
                anyInt(),
                same(gerritCause),
                isA(Action.class),
                isA(Action.class),
                isA(Action.class),
                isParameterActionWithStringParameterValues(
                        nameVal(GERRIT_CHANGE_OWNER.name(), owner.getNameAndEmail()),
                        nameVal(GERRIT_CHANGE_OWNER_NAME.name(), owner.getName()),
                        nameVal(GERRIT_CHANGE_OWNER_EMAIL.name(), owner.getEmail()),
                        nameVal(GERRIT_PATCHSET_UPLOADER.name(), uploader.getNameAndEmail()),
                        nameVal(GERRIT_PATCHSET_UPLOADER_NAME.name(), uploader.getName()),
                        nameVal(GERRIT_PATCHSET_UPLOADER_EMAIL.name(), uploader.getEmail())));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with correct change owner and uploader parameters when there are no default parameters present.
     * And sets the event.patchSet.uploader to null keeping event.uploader set.
     */
    @Test
    public void testScheduleWithOwnerAndOtherUploaderNull() {
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullDisplayName()).thenReturn("MockedProject");
        ParametersDefinitionProperty parameters = mock(ParametersDefinitionProperty.class);
        when(parameters.getParameterDefinitions()).thenReturn(Collections.EMPTY_LIST);
        when(project.getProperty(ParametersDefinitionProperty.class)).thenReturn(parameters);

        Account owner = new Account("Bobby", "bobby@somewhere.com");
        Account uploader = new Account("Nisse", "nisse@acme.org");

        GerritTrigger trigger = new GerritTrigger(null, 0, 0, 0, 0, 0, 0, 0, 0, true, false, true, false, false, false,
                "", "", "", "", "", "", null);
        trigger.start(project, true);
        PatchsetCreated event = Setup.createPatchsetCreated();
        event.getChange().setOwner(owner);
        event.getPatchSet().setUploader(null);
        event.setAccount(uploader);
        GerritCause gerritCause = new GerritCause(event, true);
        gerritCause = spy(gerritCause);
        doReturn("http://mock.url").when(gerritCause).getUrl();
        PowerMockito.mockStatic(PluginImpl.class);
        PluginImpl plugin = PowerMockito.mock(PluginImpl.class);
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        config = spy(config);
        doReturn("http://mock.url").when(config).getGerritFrontEndUrlFor(any(String.class), any(String.class));
        when(plugin.getConfig()).thenReturn(config);
        PowerMockito.when(PluginImpl.getInstance()).thenReturn(plugin);

        trigger.schedule(gerritCause, event);

        verify(project).scheduleBuild2(
                anyInt(),
                same(gerritCause),
                isA(Action.class),
                isA(Action.class),
                isA(Action.class),
                isParameterActionWithStringParameterValues(
                        nameVal(GERRIT_CHANGE_OWNER.name(), owner.getNameAndEmail()),
                        nameVal(GERRIT_CHANGE_OWNER_NAME.name(), owner.getName()),
                        nameVal(GERRIT_CHANGE_OWNER_EMAIL.name(), owner.getEmail()),
                        nameVal(GERRIT_PATCHSET_UPLOADER.name(), uploader.getNameAndEmail()),
                        nameVal(GERRIT_PATCHSET_UPLOADER_NAME.name(), uploader.getName()),
                        nameVal(GERRIT_PATCHSET_UPLOADER_EMAIL.name(), uploader.getEmail())));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with correct change owner and uploader parameters when there are no default parameters present.
     * And sets the event.patchSet.uploader and event.uploader to null.
     */
    @Test
    public void testScheduleWithOwnerAndBothUploadersNull() {
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullDisplayName()).thenReturn("MockedProject");
        ParametersDefinitionProperty parameters = mock(ParametersDefinitionProperty.class);
        when(parameters.getParameterDefinitions()).thenReturn(Collections.EMPTY_LIST);
        when(project.getProperty(ParametersDefinitionProperty.class)).thenReturn(parameters);

        Account owner = new Account("Bobby", "bobby@somewhere.com");

        GerritTrigger trigger = new GerritTrigger(null, 0, 0, 0, 0, 0, 0, 0, 0, true, false, true, false, false, false,
                "", "", "", "", "", "", null);
        trigger.start(project, true);
        PatchsetCreated event = Setup.createPatchsetCreated();
        event.getChange().setOwner(owner);
        event.getPatchSet().setUploader(null);
        event.setAccount(null);
        GerritCause gerritCause = new GerritCause(event, true);
        gerritCause = spy(gerritCause);
        doReturn("http://mock.url").when(gerritCause).getUrl();
        PowerMockito.mockStatic(PluginImpl.class);
        PluginImpl plugin = PowerMockito.mock(PluginImpl.class);
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        config = spy(config);
        doReturn("http://mock.url").when(config).getGerritFrontEndUrlFor(any(String.class), any(String.class));
        when(plugin.getConfig()).thenReturn(config);
        PowerMockito.when(PluginImpl.getInstance()).thenReturn(plugin);

        trigger.schedule(gerritCause, event);

        verify(project).scheduleBuild2(
                anyInt(),
                same(gerritCause),
                isA(Action.class),
                isA(Action.class),
                isA(Action.class),
                isParameterActionWithStringParameterValues(
                        nameVal(GERRIT_CHANGE_OWNER.name(), owner.getNameAndEmail()),
                        nameVal(GERRIT_CHANGE_OWNER_NAME.name(), owner.getName()),
                        nameVal(GERRIT_CHANGE_OWNER_EMAIL.name(), owner.getEmail()),
                        nameVal(GERRIT_PATCHSET_UPLOADER.name(), ""),
                        nameVal(GERRIT_PATCHSET_UPLOADER_NAME.name(), ""),
                        nameVal(GERRIT_PATCHSET_UPLOADER_EMAIL.name(), "")));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with correct change owner and uploader parameters when there are no default parameters present.
     * And sets the event.patchSet.uploader and event.uploader to null.
     */
    @Test
    public void testScheduleWithOwnerAndPartOfUploadersNull() {
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullDisplayName()).thenReturn("MockedProject");
        ParametersDefinitionProperty parameters = mock(ParametersDefinitionProperty.class);
        when(parameters.getParameterDefinitions()).thenReturn(Collections.EMPTY_LIST);
        when(project.getProperty(ParametersDefinitionProperty.class)).thenReturn(parameters);

        Account owner = new Account("Bobby", "bobby@somewhere.com");
        Account uploader = new Account("Bobby", null);

        GerritTrigger trigger = new GerritTrigger(null, 0, 0, 0, 0, 0, 0, 0, 0,
                true, false, true, false, false, false, "", "", "", "", "", "", null);
        trigger.start(project, true);
        PatchsetCreated event = Setup.createPatchsetCreated();
        event.getChange().setOwner(owner);
        event.getPatchSet().setUploader(uploader);
        event.setAccount(uploader);
        GerritCause gerritCause = new GerritCause(event, true);
        gerritCause = spy(gerritCause);
        doReturn("http://mock.url").when(gerritCause).getUrl();
        PowerMockito.mockStatic(PluginImpl.class);
        PluginImpl plugin = PowerMockito.mock(PluginImpl.class);
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        config = spy(config);
        doReturn("http://mock.url").when(config).getGerritFrontEndUrlFor(any(String.class), any(String.class));
        when(plugin.getConfig()).thenReturn(config);
        PowerMockito.when(PluginImpl.getInstance()).thenReturn(plugin);

        trigger.schedule(gerritCause, event);

        verify(project).scheduleBuild2(
                anyInt(),
                same(gerritCause),
                isA(Action.class),
                isA(Action.class),
                isA(Action.class),
                isParameterActionWithStringParameterValues(
                        nameVal(GERRIT_CHANGE_OWNER.name(), owner.getNameAndEmail()),
                        nameVal(GERRIT_CHANGE_OWNER_NAME.name(), owner.getName()),
                        nameVal(GERRIT_CHANGE_OWNER_EMAIL.name(), owner.getEmail()),
                        nameVal(GERRIT_PATCHSET_UPLOADER.name(), ""),
                        nameVal(GERRIT_PATCHSET_UPLOADER_NAME.name(), uploader.getName()),
                        nameVal(GERRIT_PATCHSET_UPLOADER_EMAIL.name(), "")));
    }

    /**
     * Tests GerritTrigger.retriggerThisBuild.
     */
    @Test
    public void testRetriggerThisBuild() {
        mockPluginConfig();
        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullDisplayName()).thenReturn("MockedProject");
        when(project.isBuildable()).thenReturn(true);

        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getNumber()).thenReturn(1);
        when(build.getProject()).thenReturn(project);

        PatchsetCreated event = Setup.createPatchsetCreated();

        when(listener.isBuilding(project, event)).thenReturn(false);

        GerritTrigger trigger = new GerritTrigger(Collections.EMPTY_LIST,
                0, 0, 0, 0, 0, 0, 0, 0, false, false, true, false, false, false, "", "", "", "", "", "", null);

        TriggerContext context = new TriggerContext(build, event, Collections.EMPTY_LIST);

        trigger.retriggerThisBuild(context);

        verify(listener).onRetriggered(same(project), same(event), anyListOf(AbstractBuild.class));

        verify(project).scheduleBuild2(
                anyInt(),
                isA(GerritUserCause.class),
                isA(BadgeAction.class),
                isA(RetriggerAction.class),
                isA(RetriggerAllAction.class),
                isA(Action.class));
    }

    /**
     * Tests GerritTrigger.retriggerThisBuild when the trigger is configured for silentMode.
     */
    @Test
    public void testRetriggerThisBuildSilent() {
        mockPluginConfig();
        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullDisplayName()).thenReturn("MockedProject");
        when(project.isBuildable()).thenReturn(true);

        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getNumber()).thenReturn(1);
        when(build.getProject()).thenReturn(project);

        PatchsetCreated event = Setup.createPatchsetCreated();

        when(listener.isBuilding(project, event)).thenReturn(false);

        GerritTrigger trigger = new GerritTrigger(Collections.EMPTY_LIST,
                0, 0, 0, 0, 0, 0, 0, 0, true, true, true, false, false, false, "", "", "", "", "", "", null);

        TriggerContext context = new TriggerContext(build, event, Collections.EMPTY_LIST);

        trigger.retriggerThisBuild(context);

        verify(listener, never()).onRetriggered(isA(AbstractProject.class),
                isA(PatchsetCreated.class),
                anyListOf(AbstractBuild.class));

        verify(project).scheduleBuild2(
                anyInt(),
                isA(GerritUserCause.class),
                isA(BadgeAction.class),
                isA(RetriggerAction.class),
                isA(RetriggerAllAction.class),
                isA(Action.class));
    }

    /**
     * Tests GerritTrigger.retriggerAllBuilds with one additional build in the context.
     */
    @Test
    public void testRetriggerAllBuilds() {
        mockPluginConfig();

        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        AbstractProject thisProject = PowerMockito.mock(AbstractProject.class);
        when(thisProject.getFullDisplayName()).thenReturn("MockedProject");
        when(thisProject.isBuildable()).thenReturn(true);

        AbstractBuild thisBuild = mock(AbstractBuild.class);
        when(thisBuild.getNumber()).thenReturn(1);
        when(thisBuild.getProject()).thenReturn(thisProject);

        PatchsetCreated event = Setup.createPatchsetCreated();

        when(listener.isBuilding(event)).thenReturn(false);

        GerritTrigger thisTrigger = new GerritTrigger(Collections.EMPTY_LIST,
                0, 0, 0, 0, 0, 0, 0, 0, false, false, true, false, false, false, "", "", "", "", "", "", null);
        doReturn(thisTrigger).when(thisProject).getTrigger(GerritTrigger.class);

        GerritTrigger otherTrigger = new GerritTrigger(Collections.EMPTY_LIST,
                0, 0, 0, 0, 0, 0, 0, 0, false, false, true, false, false, false, "", "", "", "", "", "", null);
        AbstractProject otherProject = PowerMockito.mock(AbstractProject.class);
        when(otherProject.getFullDisplayName()).thenReturn("Other_MockedProject");
        when(otherProject.isBuildable()).thenReturn(true);
        doReturn(otherTrigger).when(otherProject).getTrigger(GerritTrigger.class);

        AbstractBuild otherBuild = mock(AbstractBuild.class);
        when(otherBuild.getNumber()).thenReturn(1);
        when(otherBuild.getProject()).thenReturn(otherProject);

        TriggerContext context = new TriggerContext(event);
        context.setThisBuild(thisBuild);
        context.addOtherBuild(otherBuild);

        thisTrigger.retriggerAllBuilds(context);

        verify(listener).onRetriggered(thisProject, event, null);

        verify(thisProject).scheduleBuild2(
                anyInt(),
                isA(GerritUserCause.class),
                isA(BadgeAction.class),
                isA(RetriggerAction.class),
                isA(RetriggerAllAction.class),
                isA(Action.class));

        verify(listener).onRetriggered(otherProject, event, null);

        verify(otherProject).scheduleBuild2(
                anyInt(),
                isA(GerritUserCause.class),
                isA(BadgeAction.class),
                isA(RetriggerAction.class),
                isA(RetriggerAllAction.class),
                isA(Action.class));
    }

    /**
     * Tests {@link GerritTrigger#gerritEvent(PatchsetCreated)} with a normal scenario.
     */
    @Test
    public void testGerritEvent() {
        mockPluginConfig();
        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.isBuildable()).thenReturn(true);

        GerritProject gP = mock(GerritProject.class);
        doReturn(true).when(gP).isInteresting(any(String.class), any(String.class));

        GerritTrigger trigger = new GerritTrigger(Collections.nCopies(1, gP),
                0, 0, 0, 0, 0, 0, 0, 0, false, false, true, false, false, false, "", "", "", "", "", "", null);
        Whitebox.setInternalState(trigger, "myProject", project);

        PatchsetCreated event = Setup.createPatchsetCreated();

        trigger.gerritEvent(event);

        verify(listener).onTriggered(same(project), same(event));

        verify(project).scheduleBuild2(
                anyInt(),
                isA(GerritCause.class),
                isA(BadgeAction.class),
                isA(RetriggerAction.class),
                isA(RetriggerAllAction.class),
                isA(Action.class));
    }

    /**
     * Tests {@link GerritTrigger#gerritEvent(PatchsetCreated)} with a non buildable project.
     */
    @Test
    public void testGerritEventNotBuildable() {
        mockPluginConfig();
        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.isBuildable()).thenReturn(false);

        GerritTrigger trigger = new GerritTrigger(Collections.EMPTY_LIST,
                0, 0, 0, 0, 0, 0, 0, 0, false, false, true, false, false, false, "", "", "", "", "", "", null);
        Whitebox.setInternalState(trigger, "myProject", project);

        PatchsetCreated event = Setup.createPatchsetCreated();

        trigger.gerritEvent(event);

        verifyZeroInteractions(listener);
        verify(project).isBuildable();
        verifyNoMoreInteractions(project);
    }

    /**
     * Tests {@link GerritTrigger#gerritEvent(PatchsetCreated)} with a non interesting change.
     */
    @Test
    public void testGerritEventNotInteresting() {
        mockPluginConfig();
        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.isBuildable()).thenReturn(true);

        GerritProject gP = mock(GerritProject.class);
        doReturn(false).when(gP).isInteresting(any(String.class), any(String.class));

        GerritTrigger trigger = new GerritTrigger(Collections.nCopies(1, gP),
                0, 0, 0, 0, 0, 0, 0, 0, false, false, true, false, false, false, "", "", "", "", "", "", null);
        Whitebox.setInternalState(trigger, "myProject", project);

        PatchsetCreated event = Setup.createPatchsetCreated();

        trigger.gerritEvent(event);

        verifyZeroInteractions(listener);
        verify(project).isBuildable();
        verifyNoMoreInteractions(project);
    }

    /**
     * Tests {@link GerritTrigger#gerritEvent(PatchsetCreated)} with a normal scenario.
     * With a ManualPatchsetCreated event.
     */
    @Test
    public void testGerritEventManualEvent() {
        mockPluginConfig();
        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.isBuildable()).thenReturn(true);

        GerritProject gP = mock(GerritProject.class);
        doReturn(true).when(gP).isInteresting(any(String.class), any(String.class));

        GerritTrigger trigger = new GerritTrigger(Collections.nCopies(1, gP),
                0, 0, 0, 0, 0, 0, 0, 0, false, false, true, false, false, false, "", "", "", "", "", "", null);
        Whitebox.setInternalState(trigger, "myProject", project);

        ManualPatchsetCreated event = Setup.createManualPatchsetCreated();

        trigger.gerritEvent(event);

        verify(listener).onTriggered(same(project), same(event));

        verify(project).scheduleBuild2(
                anyInt(),
                isA(GerritManualCause.class),
                isA(BadgeAction.class),
                isA(RetriggerAction.class),
                isA(RetriggerAllAction.class),
                isA(Action.class));
    }

    /**
     * Tests {@link GerritTrigger#gerritEvent(PatchsetCreated)} with a normal scenario, but with silentMode on.
     */
    @Test
    public void testGerritEventSilentMode() {
        mockPluginConfig();
        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.isBuildable()).thenReturn(true);

        GerritProject gP = mock(GerritProject.class);
        doReturn(true).when(gP).isInteresting(any(String.class), any(String.class));

        GerritTrigger trigger = new GerritTrigger(Collections.nCopies(1, gP),
                0, 0, 0, 0, 0, 0, 0, 0, true, true, true, false, false, false, "", "", "", "", "", "", null);
        Whitebox.setInternalState(trigger, "myProject", project);

        PatchsetCreated event = Setup.createPatchsetCreated();

        trigger.gerritEvent(event);

        verifyNoMoreInteractions(listener);

        verify(project).scheduleBuild2(
                anyInt(),
                isA(GerritCause.class),
                isA(BadgeAction.class),
                isA(RetriggerAction.class),
                isA(RetriggerAllAction.class),
                isA(Action.class));
    }

    /**
     * Tests {@link GerritTrigger#gerritEvent(PatchsetCreated)}.
     * With a ManualPatchsetCreated event and silentMode on.
     */
    @Test
    public void testGerritEventManualEventSilentMode() {
        mockPluginConfig();
        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.isBuildable()).thenReturn(true);

        GerritProject gP = mock(GerritProject.class);
        doReturn(true).when(gP).isInteresting(any(String.class), any(String.class));

        GerritTrigger trigger = new GerritTrigger(Collections.nCopies(1, gP),
                0, 0, 0, 0, 0, 0, 0, 0, true, true, true, false, false, false, "", "", "", "", "", "", null);
        Whitebox.setInternalState(trigger, "myProject", project);

        ManualPatchsetCreated event = Setup.createManualPatchsetCreated();

        trigger.gerritEvent(event);

        verifyNoMoreInteractions(listener);

        verify(project).scheduleBuild2(
                anyInt(),
                isA(GerritManualCause.class),
                isA(BadgeAction.class),
                isA(RetriggerAction.class),
                isA(RetriggerAllAction.class),
                isA(Action.class));
    }

    /**
     * Tests {@link GerritTrigger#createParameters(PatchsetCreated event,
     * AbstractProject project)} with a normal scenario.
     * this is a test case that checks that
     * the Trigger is creating parameters having escaped quotes or not
     * when the escapeQuotes setting is on.
     */
    @Test
    public void testCreateParametersWhenTriggerWithEscapeQuotesOn() {

        String stringWithQuotes = "Fixed \" the thing to make \" some thing fun";
        String stringWithQuotesEscaped = "Fixed \\\" the thing to make \\\" some thing fun";
        String stringWithoutQuotes = "Fixed  the thing to make  some thing fun";

        //prepare AbstractProject object
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        ParametersDefinitionProperty parameters = mock(ParametersDefinitionProperty.class);
        when(parameters.getParameterDefinitions()).thenReturn(Collections.EMPTY_LIST);
        when(project.getProperty(ParametersDefinitionProperty.class)).thenReturn(parameters);

        //prepare  PatchsetCreated object
        JSONObject patch = new JSONObject();
        patch.put(NUMBER, "2");
        patch.put(REVISION, "ad123456789");
        patch.put(REF, "refs/changes/00/100/2");

        JSONObject jsonAccount = new JSONObject();
        jsonAccount.put(EMAIL, "robert.sandell@sonyericsson.com");
        jsonAccount.put(NAME, "Bobby");

        Change changeWithQuotes = prepareChangeObjForMockTest("project", "branch", "I2343434344",
                "100", stringWithQuotes, jsonAccount, "http://localhost:8080");
        Change changeWithoutQuotes = prepareChangeObjForMockTest("project", "branch", "I2343434344",
                "100", stringWithoutQuotes, jsonAccount, "http://localhost:8080");

        PatchsetCreated eventWithQuotes = preparePatchsetCreatedObjForMockTest(changeWithQuotes,
                new PatchSet(patch), GerritEventType.PATCHSET_CREATED);
        PatchsetCreated eventWithoutQuotes = preparePatchsetCreatedObjForMockTest(changeWithoutQuotes,
                new PatchSet(patch), GerritEventType.PATCHSET_CREATED);
        //mock the returned url
        PowerMockito.mockStatic(PluginImpl.class);
        PluginImpl plugin = PowerMockito.mock(PluginImpl.class);
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        config = spy(config);
        doReturn("http://mock.url").when(config).getGerritFrontEndUrlFor(any(String.class), any(String.class));
        when(plugin.getConfig()).thenReturn(config);
        PowerMockito.when(PluginImpl.getInstance()).thenReturn(plugin);

        //prepare GerritTrigger object with the escapeQuotes setting is on.
        GerritTrigger triggerWithEscapeQuotesOn =
                new GerritTrigger(null, 0, 0, 0, 0, 0, 0, 0, 0, true, true, true, false, false, false,
                        "", "", "", "", "", "", null);

        //the Trigger is creating parameters with escaped quote in "subject".
        ParametersAction paremetersAction =
                triggerWithEscapeQuotesOn.createParameters(eventWithQuotes, project);
        ParameterValue strPara =
                new StringParameterValue(GERRIT_CHANGE_SUBJECT.name(), stringWithQuotesEscaped);
        verify(changeWithQuotes, times(1)).getSubject();
        assertEquals(strPara, paremetersAction.getParameter(GERRIT_CHANGE_SUBJECT.name()));

        //the Trigger is creating parameters without escaped quote in "subject".
        paremetersAction = triggerWithEscapeQuotesOn.createParameters(eventWithoutQuotes, project);
        strPara = new StringParameterValue(GERRIT_CHANGE_SUBJECT.name(), stringWithoutQuotes);
        verify(changeWithoutQuotes, times(1)).getSubject();
        assertEquals(strPara, paremetersAction.getParameter(GERRIT_CHANGE_SUBJECT.name()));

    }

    /**
     * Tests {@link GerritTrigger#createParameters(PatchsetCreated event,
     * AbstractProject project)} with a normal scenario.
     * this is a test case that checks that
     * the Trigger is creating parameters having escaped quotes or not
     * when the escapeQuotes setting is off.
     */
    @Test
    public void testCreateParametersWhenTriggerWithEscapeQuotesOff() {

        String stringWithQuotes = "Fixed \" the thing to make \" some thing fun";
        String stringWithoutQuotes = "Fixed  the thing to make  some thing fun";

        //prepare AbstractProject object
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        ParametersDefinitionProperty parameters = mock(ParametersDefinitionProperty.class);
        when(parameters.getParameterDefinitions()).thenReturn(Collections.EMPTY_LIST);
        when(project.getProperty(ParametersDefinitionProperty.class)).thenReturn(parameters);

        //prepare  PatchsetCreated object
        JSONObject patch = new JSONObject();
        patch.put(NUMBER, "2");
        patch.put(REVISION, "ad123456789");
        patch.put(REF, "refs/changes/00/100/2");

        JSONObject jsonAccount = new JSONObject();
        jsonAccount.put(EMAIL, "robert.sandell@sonyericsson.com");
        jsonAccount.put(NAME, "Bobby");

        Change changeWithQuotes = prepareChangeObjForMockTest("project", "branch", "I2343434344",
                "100", stringWithQuotes, jsonAccount, "http://localhost:8080");
        Change changeWithoutQuotes = prepareChangeObjForMockTest("project", "branch", "I2343434344",
                "100", stringWithoutQuotes, jsonAccount, "http://localhost:8080");

        PatchsetCreated eventWithQuotes = preparePatchsetCreatedObjForMockTest(changeWithQuotes,
                new PatchSet(patch), GerritEventType.PATCHSET_CREATED);
        PatchsetCreated eventWithoutQuotes = preparePatchsetCreatedObjForMockTest(changeWithoutQuotes,
                new PatchSet(patch), GerritEventType.PATCHSET_CREATED);

        //mock the returned url
        PowerMockito.mockStatic(PluginImpl.class);
        PluginImpl plugin = PowerMockito.mock(PluginImpl.class);
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        config = spy(config);
        doReturn("http://mock.url").when(config).getGerritFrontEndUrlFor(any(String.class), any(String.class));
        when(plugin.getConfig()).thenReturn(config);
        PowerMockito.when(PluginImpl.getInstance()).thenReturn(plugin);

        //prepare GerritTrigger object with the escapeQuotes setting is off.
        GerritTrigger triggerWithEscapeQuotesOff =
                new GerritTrigger(null, 0, 0, 0, 0, 0, 0, 0, 0, true, false, true, false, false, false,
                        "", "", "", "", "", "", null);

        //the Trigger is creating parameters with escaped quote in "subject"
        ParametersAction paremetersAction =
                triggerWithEscapeQuotesOff.createParameters(eventWithQuotes, project);
        ParameterValue strPara =
                new StringParameterValue(GERRIT_CHANGE_SUBJECT.name(), stringWithQuotes);
        verify(changeWithQuotes, times(1)).getSubject();
        assertEquals(strPara, paremetersAction.getParameter(GERRIT_CHANGE_SUBJECT.name()));

        //the Trigger is creating parameters without escaped quote in "subject"
        paremetersAction = triggerWithEscapeQuotesOff.createParameters(eventWithoutQuotes, project);
        strPara = new StringParameterValue(GERRIT_CHANGE_SUBJECT.name(), stringWithoutQuotes);
        verify(changeWithoutQuotes, times(1)).getSubject();
        assertEquals(strPara, paremetersAction.getParameter(GERRIT_CHANGE_SUBJECT.name()));
    }

    /**
     * Prepare a new Mock Object of Change for utility test
     * {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change}.
     *
     * @param project     the result of calling getProject() on this mocked Object.
     * @param branch      the result of calling getBranch() on this mocked Object.
     * @param id          the result of calling getId() on this mocked Object.
     * @param number      the result of calling getNumber() on this mocked Object.
     * @param subject     the result of calling getSubject() on this mocked Object.
     * @param jsonAccount used for creating a Account object as the result of
     *                    calling getOwner() on this mocked Object.
     * @param url         the result of calling getUrl() on this mocked Object.
     * @return a new Change Object.
     */
    private Change prepareChangeObjForMockTest(
            String project,
            String branch,
            String id,
            String number,
            String subject,
            JSONObject jsonAccount,
            String url) {
        Change change = PowerMockito.mock(Change.class);
        doReturn(project).when(change).getProject();
        doReturn(branch).when(change).getBranch();
        doReturn(id).when(change).getId();
        doReturn(number).when(change).getNumber();
        when(change.getSubject()).thenReturn(subject);
        doReturn(new Account(jsonAccount)).when(change).getOwner();
        doReturn(url).when(change).getUrl();
        return change;
    }

    /**
     * Prepare a new Mock Object of PatchsetCreated for utility test
     * {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated}.
     *
     * @param change    mock the result of calling getChange() on this mock Object.
     * @param patchSet  mock the result of calling getCPatchSet() on this mock Object.
     * @param enentType mock the result of calling getEventType() on this mock Object.
     * @return a new PatchsetCreated Object.
     */
    private PatchsetCreated preparePatchsetCreatedObjForMockTest(
            Change change,
            PatchSet patchSet,
            GerritEventType enentType) {
        PatchsetCreated patchsetCretedObj = PowerMockito.mock(PatchsetCreated.class);
        doReturn(change).when(patchsetCretedObj).getChange();
        doReturn(patchSet).when(patchsetCretedObj).getPatchSet();
        doReturn(enentType).when(patchsetCretedObj).getEventType();
        return patchsetCretedObj;
    }

    /**
     * Does a static mock of {@link PluginImpl}.
     * And specifically the retrieval of Config and the frontendUrl.
     */
    private static void mockPluginConfig() {
        PowerMockito.mockStatic(PluginImpl.class);
        PluginImpl plugin = PowerMockito.mock(PluginImpl.class);
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        config = spy(config);
        doReturn("http://mock.url").when(config).getGerritFrontEndUrlFor(any(String.class), any(String.class));
        when(plugin.getConfig()).thenReturn(config);
        PowerMockito.when(PluginImpl.getInstance()).thenReturn(plugin);
    }

    /**
     * Convenience method for creating a {@link IsParameterActionWithStringParameterValue}. So it is easier to read.
     *
     * @param name  the name of the parameter to check.
     * @param value the value of the parameter to check.
     * @return an argThat IsParameterActionWithStringParameterValue
     */
    static Action isParameterActionWithStringParameterValue(String name, String value) {
        return argThat(new IsParameterActionWithStringParameterValue(name, value));
    }

    /**
     * Convenience method for creating a {@link IsParameterActionWithStringParameterValue}. So it is easier to read.
     *
     * @param nameValues the names and values of the parameters to check.
     * @return an argThat IsParameterActionWithStringParameterValue
     */
    static Action isParameterActionWithStringParameterValues(
            IsParameterActionWithStringParameterValue.NameAndValue... nameValues) {
        return argThat(new IsParameterActionWithStringParameterValue(nameValues));
    }

    /**
     * Convenience method for creating a {@link IsParameterActionWithStringParameterValue}. So it is easier to read.
     *
     * @param name the name and values of the parameters to check.
     * @param val  the value of the parameters to check.
     * @return an argThat IsParameterActionWithStringParameterValue
     */
    static IsParameterActionWithStringParameterValue.NameAndValue nameVal(String name, String val) {
        return new IsParameterActionWithStringParameterValue.NameAndValue(name, val);
    }

    /**
     * An ArgumentMatcher that checks if the argument is a {@link ParametersAction}.
     * And if it contains a specific ParameterValue.
     */
    static class IsParameterActionWithStringParameterValue extends ArgumentMatcher<Action> {

        NameAndValue[] nameAndValues;

        /**
         * Standard Constructor.
         *
         * @param name  the name of the parameter to check.
         * @param value the value of the parameter to check.
         */
        public IsParameterActionWithStringParameterValue(String name, String value) {
            nameAndValues = new NameAndValue[]{new NameAndValue(name, value)};
        }

        /**
         * Standard Constructor.
         *
         * @param nameVal the name and values of the parameters to check.
         */
        public IsParameterActionWithStringParameterValue(NameAndValue... nameVal) {
            nameAndValues = nameVal;
        }


        @Override
        public boolean matches(Object argument) {
            Action action = (Action)argument;
            if (action instanceof ParametersAction) {
                for (NameAndValue nv : nameAndValues) {
                    ParameterValue parameterValue = ((ParametersAction)action).getParameter(nv.name);

                    if (parameterValue != null && parameterValue instanceof StringParameterValue) {
                        StringParameterValue param = (StringParameterValue)parameterValue;
                        if (!nv.name.equals(param.getName()) || !nv.value.equals(param.value)) {
                            System.err.println("Required parameter is [" + param.getName() + "=" + param.value
                                    + "] should be [" + nv.toString() + "]");
                            return false;
                        }
                    } else {
                        System.err.println("Missing required parameter " + nv.name);
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * Data structure for a name and a value.
         */
        static class NameAndValue {
            private String name;
            private String value;

            /**
             * Standard constructor.
             *
             * @param name  the name.
             * @param value the value.
             */
            NameAndValue(String name, String value) {
                this.name = name;
                this.value = value;
            }

            @Override
            public String toString() {
                return name + "=" + value;
            }
        }
    }
}
