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

import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.rest.object.CommentedFile;
import hudson.ExtensionPoint;
import hudson.model.AbstractBuild;
import hudson.model.Hudson;

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
    public String getBuildStartedMessage(AbstractBuild build) {
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
    public String getBuildCompletedMessage(AbstractBuild build) {
        return null;
    }

    /**
     * Provide any file comments.
     *
     * @param build the build to complain about
     * @return the file comments, default is an empty list.
     */
    public Collection<CommentedFile> getFileComments(AbstractBuild build) {
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
}
