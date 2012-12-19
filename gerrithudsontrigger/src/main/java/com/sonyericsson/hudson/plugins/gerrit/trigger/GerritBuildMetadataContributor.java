package com.sonyericsson.hudson.plugins.gerrit.trigger;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.metadata.contributors.BuildMetadataContributor;
import com.sonyericsson.hudson.plugins.metadata.model.values.MetadataValue;
import com.sonyericsson.hudson.plugins.metadata.model.values.TreeNodeMetadataValue;
import com.sonyericsson.hudson.plugins.metadata.model.values.TreeStructureUtil;
import hudson.Extension;
import hudson.model.AbstractBuild;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Robert Sandell &lt;sandell.robert@gmail.com&gt;
 */
@Extension
public class GerritBuildMetadataContributor extends BuildMetadataContributor {
    @Override
    public List<MetadataValue> getMetaDataFor(AbstractBuild build) {
        GerritCause cause = (GerritCause) build.getCause(GerritCause.class);
        if (cause != null) {
            TreeNodeMetadataValue[] path = TreeStructureUtil.createTreePath("Gerrit info",
                                                                        "trigger", "gerrit");
            GerritTriggeredEvent event = cause.getEvent();
            TreeStructureUtil.addValue(path[1], event.getEventType().getTypeValue(),
                                                "Type of event",
                                                "kind");
            if (event instanceof ChangeBasedEvent) {
                ChangeBasedEvent cEvent = (ChangeBasedEvent) event;
                TreeStructureUtil.addValue(path[1], cEvent.getChange().getProject(),
                                                    "",
                                                    "change", "project");
                TreeStructureUtil.addValue(path[1], cEvent.getChange().getBranch(),
                                                    "",
                                                    "change", "branch");
                TreeStructureUtil.addValue(path[1], cEvent.getChange().getId(),
                                                    "",
                                                    "change", "id");
                TreeStructureUtil.addValue(path[1], cEvent.getChange().getNumber(),
                                                    "",
                                                    "change", "number");
                TreeStructureUtil.addValue(path[1], cEvent.getPatchSet().getNumber(),
                                                    "",
                                                    "patch-set", "number");
                TreeStructureUtil.addValue(path[1], cEvent.getPatchSet().getRevision(),
                                                    "",
                                                    "patch-set", "revision");
            }
            List<MetadataValue> list = new LinkedList<MetadataValue>();
            list.add(path[0]);
            return list;
        }
        return Collections.emptyList();
    }
}
