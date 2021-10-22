/*
 * The MIT License
 *
 * Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 * Copyright 2012 Sony Mobile Communications AB. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import hudson.model.Queue.Item;
import hudson.model.queue.QueueTaskFuture;

import java.util.concurrent.TimeUnit;

import jenkins.model.Jenkins;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;

/**
 * Tests for {@link ToGerritRunListener}.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritQueueListenerTest {

    private static final int TIMEOUT_SECONDS = 60;
    private static final int QUIET_PERIOD = 5;

    /**
     * Jenkins rule instance.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 3 LINES. REASON: Mocks tests.
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    /**
     * Tests that event is properly removed if only one project is triggered
     * which is cancelled while in the queue.
     *
     * @throws Exception if something goes wrong
     */
    @Test
    public void testCancelledQueueItemIsOnlyTriggeredProject() throws Exception {
        FreeStyleProject project = jenkinsRule.createFreeStyleProject();
        PatchsetCreated event = Setup.createPatchsetCreated();
        final GerritCause gerritCause = new GerritCause(event, false);

        ToGerritRunListener runListener = ToGerritRunListener.getInstance();
        runListener.onTriggered(project, event);
        project.scheduleBuild2(QUIET_PERIOD, gerritCause);

        Item item = waitForBlockedItem(project, TIMEOUT_SECONDS);
        Queue queue = jenkinsRule.getInstance().getQueue();
        queue.doCancelItem(item.getId());
        assertThat(queue.isEmpty(), equalTo(true));
        assertThat(project.getBuilds().size(), equalTo(0));
        assertThat(runListener.isBuilding(event), equalTo(false));
    }

    /**
     * Tests that event is properly removed if only two different projects are triggered
     * and one of them is cancelled while in the queue.
     *
     * @throws Exception if something goes wrong
     */
    @Test
    public void testCancelledOneQueueItemOfTwo() throws Exception {
        FreeStyleProject project = jenkinsRule.createFreeStyleProject();
        FreeStyleProject project2 = jenkinsRule.createFreeStyleProject();
        PatchsetCreated event = Setup.createPatchsetCreated();
        final GerritCause gerritCause = new GerritCause(event, false);

        ToGerritRunListener runListener = ToGerritRunListener.getInstance();
        runListener.onTriggered(project, event);
        runListener.onTriggered(project2, event);
        project.scheduleBuild2(QUIET_PERIOD, gerritCause);
        QueueTaskFuture<FreeStyleBuild> future2 = project2.scheduleBuild2(QUIET_PERIOD, gerritCause);

        Item item = waitForBlockedItem(project, TIMEOUT_SECONDS);
        Queue queue = jenkinsRule.getInstance().getQueue();
        queue.doCancelItem(item.getId());
        FreeStyleBuild build = future2.get();
        assertThat(queue.isEmpty(), equalTo(true));
        assertThat(project.getBuilds().size(), equalTo(0));
        assertThat(project2.getBuilds().size(), equalTo(1));
        assertThat(runListener.isBuilding(event), equalTo(false));
    }

    /**
     * Waits that the given project shows up in the queue and that it is blocked.
     *
     * @param project The project to wait for
     * @param timeout seconds to wait before aborting
     * @return the found item, null if the item didn't show up in the queue until timeout
     * @throws InterruptedException if interrupted
     */
    private Item waitForBlockedItem(FreeStyleProject project, int timeout) throws InterruptedException {
        Queue jenkinsQueue = Jenkins.get().getQueue();
        Item queueItem = null;

        int elapsedSeconds = 0;
        while (elapsedSeconds <= timeout) {
            queueItem = jenkinsQueue.getItem(project);
            if (queueItem != null) {
                return queueItem;
            }
            TimeUnit.SECONDS.sleep(1);
            elapsedSeconds++;
        }
        return queueItem;
    }

}
