package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.rest.object;

public class LineComment {
    private final String message;
    private final int line;

    public LineComment(int line, String message) {
        this.message = message;
        this.line = line;
    }
}
