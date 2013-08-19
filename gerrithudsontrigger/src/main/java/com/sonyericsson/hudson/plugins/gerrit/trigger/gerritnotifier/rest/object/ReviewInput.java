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

import java.util.*;

public class ReviewInput {
    final String message;
    final Map<String, Integer> labels = new HashMap<String, Integer>();
    final Map<String, List<LineComment>> comments = new HashMap<String, List<LineComment>>();

    public ReviewInput(String message, String labelName, int labelValue) {
        this(message, Collections.singleton(new ReviewLabel(labelName, labelValue)));
    }

    public ReviewInput(String message, ReviewLabel... labels) {
        this(message, Arrays.asList(labels));
    }

    public ReviewInput(String message, Collection<ReviewLabel> labels) {
        this(message, labels, Collections.<CommentedFile>emptyList());
    }

    public ReviewInput(String message, Collection<CommentedFile> commentedFiles, ReviewLabel... labels) {
        this(message, Arrays.asList(labels), commentedFiles);
    }
    public ReviewInput(String message, Collection<ReviewLabel> labels, Collection<CommentedFile> commentedFiles) {
        this.message = message;
        for(ReviewLabel label : labels) {
            this.labels.put(label.getName(), label.getValue());
        }
        for(CommentedFile file : commentedFiles) {
            if(!comments.containsKey(file.getFileName())) {
                comments.put(file.getFileName(), new ArrayList<LineComment>());
            }
            comments.get(file.getFileName()).addAll(file.getLineComments());
        }
    }
}
