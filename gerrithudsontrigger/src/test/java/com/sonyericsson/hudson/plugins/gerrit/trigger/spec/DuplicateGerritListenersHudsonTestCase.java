/*
 * The MIT License
 *
 * Copyright 2011 Sony Ericsson Mobile Communications. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.spec;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritEventListener;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritHandler;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.Config;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.SshdServerMock;

import hudson.model.Item;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;
import org.powermock.reflect.Whitebox;

import java.io.File;
import java.util.Map;

import static com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil.createGerritTriggeredJob;
import static com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil.getFormWithAction;

//CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: Test data.

/**
 * This tests different scenarios of adding listeners to the
 * {@link com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritHandler}
 * to make sure that no duplicates are created.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class DuplicateGerritListenersHudsonTestCase extends HudsonTestCase {

    private File keyFile;

    @Override
    protected void setUp() throws Exception {
        keyFile = SshdServerMock.generateKeyPair();
        super.setUp();
    }

    /**
     * Tests creating a new project.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testNewProjectCreation() throws Exception {
        createGerritTriggeredJob(this, "testJob1");
        GerritHandler handler = Whitebox.getInternalState(PluginImpl.getInstance(), GerritHandler.class);
        Map<Integer, GerritEventListener> gerritEventListeners =
                Whitebox.getInternalState(handler, "gerritEventListeners");
        assertEquals(1, gerritEventListeners.size());
    }

    /**
     * Tests resaving a project.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testNewProjectCreationWithReSave() throws Exception {
        FreeStyleProject p = createGerritTriggeredJob(this, "testJob2");
        configRoundtrip((Item)p);
        GerritHandler handler = Whitebox.getInternalState(PluginImpl.getInstance(), GerritHandler.class);
        Map<Integer, GerritEventListener> gerritEventListeners =
                Whitebox.getInternalState(handler, "gerritEventListeners");
        assertEquals(1, gerritEventListeners.size());
    }

    /**
     * Tests correct addListener behaviour when a project is renamed.
     *
     * @throws Exception id so.
     */
    @LocalData
    public void testNewProjectCreationWithReName() throws Exception {
        FreeStyleProject p = createGerritTriggeredJob(this, "testJob3");

        HtmlForm form = createWebClient().getPage(p, "configure").getFormByName("config");
        form.getInputByName("name").setValueAttribute("testJob33");
        HtmlPage confirmPage = submit(form);
        submit(getFormWithAction("doRename", confirmPage.getForms()));
        //configRoundtrip(p);
        assertEquals("testJob33", p.getName());
        GerritHandler handler = Whitebox.getInternalState(PluginImpl.getInstance(), GerritHandler.class);
        Map<Integer, GerritEventListener> gerritEventListeners =
                Whitebox.getInternalState(handler, "gerritEventListeners");
        assertEquals(1, gerritEventListeners.size());
    }

    /**
     * Tests that the listeners are added correctly to the handler when a connection is established for the first time.
     *
     * @throws Exception if so.
     * @see com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl#restartConnection()
     */
    public void testNewProjectCreationFirstNoConnection() throws Exception {
        FreeStyleProject p = createGerritTriggeredJob(this, "testJob4");
        GerritHandler handler = Whitebox.getInternalState(PluginImpl.getInstance(), GerritHandler.class);
        assertNull(handler);
        Map<Integer, GerritEventListener> savedEventListeners =
                Whitebox.getInternalState(PluginImpl.getInstance(), "savedEventListeners");
        assertEquals(1, savedEventListeners.size());
        ((Config)PluginImpl.getInstance().getConfig()).setGerritAuthKeyFile(keyFile);
        ((Config)PluginImpl.getInstance().getConfig()).setGerritHostName("localhost");
        ((Config)PluginImpl.getInstance().getConfig()).setGerritFrontEndURL("http://localhost");
        ((Config)PluginImpl.getInstance().getConfig()).setGerritSshPort(29418);
        PluginImpl.getInstance().restartConnection();

        handler = Whitebox.getInternalState(PluginImpl.getInstance(), GerritHandler.class);
        Map<Integer, GerritEventListener> gerritEventListeners =
                Whitebox.getInternalState(handler, "gerritEventListeners");
        assertEquals(1, gerritEventListeners.size());
    }


}
