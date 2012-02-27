/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier;

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory.MemoryImprint.Entry;
import com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildsStartedStats;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import hudson.model.AbstractBuild;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.TaskListener;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Expands a parameterized string to its full potential.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class ParameterExpander {

    /**
     * How many default parameters there are (plus one) to initialize the size of the parameters-map.
     */
    public static final int DEFAULT_PARAMETERS_COUNT = 11;

    /**
     * The delimiter used to separate build URLs from their messages.
     */
    public static final String MESSAGE_DELIMITER = " : ";

    private static final Logger logger = LoggerFactory.getLogger(ParameterExpander.class);
    private IGerritHudsonTriggerConfig config;
    private Hudson hudson;

    /**
     * Constructor.
     * @param config the global config.
     * @param hudson the Hudson instance.
     */
    public ParameterExpander(IGerritHudsonTriggerConfig config, Hudson hudson) {
        this.config = config;
        this.hudson = hudson;
    }

    /**
     * Constructor.
     * @param config the global config.
     */
    public ParameterExpander(IGerritHudsonTriggerConfig config) {
        this(config, Hudson.getInstance());
    }

    /**
     * Gets the expanded string to send to Gerrit for a build-started event.
     * @param r the build.
     * @param taskListener the taskListener.
     * @param event the event.
     * @param stats the statistics.
     * @return the "expanded" command string.
     */
    public String getBuildStartedCommand(AbstractBuild r, TaskListener taskListener,
            GerritTriggeredEvent event, BuildsStartedStats stats) {

        GerritTrigger trigger = GerritTrigger.getTrigger(r.getProject());
        String gerritCmd = config.getGerritCmdBuildStarted();
        Map<String, String> parameters = createStandardParameters(r, event,
                getBuildStartedCodeReviewValue(r),
                getBuildStartedVerifiedValue(r));
        StringBuilder startedStats = new StringBuilder();
        if (stats.getTotalBuildsToStart() > 1) {
            startedStats.append(stats.toString());
        }
        String buildStartMessage = trigger.getBuildStartMessage();
        if (buildStartMessage != null && !buildStartMessage.equals("")) {
            startedStats.append(" \n ").append(buildStartMessage);
        }
        parameters.put("STARTED_STATS", startedStats.toString());
        return expandParameters(gerritCmd, r, taskListener, parameters);
    }

    /**
     * Finds the verified vote for build started of the specified build.
     * If there is a {@link GerritTrigger} and it has a {@link GerritTrigger#getGerritBuildStartedVerifiedValue()}
     * specified, that value will be used, otherwise the global config value in
     * {@link IGerritHudsonTriggerConfig#getGerritBuildStartedVerifiedValue()} will be used.
     * @param r the build.
     * @return the value.
     */
    private int getBuildStartedVerifiedValue(AbstractBuild r) {
        GerritTrigger trigger = GerritTrigger.getTrigger(r.getProject());
        if (trigger == null) {
            logger.warn("Unable to get trigger config for build {} will use global value.");
            return config.getGerritBuildStartedVerifiedValue();
        } else if (trigger.getGerritBuildStartedVerifiedValue() != null) {
            final Integer value = trigger.getGerritBuildStartedVerifiedValue();
            logger.trace("BuildStartedVerified overridden in project config. returning {}", value);
            return value;
        } else {
            final int value = config.getGerritBuildStartedVerifiedValue();
            logger.trace("BuildStartedVerified standard value used {}", value);
            return value;
        }
    }

    /**
     * Finds the code review vote for build started of the specified build.
     * If there is a {@link GerritTrigger} and it has a {@link GerritTrigger#getGerritBuildStartedCodeReviewValue()}
     * specified, that value will be used, otherwise the global config value in
     * {@link IGerritHudsonTriggerConfig#getGerritBuildStartedCodeReviewValue()} will be used.
     * @param r the build.
     * @return the value.
     */
    private int getBuildStartedCodeReviewValue(AbstractBuild r) {
        GerritTrigger trigger = GerritTrigger.getTrigger(r.getProject());
        if (trigger == null) {
            logger.warn("Unable to get trigger config for build {} will use global value.");
            return config.getGerritBuildStartedCodeReviewValue();
        } else if (trigger.getGerritBuildStartedCodeReviewValue() != null) {
            final Integer value = trigger.getGerritBuildStartedCodeReviewValue();
            logger.trace("BuildStartedCodeReview overridden in project config. returning {}", value);
            return value;
        } else {
            final int value = config.getGerritBuildStartedCodeReviewValue();
            logger.trace("BuildStartedCodeReview standard value used {}", value);
            return value;
        }
    }

    /**
     * Creates a list of the "standard" trigger parameters.
     * They are present both for build started and completed.
     * The parameters are:
     * <ul>
     *  <li><strong>GERRIT_NAME</strong>: The Gerrit project name.</li>
     *  <li><strong>CHANGE_ID</strong>: the Gerrit change-id (SHA-1)</li>
     *  <li><strong>BRANCH</strong>: The branch of the project.</li>
     *  <li><strong>CHANGE</strong>: The change number.</li>
     *  <li><strong>PATCHSET</strong>: The patchset number.</li>
     *  <li><strong>REFSPEC</strong>: the ref-spec. (refs/changes/xx/xxxx/z</li>
     *  <li><strong>BUILDURL</strong>: The URL to the build.</li>
     *  <li><strong>VERIFIED</strong>: The verified vote.</li>
     *  <li><strong>CODE_REVIEW</strong>: The code review vote.</li>
     * </ul>
     * @param r the build.
     * @param event the event.
     * @param codeReview the code review vote.
     * @param verified the verified vote.
     * @return the parameters and their values.
     */
    private Map<String, String> createStandardParameters(AbstractBuild r, GerritTriggeredEvent event,
            int codeReview, int verified) {
        //<GERRIT_NAME> <BRANCH> <CHANGE> <PATCHSET> <REFSPEC> <BUILDURL> VERIFIED CODE_REVIEW
        Map<String, String> map = new HashMap<String, String>(DEFAULT_PARAMETERS_COUNT);
        if (event.getChange() != null) {
            map.put("GERRIT_NAME", event.getChange().getProject());
            map.put("CHANGE_ID", event.getChange().getId());
            map.put("BRANCH", event.getChange().getProject());
            map.put("CHANGE", event.getChange().getNumber());
            map.put("PATCHSET", event.getPatchSet().getNumber());
            map.put("REFSPEC", StringUtil.makeRefSpec(event));
        }
        if (r != null) {
            map.put("BUILDURL", hudson.getRootUrl() + r.getUrl());
        }
        map.put("VERIFIED", String.valueOf(verified));
        map.put("CODE_REVIEW", String.valueOf(codeReview));

        return map;
    }

    /**
     * Expands all types of parameters in the string and returns the "replaced" string.
     * Both types means both $ENV_VARS and &lt;PLUGIN_VARS&gt;
     * @param gerritCommand the command "template"
     * @param r the build containing the environment vars.
     * @param taskListener the taskListener
     * @param parameters the &lt;parameters&gt; from the trigger.
     * @return the expanded string.
     */
    private String expandParameters(String gerritCommand, AbstractBuild r, TaskListener taskListener,
            Map<String, String> parameters) {

        if (r != null && taskListener != null) {
            try {
                gerritCommand = r.getEnvironment(taskListener).expand(gerritCommand);
            } catch (Exception ex) {
                logger.error("Failed to expand env vars into gerrit cmd. Gerrit won't be notified!!", ex);
                return null;
            }
        }

        for (Map.Entry<String, String> param : parameters.entrySet()) {
            gerritCommand = gerritCommand.replace("<" + param.getKey() + ">", param.getValue());
        }

        return gerritCommand;
    }

    /**
     * Finds the code review value for the specified build result on the configured trigger.
     * @param res the build result.
     * @param trigger the trigger that might have overridden values.
     * @return the value.
     */
    protected int getCodeReviewValue(Result res, GerritTrigger trigger) {
        if (res == Result.SUCCESS) {
            if (trigger.getGerritBuildSuccessfulCodeReviewValue() != null) {
                return trigger.getGerritBuildSuccessfulCodeReviewValue();
            } else {
                return config.getGerritBuildSuccessfulCodeReviewValue();
            }
        } else if (res == Result.FAILURE || res == Result.ABORTED) {
            if (trigger.getGerritBuildFailedCodeReviewValue() != null) {
                return trigger.getGerritBuildFailedCodeReviewValue();
            } else {
                return config.getGerritBuildFailedCodeReviewValue();
            }
        } else if (res == Result.UNSTABLE) {
            if (trigger.getGerritBuildUnstableCodeReviewValue() != null) {
                return trigger.getGerritBuildUnstableCodeReviewValue();
            } else {
                return config.getGerritBuildUnstableCodeReviewValue();
            }
        } else {
            //As bad as failue, for now
            if (trigger.getGerritBuildFailedCodeReviewValue() != null) {
                return trigger.getGerritBuildFailedCodeReviewValue();
            } else {
                return config.getGerritBuildFailedCodeReviewValue();
            }
        }
    }

    /**
     * Finds the verified value for the specified build result on the configured trigger.
     * @param res the build result.
     * @param trigger the trigger that might have overridden values.
     * @return the value.
     */
    protected int getVerifiedValue(Result res, GerritTrigger trigger) {
        if (res == Result.SUCCESS) {
            if (trigger.getGerritBuildSuccessfulVerifiedValue() != null) {
                return trigger.getGerritBuildSuccessfulVerifiedValue();
            } else {
                return config.getGerritBuildSuccessfulVerifiedValue();
            }
        } else if (res == Result.FAILURE || res == Result.ABORTED) {
            if (trigger.getGerritBuildFailedVerifiedValue() != null) {
                return trigger.getGerritBuildFailedVerifiedValue();
            } else {
                return config.getGerritBuildFailedVerifiedValue();
            }
        } else if (res == Result.UNSTABLE) {
            if (trigger.getGerritBuildUnstableVerifiedValue() != null) {
                return trigger.getGerritBuildUnstableVerifiedValue();
            } else {
                return config.getGerritBuildUnstableVerifiedValue();
            }
        } else {
            //As bad as failure, for now
            if (trigger.getGerritBuildFailedVerifiedValue() != null) {
                return trigger.getGerritBuildFailedVerifiedValue();
            } else {
                return config.getGerritBuildFailedVerifiedValue();
            }
        }
    }

    /**
     * Returns the minimum verified value for the build results in the memory.
     * @param memoryImprint the memory.
     * @return the lowest verified value.
     */
    protected int getMinimumVerifiedValue(MemoryImprint memoryImprint) {
        int verified = Integer.MAX_VALUE;
        for (Entry entry : memoryImprint.getEntries()) {
            verified = Math.min(verified, getVerifiedValue(
                    entry.getBuild().getResult(),
                    GerritTrigger.getTrigger(entry.getProject())));
        }
        return verified;
    }

    /**
     * Returns the minimum code review value for the build results in the memory.
     * @param memoryImprint the memory
     * @return the lowest code review value.
     */
    protected int getMinimumCodeReviewValue(MemoryImprint memoryImprint) {
        int codeReview = Integer.MAX_VALUE;
        for (Entry entry : memoryImprint.getEntries()) {
            codeReview = Math.min(codeReview, getCodeReviewValue(
                    entry.getBuild().getResult(),
                    GerritTrigger.getTrigger(entry.getProject())));
        }
        return codeReview;
    }

    /**
     * Gets the "expanded" build completed command to send to gerrit.
     * @param memoryImprint the memory with all the information
     * @param listener the taskListener
     * @return the command.
     */
    public String getBuildCompletedCommand(MemoryImprint memoryImprint, TaskListener listener) {
        String command;
        if (memoryImprint.whereAllBuildsSuccessful()) {
            command = config.getGerritCmdBuildSuccessful();
        } else if (memoryImprint.whereAnyBuildsFailed()) {
            command = config.getGerritCmdBuildFailed();
        } else if (memoryImprint.whereAnyBuildsUnstable()) {
            command = config.getGerritCmdBuildUnstable();
        } else {
            //Just as bad as failed for now.
            command = config.getGerritCmdBuildFailed();
        }

        int verified = getMinimumVerifiedValue(memoryImprint);
        int codeReview = getMinimumCodeReviewValue(memoryImprint);

        Map<String, String> parameters = createStandardParameters(null, memoryImprint.getEvent(), codeReview, verified);
        parameters.put("BUILDS_STATS", createBuildsStats(memoryImprint, listener, parameters));


        AbstractBuild build = null;
        Entry[] entries = memoryImprint.getEntries();
        if (entries.length > 0 && entries[0].getBuild() != null) {
            build = entries[0].getBuild();
        }
        return expandParameters(command, build, listener, parameters);
    }

    /**
     * Creates the BUILD_STATS string to send in a message,
     * it contains the status of every build with its URL.
     * @param memoryImprint the memory of all the builds.
     * @param listener the taskListener
     * @param parameters the &lt;parameters&gt; from the trigger.
     * @return the string.
     */
    private String createBuildsStats(MemoryImprint memoryImprint, TaskListener listener,
            Map<String, String> parameters) {
        StringBuilder str = new StringBuilder("");
        final String rootUrl = hudson.getRootUrl();

        Entry[] entries = memoryImprint.getEntries();

        // In Gerrit, all lines before the first empty line are used as the summary.
        // For the summary all single linefeeds will be removed (only in Gerrit, not sent mails).
        // Hence, for the multi-builds, we will add a double linefeed before actually listing
        // the build results.
        if (entries.length > 0) {
            str.append("\n");
            for (Entry entry : entries) {
                AbstractBuild build = entry.getBuild();
                if (build != null) {
                    GerritTrigger trigger = GerritTrigger.getTrigger(build.getProject());
                    Result res = build.getResult();
                    String customMessage = null;

                    // For some reason, Gerrit won't accept command linefeeds without a space.
                    str.append(" \n");
                    if (trigger.getCustomUrl() == null || trigger.getCustomUrl().isEmpty()) {
                        str.append(rootUrl).append(entry.getBuild().getUrl());
                    } else {
                        str.append(expandParameters(trigger.getCustomUrl(), build, listener, parameters));
                    }
                    str.append(MESSAGE_DELIMITER);

                    if (res == Result.SUCCESS) {
                        customMessage = trigger.getBuildSuccessfulMessage();
                    } else if (res == Result.FAILURE || res == Result.ABORTED) {
                        customMessage = trigger.getBuildFailureMessage();
                    } else if (res == Result.UNSTABLE) {
                        customMessage = trigger.getBuildUnstableMessage();
                    } else {
                        customMessage = trigger.getBuildFailureMessage();
                    }

                    // If the user has specified a message, use it
                    // otherwise use a generic indicator
                    if (customMessage == null || customMessage.equals("")) {
                        str.append(res.toString());
                    } else {
                        str.append(customMessage);
                    }
                }
            }
        } else {
            logger.error("I got a request to create build statistics, but no entries where found!");
        }

        return str.toString();
    }
}
