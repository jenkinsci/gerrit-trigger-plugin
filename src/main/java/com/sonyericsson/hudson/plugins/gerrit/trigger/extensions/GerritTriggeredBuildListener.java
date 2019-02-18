/*
 *  The MIT License
 *
 *  Copyright 2013 rinrinne All rights reserved.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jenkins.model.Jenkins;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Result;

import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint;

/**
 * A abstract class for listening Gerrit triggered build result.
 *
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public abstract class GerritTriggeredBuildListener implements ExtensionPoint {

    private static final Logger logger = LoggerFactory.getLogger(GerritTriggeredBuildListener.class);
    /**
     * Calls when build started.
     *
     * @param event the event.
     * @param command the command.
     */
     public abstract void onStarted(GerritTriggeredEvent event, String command);

    /**
     * Calls when all builds completed.
     *
     * @param result the result.
     * @param event the event.
     * @param command the command.
     */
     public abstract void onCompleted(Result result, GerritTriggeredEvent event, String command);

     /**
      * Fire onStarted.
      *
      * @param event the event
      * @param command the command.
      */
     public static void fireOnStarted(GerritTriggeredEvent event, String command) {
         for (GerritTriggeredBuildListener listener : all()) {
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
         } else if (memoryImprint.wereAnyBuildsAborted()) {
             result = Result.ABORTED;
         }
         for (GerritTriggeredBuildListener listener : all()) {
             try {
                 listener.onCompleted(result, memoryImprint.getEvent(), command);
             } catch (Exception ex) {
                 logger.warn(ex.getMessage());
             }
         }
     }

     /**
      * Gets all listeners.
      *
      * @return the extension list.
      */
     public static ExtensionList<GerritTriggeredBuildListener> all() {
         Jenkins jenkins = Jenkins.getInstance();
         assert jenkins != null;
         return jenkins.getExtensionList(GerritTriggeredBuildListener.class);
     }
}
