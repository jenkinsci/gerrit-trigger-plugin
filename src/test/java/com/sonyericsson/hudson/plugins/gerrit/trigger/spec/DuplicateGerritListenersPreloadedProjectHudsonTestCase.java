/*
 * The MIT License
 *
 * Copyright 2011 Sony Mobile Communications Inc. All rights reserved.
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

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.util.UrlUtils;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritProjectListUpdater;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.dependency.DependencyQueueTaskDispatcher;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.EventListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import com.sonyericsson.hudson.plugins.gerrit.trigger.playback.GerritMissedEventsPlaybackManager;
import com.sonyericsson.hudson.plugins.gerrit.trigger.replication.ReplicationQueueTaskDispatcher;
import com.sonymobile.tools.gerrit.gerritevents.GerritEventListener;
import com.sonymobile.tools.gerrit.gerritevents.GerritHandler;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.TopLevelItem;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.powermock.reflect.Whitebox;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil.createGerritTriggeredJob;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

//CS IGNORE MagicNumber FOR NEXT 400 LINES. REASON: testdata.

/**
 * This tests different scenarios of adding listeners to the
 * {@link com.sonymobile.tools.gerrit.gerritevents.GerritHandler}
 * with a pre-loaded project configured, to make sure that no duplicates are created.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class DuplicateGerritListenersPreloadedProjectHudsonTestCase {
    /**
     * An instance of Jenkins Rule.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 2 LINES. REASON: JenkinsRule.
    @Rule
    public final JenkinsRule j = new JenkinsRule();

    private Collection<GerritEventListener> originalListeners;

    /**
     * Save the initial listeners so verification can focus on those that are introduced during test.
     */
    @Before
    public void setup() {
        originalListeners = getGerritEventListeners();
    }

    /**
     * Tests that the trigger is added as a listener during startup of the server.
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testProject() throws Exception {
        assertNrOfEventListeners(0);
    }

    /**
     * Tests that the re-loaded trigger is still there when a new triggered project is added.
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testCreateNewProject() throws Exception {
        @SuppressWarnings("unused")
        FreeStyleProject p = createGerritTriggeredJob(j, "testing1");
        assertNrOfEventListeners(1);
    }

    /**
     * Tests that the re-loaded trigger is still there when a new triggered project is added and reconfigured.
     *
     * @throws Exception if so.
     */
    @Test
    @LocalData
    public void testReconfigureNewProject() throws Exception {
        FreeStyleProject p = createGerritTriggeredJob(j, "testing1");

        assertNrOfEventListeners(1);
        j.configRoundtrip((Item)p);
        assertNrOfEventListeners(1);
    }

    /**
     * Tests that configuring an existing project via jenkins http rest doesn't produce duplicated triggers
     * and that the trigger is configured for the new project pattern.
     *
     * @throws Exception if so
     */
    @Test
    @LocalData
    public void testReconfigureUsingRestApi() throws Exception {
        assertNrOfEventListeners(0);
        TopLevelItem testProj = j.jenkins.getItem("testProj");
        String gerritProjectPattern = "someotherproject";
        XmlPage xmlPage = loadConfigXmlViaHttp(testProj);
        Document document = xmlPage.getXmlDocument();
        String xml = changeConfigXml(gerritProjectPattern, document);
        URL url = UrlUtils.toUrlUnsafe(j.getURL().toExternalForm() + testProj.getUrl() + "config.xml");
        WebRequest request = new WebRequest(url, HttpMethod.POST);
        request.setRequestBody(xml);
        j.jenkins.setCrumbIssuer(null);
        Page page = j.createWebClient().getPage(request);
        j.assertGoodStatus(page);
        assertNrOfEventListeners(0);
        assertEventListenerWithSomeOtherProjectSet(gerritProjectPattern);
    }

    /**
     * Tests that configuring an existing project via jenkins cli doesn't produce duplicated triggers
     * and that the trigger is configured for the new project pattern.
     *
     * @throws Exception if so
     */
    @Test
    @LocalData
    public void testReconfigureUsingCli() throws Exception {
        assertNrOfEventListeners(0);
        TopLevelItem testProj = j.jenkins.getItem("testProj");
        String gerritProjectPattern = "someotherproject";
        Document document = loadConfigXmlViaCli(testProj);
        String xml = changeConfigXml(gerritProjectPattern, document);

        List<String> cmd = javaCliJarCmd("update-job", testProj.getFullName());
        Process process = Runtime.getRuntime().exec(cmd.toArray(new String[cmd.size()]));
        OutputStream output = process.getOutputStream();
        IOUtils.write(xml, output);
        IOUtils.closeQuietly(output);
        String response = IOUtils.toString(process.getInputStream());
        System.out.println(response);
        assertEquals(0, process.waitFor());
        assertNrOfEventListeners(0);
        assertEventListenerWithSomeOtherProjectSet(gerritProjectPattern);
    }

    /**
     * Adds a new gerritProject configuration to the gived XML document.
     * Assumes that the document is structured like the original project config.xml in the LocalData for this class.
     *
     * @param gerritProjectPattern the {@link GerritProject#pattern} to set.
     * @param document             the config.xml
     * @return the new xml
     * @throws Exception if so
     */
    private String changeConfigXml(String gerritProjectPattern, Document document) throws Exception {
        Node projects = xPath(document, "/project/triggers/*[1]/gerritProjects");
        String tagName = "com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject";
        Node newProject = document.createElement(tagName);

        setXmlConfig(document, newProject, "ANT", gerritProjectPattern);
        Node branches = document.createElement("branches");
        tagName = "com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch";
        Node branch = document.createElement(tagName);
        setXmlConfig(document, branch, "ANT", "**");
        branches.appendChild(branch);
        newProject.appendChild(branches);
        projects.appendChild(newProject);
        document.normalizeDocument();
        return xmlToString(document);
    }

    /**
     * Checks the list of event listeners for a single
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.EventListener}
     * that points to a {@link GerritTrigger} and checks if that is configured for the project pattern.
     *
     * @param gerritProjectPattern the pattern to check
     * @throws Exception if so
     */
    private void assertEventListenerWithSomeOtherProjectSet(String gerritProjectPattern) throws Exception {
        Collection<GerritEventListener> eventListeners = getGerritEventListeners();
        boolean found = false;
        for (GerritEventListener listener : eventListeners) {
            if (listener.getClass().getName().equals(
                    "com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.EventListener")) {
                found = true;
                GerritTrigger trigger = Whitebox.invokeMethod(listener, "getTrigger");
                assertNotNull("No Trigger for EventListener", trigger);
                assertSame(Whitebox.getInternalState(trigger, "job"), j.jenkins.getItem("testProj"));
                List<GerritProject> projectList = trigger.getGerritProjects();
                assertEquals(2, projectList.size());
                boolean foundSomeOtherProject = false;
                for (GerritProject project : projectList) {
                    if (gerritProjectPattern.equals(project.getPattern())) {
                        foundSomeOtherProject = true;
                    }
                }
                assertTrue("Could not find " + gerritProjectPattern, foundSomeOtherProject);
            }
        }
        assertTrue("No EventListener", found);
    }

    /**
     * Transforms the xml document into an xml string.
     *
     * @param doc the document
     * @return the xml
     * @throws TransformerException if so.
     */
    String xmlToString(Document doc) throws TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.getBuffer().toString();
    }

    /**
     * Evaluates the xpath expression on the document and returns the node it resolves to.
     *
     * @param doc        the doc to search
     * @param expression the xpath expression
     * @return the node
     * @throws XPathExpressionException if so.
     */
    Node xPath(Document doc, String expression) throws XPathExpressionException {
        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();
        XPathExpression expr = xpath.compile(expression);
        return (Node)expr.evaluate(doc, XPathConstants.NODE);
    }

    /**
     * Adds a compareType and pattern node as child elements to the provided parent node.
     *
     * @param xml         the document to create xml elements from.
     * @param parent      the parent to add to
     * @param compareType the name of the compare type
     *                    (See {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType})
     * @param pattern     the pattern.
     */
    void setXmlConfig(Document xml, Node parent, String compareType, String pattern) {
        Node compareTypeN = xml.createElement("compareType");
        compareTypeN.setTextContent(compareType);
        parent.appendChild(compareTypeN);
        Node patternN = xml.createElement("pattern");
        patternN.setTextContent(pattern);
        parent.appendChild(patternN);
    }

    /**
     * Loads the job's config.xml via the jenkins cli command <code>get-job</code>.
     *
     * @param job the job to get the config for
     * @return the xml document
     * @throws Exception if so
     */
    Document loadConfigXmlViaCli(TopLevelItem job) throws Exception {
        List<String> cmd = javaCliJarCmd("get-job", job.getFullName());
        Process process = Runtime.getRuntime().exec(cmd.toArray(new String[cmd.size()]));
        String xml = IOUtils.toString(process.getInputStream());

        assertEquals(0, process.waitFor());

        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        InputSource is = new InputSource();
        is.setCharacterStream(new StringReader(xml));

        return db.parse(is);
    }

    /**
     * Constructs the java command for launching a jenkins-cli command.
     *
     * @param cmd the cli command to run with parameters.
     * @return the full command <code>java -jar path/to/jenkins-cli.jar -s theurl cmd...</code>.
     * @throws IOException        if so
     * @throws URISyntaxException if so
     */
    List<String> javaCliJarCmd(String... cmd) throws IOException, URISyntaxException {
        List<String> commands = new ArrayList<String>(cmd.length + 5);
        commands.add(String.format("%s/bin/java", System.getProperty("java.home")));
        commands.add("-jar");
        commands.add(new File(j.jenkins.getJnlpJars("jenkins-cli.jar").getURL().toURI()).getAbsolutePath());
        commands.add("-s");
        commands.add(j.getURL().toExternalForm());
        Collections.addAll(commands, cmd);
        return commands;
    }

    /**
     * Loads the config.xml for the job via http.
     * I.e. http://jenkinshost/job/thejob/config.xml
     *
     * @param job the job
     * @return the xml page
     * @throws IOException  if so
     * @throws SAXException if so
     */
    XmlPage loadConfigXmlViaHttp(TopLevelItem job) throws IOException, SAXException {
        return j.createWebClient().goToXml(job.getUrl() + "config.xml");
    }

    /**
     * Checks the size of the listeners collection retrieved by {@link #getGerritEventListeners()}.
     *
     * @param extra number of added listeners, other than the default.
     */
    void assertNrOfEventListeners(int extra) {
        Collection<GerritEventListener> gerritEventListeners = getGerritEventListeners();
        GerritServer server = PluginImpl.getServer_(PluginImpl.DEFAULT_SERVER_NAME);

        assertThat(gerritEventListeners, Matchers.hasItem(Matchers.instanceOf(EventListener.class)));
        assertThat(gerritEventListeners, Matchers.hasItem(Matchers.instanceOf(DependencyQueueTaskDispatcher.class)));
        assertThat(gerritEventListeners, Matchers.hasItem(Matchers.instanceOf(ReplicationQueueTaskDispatcher.class)));
        assertThat(gerritEventListeners, Matchers.hasItem(Matchers.instanceOf(GerritMissedEventsPlaybackManager.class)));
        if (server.isConnected() && server.getConfig().isEnableProjectAutoCompletion()
                && server.isProjectCreatedEventsSupported()) {
            assertThat(gerritEventListeners, Matchers.hasItem(Matchers.instanceOf(GerritProjectListUpdater.class)));
        }

        gerritEventListeners.removeAll(originalListeners);

        assertThat(gerritEventListeners, iterableWithSize(extra));
    }

    /**
     * Gets the list of listeners.
     *
     * @return the list.
     */
    private Collection<GerritEventListener> getGerritEventListeners() {
        GerritHandler handler = Whitebox.getInternalState(PluginImpl.getInstance().
                getServer(PluginImpl.DEFAULT_SERVER_NAME), GerritHandler.class);
        return new ArrayList<>(handler.getGerritEventListenersView());
    }
}
