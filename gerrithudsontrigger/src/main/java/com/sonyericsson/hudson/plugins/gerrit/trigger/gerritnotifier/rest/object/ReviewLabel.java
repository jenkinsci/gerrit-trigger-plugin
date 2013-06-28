package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.rest.object;

public class ReviewLabel {

    private final String name;
    private final int value;

    public ReviewLabel(String name, int value) {
        this.name = name;
        this.value = value;
    }

    public static ReviewLabel codeReview(int codeReview) {
        return new ReviewLabel("Code-Review", codeReview);
    }

    public static ReviewLabel verified(int verified) {
        return new ReviewLabel("Verified", verified);
    }

    public String getName() {
        return name;
    }

    public int getValue() {
        return value;
    }
}
