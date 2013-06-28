package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.rest.object;

public class ChangeId {
    private final String projectName;
    private final String branchName;
    private final String id;

    public ChangeId(String projectName, String branchName, String id) {
        this.projectName = projectName;
        this.branchName = branchName;
        this.id = id;
    }

    public String asUrlPart() {
        return projectName + "~" + branchName + "~" + id;
    }
}
