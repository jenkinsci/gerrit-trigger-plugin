package com.sonyericsson.hudson.plugins.gerrit.trigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import hudson.Extension;
import hudson.MarkupText;
import hudson.MarkupText.SubText;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.scm.ChangeLogAnnotator;
import hudson.scm.ChangeLogSet.Entry;
import hudson.triggers.Trigger;

import java.util.regex.Pattern;

/**
 * Turns "Change-ID: XXXX" into a hyperlink to Gerrit.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class ChangeIdAnnotator extends ChangeLogAnnotator {
    @Override
    public void annotate(AbstractBuild<?, ?> build, Entry change, MarkupText text) {
        String serverName = GerritTrigger.getTrigger(build.getProject()).getServerName();
        IGerritHudsonTriggerConfig config = PluginImpl.getInstance().getServer(serverName).getConfig();
        annotate(build.getProject(), text, config);
    }

    /**
     * Annotates Gerrit change IDs in changelogs.
     * @param project The project
     * @param text The initial text
     * @param config The Gerrit trigger config
     */
    public void annotate(AbstractProject<?, ?> project, MarkupText text, IGerritHudsonTriggerConfig config) {
        for (SubText token : text.findTokens(CHANGE_ID)) {
            if (!hasGerritTrigger(project)) {
                return; // not configured with Gerrit
            }
            token.href(config.getGerritFrontEndUrl() + "r/" + token.getText());
        }
    }

    /**
     * Does this project have the Gerrit trigger configured?
     * @param project The project
     * @return True if the gerrit trigger is configured.
     */
    private boolean hasGerritTrigger(AbstractProject<?, ?> project) {
        for (Trigger t : project.getTriggers().values()) {
            if (t instanceof GerritTrigger) {
                return true;
            }
        }
        return false;
    }

    private static final Pattern CHANGE_ID = Pattern.compile("(?<=\\bChange-Id: )I[0-9a-fA-F]{40}\\b");
}
