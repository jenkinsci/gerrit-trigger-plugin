/*
 * The MIT License
 *
 * Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier;

import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.impl.GerritSSHCmdRunner;

/**
 * A factory for creating {@link GerritCmdRunner} and {@link GerritNotifier}.
 * This factory is mainly created and used to ease unit testing.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class NotificationFactory {
    private static NotificationFactory instance;

    /**
     * Gets the singleton instance of the NotificationFactory.
     * @return the NotificationFactory.
     */
    public static NotificationFactory getInstance() {
        if (instance == null) {
            instance = new NotificationFactory();
        }
        return instance;
    }

    /**
     * Factory method for creating a GerritCmdRunner.
     * @return a GerritCmdRunner
     */
    public GerritCmdRunner createGerritCmdRunner() {
        IGerritHudsonTriggerConfig config = getConfig();
        return new GerritSSHCmdRunner(config);
    }

    /**
     * Shortcut method to get the config from {@link com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl}.
     * Throws an IllegalStateException if PluginImpl hasn't been started yet.
     * @return the plugin-config.
     */
    public IGerritHudsonTriggerConfig getConfig() {
        if (PluginImpl.getInstance() == null) {
            //If this happens we are sincerely screwed anyways.
            throw new IllegalStateException("PluginImpl has not been loaded yet!");
        }
        return PluginImpl.getInstance().getConfig();
    }

    /**
     * Factory method for creating a GerritNotifier.
     * @return a GerritNotifier
     */
    public GerritNotifier createGerritNotifier() {
        IGerritHudsonTriggerConfig config = getConfig();
        return new GerritNotifier(config, createGerritCmdRunner());
    }

}
