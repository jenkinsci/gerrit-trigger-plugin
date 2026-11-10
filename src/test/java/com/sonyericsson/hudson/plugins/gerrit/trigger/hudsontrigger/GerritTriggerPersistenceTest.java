/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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

import com.sonyericsson.jenkins.plugins.bfa.test.utils.Whitebox;
import hudson.model.FreeStyleProject;
import hudson.model.Items;
import hudson.model.Job;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import javax.xml.transform.stream.StreamSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runtime timer state must neither be saved nor restored from legacy job configurations.
 */
@WithJenkins
class GerritTriggerPersistenceTest {

    /**
     * Timer state written by older plugin versions, including the inherited Object lock.
     */
    private static final String LEGACY_TIMER_XML = """
            <gerritTriggerTimerTask>
              <lock/>
              <state>0</state>
              <nextExecutionTime>0</nextExecutionTime>
              <period>0</period>
              <job>dynamic</job>
            </gerritTriggerTimerTask>""";

    /**
     * Checks that legacy timer state is skipped before a trigger is started.
     * @param j Jenkins rule
     */
    @Test
    void legacyTimerIsNotDeserialized(JenkinsRule j) {
        String type = GerritTrigger.class.getName();
        GerritTrigger trigger = (GerritTrigger)Items.XSTREAM.fromXML(
                "<" + type + "><spec/>" + LEGACY_TIMER_XML + "</" + type + ">");
        assertNull(Whitebox.getInternalState(trigger, "gerritTriggerTimerTask"));
    }

    /**
     * Checks persistence and reload of a freestyle trigger.
     * @param j Jenkins rule
     * @throws Exception on failure
     */
    @Test
    void freestyleTimerIsNotPersisted(JenkinsRule j) throws Exception {
        checkPersistence(j, false, false);
    }

    /**
     * Checks persistence and reload of a Pipeline trigger.
     * @param j Jenkins rule
     * @throws Exception on failure
     */
    @Test
    void pipelineTimerIsNotPersisted(JenkinsRule j) throws Exception {
        checkPersistence(j, true, false);
    }

    /**
     * Checks migration of a freestyle configuration containing legacy timer state.
     * @param j Jenkins rule
     * @throws Exception on failure
     */
    @Test
    void freestyleLegacyTimerIsIgnored(JenkinsRule j) throws Exception {
        checkPersistence(j, false, true);
    }

    /**
     * Checks migration of a Pipeline configuration containing legacy timer state.
     * @param j Jenkins rule
     * @throws Exception on failure
     */
    @Test
    void pipelineLegacyTimerIsIgnored(JenkinsRule j) throws Exception {
        checkPersistence(j, true, true);
    }

    /**
     * Saves and reloads a running dynamic trigger, optionally including old serialized timer state.
     * @param j Jenkins rule
     * @param pipeline whether to create a Pipeline job
     * @param legacy whether to inject legacy timer XML
     * @throws Exception on failure
     */
    private void checkPersistence(JenkinsRule j, boolean pipeline, boolean legacy) throws Exception {
        Path config = Files.createTempFile(j.jenkins.getRootDir().toPath(), "dynamic-trigger", ".conf");
        Files.writeString(config, "p=example-project\nb^**\n");
        GerritTrigger trigger = new GerritTrigger(Collections.emptyList());
        trigger.setDynamicTriggerConfiguration(true);
        trigger.setTriggerConfigURL(config.toUri().toString());
        Job<?, ?> job;
        if (pipeline) {
            WorkflowJob workflow = j.createProject(WorkflowJob.class, "dynamic");
            workflow.setDefinition(new CpsFlowDefinition("echo 'test'", true));
            workflow.addProperty(new PipelineTriggersJobProperty(Collections.singletonList(trigger)));
            job = workflow;
        } else {
            FreeStyleProject freestyle = j.createFreeStyleProject("dynamic");
            freestyle.addTrigger(trigger);
            trigger.start(freestyle, true);
            job = freestyle;
        }
        GerritTriggerTimerTask originalTask = Whitebox.getInternalState(trigger, "gerritTriggerTimerTask");
        assertNotNull(originalTask);
        job.save();
        String xml = job.getConfigFile().asString();
        assertFalse(xml.contains("<gerritTriggerTimerTask>"));
        if (legacy) {
            // TimerTask.lock is rejected by hardened Jenkins versions if this subtree is deserialized.
            xml = xml.replace("</triggerConfigURL>", "</triggerConfigURL>" + LEGACY_TIMER_XML);
            assertTrue(xml.contains("<lock/>"));
            job.updateByXml(new StreamSource(new StringReader(xml)));
        }
        job.doReload();
        GerritTrigger restored = GerritTrigger.getTrigger(job);
        assertNotNull(restored);
        assertTrue(restored.isDynamicTriggerConfiguration());
        assertEquals(config.toUri().toString(), restored.getTriggerConfigURL());
        GerritTriggerTimerTask restoredTask = Whitebox.getInternalState(restored, "gerritTriggerTimerTask");
        assertNotNull(restoredTask);
        assertNotSame(originalTask, restoredTask);
        // Exercise the recreated task to prove dynamic configuration still refreshes after reload.
        restoredTask.doRun();
        assertEquals(1, restored.getDynamicGerritProjects().size());
        assertEquals("example-project", restored.getDynamicGerritProjects().get(0).getPattern());
        job.save();
        assertFalse(job.getConfigFile().asString().contains("<gerritTriggerTimerTask>"));
        restored.stop();
        assertNull(Whitebox.getInternalState(restored, "gerritTriggerTimerTask"));
    }
}
