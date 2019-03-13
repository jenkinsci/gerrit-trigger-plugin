/*
 *  The MIT License
 *
 *  Copyright (c) 2010, 2014 Sony Mobile Communications Inc. All rights reserved.
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
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.ReplicationConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.dependency.DependencyQueueTaskDispatcher;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritSlave;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggeredItemEntity;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginGerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginPrivateStateChangedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginWipStateChangedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.parameters.Base64EncodedStringParameterValue;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil;
import com.sonymobile.tools.gerrit.gerritevents.GerritHandler;
import com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventType;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Account;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PrivateStateChanged;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.RefUpdated;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.WipStateChanged;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.CauseAction;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.TextParameterValue;
import hudson.model.queue.ScheduleResult;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.acegisecurity.Authentication;
import org.hamcrest.BaseMatcher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.internal.matchers.InstanceOf;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;

//CS IGNORE LineLength FOR NEXT 11 LINES. REASON: static imports can get long
import static com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerParameters.GERRIT_CHANGE_COMMIT_MESSAGE;
import static com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerParameters.GERRIT_CHANGE_ID;
import static com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerParameters.GERRIT_CHANGE_OWNER;
import static com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerParameters.GERRIT_CHANGE_OWNER_EMAIL;
import static com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerParameters.GERRIT_CHANGE_OWNER_NAME;
import static com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerParameters.GERRIT_CHANGE_SUBJECT;
import static com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerParameters.GERRIT_CHANGE_URL;
import static com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerParameters.GERRIT_PATCHSET_UPLOADER;
import static com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerParameters.GERRIT_PATCHSET_UPLOADER_EMAIL;
import static com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTriggerParameters.GERRIT_PATCHSET_UPLOADER_NAME;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.EMAIL;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.NAME;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.NUMBER;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.REF;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.REVISION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyListOf;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

//CS IGNORE MagicNumber FOR NEXT 2000 LINES. REASON: testdata.

/**
 * Tests make ref spec.
 * TODO move testMakeRefSpec* to StringUtilTest
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({
        AbstractProject.class,
        ToGerritRunListener.class,
        PluginImpl.class,
        Hudson.class,
        Jenkins.class,
        DependencyQueueTaskDispatcher.class,
        EventListener.class })
@PowerMockIgnore("javax.security.*")
public class GerritTriggerTest {
    private Hudson hudsonMock;
    private Jenkins jenkinsMock;
    private AbstractProject downstreamProject;
    private AbstractProject upstreamProject;
    private AbstractProject veryUpstreamProject;
    private GerritTrigger upstreamGerritTriggerMock;
    private GerritTrigger veryUpstreamGerritTriggerMock;
    private DependencyQueueTaskDispatcher dispatcherMock;
    private PluginImpl plugin;

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
        Queue queue = mockJenkinsQueue();
        AbstractProject project = mockProject();
        mockPluginConfig(20);

        PatchsetCreated event = Setup.createPatchsetCreated();
        final GerritCause gerritCause = spy(new GerritCause(event, true));

        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        Setup.setTrigger(trigger, project);
        doReturn("http://mock.url").when(gerritCause).getUrl();
        trigger.schedule(gerritCause, event);
        verify(queue).schedule2(same(project), eq(20), hasCauseActionContainingCause(gerritCause));
    }



    /**
     * Tests a project rename.
     * TODO complete.
     *
     * @throws Exception if so.
     */
    @Test
    public void testProjectRename() throws Exception {
        mockConfig();
        mockPluginConfig(0);
        // we'll make AbstractProject return different names over time
        final String[] name = new String[1];
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullName()).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return name[0];
            }
        });

        name[0] = "OriginalName";
        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        //doReturn(true).when(trigger).isTriggerOnDraftPublishedEnabled();
        project.addTrigger(trigger);

        // simulate a rename
        name[0] = "NewName";

        // and a reconfiguration
        trigger.stop();
        trigger.start(project, true);
    }

    /**
     * Tests that initializeTriggerOnEvents is run correctly by the start method.
     */
    @Test
    public void testInitializeTriggerOnEvents() {
        mockPluginConfig(0);
        AbstractProject project = mockProject();
        boolean silentStartMode = false;
        GerritTrigger trigger = new GerritTrigger(null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                true, silentStartMode, true, false, false, "", "", "", "", "", "", "", null, null, null,
                null, false,  "", null);
        trigger = spy(trigger);
        Object triggerOnEvents = Whitebox.getInternalState(trigger, "triggerOnEvents");

        assertNull(triggerOnEvents);
        doReturn(true).when(trigger).isTriggerOnDraftPublishedEnabled();
        trigger.start(project, true);
        triggerOnEvents = Whitebox.getInternalState(trigger, "triggerOnEvents");
        assertNotNull(triggerOnEvents);
        List<PluginGerritEvent> events = (List<PluginGerritEvent>)triggerOnEvents;
        assertEquals(events.size(), 2);
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with an negative buildScheduleDelay -20.
     */
    @Test
    public void testScheduleWithNegativeBuildScheduleDelay() {
        Queue queue = mockJenkinsQueue();
        AbstractProject project = mockProject();
        mockPluginConfig(-20);

        PatchsetCreated event = Setup.createPatchsetCreated();
        final GerritCause gerritCause = spy(new GerritCause(event, true));
        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        Setup.setTrigger(trigger, project);
        doReturn("http://mock.url").when(gerritCause).getUrl();
        trigger.schedule(gerritCause, event);
        verify(queue).schedule2(same(project), eq(0), hasCauseActionContainingCause(gerritCause));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with an negative buildScheduleDelay -20.
     */
    @Test
    public void testScheduleWithNoBuildScheduleDelay() {
        Queue queue = mockJenkinsQueue();
        AbstractProject project = mockProject();
        mockPluginConfig(0);

        PatchsetCreated event = Setup.createPatchsetCreated();
        final GerritCause gerritCause = spy(new GerritCause(event, true));

        GerritTrigger trigger = Setup.createDefaultTrigger(project);

        Setup.setTrigger(trigger, project);
        doReturn("http://mock.url").when(gerritCause).getUrl();
        trigger.schedule(gerritCause, event);
        verify(queue).schedule2(same(project), eq(0), hasCauseActionContainingCause(gerritCause));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with an negative buildScheduleDelay 10000.
     */
    @Test
    public void testScheduleWithMaximumBuildScheduleDelay() {
        Queue queue = mockJenkinsQueue();
        AbstractProject project = mockProject();
        mockPluginConfig(10000);

        PatchsetCreated event = Setup.createPatchsetCreated();
        final GerritCause gerritCause = spy(new GerritCause(event, true));

        GerritTrigger trigger = Setup.createDefaultTrigger(project);

        Setup.setTrigger(trigger, project);
        doReturn("http://mock.url").when(gerritCause).getUrl();
        trigger.schedule(gerritCause, event);
        verify(queue).schedule2(same(project), eq(10000), hasCauseActionContainingCause(gerritCause));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that
     * {@link hudson.model.AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with correct parameters when there are some default parameters present.
     */
    @Test
    public void testScheduleWithDefaultParameters() {
        Queue queue = mockJenkinsQueue();
        List<ParameterDefinition> list = new LinkedList<ParameterDefinition>();
        list.add(new StringParameterDefinition("MOCK_PARAM", "mock_value"));
        AbstractProject project = mockProject(list);

        mockPluginConfig(0);

        PatchsetCreated event = Setup.createPatchsetCreated();
        final GerritCause gerritCause = spy(new GerritCause(event, true));

        GerritTrigger trigger = Setup.createDefaultTrigger(project);

        Setup.setTrigger(trigger, project);
        doReturn("http://mock.url").when(gerritCause).getUrl();

        trigger.schedule(gerritCause, event);
        verify(queue).schedule2(same(project), eq(0), hasAllActions(
                hasCauseActionContainingCauseMatcher(gerritCause),
                hasParamActionMatcher("MOCK_PARAM", "mock_value"),
                hasParamActionMatcher(GERRIT_CHANGE_URL, "http://mock.url")
        ));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with correct parameters when there are no default parameters present.
     */
    @Test
    public void testScheduleWithNoDefaultParameters() {
        Queue queue = mockJenkinsQueue();
        AbstractProject project = mockProject(Collections.<ParameterDefinition>emptyList());

        mockPluginConfig(0);

        final PatchsetCreated event = Setup.createPatchsetCreated();
        final GerritCause gerritCause = spy(new GerritCause(event, true));
        GerritTrigger trigger = Setup.createDefaultTrigger(project);

        Setup.setTrigger(trigger, project);
        doReturn("http://mock.url").when(gerritCause).getUrl();

        trigger.schedule(gerritCause, event);
        verify(queue).schedule2(same(project), eq(0), hasAllActions(
                hasCauseActionContainingCauseMatcher(gerritCause),
                hasParamActionMatcher(GERRIT_CHANGE_ID, event.getChange().getId()),
                hasParamActionMatcher(GERRIT_CHANGE_URL, "http://mock.url")
        ));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with correct change owner and uploader parameters when there are no default parameters present.
     */
    @Test
    public void testScheduleWithOwnerAndUploader() {
        Queue queue = mockJenkinsQueue();
        AbstractProject project = mockProject(Collections.<ParameterDefinition>emptyList());

        final Account owner = new Account("Bobby", "bobby@somewhere.com");
        final Account uploader = new Account("Nisse", "nisse@acme.org");

        mockPluginConfig(0);

        final PatchsetCreated event = Setup.createPatchsetCreatedWithAccounts(owner, uploader, uploader);
        final GerritCause gerritCause = spy(new GerritCause(event, true));
        GerritTrigger trigger = Setup.createDefaultTrigger(project);

        Setup.setTrigger(trigger, project);
        trigger.setEscapeQuotes(false);
        doReturn("http://mock.url").when(gerritCause).getUrl();

        trigger.schedule(gerritCause, event);
        verify(queue).schedule2(same(project), eq(0), hasAllActions(
                hasCauseActionContainingCauseMatcher(gerritCause),
                hasParamActionMatcher(GERRIT_CHANGE_OWNER, owner.getNameAndEmail()),
                hasParamActionMatcher(GERRIT_CHANGE_OWNER_NAME, owner.getName()),
                hasParamActionMatcher(GERRIT_CHANGE_OWNER_EMAIL, owner.getEmail()),
                hasParamActionMatcher(GERRIT_PATCHSET_UPLOADER, uploader.getNameAndEmail()),
                hasParamActionMatcher(GERRIT_PATCHSET_UPLOADER_NAME, uploader.getName()),
                hasParamActionMatcher(GERRIT_PATCHSET_UPLOADER_EMAIL, uploader.getEmail())
        ));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with correct change owner and uploader parameters when there are no default parameters present.
     * And sets the event.uploader to null keeping event.patchSet.uploader.
     */
    @Test
    public void testScheduleWithOwnerAndOneUploaderNull() {
        Queue queue = mockJenkinsQueue();
        AbstractProject project = mockProject(Collections.<ParameterDefinition>emptyList());

        final Account owner = new Account("Bobby", "bobby@somewhere.com");
        final Account uploader = new Account("Nisse", "nisse@acme.org");

        mockPluginConfig(0);

        final PatchsetCreated event = Setup.createPatchsetCreatedWithAccounts(owner, uploader, null);
        final GerritCause gerritCause = spy(new GerritCause(event, true));
        GerritTrigger trigger = Setup.createDefaultTrigger(project);

        Setup.setTrigger(trigger, project);
        trigger.setEscapeQuotes(false);
        doReturn("http://mock.url").when(gerritCause).getUrl();

        trigger.schedule(gerritCause, event);
        verify(queue).schedule2(same(project), eq(0), hasAllActions(
                hasCauseActionContainingCauseMatcher(gerritCause),
                hasParamActionMatcher(GERRIT_CHANGE_OWNER, owner.getNameAndEmail()),
                hasParamActionMatcher(GERRIT_CHANGE_OWNER_NAME, owner.getName()),
                hasParamActionMatcher(GERRIT_CHANGE_OWNER_EMAIL, owner.getEmail()),
                hasParamActionMatcher(GERRIT_PATCHSET_UPLOADER, uploader.getNameAndEmail()),
                hasParamActionMatcher(GERRIT_PATCHSET_UPLOADER_NAME, uploader.getName()),
                hasParamActionMatcher(GERRIT_PATCHSET_UPLOADER_EMAIL, uploader.getEmail())
        ));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with correct change owner and uploader parameters when there are no default parameters present.
     * And sets the event.patchSet.uploader to null keeping event.uploader set.
     */
    @Test
    public void testScheduleWithOwnerAndOtherUploaderNull() {
        Queue queue = mockJenkinsQueue();
        AbstractProject project = mockProject(Collections.<ParameterDefinition>emptyList());

        final Account owner = new Account("Bobby", "bobby@somewhere.com");
        final Account uploader = new Account("Nisse", "nisse@acme.org");

        mockPluginConfig(0);

        final PatchsetCreated event = Setup.createPatchsetCreatedWithAccounts(owner, null, uploader);
        final GerritCause gerritCause = spy(new GerritCause(event, true));

        GerritTrigger trigger = Setup.createDefaultTrigger(project);

        Setup.setTrigger(trigger, project);
        trigger.setEscapeQuotes(false);
        doReturn("http://mock.url").when(gerritCause).getUrl();

        trigger.schedule(gerritCause, event);
        verify(queue).schedule2(same(project), eq(0), hasAllActions(
                hasCauseActionContainingCauseMatcher(gerritCause),
                hasParamActionMatcher(GERRIT_CHANGE_OWNER, owner.getNameAndEmail()),
                hasParamActionMatcher(GERRIT_CHANGE_OWNER_NAME, owner.getName()),
                hasParamActionMatcher(GERRIT_CHANGE_OWNER_EMAIL, owner.getEmail()),
                hasParamActionMatcher(GERRIT_PATCHSET_UPLOADER, uploader.getNameAndEmail()),
                hasParamActionMatcher(GERRIT_PATCHSET_UPLOADER_NAME, uploader.getName()),
                hasParamActionMatcher(GERRIT_PATCHSET_UPLOADER_EMAIL, uploader.getEmail())
        ));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with correct change owner and uploader parameters when there are no default parameters present.
     * And sets the event.patchSet.uploader and event.uploader to null.
     */
    @Test
    public void testScheduleWithOwnerAndBothUploadersNull() {
        Queue queue = mockJenkinsQueue();
        AbstractProject project = mockProject(Collections.<ParameterDefinition>emptyList());

        final Account owner = new Account("Bobby", "bobby@somewhere.com");

        mockPluginConfig(0);

        final PatchsetCreated event = Setup.createPatchsetCreatedWithAccounts(owner, null, null);
        final GerritCause gerritCause = spy(new GerritCause(event, true));

        GerritTrigger trigger = Setup.createDefaultTrigger(project);

        Setup.setTrigger(trigger, project);
        trigger.setEscapeQuotes(false);
        doReturn("http://mock.url").when(gerritCause).getUrl();

        trigger.schedule(gerritCause, event);
        verify(queue).schedule2(same(project), eq(0), hasAllActions(
                hasCauseActionContainingCauseMatcher(gerritCause),
                hasParamActionMatcher(GERRIT_CHANGE_OWNER, owner.getNameAndEmail()),
                hasParamActionMatcher(GERRIT_CHANGE_OWNER_NAME, owner.getName()),
                hasParamActionMatcher(GERRIT_CHANGE_OWNER_EMAIL, owner.getEmail()),
                hasParamActionMatcher(GERRIT_PATCHSET_UPLOADER, ""),
                hasParamActionMatcher(GERRIT_PATCHSET_UPLOADER_NAME, ""),
                hasParamActionMatcher(GERRIT_PATCHSET_UPLOADER_EMAIL, "")
        ));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild2(int, hudson.model.Cause, hudson.model.Action...)}
     * gets called with correct change owner and uploader parameters when there are no default parameters present.
     * And sets the event.patchSet.uploader and event.uploader to null.
     */
    @Test
    public void testScheduleWithOwnerAndPartOfUploadersNull() {
        Queue queue = mockJenkinsQueue();
        AbstractProject project = mockProject(Collections.<ParameterDefinition>emptyList());

        final Account owner = new Account("Bobby", "bobby@somewhere.com");
        final Account uploader = new Account("Bobby", null);

        mockPluginConfig(0);

        final PatchsetCreated event = Setup.createPatchsetCreatedWithAccounts(owner, uploader, uploader);
        final GerritCause gerritCause = spy(new GerritCause(event, true));

        GerritTrigger trigger = Setup.createDefaultTrigger(project);

        Setup.setTrigger(trigger, project);
        trigger.setEscapeQuotes(false);
        doReturn("http://mock.url").when(gerritCause).getUrl();

        trigger.schedule(gerritCause, event);
        verify(queue).schedule2(same(project), eq(0), hasAllActions(
                hasCauseActionContainingCauseMatcher(gerritCause),
                hasParamActionMatcher(GERRIT_CHANGE_OWNER, owner.getNameAndEmail()),
                hasParamActionMatcher(GERRIT_CHANGE_OWNER_NAME, owner.getName()),
                hasParamActionMatcher(GERRIT_CHANGE_OWNER_EMAIL, owner.getEmail()),
                hasParamActionMatcher(GERRIT_PATCHSET_UPLOADER, ""),
                hasParamActionMatcher(GERRIT_PATCHSET_UPLOADER_NAME, uploader.getName()),
                hasParamActionMatcher(GERRIT_PATCHSET_UPLOADER_EMAIL, "")
        ));
    }

    /**
     * Tests GerritTrigger.retriggerThisBuild.
     */
    @Test
    public void testRetriggerThisBuild() {
        AbstractProject project = mockProject();

        Queue queue = mockConfig(project);

        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getNumber()).thenReturn(1);
        when(build.getProject()).thenReturn(project);
        when(build.getParent()).thenReturn(project);

        GerritTrigger trigger = Setup.createDefaultTrigger(project);

        PatchsetCreated event = Setup.createPatchsetCreated();
        when(listener.isBuilding(project, event)).thenReturn(false);
        Setup.setTrigger(trigger, project);
        trigger.setGerritProjects(Collections.<GerritProject>emptyList());
        trigger.setEscapeQuotes(false);
        trigger.setSilentMode(false);

        TriggerContext context = new TriggerContext(build, event, Collections.<TriggeredItemEntity>emptyList());

        trigger.retriggerThisBuild(context);

        verify(listener).onRetriggered(same(project), same(event), anyListOf(Run.class));
        verify(queue).schedule2(same(project), eq(0), hasCauseActionContainingUserCause());
    }

    /**
     * Tests GerritTrigger.retriggerThisBuild when the trigger is configured for silentMode.
     */
    @Test
    public void testRetriggerThisBuildSilent() {
        AbstractProject project = mockProject();

        Queue queue = mockConfig(project);

        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getNumber()).thenReturn(1);
        when(build.getProject()).thenReturn(project);
        when(build.getParent()).thenReturn(project);

        PatchsetCreated event = Setup.createPatchsetCreated();

        when(listener.isBuilding(project, event)).thenReturn(false);

        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        Setup.setTrigger(trigger, project);
        trigger.setGerritProjects(Collections.<GerritProject>emptyList());

        TriggerContext context = new TriggerContext(build, event, Collections.<TriggeredItemEntity>emptyList());

        trigger.retriggerThisBuild(context);

        verify(listener, never()).onRetriggered(isA(Job.class),
                isA(PatchsetCreated.class),
                anyListOf(Run.class));
        verify(queue).schedule2(same(project), eq(0), hasCauseActionContainingUserCause());
    }

    /**
     * Tests GerritTrigger.retriggerAllBuilds with one additional build in the context.
     */
    @Test
    public void testRetriggerAllBuilds() {
        final AbstractProject thisProject = mockProject();
        final AbstractProject otherProject = mockProject("Other_MockedProject");

        Queue queue = mockConfig(thisProject, otherProject);
        mockDependencyQueueTaskDispatcherConfig();

        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        AbstractBuild thisBuild = mock(AbstractBuild.class);
        when(thisBuild.getNumber()).thenReturn(1);
        when(thisBuild.getProject()).thenReturn(thisProject);
        when(thisBuild.getParent()).thenReturn(thisProject);

        PatchsetCreated event = Setup.createPatchsetCreated();

        when(listener.isBuilding(event)).thenReturn(false);


        GerritTrigger thisTrigger = Setup.createDefaultTrigger(thisProject);
        thisTrigger.setGerritProjects(Collections.<GerritProject>emptyList());
        thisTrigger.setEscapeQuotes(false);
        thisTrigger.setSilentMode(false);
        Setup.setTrigger(thisTrigger, thisProject);


        GerritTrigger otherTrigger = Setup.createDefaultTrigger(otherProject);
        otherTrigger.setGerritProjects(Collections.<GerritProject>emptyList());
        otherTrigger.setEscapeQuotes(false);
        otherTrigger.setSilentMode(false);
        Setup.setTrigger(otherTrigger, otherProject);

        AbstractBuild otherBuild = mock(AbstractBuild.class);
        when(otherBuild.getNumber()).thenReturn(1);
        when(otherBuild.getProject()).thenReturn(otherProject);
        when(otherBuild.getParent()).thenReturn(otherProject);

        TriggerContext context = new TriggerContext(event);
        context.setThisBuild(thisBuild);
        context.addOtherBuild(otherBuild);

        thisTrigger.retriggerAllBuilds(context);

        verify(listener).onRetriggered(thisProject, event, null);
        verify(queue).schedule2(same(thisProject), eq(0), hasCauseActionContainingUserCause());

        verify(listener).onRetriggered(otherProject, event, null);
        verify(queue).schedule2(same(otherProject), eq(0), hasCauseActionContainingUserCause());

        verify(dispatcherMock, times(1)).onTriggeringAll(eq(event));
        verify(dispatcherMock, times(1)).onDoneTriggeringAll(eq(event));
    }

    /**
     * Tests {@link EventListener#gerritEvent(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)}
     * with a normal scenario.
     */
    @Test
    public void testGerritEvent() {
        AbstractProject project = mockProject();
        Queue queue = mockConfig(project);
        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        GerritProject gP = mock(GerritProject.class);
        doReturn(true).when(gP).isInteresting(any(String.class), any(String.class), any(String.class));
        when(gP.getFilePaths()).thenReturn(null);


        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        Setup.setTrigger(trigger, project);
        trigger.setGerritProjects(Collections.nCopies(1, gP));
        trigger.setEscapeQuotes(false);
        trigger.setSilentMode(false);
        Whitebox.setInternalState(trigger, "job", project);

        PatchsetCreated event = Setup.createPatchsetCreated();

        trigger.createListener().gerritEvent(event);

        verify(listener).onTriggered(same(project), same(event));
        verify(queue).schedule2(same(project), anyInt(), hasCauseActionContainingCause(null));
    }

    /**
     * Tests {@link EventListener#gerritEvent(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)}
     * with a non buildable project.
     *
     * @throws java.io.IOException if so.
     */
    @Test
    public void testGerritEventNotBuildable() throws IOException {
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullName()).thenReturn("MockedProject");
        when(project.isBuildable()).thenReturn(false);
        Queue queue = mockConfig(project);
        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        Setup.setTrigger(trigger, project);
        trigger.setGerritProjects(Collections.<GerritProject>emptyList());
        trigger.setEscapeQuotes(false);
        trigger.setSilentMode(false);

        PatchsetCreated event = Setup.createPatchsetCreated();

        EventListener eventListener = trigger.createListener();
        eventListener = PowerMockito.spy(eventListener);
        eventListener.gerritEvent(event);

        verifyZeroInteractions(listener);
        verify(project).addTrigger(same(trigger));
        verify(project).getTriggers();
        verify(project).isBuildable();
        verify(queue, never()).schedule2(same(project), anyInt(), any(List.class));
        verify(eventListener, never()).schedule(
                any(GerritTrigger.class),
                any(GerritCause.class),
                any(GerritTriggeredEvent.class));
        verify(eventListener, never()).schedule(
                any(GerritTrigger.class),
                any(GerritCause.class),
                any(GerritTriggeredEvent.class),
                any(AbstractProject.class));
    }

    /**
     * Tests {@link EventListener#gerritEvent(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)}
     * with a non interesting change.
     */
    @Test
    public void testGerritEventNotInteresting() {
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullName()).thenReturn("MockedProject");
        when(project.isBuildable()).thenReturn(true);

        mockConfig(project);

        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        GerritProject gP = mock(GerritProject.class);
        doReturn(false).when(gP).isInteresting(any(String.class), any(String.class), any(String.class));
        when(gP.getFilePaths()).thenReturn(null);

        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        Setup.setTrigger(trigger, project);
        trigger.setGerritProjects(Collections.nCopies(1, gP));
        trigger.setEscapeQuotes(false);
        trigger.setSilentMode(false);
        Whitebox.setInternalState(trigger, "job", project);

        PatchsetCreated event = Setup.createPatchsetCreated();

        EventListener eventListener = trigger.createListener();
        eventListener = PowerMockito.spy(eventListener);
        eventListener.gerritEvent(event);

        verify(listener, never()).onTriggered(same(project), same(event));
        verify(project).isBuildable();
        verify(eventListener, never()).schedule(
                any(GerritTrigger.class),
                any(GerritCause.class),
                any(GerritTriggeredEvent.class));
        verify(eventListener, never()).schedule(
                any(GerritTrigger.class),
                any(GerritCause.class),
                any(GerritTriggeredEvent.class),
                any(AbstractProject.class));
    }

    /**
     * Tests {@link EventListener#gerritEvent(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)}.
     * With a ManualPatchsetCreated event.
     */
    @Test
    public void testGerritEventManualEvent() {
        AbstractProject project = mockProject();

        Queue queue = mockConfig(project);

        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        GerritProject gP = mock(GerritProject.class);
        doReturn(true).when(gP).isInteresting(any(String.class), any(String.class), any(String.class));
        when(gP.getFilePaths()).thenReturn(null);

        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        Setup.setTrigger(trigger, project);
        trigger.setGerritProjects(Collections.nCopies(1, gP));
        trigger.setEscapeQuotes(false);
        trigger.setSilentMode(false);
        Whitebox.setInternalState(trigger, "job", project);

        ManualPatchsetCreated event = Setup.createManualPatchsetCreated();

        trigger.createListener().gerritEvent(event);

        verify(listener).onTriggered(same(project), same(event));
        verify(queue).schedule2(same(project), eq(0), hasCauseActionContainingUserCause());
    }

    /**
     * Tests {@link EventListener#gerritEvent(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)}.
     * With a RefUpdated event with short ref name.
     */
    @Test
    public void testGerritEventRefUpdatedShortName() {
        AbstractProject project = mockProject();

        Queue queue = mockConfig(project);

        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        List<Branch> branches = new ArrayList<Branch>();
        Branch br = new Branch();
        br.setCompareType(CompareType.PLAIN);
        br.setPattern("master");
        branches.add(br);

        GerritProject gP = new GerritProject(
                CompareType.PLAIN, "job", branches, null, null, null, false);

        GerritTrigger trigger = Setup.createRefUpdatedTrigger(project);
        Setup.setTrigger(trigger, project);
        trigger.setGerritProjects(Collections.nCopies(1, gP));
        trigger.setEscapeQuotes(false);
        trigger.setSilentMode(false);
        Whitebox.setInternalState(trigger, "job", project);

        RefUpdated event = Setup.createRefUpdated(PluginImpl.DEFAULT_SERVER_NAME, "job", "master");

        trigger.createListener().gerritEvent(event);

        verify(listener).onTriggered(same(project), same(event));
        verify(queue).schedule2(same(project), eq(3), anyListOf(Action.class));
    }

    /**
     * Tests {@link EventListener#gerritEvent(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)}.
     * With a RefUpdated event with long ref name.
     */
    @Test
    public void testGerritEventRefUpdatedLongName() {
        AbstractProject project = mockProject();

        Queue queue = mockConfig(project);

        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        List<Branch> branches = new ArrayList<Branch>();
        Branch br = new Branch();
        br.setCompareType(CompareType.PLAIN);
        br.setPattern("master");
        branches.add(br);

        GerritProject gP = new GerritProject(
                CompareType.PLAIN, "job", branches, null, null, null, false);

        GerritTrigger trigger = Setup.createRefUpdatedTrigger(project);
        Setup.setTrigger(trigger, project);
        trigger.setGerritProjects(Collections.nCopies(1, gP));
        trigger.setEscapeQuotes(false);
        trigger.setSilentMode(false);
        Whitebox.setInternalState(trigger, "job", project);

        RefUpdated event = Setup.createRefUpdated(PluginImpl.DEFAULT_SERVER_NAME, "job", "refs/heads/master");

        trigger.createListener().gerritEvent(event);

        verify(listener).onTriggered(same(project), same(event));
        verify(queue).schedule2(same(project), eq(3), anyListOf(Action.class));
    }

    /**
     * Tests {@link EventListener#gerritEvent(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)}.
     * With a RefUpdated event with a tag as ref name.
     */
    @Test
    public void testGerritEventRefUpdatedWithTag() {
        AbstractProject project = mockProject();

        Queue queue = mockConfig(project);

        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        List<Branch> branches = new ArrayList<Branch>();
        Branch br = new Branch();
        br.setCompareType(CompareType.PLAIN);
        br.setPattern("refs/tags/1.0");
        branches.add(br);

        GerritProject gP = new GerritProject(
                CompareType.PLAIN, "job", branches, null, null, null, false);

        GerritTrigger trigger = Setup.createRefUpdatedTrigger(project);
        Setup.setTrigger(trigger, project);
        trigger.setGerritProjects(Collections.nCopies(1, gP));
        trigger.setEscapeQuotes(false);
        trigger.setSilentMode(false);
        Whitebox.setInternalState(trigger, "job", project);

        RefUpdated event = Setup.createRefUpdated(PluginImpl.DEFAULT_SERVER_NAME, "job", "refs/tags/1.0");

        trigger.createListener().gerritEvent(event);

        verify(listener).onTriggered(same(project), same(event));
        verify(queue).schedule2(same(project), eq(3), anyListOf(Action.class));
    }

    /**
     * Tests {@link EventListener#gerritEvent(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)}
     * with a normal scenario, but with silentMode on.
     */
    @Test
    public void testGerritEventSilentMode() {
        AbstractProject project = mockProject();

        Queue queue = mockConfig(project);

        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        GerritProject gP = mock(GerritProject.class);
        doReturn(true).when(gP).isInteresting(any(String.class), any(String.class), any(String.class));
        when(gP.getFilePaths()).thenReturn(null);

        GerritTrigger trigger = Setup.createDefaultTrigger(null);
        Setup.setTrigger(trigger, project);
        trigger.setGerritProjects(Collections.nCopies(1, gP));
        Whitebox.setInternalState(trigger, "job", project);

        PatchsetCreated event = Setup.createPatchsetCreated();

        trigger.createListener().gerritEvent(event);

        verify(listener, never()).onTriggered(same(project), same(event));
        verify(queue).schedule2(same(project), anyInt(), hasCauseActionContainingCause(null));
    }

    /**
     * Tests {@link EventListener#gerritEvent(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)}.
     * With a ManualPatchsetCreated event and silentMode on.
     */
    @Test
    public void testGerritEventManualEventSilentMode() {
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullName()).thenReturn("MockProject");
        when(project.isBuildable()).thenReturn(true);
        Queue queue = mockConfig(project);
        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        GerritProject gP = mock(GerritProject.class);
        doReturn(true).when(gP).isInteresting(any(String.class), any(String.class), any(String.class));
        when(gP.getFilePaths()).thenReturn(null);

        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        Setup.setTrigger(trigger, project);
        trigger.setGerritProjects(Collections.nCopies(1, gP));
        //Whitebox.setInternalState(trigger, "job", project);

        ManualPatchsetCreated event = Setup.createManualPatchsetCreated();

        EventListener eventListener = trigger.createListener();
        eventListener = spy(eventListener);
        eventListener.gerritEvent(event);

        verify(listener, never()).onTriggered(same(project), same(event));
        verify(eventListener).schedule(same(trigger), argThat(new IsAManualCause(true)), same(event));
        verify(queue).schedule2(same(project), eq(0), hasCauseActionContainingCause(null));
    }

    /**
     * Simple instance matcher for
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritManualCause}.
     */
    static class IsAManualCause extends BaseMatcher<GerritCause> {

        private final InstanceOf internal;
        private boolean silentMode;

        /**
         * Constructor.
         *
         * @param silentMode the silent mode value to check for
         */
        public IsAManualCause(boolean silentMode) {
            internal = new InstanceOf(GerritManualCause.class);
            this.silentMode = silentMode;
        }

        @Override
        public boolean matches(Object actual) {
            if (internal.matches(actual)) {
                GerritManualCause c = (GerritManualCause)actual;
                return c.isSilentMode() == silentMode;
            }
            return false;
        }

        @Override
        public void describeTo(Description description) {
            internal.describeTo(description);
            if (silentMode) {
                description.appendText("silent ");
            } else {
                description.appendText("loud ");
            }
        }
    }

    /**
     * Tests {@link GerritTrigger#createParameters(
     * com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent,
     * hudson.model.Job)} with a normal scenario.
     * this is a test case that checks that
     * the Trigger is creating parameters having escaped quotes or not
     * when the escapeQuotes setting is on.
     */
    @Test
    public void testCreateParametersWhenTriggerWithEscapeQuotesOn() {
        mockConfig();
        String stringWithQuotes = "Fixed \" the thing to make \" some thing fun";
        String stringWithQuotesEscaped = "Fixed \\\" the thing to make \\\" some thing fun";
        String stringWithoutQuotes = "Fixed  the thing to make  some thing fun";

        //prepare AbstractProject object
        AbstractProject project = mockProject(Collections.<ParameterDefinition>emptyList());

        //prepare  PatchsetCreated object
        JSONObject patch = new JSONObject();
        patch.put(NUMBER, "2");
        patch.put(REVISION, "ad123456789");
        patch.put(REF, "refs/changes/00/100/2");

        JSONObject jsonAccount = new JSONObject();
        jsonAccount.put(EMAIL, "robert.sandell@sonyericsson.com");
        jsonAccount.put(NAME, "Bobby");

        Change changeWithQuotes = prepareChangeObjForMockTest("project", "branch", "I2343434344",
                "100", stringWithQuotes, "commitMessage", jsonAccount, "http://localhost:8080");
        Change changeWithoutQuotes = prepareChangeObjForMockTest("project", "branch", "I2343434344",
                "100", stringWithoutQuotes, "commitMessage", jsonAccount, "http://localhost:8080");

        PatchsetCreated eventWithQuotes = preparePatchsetCreatedObjForMockTest(changeWithQuotes,
                new PatchSet(patch), GerritEventType.PATCHSET_CREATED);
        PatchsetCreated eventWithoutQuotes = preparePatchsetCreatedObjForMockTest(changeWithoutQuotes,
                new PatchSet(patch), GerritEventType.PATCHSET_CREATED);
        //mock the returned url
        mockPluginConfig(0);

        //prepare GerritTrigger object with the escapeQuotes setting is on.
        GerritTrigger triggerWithEscapeQuotesOn = Setup.createDefaultTrigger(project);
        Setup.setTrigger(triggerWithEscapeQuotesOn, project);

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
     * Tests {@link GerritTrigger#createParameters(
     * com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent,
     * hudson.model.Job)} with a normal scenario.
     * this is a test case that checks that
     * the Trigger is creating parameters having escaped quotes or not
     * when the escapeQuotes setting is off.
     */
    @Test
    public void testCreateParametersWhenTriggerWithEscapeQuotesOff() {
        mockConfig();
        String stringWithQuotes = "Fixed \" the thing to make \" some thing fun";
        String stringWithoutQuotes = "Fixed  the thing to make  some thing fun";

        //prepare AbstractProject object
        AbstractProject project = mockProject(Collections.<ParameterDefinition>emptyList());

        //prepare  PatchsetCreated object
        JSONObject patch = new JSONObject();
        patch.put(NUMBER, "2");
        patch.put(REVISION, "ad123456789");
        patch.put(REF, "refs/changes/00/100/2");

        JSONObject jsonAccount = new JSONObject();
        jsonAccount.put(EMAIL, "robert.sandell@sonyericsson.com");
        jsonAccount.put(NAME, "Bobby");

        Change changeWithQuotes = prepareChangeObjForMockTest("project", "branch", "I2343434344",
                "100", stringWithQuotes, "commitMessage", jsonAccount, "http://localhost:8080");
        Change changeWithoutQuotes = prepareChangeObjForMockTest("project", "branch", "I2343434344",
                "100", stringWithoutQuotes, "commitMessage", jsonAccount, "http://localhost:8080");

        PatchsetCreated eventWithQuotes = preparePatchsetCreatedObjForMockTest(changeWithQuotes,
                new PatchSet(patch), GerritEventType.PATCHSET_CREATED);
        PatchsetCreated eventWithoutQuotes = preparePatchsetCreatedObjForMockTest(changeWithoutQuotes,
                new PatchSet(patch), GerritEventType.PATCHSET_CREATED);

        //mock the returned url
        mockPluginConfig(0);

        //prepare GerritTrigger object with the escapeQuotes setting is off.
        GerritTrigger triggerWithEscapeQuotesOff = Setup.createDefaultTrigger(project);
        Setup.setTrigger(triggerWithEscapeQuotesOff, project);
        triggerWithEscapeQuotesOff.setEscapeQuotes(false);

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
     * Tests {@link GerritTrigger#createParameters(
     * com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent,
     * hudson.model.Job)} with a normal scenario.
     * this is a test case that checks that
     * the Trigger is creating parameters having human readable message or not
     * when the readableMessage setting is on.
     */
    @Test
    public void testCreateParametersWhenTriggerWithReadableMessageOn() {
        mockConfig();
        String stringReadable = "This is human readable message";

        //prepare AbstractProject object
        AbstractProject project = mockProject(Collections.<ParameterDefinition>emptyList());

        //prepare  PatchsetCreated object
        JSONObject patch = new JSONObject();
        patch.put(NUMBER, "2");
        patch.put(REVISION, "ad123456789");
        patch.put(REF, "refs/changes/00/100/2");

        JSONObject jsonAccount = new JSONObject();
        jsonAccount.put(EMAIL, "robert.sandell@sonyericsson.com");
        jsonAccount.put(NAME, "Bobby");

        Change change = prepareChangeObjForMockTest("project", "branch", "I2343434344",
                "100", "Subject", stringReadable, jsonAccount, "http://localhost:8080");

        PatchsetCreated event = preparePatchsetCreatedObjForMockTest(change,
                new PatchSet(patch), GerritEventType.PATCHSET_CREATED);
        //mock the returned url
        mockPluginConfig(0);

        //prepare GerritTrigger object with the readableMessage setting is on.
        GerritTrigger triggerWithReadableMessageOn = Setup.createDefaultTrigger(project);
        Setup.setTrigger(triggerWithReadableMessageOn, project);
        triggerWithReadableMessageOn.setReadableMessage(true);

        //the Trigger is creating parameters with encoded message in "commitMessage".
        ParametersAction paremetersAction =
                triggerWithReadableMessageOn.createParameters(event, project);
        ParameterValue strPara =
                new TextParameterValue(GERRIT_CHANGE_COMMIT_MESSAGE.name(), stringReadable);
        verify(change, times(1)).getCommitMessage();
        assertEquals(strPara, paremetersAction.getParameter(GERRIT_CHANGE_COMMIT_MESSAGE.name()));
    }

    /**
     * Tests {@link GerritTrigger#createParameters(
     * com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent,
     * hudson.model.Job)} with a normal scenario.
     * this is a test case that checks that
     * the Trigger is creating parameters having human readable message or not
     * when the readableMessage setting is off.
     */
    @Test
    public void testCreateParametersWhenTriggerWithReadableMessageOff() {
        mockConfig();
        String stringReadable = "This is human readable message";
        String stringEncoded = "VGhpcyBpcyBodW1hbiByZWFkYWJsZSBtZXNzYWdl";

        //prepare AbstractProject object
        AbstractProject project = mockProject(Collections.<ParameterDefinition>emptyList());

        //prepare  PatchsetCreated object
        JSONObject patch = new JSONObject();
        patch.put(NUMBER, "2");
        patch.put(REVISION, "ad123456789");
        patch.put(REF, "refs/changes/00/100/2");

        JSONObject jsonAccount = new JSONObject();
        jsonAccount.put(EMAIL, "robert.sandell@sonyericsson.com");
        jsonAccount.put(NAME, "Bobby");

        Change change = prepareChangeObjForMockTest("project", "branch", "I2343434344",
                "100", "Subject", stringReadable, jsonAccount, "http://localhost:8080");

        PatchsetCreated event = preparePatchsetCreatedObjForMockTest(change,
                new PatchSet(patch), GerritEventType.PATCHSET_CREATED);

        //mock the returned url
        mockPluginConfig(0);

        //prepare GerritTrigger object with the escapeQuotes setting is off.
        GerritTrigger triggerWithReadableMessageOff = Setup.createDefaultTrigger(project);
        Setup.setTrigger(triggerWithReadableMessageOff, project);

        //the Trigger is creating parameters with escaped quote in "subject"
        ParametersAction paremetersAction =
                triggerWithReadableMessageOff.createParameters(event, project);
        ParameterValue strPara =
                new Base64EncodedStringParameterValue(GERRIT_CHANGE_COMMIT_MESSAGE.name(), stringEncoded);
        verify(change, times(1)).getCommitMessage();
        assertEquals(strPara, paremetersAction.getParameter(GERRIT_CHANGE_COMMIT_MESSAGE.name()));
    }

    /**
     * Prepare a new Mock Object of Change for utility test
     * {@link com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change}.
     *
     * @param project       the result of calling getProject() on this mocked Object.
     * @param branch        the result of calling getBranch() on this mocked Object.
     * @param id            the result of calling getId() on this mocked Object.
     * @param number        the result of calling getNumber() on this mocked Object.
     * @param subject       the result of calling getSubject() on this mocked Object.
     * @param commitMessage the result of calling getCommitMessage() on this mocked Object.
     * @param jsonAccount   used for creating a Account object as the result of
     *                      calling getOwner() on this mocked Object.
     * @param url           the result of calling getUrl() on this mocked Object.
     * @return a new Change Object.
     */
    private Change prepareChangeObjForMockTest(
            String project,
            String branch,
            String id,
            String number,
            String subject,
            String commitMessage,
            JSONObject jsonAccount,
            String url) {
        Change change = PowerMockito.mock(Change.class);
        doReturn(project).when(change).getProject();
        doReturn(branch).when(change).getBranch();
        doReturn(id).when(change).getId();
        doReturn(number).when(change).getNumber();
        when(change.getSubject()).thenReturn(subject);
        doReturn(commitMessage).when(change).getCommitMessage();
        doReturn(new Account(jsonAccount)).when(change).getOwner();
        doReturn(url).when(change).getUrl();
        return change;
    }

    /**
     * Prepare a new Mock Object of PatchsetCreated for utility test
     * {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated}.
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
     * Does a static mock of {@link PluginImpl} and other singletons.
     * And specifically the retrieval of Config and the frontendUrl.
     *
     * @param jobs the jobs that should be retrievable from {@link Jenkins#getItemByFullName(String, Class)}.
     * @return the mocked Queue object to use for schedule verification.
     */
    private static Queue mockConfig(AbstractProject... jobs) {
        PowerMockito.mockStatic(PluginImpl.class);
        PluginImpl plugin = PowerMockito.mock(PluginImpl.class);
        GerritServer server = mock(GerritServer.class);
        when(plugin.getServer(any(String.class))).thenReturn(server);
        GerritHandler handler = mock(GerritHandler.class);
        when(plugin.getHandler()).thenReturn(handler);
        mockStatic(Jenkins.class);
        Jenkins jenkins = mock(Jenkins.class);
        PowerMockito.when(Jenkins.getInstance()).thenReturn(jenkins);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("Tester");
        PowerMockito.when(Jenkins.getAuthentication()).thenReturn(authentication);
        if (jobs != null) {
            for (AbstractProject j: jobs) {
                if (j != null) {
                    when(jenkins.getItemByFullName(eq(j.getFullName()), same(AbstractProject.class))).thenReturn(j);
                    when(jenkins.getItemByFullName(eq(j.getFullName()), same(Job.class))).thenReturn(j);
                }
            }
        }
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        config = spy(config);
        doReturn("http://mock.url").when(config).getGerritFrontEndUrlFor(any(String.class), any(String.class));
        when(server.getConfig()).thenReturn(config);
        PowerMockito.when(PluginImpl.getInstance()).thenReturn(plugin);

        return mockQueue(jenkins);
    }

    /**
     * Does a mock of {@link DependencyQueueTaskDispatcher}.
     * And specifically the retrieval of Config and the frontendUrl.
     */
    private void mockDependencyQueueTaskDispatcherConfig() {
        PowerMockito.mockStatic(DependencyQueueTaskDispatcher.class);
        dispatcherMock = PowerMockito.mock(DependencyQueueTaskDispatcher.class);
        PowerMockito.when(DependencyQueueTaskDispatcher.getInstance()).thenReturn(dispatcherMock);
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

    /**
     * Tests {@link GerritTrigger#gerritSlavesToWaitFor(String)}. It should
     * return empty slave list when the Gerrit Server is not found.
     */
    @Test
    public void shouldReturnEmptySlaveListWhenGerritServerNotFound() {
        // setup
        mockConfig();
        GerritTrigger gerritTrigger = Setup.createDefaultTrigger(null);

        // actual test
        List<GerritSlave> slaves = gerritTrigger.gerritSlavesToWaitFor("unexistingServer");
        assertNotNull(slaves);
        assertEquals(0, slaves.size());
    }

    /**
     * Tests {@link GerritTrigger#gerritSlavesToWaitFor(String)}. It should
     * return empty slave list when not configured.
     */
    @Test
    public void shouldReturnEmptySlaveListWhenNotConfigured() {
        mockConfig();
        IGerritHudsonTriggerConfig configMock = setupSeverConfigMock();
        GerritTrigger gerritTrigger = Setup.createDefaultTrigger(null);

        // Replication config not defined
        List<GerritSlave> slaves = gerritTrigger.gerritSlavesToWaitFor(PluginImpl.DEFAULT_SERVER_NAME);
        assertNotNull(slaves);
        assertEquals(0, slaves.size());

        // ReplicationConfig is defined but is not configured
        ReplicationConfig replicationConfigMock = mock(ReplicationConfig.class);
        when(configMock.getReplicationConfig()).thenReturn(replicationConfigMock);
        slaves = gerritTrigger.gerritSlavesToWaitFor(PluginImpl.DEFAULT_SERVER_NAME);
        assertNotNull(slaves);
        assertEquals(0, slaves.size());
    }

    /**
     * Tests {@link GerritTrigger#gerritSlavesToWaitFor(String)}. It should
     * return slaves configured globally, at the administrative level.
     */
    @Test
    public void shouldReturnGlobalSlavesWhenConfigured() {
        ReplicationConfig replicationConfigMock = setupReplicationConfigMock();
        GerritTrigger gerritTrigger = Setup.createDefaultTrigger(null);

        // Replication is enable but slave list is null
        when(replicationConfigMock.isEnableReplication()).thenReturn(true);
        when(replicationConfigMock.isEnableSlaveSelectionInJobs()).thenReturn(false);
        when(replicationConfigMock.getGerritSlaves()).thenReturn(null);
        List<GerritSlave> slaves = gerritTrigger.gerritSlavesToWaitFor(PluginImpl.DEFAULT_SERVER_NAME);
        assertNotNull(slaves);
        assertEquals(0, slaves.size());

        // Replication is enable but slave list is empty
        when(replicationConfigMock.isEnableReplication()).thenReturn(true);
        when(replicationConfigMock.isEnableSlaveSelectionInJobs()).thenReturn(false);
        when(replicationConfigMock.getGerritSlaves()).thenReturn(Collections.<GerritSlave> emptyList());
        slaves = gerritTrigger.gerritSlavesToWaitFor(PluginImpl.DEFAULT_SERVER_NAME);
        assertNotNull(slaves);
        assertEquals(0, slaves.size());

        // ReplicationConfig is enabled and slaves are defined
        List<GerritSlave> expectedSlaves = Arrays.asList(new GerritSlave("slave1", "slave1", 1234),
            new GerritSlave("slave2", "slave2", 1234));
        when(replicationConfigMock.isEnableReplication()).thenReturn(true);
        when(replicationConfigMock.isEnableSlaveSelectionInJobs()).thenReturn(false);
        when(replicationConfigMock.getGerritSlaves()).thenReturn(expectedSlaves);
        slaves = gerritTrigger.gerritSlavesToWaitFor(PluginImpl.DEFAULT_SERVER_NAME);
        assertNotNull(slaves);
        assertEquals(2, slaves.size());
        assertEquals(expectedSlaves, slaves);
    }

    /**
     * Tests {@link GerritTrigger#gerritSlavesToWaitFor(String)}. It should
     * return slave configured at the job level.
     */
    @Test
    public void shouldReturnSlaveSelectedInJobWhenConfigured() {
        ReplicationConfig replicationConfigMock = setupReplicationConfigMock();
        GerritTrigger gerritTrigger = new GerritTrigger(null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, true, false, true,
                false, false, "", "", "", "", "", "", "", null, PluginImpl.DEFAULT_SERVER_NAME, "slaveUUID", null,
                false, "", null);

        when(replicationConfigMock.isEnableReplication()).thenReturn(true);
        when(replicationConfigMock.isEnableSlaveSelectionInJobs()).thenReturn(true);
        GerritSlave expectedSlave = new GerritSlave("slaveUUID", "slave1", "slave1", 1234);
        when(replicationConfigMock.getGerritSlave("slaveUUID", true)).thenReturn(expectedSlave);
        List<GerritSlave> slaves = gerritTrigger.gerritSlavesToWaitFor(PluginImpl.DEFAULT_SERVER_NAME);
        assertNotNull(slaves);
        assertEquals(1, slaves.size());
        assertEquals(expectedSlave, slaves.get(0));
    }

    /**
     * Tests {@link GerritTrigger#gerritSlavesToWaitFor(String serverName)}. It should
     * return default slave when slave configure at the job level does not exist.
     */
    @Test
    public void shouldReturnDefaultSlaveWhenJobConfiguredSlaveDoesNotExist() {
        ReplicationConfig replicationConfigMock = setupReplicationConfigMock();
        GerritTrigger gerritTrigger = new GerritTrigger(null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, true, false, true,
                false, false, "", "", "", "", "", "", "", null, PluginImpl.DEFAULT_SERVER_NAME, "slaveUUID", null,
                false, "", null);

        // Replication is configured at job level but slave and default no longer exist.
        when(replicationConfigMock.isEnableReplication()).thenReturn(true);
        when(replicationConfigMock.isEnableSlaveSelectionInJobs()).thenReturn(true);
        when(replicationConfigMock.getGerritSlave("slaveUUID", true)).thenReturn(null);
        List<GerritSlave> slaves = gerritTrigger.gerritSlavesToWaitFor(PluginImpl.DEFAULT_SERVER_NAME);
        assertNotNull(slaves);
        assertEquals(0, slaves.size());

        // Replication is configured at job level but slave no longer exist
        when(replicationConfigMock.isEnableReplication()).thenReturn(true);
        when(replicationConfigMock.isEnableSlaveSelectionInJobs()).thenReturn(true);
        GerritSlave expectedSlave = new GerritSlave("defaultSlaveUUID", "defaultSlave", "defaultSlave", 1234);
        when(replicationConfigMock.getGerritSlave("slaveUUID", true)).thenReturn(expectedSlave);
        slaves = gerritTrigger.gerritSlavesToWaitFor(PluginImpl.DEFAULT_SERVER_NAME);
        assertNotNull(slaves);
        assertEquals(1, slaves.size());
        assertEquals(expectedSlave, slaves.get(0));
    }

    /**
     * Tests {@link GerritTrigger.DescriptorImpl#doCheckDependencyJobsNames(Item project, String value)}.
     * This should check that a project with no dependencies validates; and a project with itself as a dep does not.
     */
    @Test
    public void testDependencyValidationOnlyOneProjectInvolved() {
        dependencySetUp();
        GerritTrigger.DescriptorImpl descriptor = new GerritTrigger.DescriptorImpl();
        assertNotNull(descriptor);
        // No dependencies
        assertSame(FormValidation.Kind.OK, descriptor.doCheckDependencyJobsNames(downstreamProject, "").kind);
        // Self dependency
        assertSame(FormValidation.Kind.ERROR,
                descriptor.doCheckDependencyJobsNames(downstreamProject, "MockedProject").kind);
    }

    /**
     * Tests {@link GerritTrigger.DescriptorImpl#doCheckDependencyJobsNames(Item project, String value)}.
     * It should prevent a cycle from forming and return FormValidation.ok() when no cycle exists.
     */
    @Test
    public void testDependencyValidationTwoProjectsInvolved() {
        dependencySetUp();
        GerritTrigger.DescriptorImpl descriptor = new GerritTrigger.DescriptorImpl();
        assertNotNull(descriptor);

        // Basic dependency value
        assertSame(FormValidation.Kind.OK,
                descriptor.doCheckDependencyJobsNames(downstreamProject, "MockedUpstreamProject").kind);
        // Incorrect dependency value
        assertSame(FormValidation.Kind.ERROR,
                descriptor.doCheckDependencyJobsNames(downstreamProject, "MockedDependency").kind);
        // Two member cycle
        when(upstreamGerritTriggerMock.getDependencyJobsNames()).thenReturn("MockedProject");
        assertSame(FormValidation.Kind.ERROR,
                descriptor.doCheckDependencyJobsNames(downstreamProject, "MockedUpstreamProject").kind);
    }

    /**
     * Tests {@link GerritTrigger.DescriptorImpl#doCheckDependencyJobsNames(Item project, String value)}.
     * It should prevent a cycle from forming and return FormValidation.ok() when no cycle exists.
     */
    @Test
    public void testDependencyValidationThreeProjectsInvolved() {
        dependencySetUp();
        GerritTrigger.DescriptorImpl descriptor = new GerritTrigger.DescriptorImpl();
        assertNotNull(descriptor);

        //Setup dependencies: downstream on upstream, upstream on very-upstream
        when(upstreamGerritTriggerMock.getDependencyJobsNames()).thenReturn("MockedVeryUpstreamProject");
        // Basic dependency chain
        assertSame(FormValidation.Kind.OK,
                descriptor.doCheckDependencyJobsNames(downstreamProject, "MockedUpstreamProject").kind);
        // Three member cycle
        when(veryUpstreamGerritTriggerMock.getDependencyJobsNames()).thenReturn("MockedProject");
        assertSame(FormValidation.Kind.ERROR,
                descriptor.doCheckDependencyJobsNames(downstreamProject, "MockedUpstreamProject").kind);
    }

    /**
     * Tests {@link EventListener#gerritEvent(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)}.
     * With a RefUpdated event with long ref name.
     */
    @Test
    public void testGerritEventPrivateStateChanged() {
        AbstractProject project = mockProject();
        Queue queue = mockConfig(project);
        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        GerritProject gP = mock(GerritProject.class);
        doReturn(true).when(gP).isInteresting(any(String.class), any(String.class), any(String.class));
        when(gP.getFilePaths()).thenReturn(null);


        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        Setup.setTrigger(trigger, project);
        trigger.setGerritProjects(Collections.nCopies(1, gP));
        trigger.setEscapeQuotes(false);
        trigger.setSilentMode(false);
        PluginPrivateStateChangedEvent pluginEvent = new PluginPrivateStateChangedEvent();
        List<PluginGerritEvent> triggerOnEvents = new LinkedList<PluginGerritEvent>();
        triggerOnEvents.add(pluginEvent);
        trigger.setTriggerOnEvents(triggerOnEvents);
        Whitebox.setInternalState(trigger, "job", project);

        PrivateStateChanged event = Setup.createPrivateStateChanged(PluginImpl.DEFAULT_SERVER_NAME, "job", "master");

        trigger.createListener().gerritEvent(event);

        verify(listener).onTriggered(same(project), same(event));
        verify(queue).schedule2(same(project), anyInt(), hasCauseActionContainingCause(null));
    }

    /**
     * Tests {@link EventListener#gerritEvent(com.sonymobile.tools.gerrit.gerritevents.dto.GerritEvent)}.
     * With a RefUpdated event with long ref name.
     */
    @Test
    public void testGerritEventWipStateChanged() {
        AbstractProject project = mockProject();
        Queue queue = mockConfig(project);
        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        GerritProject gP = mock(GerritProject.class);
        doReturn(true).when(gP).isInteresting(any(String.class), any(String.class), any(String.class));
        when(gP.getFilePaths()).thenReturn(null);


        GerritTrigger trigger = Setup.createDefaultTrigger(project);
        Setup.setTrigger(trigger, project);
        trigger.setGerritProjects(Collections.nCopies(1, gP));
        trigger.setEscapeQuotes(false);
        trigger.setSilentMode(false);
        PluginWipStateChangedEvent pluginEvent = new PluginWipStateChangedEvent();
        List<PluginGerritEvent> triggerOnEvents = new LinkedList<PluginGerritEvent>();
        triggerOnEvents.add(pluginEvent);
        trigger.setTriggerOnEvents(triggerOnEvents);
        Whitebox.setInternalState(trigger, "job", project);

        WipStateChanged event = Setup.createWipStateChanged(PluginImpl.DEFAULT_SERVER_NAME, "job", "master");

        trigger.createListener().gerritEvent(event);

        verify(listener).onTriggered(same(project), same(event));
        verify(queue).schedule2(same(project), anyInt(), hasCauseActionContainingCause(null));
    }

    /**
     * Tests that dynamic project configurations do not miss an event from the time
     * at which the trigger was started until the time at which the URL was fetched.
     * @throws Exception on failure
     */
    @PrepareForTest({
            GerritTrigger.class,
            AbstractProject.class,
            ToGerritRunListener.class,
            PluginImpl.class,
            Hudson.class,
            Jenkins.class,
            DependencyQueueTaskDispatcher.class,
            EventListener.class })
    @Test
    public void testDynamicTriggerConfigurationTimeGap() throws Exception {
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullName()).thenReturn("MockedProject");
        when(project.isBuildable()).thenReturn(true);

        Queue queue = mockConfig(project);

        PowerMockito.mockStatic(ToGerritRunListener.class);
        ToGerritRunListener listener = PowerMockito.mock(ToGerritRunListener.class);
        PowerMockito.when(ToGerritRunListener.getInstance()).thenReturn(listener);

        // Set up a temporary file to use as our dynamic configuration URL.
        java.nio.file.Path temporaryConfigFile = java.nio.file.Files.createTempFile("GerritTriggerTest", null);
        java.nio.file.Files.write(temporaryConfigFile, "p=my-project\nb^**".getBytes());
        System.out.println("Temporary file: " + temporaryConfigFile.toString());

        GerritTrigger trigger = new GerritTrigger(null);
        trigger.setDynamicTriggerConfiguration(true);
        trigger.setTriggerConfigURL("file://" + temporaryConfigFile.toString());

        // Set up the job within the trigger.
        Whitebox.setInternalState(trigger, "job", project);

        // We need to make sure that whenever anyone asks for the trigger for any job
        // that it returns this one.
        PowerMockito.mockStatic(GerritTrigger.class);
        when(GerritTrigger.getTrigger(any(Job.class))).thenReturn(trigger);
        assertEquals(trigger, GerritTrigger.getTrigger(project));

        // Because the "stub" methodology doesn't work, we also need to manually replace the "createListener"
        // static method.
        EventListener myListener = new EventListener(project);
        PowerMockito.when(GerritTrigger.createListener(any(Job.class))).thenReturn(myListener);
        assertNotNull(trigger.createListener());

        // Start the trigger.
        trigger.start(project, true);

        // Make sure that the timer task started.
        assertNotNull(Whitebox.getInternalState(trigger, "gerritTriggerTimerTask"));

        // There should be no dynamic projects yet.  They won't show up until the timer
        // task runs once, and there's a constant delay before that happens.
        List<GerritProject> dynamicGerritProjects = trigger.getDynamicGerritProjects();
        assertNull(dynamicGerritProjects);

        PatchsetCreated event = Setup.createPatchsetCreated("my-servername", "my-project", "my-ref");
        trigger.createListener().gerritEvent(event);

        // Wait until the timer task has run for the first time.
        Thread.sleep(GerritTriggerTimer.DELAY_MILLISECONDS + 1000);

        // Now check the dynamic projects again.  This time, there should be one.
        dynamicGerritProjects = trigger.getDynamicGerritProjects();
        assertEquals(1, dynamicGerritProjects.size());

        // Make sure that a job got scheduled.
        verify(listener).onTriggered(same(project), same(event));
        verify(queue).schedule2(same(project), anyInt(), hasCauseActionContainingCause(null));

        // Get rid of the temporary file.
        java.nio.file.Files.delete(temporaryConfigFile);
    }

    /**
     * Setup a ReplicationConfig mock
     * @return the ReplicationConfig mock
     */
    private ReplicationConfig setupReplicationConfigMock() {
        mockConfig();
        IGerritHudsonTriggerConfig configMock = setupSeverConfigMock();
        ReplicationConfig replicationConfigMock = mock(ReplicationConfig.class);
        when(configMock.getReplicationConfig()).thenReturn(replicationConfigMock);
        return replicationConfigMock;
    }

    /**
     * Setup a sever config mock
     * @return the server config mock
     */
    private IGerritHudsonTriggerConfig setupSeverConfigMock() {
        PowerMockito.mockStatic(PluginImpl.class);
        PluginImpl pluginMock = mock(PluginImpl.class);
        when(PluginImpl.getInstance()).thenReturn(pluginMock);
        GerritServer serverMock = mock(GerritServer.class);
        when(pluginMock.getServer(PluginImpl.DEFAULT_SERVER_NAME)).thenReturn(serverMock);
        when(PluginImpl.getServer_(eq(PluginImpl.DEFAULT_SERVER_NAME))).thenReturn(serverMock);
        IGerritHudsonTriggerConfig configMock = mock(IGerritHudsonTriggerConfig.class);
        when(serverMock.getConfig()).thenReturn(configMock);
        return configMock;
    }

    /**
     * Setup the dependency-related fixtures (for form validation).
     */
    public void dependencySetUp() {
        //setup hudson / jenkins (both are needed)
        hudsonMock = mock(Hudson.class);
        PowerMockito.mockStatic(Hudson.class);
        when(Hudson.getInstance()).thenReturn(hudsonMock);
        jenkinsMock = mock(Jenkins.class);
        PowerMockito.mockStatic(Jenkins.class);
        when(Jenkins.getInstance()).thenReturn(jenkinsMock);
        //setup the gerritTrigger mocks which will manage the upstream projects
        upstreamGerritTriggerMock = mock(GerritTrigger.class);
        veryUpstreamGerritTriggerMock = mock(GerritTrigger.class);
        PowerMockito.mockStatic(GerritTrigger.class);
        // Setup of three projects (needed for dependency form validation)
        downstreamProject = PowerMockito.mock(AbstractProject.class);
        upstreamProject = PowerMockito.mock(AbstractProject.class);
        veryUpstreamProject = PowerMockito.mock(AbstractProject.class);
        when(downstreamProject.getFullName()).thenReturn("MockedProject");
        when(upstreamProject.getFullName()).thenReturn("MockedUpstreamProject");
        when(veryUpstreamProject.getFullName()).thenReturn("MockedVeryUpstreamProject");
        when(hudsonMock.getItem(eq("MockedProject"), any(Item.class), eq(Item.class))).
            thenReturn(downstreamProject);
        when(hudsonMock.getItem(eq("MockedUpstreamProject"), any(Item.class), eq(Item.class))).
            thenReturn(upstreamProject);
        when(hudsonMock.getItem(eq("MockedVeryUpstreamProject"), any(Item.class), eq(Item.class))).
            thenReturn(veryUpstreamProject);
        when(jenkinsMock.getItem(eq("MockedProject"), any(Item.class), eq(Item.class))).
            thenReturn(downstreamProject);
        when(jenkinsMock.getItem(eq("MockedUpstreamProject"), any(Item.class), eq(Item.class))).
            thenReturn(upstreamProject);
        when(jenkinsMock.getItem(eq("MockedVeryUpstreamProject"), any(Item.class), eq(Item.class))).
            thenReturn(veryUpstreamProject);
        Setup.setTrigger(upstreamGerritTriggerMock, upstreamProject);
        Setup.setTrigger(veryUpstreamGerritTriggerMock, veryUpstreamProject);
        //No dependencies setup initially.
        when(upstreamGerritTriggerMock.getDependencyJobsNames()).thenReturn("");
        when(veryUpstreamGerritTriggerMock.getDependencyJobsNames()).thenReturn("");
        //next is only for error messages to not fail on NPE.
        PowerMockito.mockStatic(AbstractProject.class);
        when(AbstractProject.findNearest(any(String.class), any(ItemGroup.class))).thenReturn(downstreamProject);
    }

    /**
     * A {@link CoreMatchers#allOf(Iterable)} version for mockito {@link Action} matchers.
     * the list could contain more Actions than provided to check.
     *
     * @param all all the {@link Matcher}s that the list should contain.
     * @return the matcher.
     *
     * @see #hasCauseActionContainingCauseMatcher(GerritCause)
     * @see #hasParamActionMatcher(String, String)
     */
    protected List<Action> hasAllActions(Matcher<List<Action>>... all) {
        return argThat(CoreMatchers.allOf(all));
    }

    /**
     * {@link org.mockito.Matchers#argThat(Matcher)} version of
     * {@link #hasCauseActionContainingCauseMatcher(GerritCause)}.
     *
     * @param cause the GerritCause to check for instance equality.
     * @return the matcher.
     * @see #hasCauseActionContainingCauseMatcher(GerritCause)
     */
    protected List<Action> hasCauseActionContainingCause(final GerritCause cause) {
        return argThat(hasCauseActionContainingCauseMatcher(cause));
    }

    /**
     * A {@link Matcher} that checks a list of {@link Action}s for a {@link CauseAction}
     * containing the provided instance of a  {@link GerritCause}.
     *
     * @param expectedSame the GerritCause to check for instance equality.
     *
     * @return the matcher.
     */
    private BaseMatcher<List<Action>> hasCauseActionContainingCauseMatcher(final GerritCause expectedSame) {
        return new BaseMatcher<List<Action>>() {
            @Override
            public boolean matches(Object item) {
                if (item instanceof List) {
                    for (Action a : ((List<Action>)item)) {
                        if (a instanceof CauseAction) {
                            GerritCause cause = ((CauseAction)a).findCause(GerritCause.class);
                            if (expectedSame == null && cause != null) {
                                //Don't care about the instance just that it exists
                                return true;
                            } else if (expectedSame == null && cause == null) {
                                return false;
                            }
                            if (cause == expectedSame) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                if (expectedSame == null) {
                    description.appendText("does not contain a CauseAction with a GerritCause.");
                } else {
                    description.appendText("does not contain a CauseAction with the valid GerritCause.");
                }
            }
        };
    }

    /**
     * A {@link org.mockito.Matchers#argThat(Matcher)} version of {@link #hasCauseActionContainingUserCauseMatcher()}.
     *
     * @return the matcher.
     * @see #hasCauseActionContainingUserCauseMatcher()
     */
    private List<Action> hasCauseActionContainingUserCause() {
        return argThat(hasCauseActionContainingUserCauseMatcher());
    }

    /**
     * A {@link Matcher} that checks a list of {@link Action}s for a {@link CauseAction}
     * containing any {@link GerritUserCause}.
     *
     * @return the matcher.
     */
    private BaseMatcher<List<Action>> hasCauseActionContainingUserCauseMatcher() {
        return new BaseMatcher<List<Action>>() {
            @Override
            public boolean matches(Object item) {
                if (item instanceof List) {
                    for (Action a : ((List<Action>)item)) {
                        if (a instanceof CauseAction) {
                            GerritCause cause = ((CauseAction)a).findCause(GerritUserCause.class);
                            if (cause != null) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("does not contain a CauseAction with a GerritUserCause.");
            }
        };
    }


    /**
     * A {@link org.mockito.Matchers#argThat(Matcher)} version of {@link #hasParamActionMatcher(String, String)}.
     *
     * @param key the key to find
     * @param value the value to compare
     * @return the matcher
     * @see #hasParamActionMatcher(String, String)
     */
    protected List<Action> hasParamAction(final String key, final String value) {
        return argThat(hasParamActionMatcher(key, value));
    }

    /**
     * A version of {@link #hasParamActionMatcher(String, String)} using {@link GerritTriggerParameters}
     * for the key for simpler usage.
     * @param key the key
     * @param value the value to compare
     * @return the matcher
     * @see #hasParamActionMatcher(String, String)
     */
    private BaseMatcher<List<Action>> hasParamActionMatcher(final GerritTriggerParameters key, final String value) {
        return hasParamActionMatcher(key.name(), value);
    }

    /**
     * {@link Matcher} that checks a list of {@link Action}s for a {@link ParametersAction} that contains any parameter
     * with the specified key whose {@code toString()} method equals the specified value.
     * @param key the key to find
     * @param value the value to compare
     * @return the matcher
     */
    private BaseMatcher<List<Action>> hasParamActionMatcher(final String key, final String value) {
        return new BaseMatcher<List<Action>>() {
            @Override
            public boolean matches(Object item) {
                if (item instanceof List) {
                    for (Action a : ((List<Action>)item)) {
                        if (a instanceof ParametersAction) {
                            ParametersAction parameters = (ParametersAction)a;
                            ParameterValue parameter = parameters.getParameter(key);
                            if (parameter != null) {
                                return value.equals(parameter.getValue());
                            }
                        }
                    }
                }

                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("does not contain a parameter ").
                        appendText(key).appendText(" with value ").appendText(value);
            }
        };
    }


    /**
     * Sets up a more functioning plugin config than {@link #setupSeverConfigMock()}
     * but not as excessive as {@link #mockConfig(AbstractProject[])}.
     *
     * @param buildScheduleDelay the {@link IGerritHudsonTriggerConfig#getBuildScheduleDelay()} to return.
     *
     * @return the config.
     */
    private IGerritHudsonTriggerConfig mockPluginConfig(int buildScheduleDelay) {
        PowerMockito.mockStatic(PluginImpl.class);
        plugin = PowerMockito.mock(PluginImpl.class);
        GerritServer server = mock(GerritServer.class);
        IGerritHudsonTriggerConfig config = Setup.createConfig();
        config = spy(config);
        doReturn("http://mock.url").when(config).getGerritFrontEndUrlFor(any(String.class), any(String.class));
        doReturn("http://mock.url").when(config).getGerritFrontEndUrlFor(any(GerritTriggeredEvent.class));
        when(plugin.getServer(any(String.class))).thenReturn(server);
        when(PluginImpl.getServer_(any(String.class))).thenReturn(server);
        GerritHandler handler = mock(GerritHandler.class);
        when(plugin.getHandler()).thenReturn(handler);
        when(server.getConfig()).thenReturn(config);
        when(PluginImpl.getInstance()).thenReturn(plugin);
        when(config.getBuildScheduleDelay()).thenReturn(buildScheduleDelay);
        return config;
    }

    /**
     * Creates a mocked {@link AbstractProject} with a mocked {@link ParametersDefinitionProperty}
     * containing the provided parameters and a default name.
     * The default name is <strong>MockedProject</strong>
     * @param parameterDefinitionList the parameters
     * @return the mocked project
     * @see #mockProject(String, List)
     */
    private AbstractProject mockProject(List<ParameterDefinition> parameterDefinitionList) {
        return mockProject("MockedProject", parameterDefinitionList);
    }

    /**
     * Creates a mocked {@link AbstractProject} with no {@link ParametersDefinitionProperty} and a default name.
     *
     * @return the mocked project.
     * @see #mockProject(List)
     */
    private AbstractProject mockProject() {
        return mockProject((List<ParameterDefinition>)null);
    }

    /**
     * Creates a mocked {@link AbstractProject} with no {@link ParametersDefinitionProperty}.
     * @param name the name it should have.
     * @return the project.
     * @see #mockProject(String, List)
     */
    private AbstractProject mockProject(String name) {
        return mockProject(name, null);
    }

    /**
     * Creates a mocked {@link AbstractProject} that returns the provided name and parameters
     * @param name the name
     * @param parameterDefinitionList the list of parameters or
     *              {@code null} to not mock the {@link ParametersDefinitionProperty} at all.
     * @return the mocked project
     */
    private AbstractProject mockProject(String name, List<ParameterDefinition> parameterDefinitionList) {
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullDisplayName()).thenReturn(name);
        when(project.getFullName()).thenReturn(name);
        when(project.isBuildable()).thenReturn(true);

        if (parameterDefinitionList != null) {
            ParametersDefinitionProperty parameters = mock(ParametersDefinitionProperty.class);
            when(parameters.getParameterDefinitions()).thenReturn(parameterDefinitionList);
            when(project.getProperty(ParametersDefinitionProperty.class)).thenReturn(parameters);
        }
        return project;
    }

    /**
     * Creates a mock of {@link Jenkins} and its containing {@link Queue}.
     *
     * @return the mocked Queue.
     * @see #mockQueue(Jenkins)
     */
    private static Queue mockJenkinsQueue() {
        Jenkins jenkins = PowerMockito.mock(Jenkins.class);
        PowerMockito.mockStatic(Jenkins.class);
        PowerMockito.when(Jenkins.getInstance()).thenReturn(jenkins);
        return mockQueue(jenkins);
    }

    /**
     * Mocks {@link Jenkins#getQueue()} and sets it up to return {@link ScheduleResult#created(Queue.WaitingItem)} on
     * any calls to {@link Queue#schedule2(Queue.Task, int, List)}.
     *
     * @param jenkins the jenkins mock.
     * @return the mocked queue.
     */
    private static Queue mockQueue(Jenkins jenkins) {
        Queue queue = PowerMockito.mock(Queue.class);
        when(jenkins.getQueue()).thenReturn(queue);
        when(queue.schedule2(any(Queue.Task.class), anyInt(), anyList())).thenAnswer(new Answer<ScheduleResult>() {
            @Override
            public ScheduleResult answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                Queue.WaitingItem i = new Queue.WaitingItem(
                        new GregorianCalendar(), (Queue.Task)arguments[0], (List<Action>)arguments[2]);
                return ScheduleResult.created(i);
            }
        });
        return queue;
    }
}
