package com.sonyericsson.hudson.plugins.gerrit.trigger;

import hudson.Extension;
import hudson.MarkupText;
import hudson.MarkupText.SubText;
import hudson.model.Run;
import hudson.scm.ChangeLogAnnotator;
import hudson.scm.ChangeLogSet.Entry;

import java.util.regex.Pattern;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;

/**
 * Turns "Change-ID: XXXX" into a hyperlink to Gerrit.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class ChangeIdAnnotator extends ChangeLogAnnotator {
    @Override
    public void annotate(Run<?, ?> build, Entry change, MarkupText text) {
        for (SubText token : text.findTokens(CHANGE_ID)) {
            GerritCause gerritCause = build.getCause(GerritCause.class);
            if (gerritCause != null
                && gerritCause.getEvent() != null
                && gerritCause.getEvent().getProvider() != null
                && gerritCause.getEvent().getProvider().getUrl() != null
                && !gerritCause.getEvent().getProvider().getUrl().trim().isEmpty()) {
                token.href(gerritCause.getEvent().getProvider().getUrl() + "r/" + token.getText());
            }
        }
    }

    private static final Pattern CHANGE_ID = Pattern.compile("(?<=\\bChange-Id: )I[0-9a-fA-F]{40}\\b");
}
