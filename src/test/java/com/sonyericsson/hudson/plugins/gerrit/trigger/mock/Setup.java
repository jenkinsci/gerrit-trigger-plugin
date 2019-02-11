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
package com.sonyericsson.hudson.plugins.gerrit.trigger.mock;

import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Account;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Approval;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Change;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Provider;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.RefUpdate;
import com.sonyericsson.hudson.plugins.gerrit.trigger.VerdictCategory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.SkipVote;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginGerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginPatchsetCreatedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginRefUpdatedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil;

import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeMerged;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeRestored;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.DraftPublished;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PrivateStateChanged;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.RefReplicated;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.RefUpdated;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.TopicChanged;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.WipStateChanged;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.security.SecurityRealm;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Assert;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.powermock.api.mockito.PowerMockito;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.spy;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.BRANCH;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.CHANGE;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.EMAIL;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.ID;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.NAME;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.NUMBER;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.OWNER;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.PATCH_SET;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.PROJECT;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.REF;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.REVISION;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.SUBJECT;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.UPLOADER;
import static com.sonymobile.tools.gerrit.gerritevents.dto.GerritEventKeys.URL;


/**
 * Utility class for standard data during testing.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public final class Setup {

    /**
     * Utility class.
     */
    private Setup() {

    }

    /**
     * Gives you a Config mock.
     * @return MockGerritHudsonTriggerConfig mock.
     */
    public static MockGerritHudsonTriggerConfig createConfig() {
        return new MockGerritHudsonTriggerConfig();
    }

    /**
     * Gives you a Config mock with nulled code review values.
     * @return MockGerritHudsonTriggerConfig mock.
     */
    public static MockGerritHudsonTriggerConfig createConfigWithCodeReviewsNull() {
        MockGerritHudsonTriggerConfig config = new MockGerritHudsonTriggerConfig();
        config.setGerritBuildFailedCodeReviewValue(null);
        config.setGerritBuildSuccessfulCodeReviewValue(null);
        config.setGerritBuildNotBuiltCodeReviewValue(null);
        config.setGerritBuildStartedCodeReviewValue(null);
        config.setGerritBuildUnstableCodeReviewValue(null);
        return config;
    }

    /**
     * Gives you a PatchsetCreated mock.
     * @return PatchsetCreated mock.
     */
    public static PatchsetCreated createPatchsetCreated() {
        return createPatchsetCreated(PluginImpl.DEFAULT_SERVER_NAME);
    }

    /**
     * Gives you a PatchsetCreated mock for a specific server name.
     * @param serverName name of the server.
     * @return PatchsetCreated mock.
     */
    public static PatchsetCreated createPatchsetCreated(String serverName) {
        return createPatchsetCreated(serverName, "project", "ref");
    }

    /**
     * Create a new RefUpdated event with given data.
     * @param serverName The server name.
     * @param project The project.
     * @param ref The ref.
     * @return a RefUpdated event
     */
    public static RefUpdated createRefUpdated(String serverName, String project, String ref) {
        RefUpdated event = new RefUpdated();
        Account account = new Account();
        account.setEmail("email@domain.com");
        account.setName("Name");
        event.setAccount(account);
        event.setProvider(new Provider(serverName, "gerrit", "29418", "ssh", "http://gerrit/", "1"));

        RefUpdate refUpdate = new RefUpdate();
        refUpdate.setNewRev("2");
        refUpdate.setOldRev("1");
        refUpdate.setProject(project);
        refUpdate.setRefName(ref);
        event.setRefUpdate(refUpdate);
        event.setEventCreatedOn("1418133772");

        return event;
    }

    /**
     * Create a new patchset created event with the given data.
     * @param serverName The server name
     * @param project The project
     * @param ref The ref
     * @return a pactchsetCreated event
     */
    public static PatchsetCreated createPatchsetCreated(String serverName, String project, String ref) {
        return createPatchsetCreated(serverName, project, ref, "1418133772");
    }

    /**
     * Create a new patchset created event with the given data.
     * @param serverName The server name
     * @param project The project
     * @param ref The ref
     * @param eventCreateOn Timestamp for eventcreateon.
     * @return a patchsetCreated event
     */
    public static PatchsetCreated createPatchsetCreated(String serverName,
                                                        String project,
                                                        String ref,
                                                        String eventCreateOn) {
        PatchsetCreated event = new PatchsetCreated();
        Change change = new Change();
        change.setBranch("branch");
        change.setId("Iddaaddaa123456789");
        change.setNumber("1000");
        Account account = new Account();
        account.setEmail("email@domain.com");
        account.setName("Name");
        change.setOwner(account);
        change.setProject(project);
        change.setSubject("subject");
        change.setUrl("http://gerrit/1000");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        patch.setRevision("9999");
        patch.setRef(ref);
        event.setPatchset(patch);
        event.setProvider(new Provider(serverName, "gerrit", "29418", "ssh", "http://gerrit/", "1"));
        event.setEventCreatedOn(eventCreateOn);
        return event;
    }

    /**
     * Create a new patchset created event with the given data.
     *
     * @param owner owner of the change
     * @param uploader uploader of the patchset
     * @param account account that caused event
     * @return a new PatchsetCreated event.
     */
    public static PatchsetCreated createPatchsetCreatedWithAccounts(Account owner, Account uploader, Account account)
    {
        PatchsetCreated event = createPatchsetCreated();
        event.getChange().setOwner(owner);
        event.getPatchSet().setUploader(uploader);
        event.setAccount(account);
        event.setEventCreatedOn("1418133772");

        return event;
    }

    /**
     * Gives you a DraftPublished mock.
     * @return DraftPublished mock.
     */
    public static DraftPublished createDraftPublished() {
        DraftPublished event = new DraftPublished();
        Change change = new Change();
        change.setBranch("branch");
        change.setId("Iddaaddaa123456789");
        change.setNumber("1000");
        Account account = new Account();
        account.setEmail("email@domain.com");
        account.setName("Name");
        change.setOwner(account);
        change.setProject("project");
        change.setSubject("subject");
        change.setUrl("http://gerrit/1000");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        patch.setRevision("9999");
        event.setPatchset(patch);
        patch.setRef("ref");
        event.setProvider(new Provider(PluginImpl.DEFAULT_SERVER_NAME, "gerrit", "29418", "ssh", "http://gerrit/", "1"));
        event.setEventCreatedOn("1418133772");
        return event;
    }

    /**
     * Gives you a ChangeAbandoned mock.
     * @return ChangeAbandoned mock.
     */
    public static ChangeAbandoned createChangeAbandoned() {
        ChangeAbandoned event = new ChangeAbandoned();
        Change change = new Change();
        change.setBranch("branch");
        change.setId("Iddaaddaa123456789");
        change.setNumber("1000");
        Account account = new Account("Name", "email@domain.com");
        change.setOwner(account);
        change.setProject("project");
        change.setSubject("subject");
        change.setUrl("http://gerrit/1000");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        patch.setRevision("9999");
        event.setPatchset(patch);
        Account abandoner = new Account("Name1", "email@domain1.com");
        event.setAbandoner(abandoner);
        event.setProvider(new Provider(PluginImpl.DEFAULT_SERVER_NAME, "gerrit", "29418", "ssh", "http://gerrit/", "1"));
        event.setEventCreatedOn("1418133772");
        return event;
    }

    /**
     * Gives you a TopicChanged mock.
     * @return TopicChanged mock.
     */
    public static TopicChanged createTopicChanged() {
        TopicChanged event = new TopicChanged();
        Change change = new Change();
        change.setBranch("branch");
        change.setId("Iddaaddaa123456789");
        change.setNumber("1000");
        Account account = new Account("Name", "email@domain.com");
        change.setOwner(account);
        change.setProject("project");
        change.setSubject("subject");
        change.setUrl("http://gerrit/1000");
        change.setTopic("new-topic");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        patch.setRevision("9999");
        event.setPatchset(patch);
        Account changer = new Account("Name1", "email@domain1.com");
        event.setOldTopic("old-topic");
        event.setChanger(changer);
        event.setProvider(new Provider(PluginImpl.DEFAULT_SERVER_NAME, "gerrit", "29418", "ssh", "http://gerrit/", "1"));
        event.setEventCreatedOn("1418133772");
        return event;
    }

    /**
     * Gives you a ChangeMerged mock.
     * @param serverName The server name
     * @param project The project
     * @param ref The ref
     * @return ChangeMerged mock.
     */
    public static ChangeMerged createChangeMerged(String serverName, String project, String ref) {
        return createChangeMergedWithPatchSetDate(serverName, project, ref, new Date());
    }

    /**
     * Gives you a DraftPublished mock.
     * @param serverName The server name
     * @param project The project
     * @param ref The ref
     * @return DraftPublished mock.
     */
    public static DraftPublished createDraftPublished(String serverName, String project, String ref) {
        return createDraftPublishedWithPatchSetDate(serverName, project, ref, new Date());
    }

    /**
     * Gives you a ChangeMerged mock.
     * @return ChangeMerged mock.
     */
    public static ChangeMerged createChangeMerged() {
        ChangeMerged event = new ChangeMerged();
        Change change = new Change();
        change.setBranch("branch");
        change.setId("Iddaaddaa123456789");
        change.setNumber("1000");
        Account account = new Account();
        account.setEmail("email@domain.com");
        account.setName("Name");
        change.setOwner(account);
        change.setProject("project");
        change.setSubject("subject");
        change.setUrl("http://gerrit/1000");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        patch.setRevision("9999");
        event.setPatchset(patch);
        event.setEventCreatedOn("1418133772");
        return event;
    }

    /**
     * Gives you a ChangeMerged mock.
     * @param serverName The server name
     * @param project The project
     * @param ref The ref
     * @param date The patchset's createdOn date
     * @return ChangeMerged mock.
     */
    public static ChangeMerged createChangeMergedWithPatchSetDate(String serverName, String project,
            String ref, Date date) {
        ChangeMerged event = new ChangeMerged();
        Change change = new Change();
        change.setBranch("branch");
        change.setId("Iddaaddaa123456789");
        change.setNumber("1000");
        Account account = new Account();
        account.setEmail("email@domain.com");
        account.setName("Name");
        change.setOwner(account);
        change.setProject(project);
        change.setSubject("subject");
        change.setUrl("http://gerrit/1000");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        patch.setRevision("9999");
        event.setPatchset(patch);
        patch.setRef(ref);
        patch.setCreatedOn(date);
        event.setProvider(new Provider(serverName, "gerrit", "29418", "ssh", "http://gerrit/", "1"));
        event.setEventCreatedOn("1418133772");
        return event;
    }

    /**
     * Gives you a DraftPublished mock.
     * @param serverName The server name
     * @param project The project
     * @param ref The ref
     * @param date The patchset's createdOn date
     * @return DraftPublished mock.
     */
    public static DraftPublished createDraftPublishedWithPatchSetDate(String serverName, String project,
            String ref, Date date) {
        DraftPublished event = new DraftPublished();
        Change change = new Change();
        change.setBranch("branch");
        change.setId("Iddaaddaa123456789");
        change.setNumber("1000");
        Account account = new Account();
        account.setEmail("email@domain.com");
        account.setName("Name");
        change.setOwner(account);
        change.setProject(project);
        change.setSubject("subject");
        change.setUrl("http://gerrit/1000");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        patch.setRevision("9999");
        event.setPatchset(patch);
        patch.setRef(ref);
        patch.setCreatedOn(date);
        event.setProvider(new Provider(serverName, "gerrit", "29418", "ssh", "http://gerrit/", "1"));
        event.setEventCreatedOn("1418133772");
        return event;
    }

    /**
     * Gives you a ChangeRestored mock.
     * @return ChangeRestored mock.
     */
    public static ChangeRestored createChangeRestored() {
        ChangeRestored event = new ChangeRestored();
        Change change = new Change();
        change.setBranch("branch");
        change.setId("Iddaaddaa123456789");
        change.setNumber("1000");
        Account account = new Account("Name", "email@domain.com");
        change.setOwner(account);
        change.setProject("project");
        change.setSubject("subject");
        change.setUrl("http://gerrit/1000");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        patch.setRevision("9999");
        event.setPatchset(patch);
        Account restorer = new Account("Name1", "email@domain1.com");
        event.setRestorer(restorer);
        event.setProvider(new Provider(PluginImpl.DEFAULT_SERVER_NAME, "gerrit", "29418", "ssh", "http://gerrit/", "1"));
        event.setEventCreatedOn("1418133772");
        return event;
    }

    /**
     * Gives you a CommentAdded mock.
     * @return CommentAdded mock.
     */
    public static CommentAdded createCommentAdded() {
        CommentAdded event = new CommentAdded();
        Change change = new Change();
        change.setBranch("branch");
        change.setId("Iddaaddaa123456789");
        change.setNumber("1000");
        Account account = new Account();
        account.setEmail("email@domain.com");
        account.setName("Name");
        change.setOwner(account);
        change.setProject("project");
        change.setSubject("subject");
        change.setUrl("http://gerrit/1000");
        event.setChange(change);
        event.setProvider(new Provider(PluginImpl.DEFAULT_SERVER_NAME, "gerrit", "29418", "ssh", "http://gerrit/", "1"));
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        patch.setRevision("9999");
        event.setPatchset(patch);
        List<Approval> approvals = new LinkedList<Approval>();
        Approval approval = new Approval();
        approval.setType("Code-Review");
        approval.setValue("1");
        approvals.add(approval);
        event.setApprovals(approvals);
        event.setEventCreatedOn("1418133772");
        return event;
    }

    /**
     * Gives you a PatchsetCreated mock as a JSON object.
     * @return PatchsetCreated mock.
     * @see #createPatchsetCreated()
     */
    public static JSONObject createPatchsetCreatedJson() {
        JSONObject event = new JSONObject();
        JSONObject change = new JSONObject();
        JSONObject account = new JSONObject();
        account.put(NAME, "Name");
        account.put(EMAIL, "email@domain.com");

        change.put(PROJECT, "project");
        change.put(BRANCH, "branch");
        change.put(ID, "Iddaaddaa123456789");
        change.put(NUMBER, "1000");
        change.put(SUBJECT, "subject");
        change.put(URL, "http://gerrit/1000");
        change.put(OWNER, account);

        event.put(CHANGE, change);

        JSONObject patchSet = new JSONObject();
        patchSet.put(NUMBER, "1");
        patchSet.put(REVISION, "9999");
        patchSet.put(REF, "refs/changes/1000/1");
        patchSet.put(UPLOADER, account);

        event.put(PATCH_SET, patchSet);
        event.put(UPLOADER, account);

        return event;
    }

    /**
     * Gives you a ManualPatchsetCreated mock.
     * @return PatchsetCreated mock.
     */
    public static ManualPatchsetCreated createManualPatchsetCreated() {
        ManualPatchsetCreated event = new ManualPatchsetCreated();
        Change change = new Change();
        change.setBranch("branch");
        change.setId("Iddaaddaa123456789");
        change.setNumber("1000");
        Account account = new Account();
        account.setEmail("email@domain.com");
        account.setName("Name");
        change.setOwner(account);
        change.setProject("project");
        change.setSubject("subject");
        change.setUrl("http://gerrit/1000");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        patch.setRevision("9999");
        event.setPatchset(patch);
        event.setUserName("Bobby");
        event.setProvider(new Provider(PluginImpl.DEFAULT_SERVER_NAME, "gerrit", "29418", "ssh", "http://gerrit/", "1"));
        event.setEventCreatedOn("1418133772");
        return event;
    }

    /**
     * Gives you a BuildsStartedStats mock object for the given event.
     * @param event the event.
     * @return BuildsStartedStats mock.
     */
    public static BuildsStartedStats createBuildStartedStats(PatchsetCreated event) {
        //CS IGNORE MagicNumber FOR NEXT 2 LINES. REASON: mock.
        return new BuildsStartedStats(event, 3, 1);
    }

    /**
     * EnvVars mock.
     * @return EnvVars mock
     */
    public static EnvVars createEnvVars() {
        EnvVars env = new EnvVars();
        env.put("BRANCH", "branch");
        env.put("CHANGE", "1000");
        env.put("PATCHSET", "1");
        env.put("REFSPEC", StringUtil.REFSPEC_PREFIX + "00/1000/1");
        env.put("CHANGE_URL", "http://gerrit/1000");
        env.put("START_MESSAGE_VAR", "START_MESSAGE_VAL");
        return env;
    }

    /**
     * GerritTrigger mock for build.
     * @param build The build
     * @return GerritTrigger mock
     */
    public static GerritTrigger createGerritTrigger(AbstractBuild build) {
        GerritTrigger trigger = mock(GerritTrigger.class);
        AbstractProject project = build.getProject();
        setTrigger(trigger, project);
        return trigger;
    }

    /**
     * Create a new default trigger object.
     *
     * @param job if not null, start the trigger with the given project.
     * @return a new GerritTrigger object.
     */
    public static GerritTrigger createDefaultTrigger(Job job) {
        PluginPatchsetCreatedEvent pluginEvent = new PluginPatchsetCreatedEvent();
        List<PluginGerritEvent> triggerOnEvents = new LinkedList<PluginGerritEvent>();
        triggerOnEvents.add(pluginEvent);
        boolean silentMode = true;
        boolean silentStart = false;

        GerritTrigger trigger = new GerritTrigger(null);
        /*new GerritTrigger(null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                silentMode, silentStart, true, false, false, "", "", "", "", "", "", "", null,
                PluginImpl.DEFAULT_SERVER_NAME, null, triggerOnEvents, false, "", null);*/
        trigger.setTriggerOnEvents(triggerOnEvents);
        trigger.setSilentMode(silentMode);
        trigger.setSilentStartMode(silentStart);
        trigger.setGerritBuildStartedVerifiedValue(0);
        trigger.setGerritBuildStartedCodeReviewValue(0);
        trigger.setGerritBuildSuccessfulVerifiedValue(0);
        trigger.setGerritBuildSuccessfulCodeReviewValue(0);
        trigger.setGerritBuildFailedVerifiedValue(0);
        trigger.setGerritBuildFailedCodeReviewValue(0);
        trigger.setGerritBuildUnstableVerifiedValue(0);
        trigger.setGerritBuildUnstableCodeReviewValue(0);
        trigger.setGerritBuildNotBuiltVerifiedValue(0);
        trigger.setGerritBuildNotBuiltCodeReviewValue(0);
        trigger.setServerName(PluginImpl.DEFAULT_SERVER_NAME);

        if (job != null) {
            trigger.start(job, true);
            try {
                if (job instanceof AbstractProject) {
                    ((AbstractProject)job).addTrigger(trigger);
                } else if (job instanceof WorkflowJob) {
                    ((WorkflowJob)job).addTrigger(trigger);
                } else {
                    Assert.fail("Unsupported Job type: " + job.getClass().getName());
                }
            } catch (IOException e) {
                // for the sake of testing this should be ok
                throw new RuntimeException(e);
            }
        }

        return trigger;
    }

    /**
     * Create a new default trigger object.
     *
     * @param job if not null, start the trigger with the given project.
     * @return a new GerritTrigger object.
     */
    public static GerritTrigger createRefUpdatedTrigger(Job job) {
        PluginRefUpdatedEvent pluginEvent = new PluginRefUpdatedEvent();
        List<PluginGerritEvent> triggerOnEvents = new LinkedList<PluginGerritEvent>();
        triggerOnEvents.add(pluginEvent);
        boolean silentMode = true;
        boolean silentStart = false;

        GerritTrigger trigger = new GerritTrigger(null, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                silentMode, silentStart, true, false, false, "", "", "", "", "", "", "", null,
                PluginImpl.DEFAULT_SERVER_NAME, null, triggerOnEvents, false, "", null);

        if (job != null) {
            trigger.start(job, true);
            try {
                if (job instanceof AbstractProject) {
                    ((AbstractProject)job).addTrigger(trigger);
                } else if (job instanceof WorkflowJob) {
                    ((WorkflowJob)job).addTrigger(trigger);
                } else {
                    Assert.fail("Unsupported Job type: " + job.getClass().getName());
                }
            } catch (IOException e) {
                // for the sake of testing this should be ok
                throw new RuntimeException(e);
            }
        }

        return trigger;
    }

    /**
     * ToGerritRunListener mock for failure-message tests.
     *
     * @param build The build
     * @param event The event
     * @param filepath The filepath glob string
     * @return ToGerritRunListener mock (spy)
     */
    public static ToGerritRunListener createFailureMessageRunListener(final AbstractBuild build,
            final PatchsetCreated event, final String filepath) {
        GerritCause cause = new GerritCause(event, false);
        when(build.getCause(GerritCause.class)).thenReturn(cause);
        CauseAction causeAction = mock(CauseAction.class);
        when(causeAction.getCauses()).thenReturn(Collections.<Cause>singletonList(cause));
        when(build.getAction(CauseAction.class)).thenReturn(causeAction);
        when(build.getResult()).thenReturn(Result.FAILURE);

        GerritTrigger trigger = Setup.createGerritTrigger(build);
        when(trigger.getBuildUnsuccessfulFilepath()).thenReturn(filepath);

        ToGerritRunListener toGerritRunListener = spy(new ToGerritRunListener());
        return toGerritRunListener;
    }

    /**
     * Gives a List of VerdictCategories containing Code Review.
     * @return the List.
     */
    public static List<VerdictCategory> createCodeReviewVerdictCategoryList() {
        VerdictCategory cat = new VerdictCategory("Code-Review", "Code review");
        List<VerdictCategory> list = new LinkedList<VerdictCategory>();
        list.add(cat);
        return list;
    }

    /**
     * Create an MemoryImprint.Entry for the specific build and project.
     *
     * @param project the project
     * @param build the build
     * @return an entry.
     */
    public static MemoryImprint.Entry createImprintEntry(AbstractProject project, AbstractBuild build) {
        MemoryImprint.Entry entry = mock(MemoryImprint.Entry.class);
        when(entry.getBuild()).thenReturn(build);
        when(entry.getProject()).thenReturn(project);
        return entry;
    }

    /**
     * Create an MemoryImprint.Entry with a given trigger and a build that
     * returns the given result.
     *
     * @param trigger the trigger
     * @param result the result
     * @return an entry with the parameters.
     */
    public static MemoryImprint.Entry createAndSetupMemoryImprintEntry(GerritTrigger trigger, Result result) {
        AbstractProject project = mock(AbstractProject.class);
        setTrigger(trigger, project);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getResult()).thenReturn(result);
        return createImprintEntry(project, build);
    }

    /**
     * Set/mock the supplied trigger onto the supplied {@link AbstractProject} instance.
     * @param trigger The trigger.
     * @param project The project.
     */
    public static void setTrigger(GerritTrigger trigger, AbstractProject project) {
        when(project.getTrigger(GerritTrigger.class)).thenReturn(trigger);
        HashMap<TriggerDescriptor, Trigger<?>> triggers = new HashMap<TriggerDescriptor, Trigger<?>>();
        triggers.put(new GerritTrigger.DescriptorImpl(), trigger);
        PowerMockito.when(project.getTriggers()).thenReturn(triggers);
    }

    /**
     * Create an MemoryImprint.Entry with a default trigger and a build that
     * returns the given result. The trigger will be configured to "skip" the result.
     *
     * @param result the build result
     * @param resultsCodeReviewVote what vote the job should cast for the build result.
     * @param resultsVerifiedVote what vote the job should cast for the build result.
     * @param shouldSkip if the result should be configured to be skipped or not.
     * @return an entry with the parameters.
     * @see #createAndSetupMemoryImprintEntry(GerritTrigger, hudson.model.Result)
     * @see com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger#getSkipVote()
     */
    public static MemoryImprint.Entry createAndSetupMemoryImprintEntry(Result result,
            int resultsCodeReviewVote,
            int resultsVerifiedVote,
            boolean shouldSkip) {
        GerritTrigger trigger = mock(GerritTrigger.class);
        SkipVote skipVote = null;
        if (result == Result.SUCCESS) {
            when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(resultsCodeReviewVote);
            when(trigger.getGerritBuildSuccessfulVerifiedValue()).thenReturn(resultsVerifiedVote);
            if (shouldSkip) {
                skipVote = new SkipVote(true, false, false, false);
            }
        } else if (result == Result.FAILURE) {
            when(trigger.getGerritBuildFailedCodeReviewValue()).thenReturn(resultsCodeReviewVote);
            when(trigger.getGerritBuildFailedVerifiedValue()).thenReturn(resultsVerifiedVote);
            if (shouldSkip) {
                skipVote = new SkipVote(false, true, false, false);
            }
        } else if (result == Result.UNSTABLE) {
            when(trigger.getGerritBuildUnstableCodeReviewValue()).thenReturn(resultsCodeReviewVote);
            when(trigger.getGerritBuildUnstableVerifiedValue()).thenReturn(resultsVerifiedVote);
            if (shouldSkip) {
                skipVote = new SkipVote(false, false, true, false);
            }
        } else if (result == Result.NOT_BUILT) {
            when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(1);
            when(trigger.getGerritBuildSuccessfulCodeReviewValue()).thenReturn(1);
            if (shouldSkip) {
                skipVote = new SkipVote(false, false, false, true);
            }
        } else {
            throw new IllegalArgumentException("Unsupported build result setup: " + result);
        }
        if (!shouldSkip) {
            skipVote = new SkipVote(false, false, false, false);
        }
        when(trigger.getSkipVote()).thenReturn(skipVote);
        return Setup.createAndSetupMemoryImprintEntry(trigger, result);
    }

    /**
     * Create a mocked build that has the url 'test/' and the given project and
     * env-vars. TaskListener is needed to stub out AbstractBuild.getEnvironment.
     *
     * @param project the project.
     * @param taskListener the taskListener to stub out AbstractBuild.getEnvironment.
     * @param env the env vars.
     * @return a mocked build.
     * @throws java.io.IOException if so.
     * @throws InterruptedException if so.
     */
    public static AbstractBuild createBuild(AbstractProject project, TaskListener taskListener, EnvVars env)
            throws IOException, InterruptedException {
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getUrl()).thenReturn("test/");
        when(build.getProject()).thenReturn(project);
        when(build.getEnvironment(taskListener)).thenReturn(env);
        return build;
    }

    /**
     * Create a RefReplicated event with the specified info.
     * @param project the project
     * @param ref the ref
     * @param server the server
     * @param slave the slave
     * @param status hte status
     * @return a RefReplicated event
     */
    public static RefReplicated createRefReplicatedEvent(String project, String ref, String server, String slave,
            String status) {
        RefReplicated refReplicated = new RefReplicated();
        refReplicated.setProject(project);
        refReplicated.setProvider(new Provider(server, null, null, null, null, null));
        refReplicated.setRef(ref);
        refReplicated.setTargetNode(slave);
        refReplicated.setStatus(status);
        return refReplicated;
    }

    /**
     * Lock down the instance.
     * @param j JenkinsRule.
     * @throws Exception throw if so.
     */
    public static void lockDown(JenkinsRule j) throws Exception {
        SecurityRealm securityRealm = j.createDummySecurityRealm();
        j.getInstance().setSecurityRealm(securityRealm);
        j.getInstance().setAuthorizationStrategy(
                new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().toAuthenticated());
    }

    /**
     * Unlock the instance.
     * @param j JenkinsRule.
     * @throws Exception throw if so.
     */
    public static void unLock(JenkinsRule j) throws Exception {
        j.getInstance().setSecurityRealm(SecurityRealm.NO_AUTHENTICATION);
    }

    /**
     * Create a new private-state change created event with the given data.
     * @param serverName The server name
     * @param project The project
     * @param ref The ref
     * @return a patchsetCreated event
     */
    public static PrivateStateChanged createPrivateStateChanged(String serverName,
                                                        String project,
                                                        String ref) {
        PrivateStateChanged event = new PrivateStateChanged();
        Change change = new Change();
        change.setBranch("branch");
        change.setId("Iddaaddaa123456789");
        change.setNumber("1000");
        change.setPrivate(true);
        Account account = new Account();
        account.setEmail("email@domain.com");
        account.setName("Name");
        change.setOwner(account);
        change.setProject(project);
        change.setSubject("subject");
        change.setUrl("http://gerrit/1000");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        patch.setRevision("9527");
        patch.setRef(ref);
        event.setPatchset(patch);
        event.setProvider(new Provider(serverName, "gerrit", "29418", "ssh", "http://gerrit/", "1"));
        event.setEventCreatedOn("1418133772");
        return event;
    }

    /**
     * Create a new WIP-state change created event with the given data.
     * @param serverName The server name
     * @param project The project
     * @param ref The ref
     * @return a patchsetCreated event
     */
    public static WipStateChanged createWipStateChanged(String serverName,
                                                        String project,
                                                        String ref) {
        WipStateChanged event = new WipStateChanged();
        Change change = new Change();
        change.setBranch("branch");
        change.setId("Iddaaddaa123456789");
        change.setNumber("1000");
        change.setWip(true);
        Account account = new Account();
        account.setEmail("email@domain.com");
        account.setName("Name");
        change.setOwner(account);
        change.setProject(project);
        change.setSubject("subject");
        change.setUrl("http://gerrit/1000");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        patch.setRevision("9527");
        patch.setRef(ref);
        event.setPatchset(patch);
        event.setProvider(new Provider(serverName, "gerrit", "29418", "ssh", "http://gerrit/", "1"));
        event.setEventCreatedOn("1418133772");
        return event;
    }

}
