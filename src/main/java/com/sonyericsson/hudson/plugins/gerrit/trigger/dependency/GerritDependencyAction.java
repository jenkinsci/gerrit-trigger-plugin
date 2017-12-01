package com.sonyericsson.hudson.plugins.gerrit.trigger.dependency;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentContributingAction;
import hudson.model.InvisibleAction;
import hudson.model.Run;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds Action that stores the data about dependency jobs.
 */
public class GerritDependencyAction extends InvisibleAction implements EnvironmentContributingAction {
    @Nonnull
    private final List<String> deps;

    /**
     * Saves the important information about the parent runs to be persistent.
     * Stores string to keep the data even if original execution of parent job would be removed from history.
     * @param runs List of runs it depend on
     */
    public GerritDependencyAction(List<Run> runs) {
        deps = new ArrayList<String>(runs.size());
        for (Run run : runs) {
            deps.add(run.getParent().getFullName() + "#" + run.getNumber() + "#" + run.getResult());
        }
    }


    /**
     * The same as {@code buildEnvVars}, but for Run. Part of Core API since Jenkins 2.76.
     * See https://issues.jenkins-ci.org/browse/JENKINS-29537 for more details.
     *  @param run The calling build.
     *  @param env Environment variables should be added to this map.
     */
    @SuppressWarnings("unused")
    public void buildEnvironment(@Nonnull Run<?, ?> run, @Nonnull EnvVars env) {
        fillEnv(env);
    }

    @Override
    public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
        fillEnv(env);
    }

    /**
     * Fills the environment variables based on build dependencies.
     * @param env Environment variables should be added to this map.
     */
    private void fillEnv(EnvVars env) {
        final StringBuilder depKeys = new StringBuilder();
        for (String dependency : deps) {
            String[] tokens = dependency.split("#");
            String originalName = tokens[0];
            String number = tokens[1];
            String result = tokens[2];

            String keyName = originalName.replaceAll("[^a-zA-Z0-9]+", "_");
            String prefix = "TRIGGER_" + keyName;

            env.put(prefix + "_BUILD_NAME", originalName);
            env.put(prefix + "_BUILD_NUMBER", number);
            env.put(prefix + "_BUILD_RESULT", result);

            depKeys.append(keyName);
            depKeys.append(" ");
            }

        env.put("TRIGGER_DEPENDENCY_KEYS", depKeys.toString().trim());
    }
}
