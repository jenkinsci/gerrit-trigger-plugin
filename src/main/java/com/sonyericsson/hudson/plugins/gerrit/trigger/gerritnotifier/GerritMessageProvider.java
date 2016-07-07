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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier;

import com.sonymobile.tools.gerrit.gerritevents.dto.rest.CommentedFile;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Hudson;
import hudson.model.Run;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * ExtensionPoint that allows other plug-ins to provide custom messages.
 *
 * @author Gustaf Lundh &lt;gustaf.lundh@sonymobile.com&gt;
 */
public abstract class GerritMessageProvider implements Serializable, ExtensionPoint {
    private static final long serialVersionUID = -2019176207418719449L;

    /**
     * Method allowing plug-ins to provide extra custom messages to Gerrit when a build is started.
     *
     * Return null if no message should be added.
     *
     * @param build Triggered build to provide custom message for
     * @return the custom message
     */
    public String getBuildStartedMessage(Run build) {
        // If the overriding class still overrides the deprecated method, call that.
        if (build instanceof AbstractBuild && thisOverrides("getBuildStartedMessage", AbstractBuild.class)) {
            return getBuildStartedMessage((AbstractBuild)build);
        }
        return null;
    }

    /**
     * Method allowing plug-ins to provide extra custom messages to Gerrit when a build is started.
     *
     * Return null if no message should be added.
     *
     * @param build Triggered build to provide custom message for
     * @return the custom message
     * @deprecated Use {@link #getBuildStartedMessage(hudson.model.Run)}
     */
    @Deprecated
    public String getBuildStartedMessage(AbstractBuild build) {
        if (thisOverrides("getBuildStartedMessage", Run.class)) {
            return getBuildStartedMessage((Run)build);
        }
        return null;
    }

    /**
     * Method allowing plug-ins to provide extra custom messages to Gerrit when a build is completed.
     *
     * Return null if no message should be added.
     *
     * @param build Triggered build to provide custom message for
     * @return the custom message
     */
    public String getBuildCompletedMessage(Run build) {
        // If the overriding class still overrides the deprecated method, call that.
        if (build instanceof AbstractBuild && thisOverrides("getBuildCompletedMessage", AbstractBuild.class)) {
            return getBuildCompletedMessage((AbstractBuild)build);
        }
        return null;
    }

    /**
     * Method allowing plug-ins to provide extra custom messages to Gerrit when a build is completed.
     *
     * Return null if no message should be added.
     *
     * @param build Triggered build to provide custom message for
     * @return the custom message
     * @deprecated Use {@link #getBuildCompletedMessage(hudson.model.Run)}
     */
    @Deprecated
    public String getBuildCompletedMessage(AbstractBuild build) {
        if (thisOverrides("getBuildCompletedMessage", Run.class)) {
            return getBuildCompletedMessage((Run)build);
        }
        return null;
    }

    /**
     * Provide any file comments.
     *
     * @param build the build to complain about
     * @return the file comments, default is an empty list.
     */
    public Collection<CommentedFile> getFileComments(Run build) {
        // If the overriding class still overrides the deprecated method, call that.
        if (build instanceof AbstractBuild && thisOverrides("getFileComments", AbstractBuild.class)) {
            return getFileComments((AbstractBuild)build);
        }
        return Collections.emptyList();
    }

    /**
     * Provide any file comments.
     *
     * @param build the build to complain about
     * @return the file comments, default is an empty list.
     * @deprecated Use {@link #getFileComments(hudson.model.Run)}
     */
    @Deprecated
    public Collection<CommentedFile> getFileComments(AbstractBuild build) {
        if (thisOverrides("getFileComments", Run.class)) {
            return getFileComments((Run)build);
        }
        return Collections.emptyList();
    }

     /**
     * Method fetching instances of ExtensionPoints implementing GerritMessageProvider.
     *
     * @return list of classes extending GerritMessageProvider
     */
    public static List<GerritMessageProvider> all() {
        return Hudson.getInstance().getExtensionList(GerritMessageProvider.class);
    }

    /**
     * Check if the extension point impl has an overridden impl of the specified method.
     * @param methodName The method name.
     * @param argTypes The method arg types.
     * @return True if it has an overridden impl of the specified method, otherwise false.
     */
    private boolean thisOverrides(String methodName, Class... argTypes) {
        return Util.isOverridden(GerritMessageProvider.class, getClass(), methodName, argTypes);
    }
}
