package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import java.util.LinkedList;
import java.util.List;

/**
 * A {@link com.thoughtworks.xstream.XStream} converter that can marshal/unmarshal {@link TriggerContext}s.
 * This aids in the backwards comparability issue when refactoring the TriggerContext class.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 * @since 2.2.0
 */
public class TriggerContextConverter implements Converter {
    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        TriggerContext tc = (TriggerContext)source;
        if (tc.getEvent() != null) {
            writer.startNode("event");
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
              GerritTriggeredEvent event = (GerritTriggeredEvent)context.convertAnother(tc, GerritTriggeredEvent.class);
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


    @Override
    public boolean canConvert(Class type) {
        return type != null && type.equals(TriggerContext.class);
    }
}
