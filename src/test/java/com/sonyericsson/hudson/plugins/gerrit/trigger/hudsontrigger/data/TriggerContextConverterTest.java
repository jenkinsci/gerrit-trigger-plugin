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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data;

import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeMerged;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeRestored;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.DraftPublished;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.RetriggerAction;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.thoughtworks.xstream.XStream;
import hudson.ExtensionList;
import hudson.diagnosis.OldDataMonitor;
import hudson.matrix.MatrixRun;
import hudson.model.Cause;
import hudson.model.Saveable;
import hudson.util.XStream2;

import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollection;
import static org.mockito.Matchers.same;
import static org.powermock.api.mockito.PowerMockito.doNothing;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;


/**
 * Tests for {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContextConverter}.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({Jenkins.class, OldDataMonitor.class, ExtensionList.class })
public class TriggerContextConverterTest {
    private Jenkins jenkins;

    //CS IGNORE MagicNumber FOR NEXT 600 LINES. REASON: test data.

    /**
     * Mock Jenkins.
     */
    @Before
    public void setup() {
        PowerMockito.mockStatic(Jenkins.class);
        jenkins = mock(Jenkins.class);
        when(Jenkins.getInstance()).thenReturn(jenkins);
        when(jenkins.getFullName()).thenReturn("");
    }

    //CS IGNORE LineLength FOR NEXT 4 LINES. REASON: Javadoc.

    /**
     * Tests {@link TriggerContextConverter#marshal(Object, com.thoughtworks.xstream.io.HierarchicalStreamWriter,
     * com.thoughtworks.xstream.converters.MarshallingContext)}. With an empty list of "others".
     *
     * @throws Exception if so.
     */
    @Test
    public void testMarshalNoOthers() throws Exception {
        TriggeredItemEntity entity = new TriggeredItemEntity(100, "projectX");

        PatchsetCreated event = Setup.createPatchsetCreated();
        TriggerContext context = new TriggerContext(event);
        context.setThisBuild(entity);
        context.setOthers(new LinkedList<TriggeredItemEntity>());

        TestMarshalClass t = new TestMarshalClass(context, "Bobby", new TestMarshalClass(context, "SomeoneElse"));

        XStream xStream = new XStream2();
        xStream.registerConverter(new TriggerContextConverter());
        String xml = xStream.toXML(t);

        TestMarshalClass readT = (TestMarshalClass)xStream.fromXML(xml);

        assertNotNull(readT.getEntity());
        assertNotNull(readT.getEntity().getEvent());
        assertNotNull(readT.getEntity().getThisBuild());
        assertThat("Event is not a ChangeBasedEvent", readT.getEntity().getEvent(), instanceOf(ChangeBasedEvent.class));

        ChangeBasedEvent changeBasedEvent = (ChangeBasedEvent)readT.getEntity().getEvent();
        assertEquals("project", changeBasedEvent.getChange().getProject());
        assertEquals(100, readT.getEntity().getThisBuild().getBuildNumber().intValue());
        assertEquals("projectX", readT.getEntity().getThisBuild().getProjectId());

        assertSame(readT.getEntity(), readT.getTestClass().getEntity());
    }

    /**
     * Tests {@link TriggerContextConverter#marshal(Object, com.thoughtworks.xstream.io.HierarchicalStreamWriter,
     * com.thoughtworks.xstream.converters.MarshallingContext)}. With {@link TriggerContext#thisBuild} set to null.
     *
     * @throws Exception if so.
     */
    @Test
    public void testMarshalNoThisBuild() throws Exception {
        PatchsetCreated event = Setup.createPatchsetCreated();
        TriggerContext context = new TriggerContext(event);
        context.setOthers(new LinkedList<TriggeredItemEntity>());

        TestMarshalClass t = new TestMarshalClass(context, "Me", new TestMarshalClass(context, "SomeoneElse"));

        XStream xStream = null;
        String xml = null;
        try {
            xStream = new XStream2();
            xStream.registerConverter(new TriggerContextConverter());
            xml = xStream.toXML(t);
        } catch (Exception e) {
            AssertionError error = new AssertionError("This should work, but did not. " + e.getMessage());
            error.initCause(e);
            throw error;
        }

        TestMarshalClass readT = (TestMarshalClass)xStream.fromXML(xml);

        assertNotNull(readT.getEntity());
        assertNotNull(readT.getEntity().getEvent());
    }

    /**
     * Tests {@link TriggerContextConverter#marshal(Object, com.thoughtworks.xstream.io.HierarchicalStreamWriter,
     * com.thoughtworks.xstream.converters.MarshallingContext)}. With {@link TriggerContext#event} set to null.
     *
     * @throws Exception if so.
     */
    @Test
    public void testMarshalNoEvent() throws Exception {
        TriggeredItemEntity entity = new TriggeredItemEntity(100, "projectX");
        TriggerContext context = new TriggerContext(null);
        context.setThisBuild(entity);
        context.setOthers(new LinkedList<TriggeredItemEntity>());

        TestMarshalClass t = new TestMarshalClass(context, "Me", new TestMarshalClass(context, "SomeoneElse"));

        XStream xStream = null;
        String xml = null;
        try {
            xStream = new XStream2();
            xStream.registerConverter(new TriggerContextConverter());
            xml = xStream.toXML(t);
        } catch (Exception e) {
            AssertionError error = new AssertionError("This should work, but did not. " + e.getMessage());
            error.initCause(e);
            throw error;
        }

        TestMarshalClass readT = (TestMarshalClass)xStream.fromXML(xml);

        assertNotNull(readT.getEntity());
        assertNull(readT.getEntity().getEvent());

        assertNotNull(readT.getEntity().getThisBuild());

        assertEquals(100, readT.getEntity().getThisBuild().getBuildNumber().intValue());
        assertEquals("projectX", readT.getEntity().getThisBuild().getProjectId());

        assertSame(readT.getEntity(), readT.getTestClass().getEntity());
    }

    //CS IGNORE LineLength FOR NEXT 4 LINES. REASON: Javadoc.

    /**
     * Tests {@link TriggerContextConverter#marshal(Object, com.thoughtworks.xstream.io.HierarchicalStreamWriter,
     * com.thoughtworks.xstream.converters.MarshallingContext)}. With list of "others" containing two items.
     *
     * @throws Exception if so.
     */
    @Test
    public void testMarshalWithOthers() throws Exception {
        TriggeredItemEntity entity = new TriggeredItemEntity(100, "projectX");

        PatchsetCreated event = Setup.createPatchsetCreated();
        TriggerContext context = new TriggerContext(event);
        context.setThisBuild(entity);
        LinkedList<TriggeredItemEntity> otherBuilds = new LinkedList<TriggeredItemEntity>();
        otherBuilds.add(new TriggeredItemEntity(1, "projectY"));
        otherBuilds.add(new TriggeredItemEntity(12, "projectZ"));
        context.setOthers(otherBuilds);

        TestMarshalClass t = new TestMarshalClass(context, "Bobby", new TestMarshalClass(context, "SomeoneElse"));

        XStream xStream = new XStream2();
        xStream.registerConverter(new TriggerContextConverter());
        String xml = xStream.toXML(t);

        TestMarshalClass readT = (TestMarshalClass)xStream.fromXML(xml);

        assertNotNull(readT.getEntity());
        assertNotNull(readT.getEntity().getEvent());
        assertTrue(readT.getEntity().getEvent() instanceof PatchsetCreated);
        assertNotNull(readT.getEntity().getThisBuild());
        assertNotNull(readT.getEntity().getOthers());

        assertEquals(2, readT.getEntity().getOthers().size());

        TriggeredItemEntity other = readT.getEntity().getOthers().get(0);
        assertEquals(1, other.getBuildNumber().intValue());
        assertEquals("projectY", other.getProjectId());

        other = readT.getEntity().getOthers().get(1);
        assertEquals(12, other.getBuildNumber().intValue());
        assertEquals("projectZ", other.getProjectId());
    }

    /**
     * Tests {@link TriggerContextConverter#marshal(Object, com.thoughtworks.xstream.io.HierarchicalStreamWriter,
     * com.thoughtworks.xstream.converters.MarshallingContext)}. With list of "others" containing two items and a null
     * item between them.
     *
     * @throws Exception if so.
     */
    @Test
    public void testMarshalWithOthersOneNull() throws Exception {
        TriggeredItemEntity entity = new TriggeredItemEntity(100, "projectX");

        PatchsetCreated event = Setup.createPatchsetCreated();
        TriggerContext context = new TriggerContext(event);
        context.setThisBuild(entity);
        LinkedList<TriggeredItemEntity> otherBuilds = new LinkedList<TriggeredItemEntity>();
        otherBuilds.add(new TriggeredItemEntity(1, "projectY"));
        otherBuilds.add(null);
        otherBuilds.add(new TriggeredItemEntity(12, "projectZ"));
        context.setOthers(otherBuilds);

        TestMarshalClass t = new TestMarshalClass(context, "Bobby", new TestMarshalClass(context, "SomeoneElse"));

        XStream xStream = null;
        String xml = null;
        try {
            xStream = new XStream2();
            xStream.registerConverter(new TriggerContextConverter());
            xml = xStream.toXML(t);
        } catch (Exception e) {
            AssertionError error = new AssertionError("This should work, but did not. " + e.getMessage());
            error.initCause(e);
            throw error;
        }

        TestMarshalClass readT = (TestMarshalClass)xStream.fromXML(xml);

        assertNotNull(readT.getEntity());
        assertNotNull(readT.getEntity().getEvent());
        assertNotNull(readT.getEntity().getThisBuild());
        assertNotNull(readT.getEntity().getOthers());

        assertEquals(2, readT.getEntity().getOthers().size());

        TriggeredItemEntity other = readT.getEntity().getOthers().get(0);
        assertEquals(1, other.getBuildNumber().intValue());
        assertEquals("projectY", other.getProjectId());

        other = readT.getEntity().getOthers().get(1);
        assertEquals(12, other.getBuildNumber().intValue());
        assertEquals("projectZ", other.getProjectId());
    }

    /**
     * Tests {@link TriggerContextConverter#marshal(Object, com.thoughtworks.xstream.io.HierarchicalStreamWriter,
     * com.thoughtworks.xstream.converters.MarshallingContext)}.
     * With a {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeAbandoned} event and
     * list of "others" containing two items.
     *
     * @throws Exception if so.
     */
    @Test
    public void testMarshalWithOthersChangeAbandoned() throws Exception {
        TriggeredItemEntity entity = new TriggeredItemEntity(100, "projectX");

        ChangeAbandoned event = Setup.createChangeAbandoned();

        TriggerContext context = new TriggerContext(event);
        context.setThisBuild(entity);
        LinkedList<TriggeredItemEntity> otherBuilds = new LinkedList<TriggeredItemEntity>();
        otherBuilds.add(new TriggeredItemEntity(1, "projectY"));
        otherBuilds.add(new TriggeredItemEntity(12, "projectZ"));
        context.setOthers(otherBuilds);

        TestMarshalClass t = new TestMarshalClass(context, "Bobby", new TestMarshalClass(context, "SomeoneElse"));

        XStream xStream = new XStream2();
        xStream.registerConverter(new TriggerContextConverter());
        String xml = xStream.toXML(t);

        TestMarshalClass readT = (TestMarshalClass)xStream.fromXML(xml);

        assertNotNull(readT.getEntity());
        assertNotNull(readT.getEntity().getEvent());
        assertTrue(readT.getEntity().getEvent() instanceof ChangeAbandoned);
        assertNotNull(readT.getEntity().getThisBuild());
        assertNotNull(readT.getEntity().getOthers());

        assertEquals(2, readT.getEntity().getOthers().size());

        TriggeredItemEntity other = readT.getEntity().getOthers().get(0);
        assertEquals(1, other.getBuildNumber().intValue());
        assertEquals("projectY", other.getProjectId());

        other = readT.getEntity().getOthers().get(1);
        assertEquals(12, other.getBuildNumber().intValue());
        assertEquals("projectZ", other.getProjectId());
    }

    /**
     * Tests {@link TriggerContextConverter#marshal(Object, com.thoughtworks.xstream.io.HierarchicalStreamWriter,
     * com.thoughtworks.xstream.converters.MarshallingContext)}.
     * With a {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeMerged} event and
     * list of "others" containing two items.
     *
     * @throws Exception if so.
     */
    @Test
    public void testMarshalWithOthersChangeMerged() throws Exception {
        TriggeredItemEntity entity = new TriggeredItemEntity(100, "projectX");

        ChangeMerged event = Setup.createChangeMerged();

        TriggerContext context = new TriggerContext(event);
        context.setThisBuild(entity);
        LinkedList<TriggeredItemEntity> otherBuilds = new LinkedList<TriggeredItemEntity>();
        otherBuilds.add(new TriggeredItemEntity(1, "projectY"));
        otherBuilds.add(new TriggeredItemEntity(12, "projectZ"));
        context.setOthers(otherBuilds);

        TestMarshalClass t = new TestMarshalClass(context, "Bobby", new TestMarshalClass(context, "SomeoneElse"));

        XStream xStream = new XStream2();
        xStream.registerConverter(new TriggerContextConverter());
        String xml = xStream.toXML(t);

        TestMarshalClass readT = (TestMarshalClass)xStream.fromXML(xml);

        assertNotNull(readT.getEntity());
        assertNotNull(readT.getEntity().getEvent());
        assertTrue(readT.getEntity().getEvent() instanceof ChangeMerged);
        assertNotNull(readT.getEntity().getThisBuild());
        assertNotNull(readT.getEntity().getOthers());

        assertEquals(2, readT.getEntity().getOthers().size());

        TriggeredItemEntity other = readT.getEntity().getOthers().get(0);
        assertEquals(1, other.getBuildNumber().intValue());
        assertEquals("projectY", other.getProjectId());

        other = readT.getEntity().getOthers().get(1);
        assertEquals(12, other.getBuildNumber().intValue());
        assertEquals("projectZ", other.getProjectId());
    }

    /**
     * Tests {@link TriggerContextConverter#marshal(Object, com.thoughtworks.xstream.io.HierarchicalStreamWriter,
     * com.thoughtworks.xstream.converters.MarshallingContext)}.
     * With a {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeRestored} event and
     * list of "others" containing two items.
     *
     * @throws Exception if so.
     */
    @Test
    public void testMarshalWithOthersChangeRestored() throws Exception {
        TriggeredItemEntity entity = new TriggeredItemEntity(100, "projectX");

        ChangeRestored event = Setup.createChangeRestored();

        TriggerContext context = new TriggerContext(event);
        context.setThisBuild(entity);
        LinkedList<TriggeredItemEntity> otherBuilds = new LinkedList<TriggeredItemEntity>();
        otherBuilds.add(new TriggeredItemEntity(1, "projectY"));
        otherBuilds.add(new TriggeredItemEntity(12, "projectZ"));
        context.setOthers(otherBuilds);

        TestMarshalClass t = new TestMarshalClass(context, "Bobby", new TestMarshalClass(context, "SomeoneElse"));

        XStream xStream = new XStream2();
        xStream.registerConverter(new TriggerContextConverter());
        String xml = xStream.toXML(t);

        TestMarshalClass readT = (TestMarshalClass)xStream.fromXML(xml);

        assertNotNull(readT.getEntity());
        assertNotNull(readT.getEntity().getEvent());
        assertTrue(readT.getEntity().getEvent() instanceof ChangeRestored);
        assertNotNull(readT.getEntity().getThisBuild());
        assertNotNull(readT.getEntity().getOthers());

        assertEquals(2, readT.getEntity().getOthers().size());

        TriggeredItemEntity other = readT.getEntity().getOthers().get(0);
        assertEquals(1, other.getBuildNumber().intValue());
        assertEquals("projectY", other.getProjectId());

        other = readT.getEntity().getOthers().get(1);
        assertEquals(12, other.getBuildNumber().intValue());
        assertEquals("projectZ", other.getProjectId());
    }

    //CS IGNORE LineLength FOR NEXT 4 LINES. REASON: Javadoc.

    /**
     * Tests {@link TriggerContextConverter#unmarshal(com.thoughtworks.xstream.io.HierarchicalStreamReader,
     * com.thoughtworks.xstream.converters.UnmarshallingContext)}. With "retriggerAction_oldData.xml" as input.
     *
     * @throws Exception if so.
     */
    @Test
    public void testUnmarshalOldData1() throws Exception {
        XStream xStream = new XStream2();
        xStream.registerConverter(new TriggerContextConverter());
        Object obj = xStream.fromXML(getClass().getResourceAsStream("retriggerAction_oldData.xml"));
        assertTrue(obj instanceof RetriggerAction);
        RetriggerAction action = (RetriggerAction)obj;
        TriggerContext context = Whitebox.getInternalState(action, "context");
        assertNotNull(context.getEvent());
        assertThat("Event is not a ChangeBasedEvent", context.getEvent(), instanceOf(ChangeBasedEvent.class));

        ChangeBasedEvent changeBasedEvent = (ChangeBasedEvent)context.getEvent();
        assertEquals("semctools/hudson/plugins/gerrit-trigger-plugin", changeBasedEvent.getChange().getProject());
        assertEquals("1", changeBasedEvent.getPatchSet().getNumber());

        assertNotNull(context.getThisBuild());
        assertEquals(6, context.getThisBuild().getBuildNumber().intValue());
        assertEquals("EXPERIMENTAL_Gerrit_Trigger_1", context.getThisBuild().getProjectId());

        assertNotNull(context.getOthers());
        assertEquals(1, context.getOthers().size());
        TriggeredItemEntity entity = context.getOthers().get(0);
        assertEquals(16, entity.getBuildNumber().intValue());
        assertEquals("EXPERIMENTAL_Gerrit_Trigger_2", entity.getProjectId());
    }

    /**
     * Tests {@link TriggerContextConverter#marshal(Object, com.thoughtworks.xstream.io.HierarchicalStreamWriter,
     * com.thoughtworks.xstream.converters.MarshallingContext)}.
     * With a {@link com.sonymobile.tools.gerrit.gerritevents.dto.events.DraftPublished} event and
     * list of "others" containing two items.
     *
     * @throws Exception if so.
     */
    @Test
    public void testMarshalWithOthersDraftPublished() throws Exception {
        TriggeredItemEntity entity = new TriggeredItemEntity(100, "projectX");

        DraftPublished event = Setup.createDraftPublished();

        TriggerContext context = new TriggerContext(event);
        context.setThisBuild(entity);
        LinkedList<TriggeredItemEntity> otherBuilds = new LinkedList<TriggeredItemEntity>();
        otherBuilds.add(new TriggeredItemEntity(1, "projectY"));
        otherBuilds.add(new TriggeredItemEntity(12, "projectZ"));
        context.setOthers(otherBuilds);

        TestMarshalClass t = new TestMarshalClass(context, "Bobby", new TestMarshalClass(context, "SomeoneElse"));

        XStream xStream = new XStream2();
        xStream.registerConverter(new TriggerContextConverter());
        String xml = xStream.toXML(t);

        TestMarshalClass readT = (TestMarshalClass)xStream.fromXML(xml);

        assertNotNull(readT.getEntity());
        assertNotNull(readT.getEntity().getEvent());
        assertTrue(readT.getEntity().getEvent() instanceof DraftPublished);
        assertNotNull(readT.getEntity().getThisBuild());
        assertNotNull(readT.getEntity().getOthers());

        assertEquals(2, readT.getEntity().getOthers().size());

        TriggeredItemEntity other = readT.getEntity().getOthers().get(0);
        assertEquals(1, other.getBuildNumber().intValue());
        assertEquals("projectY", other.getProjectId());

        other = readT.getEntity().getOthers().get(1);
        assertEquals(12, other.getBuildNumber().intValue());
        assertEquals("projectZ", other.getProjectId());
    }

    //CS IGNORE LineLength FOR NEXT 4 LINES. REASON: Javadoc.

    /**
     * Tests {@link TriggerContextConverter#unmarshal(com.thoughtworks.xstream.io.HierarchicalStreamReader,
     * com.thoughtworks.xstream.converters.UnmarshallingContext)}. With "retriggerAction_oldData2.xml" as input.
     *
     * @throws Exception if so.
     */
    @Test
    public void testUnmarshalOldData2() throws Exception {
        XStream xStream = new XStream2();
        xStream.registerConverter(new TriggerContextConverter());
        Object obj = xStream.fromXML(getClass().getResourceAsStream("retriggerAction_oldData2.xml"));
        assertTrue(obj instanceof RetriggerAction);
        RetriggerAction action = (RetriggerAction)obj;
        TriggerContext context = Whitebox.getInternalState(action, "context");
        assertNotNull(context.getEvent());
        assertThat("Event is not a ChangeBasedEvent", context.getEvent(), instanceOf(ChangeBasedEvent.class));
        ChangeBasedEvent changeBasedEvent = (ChangeBasedEvent)context.getEvent();
        assertEquals("semctools/hudson/plugins/gerrit-trigger-plugin", changeBasedEvent.getChange().getProject());
        if (null != changeBasedEvent.getPatchSet()) {
            assertEquals("1", changeBasedEvent.getPatchSet().getNumber());
        }

        assertNotNull(context.getThisBuild());
        assertEquals(6, context.getThisBuild().getBuildNumber().intValue());
        assertEquals("EXPERIMENTAL_Gerrit_Trigger_1", context.getThisBuild().getProjectId());

        assertNotNull(context.getOthers());
        assertEquals(2, context.getOthers().size());
        TriggeredItemEntity entity = context.getOthers().get(0);
        assertEquals(16, entity.getBuildNumber().intValue());
        assertEquals("EXPERIMENTAL_Gerrit_Trigger_2", entity.getProjectId());
        entity = context.getOthers().get(1);
        assertEquals(15, entity.getBuildNumber().intValue());
        assertEquals("EXPERIMENTAL_Gerrit_Trigger_3", entity.getProjectId());
    }

    //CS IGNORE LineLength FOR NEXT 4 LINES. REASON: Javadoc.

    /**
     * Tests {@link TriggerContextConverter#unmarshal(com.thoughtworks.xstream.io.HierarchicalStreamReader,
     * com.thoughtworks.xstream.converters.UnmarshallingContext)}. With "matrix_build.xml" as input.
     *
     * @throws Exception if so.
     */
    @Test
    public void testUnmarshalOldMatrixBuild() throws Exception {
        PowerMockito.mockStatic(OldDataMonitor.class);
        doNothing().when(OldDataMonitor.class, "report", any(Saveable.class), anyCollection());
        XStream xStream = new XStream2();
        xStream.registerConverter(new TriggerContextConverter());
        xStream.alias("matrix-run", MatrixRun.class);
        Object obj = xStream.fromXML(getClass().getResourceAsStream("matrix_build.xml"));
        assertTrue(obj instanceof MatrixRun);
        MatrixRun run = (MatrixRun)obj;
        mockStatic(ExtensionList.class);
        ExtensionList listMock = mock(ExtensionList.class);
        doReturn(Collections.emptyList().iterator()).when(listMock).iterator();
        doReturn(listMock).when(ExtensionList.class, "lookup", same(TransientActionFactory.class));
        Cause.UpstreamCause upCause = run.getCause(Cause.UpstreamCause.class);
        List upstreamCauses = Whitebox.getInternalState(upCause, "upstreamCauses");
        GerritCause cause = (GerritCause)upstreamCauses.get(0);
        assertNotNull(cause.getEvent());
        assertThat("Event is not a ChangeBasedEvent", cause.getEvent(), instanceOf(ChangeBasedEvent.class));
        ChangeBasedEvent changeBasedEvent = (ChangeBasedEvent)cause.getEvent();
        assertEquals("platform/project", changeBasedEvent.getChange().getProject());
        assertNotNull(cause.getContext());
        assertNotNull(cause.getContext().getThisBuild());

        assertEquals("Gerrit_master-theme_matrix", cause.getContext().getThisBuild().getProjectId());
        assertEquals(102, cause.getContext().getThisBuild().getBuildNumber().intValue());

        assertNotNull(cause.getContext().getOthers());
        assertEquals(1, cause.getContext().getOthers().size());

        TriggeredItemEntity entity = cause.getContext().getOthers().get(0);
        assertEquals("master-theme", entity.getProjectId());
        assertNull(entity.getBuildNumber());
    }

    /**
     * Tests {@link TriggerContextConverter#canConvert(Class)}. With {@link TriggerContext}.class as input.
     *
     * @throws Exception if so.
     */
    @Test
    public void testCanConvert() throws Exception {
        TriggerContextConverter conv = new TriggerContextConverter();
        assertTrue(conv.canConvert(TriggerContext.class));
    }

    /**
     * Tests {@link TriggerContextConverter#canConvert(Class)}. With {@link String}.class as input.
     *
     * @throws Exception if so.
     */
    @Test
    public void testCanConvertString() throws Exception {
        TriggerContextConverter conv = new TriggerContextConverter();
        assertFalse(conv.canConvert(String.class));
    }

    /**
     * Tests {@link TriggerContextConverter#canConvert(Class)}. With a subclass of {@link TriggerContext} as input.
     *
     * @throws Exception if so.
     */
    @Test
    public void testCanConvertSub() throws Exception {
        TriggerContextConverter conv = new TriggerContextConverter();
        assertFalse(conv.canConvert(TriggerContextSub.class));
    }

    /**
     * A qnd subclass of {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext}. As
     * input to the {@link #testCanConvertSub} test.
     */
    static class TriggerContextSub extends TriggerContext {

    }


    /**
     * A simple POJO class that contains a {@link TriggerContext}. To aid in the marshal testing.
     */
    static class TestMarshalClass {
        private TriggerContext entity;
        private String name;
        private TestMarshalClass testClass;

        /**
         * Default Constructor.
         */
        @SuppressWarnings("unused")
        //called by XStream
        TestMarshalClass() {
        }

        /**
         * Standard constructor.
         *
         * @param entity a context.
         * @param name   a string.
         */
        TestMarshalClass(TriggerContext entity, String name) {
            this.entity = entity;
            this.name = name;
        }

        /**
         * Standard constructor.
         *
         * @param entity    a context.
         * @param name      a string.
         * @param testClass a second level.
         */
        TestMarshalClass(TriggerContext entity, String name, TestMarshalClass testClass) {
            this.entity = entity;
            this.name = name;
            this.testClass = testClass;
        }

        /**
         * Get the context.
         *
         * @return the context.
         */
        public TriggerContext getEntity() {
            return entity;
        }

        /**
         * The name.
         *
         * @return the name.
         */
        public String getName() {
            return name;
        }

        /**
         * The second level.
         *
         * @return the second level.
         */
        public TestMarshalClass getTestClass() {
            return testClass;
        }
    }
}
