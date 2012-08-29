/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Approval;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeMerged;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.DraftPublished;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ManualPatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.VerdictCategory;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginGerritEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.events.PluginPatchsetCreatedEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Result;
import hudson.model.TaskListener;

import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.BRANCH;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.CHANGE;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.EMAIL;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.ID;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.NAME;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.NUMBER;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.OWNER;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PATCH_SET;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PROJECT;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.REF;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.REVISION;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.SUBJECT;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.UPLOADER;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.URL;


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
     * Gives you a PatchsetCreated mock.
     * @return PatchsetCreated mock.
     */
    public static PatchsetCreated createPatchsetCreated() {
        PatchsetCreated event = new PatchsetCreated();
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
        return event;
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
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        patch.setRevision("9999");
        event.setPatchset(patch);
        List<Approval> approvals = new LinkedList<Approval>();
        Approval approval = new Approval();
        approval.setType("CRVW");
        approval.setValue("1");
        approvals.add(approval);
        event.setApprovals(approvals);
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
        doReturn(trigger).when(project).getTrigger(GerritTrigger.class);
        return trigger;
    }

    /**
     * Create a new default trigger object.
     *
     * @param project if not null, start the trigger with the given project.
     * @return a new GerritTrigger object.
     */
    public static GerritTrigger createDefaultTrigger(AbstractProject project) {
        PluginPatchsetCreatedEvent pluginEvent = new PluginPatchsetCreatedEvent();
        List<PluginGerritEvent> triggerOnEvents = new LinkedList<PluginGerritEvent>();
        triggerOnEvents.add(pluginEvent);

        GerritTrigger trigger = new GerritTrigger(null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                true, true, "", "", "", "", "", null, null, triggerOnEvents);

        if (project != null) {
          trigger.start(project, true);
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
        VerdictCategory cat = new VerdictCategory("CRVW", "Code review");
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
        when(project.getTrigger(GerritTrigger.class)).thenReturn(trigger);
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getResult()).thenReturn(result);
        return createImprintEntry(project, build);
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
}
