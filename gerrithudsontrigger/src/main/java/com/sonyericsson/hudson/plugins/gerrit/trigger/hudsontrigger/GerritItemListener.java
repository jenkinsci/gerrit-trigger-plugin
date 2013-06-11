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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;

/**
 * Listens for onDeleted events, and if the deleted project has a Gerrit trigger,
 * cancels its timers. Since this class has no member variables, and doesn't need any
 * initialization, there is no constructor.
 *
 * @author Fredrik Abrahamson &lt;fredrik.abrahamson@sonymobile.com&gt;
 */
@Extension
public class GerritItemListener extends ItemListener {
    private static final Logger logger = LoggerFactory.getLogger(GerritItemListener.class);

    /**
     * Called by Jenkins when an item is about to be deleted. If this item is a project
     * (AbstractProject or any of its subclasses), then we check if it has a GerritTrigger
     * among its triggers. If so, call the trigger's cancelTimer() method.
     *
     * This class is unfortunately needed because Jenkins doesn't call Trigger.stop() when
     * a project is deleted, only when a project is reconfigured. Thus we need this class
     * to cancel the timer when a project is deleted.
     *
     * @param item the item that will be deleted, it is interesting if it is
     * a subclass of an AbstractProject
     */
    @Override
    public void onDeleted(Item item) {
        if (item instanceof AbstractProject<?, ?>) {
            AbstractProject<?, ?> project = (AbstractProject<?, ?>)item;
            GerritTrigger gerritTrigger = project.getTrigger(GerritTrigger.class);
            if (gerritTrigger != null) {
                gerritTrigger.cancelTimer();
            }
        }
    }

    /**
     * Called by Jenkins when all items are loaded.
     */
    @Override
    public void onLoaded() {
        try {
            PluginImpl.getInstance().startConnection();
        } catch (Exception e) {
            logger.error("Could not start connection. ", e);
        }
        super.onLoaded();
    }
}
