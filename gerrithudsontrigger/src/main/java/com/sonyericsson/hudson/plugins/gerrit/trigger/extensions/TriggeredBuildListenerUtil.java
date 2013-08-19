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
package com.sonyericsson.hudson.plugins.gerrit.trigger.extensions;

import hudson.ExtensionList;
import hudson.model.Result;
import jenkins.model.Jenkins;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint;

/**
 * A Utility to fire build events.
 *
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public final class TriggeredBuildListenerUtil {

    private static final Logger logger = LoggerFactory.getLogger(TriggeredBuildListenerUtil.class);

    /**
     * Default constructor.
     */
    private TriggeredBuildListenerUtil() {
    }

    /**
     * Fire onStarted.
     *
     * @param event the event
     * @param command the command.
     */
    public static void fireOnStarted(GerritTriggeredEvent event, String command) {
        for (TriggeredBuildListener listener : getAllListeners()) {
            try {
                listener.onStarted(event, command);
            } catch (Exception ex) {
                logger.warn(ex.getMessage());
            }
        }
    }

    /**
     * Fire onCompleted.
     *
     * @param memoryImprint the memoryimprint.
     * @param command the command.
     */
    public static void fireOnCompleted(MemoryImprint memoryImprint, String command) {
        Result result = Result.FAILURE;
        if (memoryImprint.wereAllBuildsSuccessful()) {
            result = Result.SUCCESS;
        } else if (memoryImprint.wereAnyBuildsFailed()) {
            result = Result.FAILURE;
        } else if (memoryImprint.wereAnyBuildsUnstable()) {
            result = Result.UNSTABLE;
        } else if (memoryImprint.wereAllBuildsNotBuilt()) {
            result = Result.NOT_BUILT;
        }
        for (TriggeredBuildListener listener : getAllListeners()) {
            try {
                listener.onCompleted(result, memoryImprint.getEvent(), command);
            } catch (Exception ex) {
                logger.warn(ex.getMessage());
            }
        }
    }

    /**
     * Gets all listeners implements {@link TriggeredBuildListener}.
     *
     * @return the extension list implements {@link TriggeredBuildListener}.
     */
    private static ExtensionList<TriggeredBuildListener> getAllListeners() {
        return Jenkins.getInstance().getExtensionList(TriggeredBuildListener.class);
    }
}
