package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonymobile.tools.gerrit.gerritevents.GerritHandler;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Job;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;

/**
 * Listens for updated job configurations. If a trigger is removed from the job
 * configuration, then we need to remove the associated listener
 */
@Extension
public class GerritSaveableListener extends SaveableListener {
    @Override
    public void onChange(Saveable o, XmlFile file) {
        if (o instanceof Job<?, ?>) {
            Job<?, ?> project = (Job<?, ?>)o;
            GerritTrigger gerritTrigger = GerritTrigger.getTrigger(project);
            if (gerritTrigger == null) {
                PluginImpl plugin = PluginImpl.getInstance();
                if (plugin != null) {
                    GerritHandler handler = plugin.getHandler();
                    if (handler != null) {
                        handler.removeListener(new EventListener(project));
                    }
                }
            }
        }
    }
}
