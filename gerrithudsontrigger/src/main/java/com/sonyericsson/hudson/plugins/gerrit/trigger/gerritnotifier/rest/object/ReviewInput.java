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
