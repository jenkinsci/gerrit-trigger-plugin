package com.sonyericsson.hudson.plugins.gerrit.trigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.metadata.contributors.JobMetadataContributor;
import com.sonyericsson.hudson.plugins.metadata.model.values.MetadataValue;
import com.sonyericsson.hudson.plugins.metadata.model.values.TreeStructureUtil;
import hudson.Extension;
import hudson.model.AbstractProject;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Robert Sandell &lt;sandell.robert@gmail.com&gt;
 */
@Extension
public class GerritJobMetadataContributor extends JobMetadataContributor {
    @Override
    public List<MetadataValue> getMetaDataFor(AbstractProject job) {
        if (GerritTrigger.getTrigger(job) != null) {
            List<MetadataValue> list = new LinkedList<MetadataValue>();
            list.add(TreeStructureUtil.createPath("true", "", "trigger", "gerrit", "enabled"));
            return list;
        }
        return Collections.emptyList();
    }
}
