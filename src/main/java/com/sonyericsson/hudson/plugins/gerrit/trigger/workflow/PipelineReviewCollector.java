/*
 * The MIT License
 *
 * Copyright (c) 2024 Amarula Solutions. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.workflow;

import com.sonymobile.tools.gerrit.gerritevents.dto.rest.CommentedFile;
import com.sonymobile.tools.gerrit.gerritevents.dto.rest.LineComment;

import hudson.model.Run;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects review comments during pipeline execution for later posting.
 * One instance per pipeline run, keyed by the build object.
 *
 * @author Michael Trimarchi
 */
public class PipelineReviewCollector {

    private static final Map<Run<?, ?>, PipelineReviewCollector> collectors =
            new ConcurrentHashMap<Run<?, ?>, PipelineReviewCollector>();

    private final List<CommentedFile> commentedFiles = new ArrayList<CommentedFile>();

    /**
     * Gets or creates the collector for the given build.
     *
     * @param build the current build.
     * @return the collector instance for this run.
     */
    public static PipelineReviewCollector get(Run<?, ?> build) {
        PipelineReviewCollector collector = collectors.get(build);
        if (collector == null) {
            collector = new PipelineReviewCollector();
            collectors.put(build, collector);
        }
        return collector;
    }

    /**
     * Removes the collector for the given build (cleanup after posting).
     *
     * @param build the build.
     */
    public static void remove(Run<?, ?> build) {
        collectors.remove(build);
    }

    /**
     * Adds a file-level comment.
     *
     * @param path    relative file path
     * @param message comment message
     */
    public void addComment(String path, String message) {
        findOrCreate(path).addLineComment(new LineComment(0, message));
    }

    /**
     * Adds a line-level comment.
     *
     * @param path    relative file path
     * @param line    line number
     * @param message comment message
     */
    public void addComment(String path, int line, String message) {
        findOrCreate(path).addLineComment(new LineComment(line, message));
    }

    /**
     * Returns all collected commented files and clears the internal list.
     *
     * @return the collected commented files.
     */
    public Collection<CommentedFile> drainComments() {
        List<CommentedFile> result = new ArrayList<CommentedFile>(commentedFiles);
        commentedFiles.clear();
        return result;
    }

    /**
     * Returns whether any comments have been collected.
     *
     * @return true if there are pending comments.
     */
    public boolean hasComments() {
        return !commentedFiles.isEmpty();
    }

    private CommentedFile findOrCreate(String path) {
        for (CommentedFile cf : commentedFiles) {
            if (cf.getFileName().equals(path)) {
                return cf;
            }
        }
        CommentedFile cf = new CommentedFile(path);
        commentedFiles.add(cf);
        return cf;
    }
}
