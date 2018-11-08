/*
 *  The MIT License
 *
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.ToGerritRunListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;

/**
 * Listens for onDeleted and onLoaded events.
 * If the deleted project has a Gerrit trigger, it will be stopped
 * If all project have been loaded, start the connections to Gerrit servers.
 * Since this class has no member variables, and doesn't need any
 * initialization, there is no constructor.
 *
 * @author Fredrik Abrahamson &lt;fredrik.abrahamson@sonymobile.com&gt;
 */
@Extension
public class GerritItemListener extends ItemListener {
    private static final Logger logger = LoggerFactory.getLogger(GerritItemListener.class);

    /**
     * Called by Jenkins when an item is about to be deleted. If this item is a project
     * (Job or any of its subclasses), then we check if it has a GerritTrigger
     * among its triggers. If so, call the trigger's stop() method.
     *
     * It removes deleted project from all ongoing builds, so, GerritTrigger
     * will not wait projects that do not have chance to be finished.
     *
     * Also this class is unfortunately needed because Jenkins doesn't call Trigger.stop() when
     * a project is deleted, only when a project is reconfigured. Thus we need this class
     * to remove the listener and cancel the timer when a project is deleted.
     *
     * @param item the item that will be deleted, it is interesting if it is
     * a subclass of an Job
     */
    @Override
    public void onDeleted(Item item) {
        if (item instanceof Job<?, ?>) {
            Job<?, ?> project = (Job<?, ?>)item;
            GerritTrigger gerritTrigger = GerritTrigger.getTrigger(project);
            if (gerritTrigger != null) {
                gerritTrigger.stop();

                ToGerritRunListener gerritRunListener = ToGerritRunListener.getInstance();
                if (gerritRunListener != null) {
                    gerritRunListener.notifyProjectRemoved(project);
                }
            }
        }
    }

    /**
     * trigger get stopped/started when a job is configured, but rename is a special operation
     * and uses a two phase confirmation, the second one doing the actual rename does not
     * stop/start the trigger, so we end up with misconfigured EventListener.
     *
     * Also see JENKINS-22936
     * @param item an item whose absolute position is now different
     * @param oldFullName the former {@link Item#getFullName}
     * @param newFullName the current {@link Item#getFullName}
     */
    @Override
    public void onLocationChanged(Item item, String oldFullName, String newFullName) {
        if (item instanceof Job<?, ?>) {
            Job<?, ?> project = (Job<?, ?>)item;
            GerritTrigger gerritTrigger = GerritTrigger.getTrigger(project);
            if (gerritTrigger != null) {
                gerritTrigger.onJobRenamed(oldFullName, newFullName);
            }
        }
    }

    /**
     * Called by Jenkins when all items are loaded.
     */
    @Override
    public void onLoaded() {
        for (GerritServer s : PluginImpl.getServers_()) {
            if (!s.isNoConnectionOnStartup()) {
                s.startConnection();
            }
        }
        super.onLoaded();
    }
}
