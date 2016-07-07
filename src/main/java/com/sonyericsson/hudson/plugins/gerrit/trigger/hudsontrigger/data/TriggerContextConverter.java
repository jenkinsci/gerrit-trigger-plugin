/*
 *  The MIT License
 *
 *  Copyright 2011 Sony Ericsson Mobile Communications. All rights reserved.
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

import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.CannotResolveClassException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hudson.model.Run;

import java.util.LinkedList;
import java.util.List;

/**
 * A {@link com.thoughtworks.xstream.XStream} converter that can marshal/unmarshal {@link TriggerContext}s. This aids in
 * the backwards comparability issue when refactoring the TriggerContext class.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 * @since 2.2.0
 */
public class TriggerContextConverter implements Converter {

    private static final Logger logger = LoggerFactory.getLogger(TriggerContextConverter.class);

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        TriggerContext tc = (TriggerContext)source;
        if (tc.getEvent() != null) {
            writer.startNode("event");
            writer.addAttribute("class", tc.getEvent().getClass().getName());
            context.convertAnother(tc.getEvent());
            writer.endNode();
        }
        if (tc.getThisBuild() != null) {
            writer.startNode("thisBuild");
            marshalItemEntity(tc.getThisBuild(), writer);
            writer.endNode();
        }
        if (tc.getOthers() != null && tc.getOthers().size() > 0) {
            writer.startNode("others");
            for (TriggeredItemEntity entity : tc.getOthers()) {
                if (entity != null) {
                    writer.startNode("triggeredItemEntity");
                    marshalItemEntity(entity, writer);
                    writer.endNode();
                }
            }
            writer.endNode();
        }
    }

    /**
     * Marshals an instance of {@link TriggeredItemEntity}.
     *
     * @param entity the entity.
     * @param writer the XStream writer.
     */
    private void marshalItemEntity(TriggeredItemEntity entity,
                                   HierarchicalStreamWriter writer) {
        writer.startNode("buildNumber");
        if (entity.getBuildNumber() != null) {
            writer.setValue(entity.getBuildNumber().toString());
        }
        writer.endNode();
        writer.startNode("projectId");
        writer.setValue(entity.getProjectId());
        writer.endNode();
    }

    /**
     * Unmarshals an instance of {@link TriggeredItemEntity}.
     *
     * @param reader  the XStream reader, placed at the position of the entity.
     * @param context the context.
     * @return an entity.
     */
    private TriggeredItemEntity unmarshalItemEntity(HierarchicalStreamReader reader, UnmarshallingContext context) {
        Integer buildNumber = null;
        String projectId = null;

        while (reader.hasMoreChildren()) {
            reader.moveDown();
            if ("buildNumber".equalsIgnoreCase(reader.getNodeName())) {
                String buildNumberStr = reader.getValue();
                if (buildNumberStr != null && buildNumberStr.length() > 0) {
                    try {
                        buildNumber = Integer.parseInt(buildNumberStr);
                    } catch (NumberFormatException e) {
                        throw new ConversionException("Wrong buildNumber format!", e);
                    }
                }
            } else if ("projectId".equalsIgnoreCase(reader.getNodeName())) {
                projectId = reader.getValue();
            }
            reader.moveUp();
        }
        return new TriggeredItemEntity(buildNumber, projectId);
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        TriggerContext tc = new TriggerContext();
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            if ("event".equalsIgnoreCase(reader.getNodeName())) {
                String clazz = reader.getAttribute("class");
                Class<? extends GerritTriggeredEvent> theClass = calculateEventClass(clazz);
                GerritTriggeredEvent event = (GerritTriggeredEvent)context.convertAnother(tc, theClass);
                tc.setEvent(event);
            } else if ("thisBuild".equalsIgnoreCase(reader.getNodeName())) {
                TriggeredItemEntity entity = unmarshalItemEntity(reader, context);
                tc.setThisBuild(entity);
            } else if ("others".equalsIgnoreCase(reader.getNodeName())) {
                List<TriggeredItemEntity> list = new LinkedList<TriggeredItemEntity>();
                while (reader.hasMoreChildren()) {
                    reader.moveDown();
                    TriggeredItemEntity entity = unmarshalItemEntity(reader, context);
                    list.add(entity);
                    reader.moveUp();
                }
                tc.setOthers(list);
            }
            reader.moveUp();
        }
        return tc;
    }

    /**
     * Tries to load the the class if it is not null. If null or failure it returns
     * {@link PatchsetCreated} as a safety measure.
     *
     * @param clazz the class to try and load.
     * @return the class.
     */
    private Class<? extends GerritTriggeredEvent> calculateEventClass(String clazz) {
        if (clazz == null) {
            //Probably old data, assume PatchsetCreated
            return PatchsetCreated.class;
        }
        Class<? extends GerritTriggeredEvent> theClass = null;
        try {
            theClass = Run.XSTREAM2.getMapper().realClass(clazz);
            return theClass;
        } catch (CannotResolveClassException e) {
            logger.error("Failed to unmarshall event type for trigger context!", e);
        } catch (ClassCastException e) {
            logger.error("Failed to unmarshall event type for trigger context!", e);
        }
        //Fallback to PatchsetCreated and pray
        return PatchsetCreated.class;
    }


    @Override
    public boolean canConvert(Class type) {
        return type != null && type.equals(TriggerContext.class);
    }
}
