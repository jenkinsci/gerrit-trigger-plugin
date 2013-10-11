/*
 *  The MIT License
 *
 *  Copyright 2013 Jyrki Puttonen. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.rest.object;

import java.util.Collection;

/**
 * TODO Missing JavaDoc and move to gerrit-events module (with the rest of the rest classes).
 */
public class CommentedFile {
    private final String fileName;
    private final Collection<LineComment> comments;

    /**
     * Standard Constructor.
     *
     * @param fileName file name
     * @param comments comments
     */
    public CommentedFile(String fileName, Collection<LineComment> comments) {
        this.fileName = fileName;
        this.comments = comments;
    }

    /**
     * File name.
     *
     * @return file name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * all of them.
     *
     * @return line comments
     */
    public Collection<? extends LineComment> getLineComments() {
        return comments;
    }
}
