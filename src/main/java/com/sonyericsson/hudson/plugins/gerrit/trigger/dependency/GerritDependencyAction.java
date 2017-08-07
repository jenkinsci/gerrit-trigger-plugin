package com.sonyericsson.hudson.plugins.gerrit.trigger.dependency;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentContributingAction;
import hudson.model.InvisibleAction;
import hudson.model.Run;

import java.util.Collections;
import java.util.List;

/**
 * Adds Action that stores the data about dependency jobs.
 */
public class GerritDependencyAction extends InvisibleAction implements EnvironmentContributingAction {
    private List<Run> runs = Collections.emptyList();

    /**
     * @param runs list of runs it depend on
     */
    public GerritDependencyAction(List<Run> runs) {
        this.runs = runs;
    }

    @Override
    public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
        final StringBuilder depKeys = new StringBuilder();
        for (Run run : runs) {
            String originalName = run.getParent().getFullName();
            String keyName = originalName.replaceAll("[^a-zA-Z0-9]+", "_");
            String prefix = "DEP_" + keyName;
            String number = String.valueOf(run.getNumber());
            String result = run.getResult().toString();
            env.put(prefix + "_BUILD_NAME", originalName);
            env.put(prefix + "_BUILD_NUMBER", number);
            env.put(prefix + "_BUILD_RESULT", result);

            depKeys.append(keyName);
            depKeys.append(" ");
            }

        env.put("DEP_KEYS", depKeys.toString().trim());
    }
}
