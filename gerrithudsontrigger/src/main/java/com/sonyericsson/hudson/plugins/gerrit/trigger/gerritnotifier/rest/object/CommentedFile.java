package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.rest.object;

import java.util.Collection;

public class CommentedFile {
    private final String fileName;
    private final Collection<LineComment> comments;

    public CommentedFile(String fileName, Collection<LineComment> comments) {
        this.fileName = fileName;
        this.comments = comments;
    }

    public String getFileName() {
        return fileName;
    }

    public Collection<? extends LineComment> getLineComments() {
        return comments;
    }
}
