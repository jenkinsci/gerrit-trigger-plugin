/*
 *  The MIT License
 *
 *  Copyright 2014 rinrinne All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.api;

import com.sonymobile.tools.gerrit.gerritevents.Handler;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.api.exception.PluginNotFoundException;
import com.sonyericsson.hudson.plugins.gerrit.trigger.api.exception.PluginStatusException;

/**
 * An API class to use this plugin from external.
 *
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public final class GerritTriggerApi {

    /**
     * Gets plugin instance if active.
     *
     * @return the instance.
     * @throws PluginNotFoundException if plugin is not found or still not loaded.
     * @throws PluginStatusException if plugin is inactive.
     */
    private PluginImpl getActivePlugin() throws PluginNotFoundException, PluginStatusException {
        PluginImpl plugin = PluginImpl.getInstance();
        if (plugin == null) {
            throw new PluginNotFoundException();
        }
        if (!plugin.isActive()) {
            throw new PluginStatusException();
        }
        return plugin;
    }

    /**
     * Gets Handler.
     *
     * @return the handler instance.
     * @throws PluginNotFoundException if plugin is not found or still not loaded.
     * @throws PluginStatusException if plugin is inactive.
     */
    public Handler getHandler() throws PluginNotFoundException, PluginStatusException {
        return getActivePlugin().getHandler();
    }
}
