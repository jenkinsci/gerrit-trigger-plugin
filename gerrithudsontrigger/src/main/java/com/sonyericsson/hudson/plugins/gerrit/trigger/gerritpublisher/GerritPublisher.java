package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritpublisher;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.Set;

/**
 * Adds reviewers to the Gerrit change that triggered the build.
 * It is actually only used as a container, since we want to add
 * reviewers per Gerrit change and not per job.
 *
 * @author Emanuele Zattin
 */
public class GerritPublisher extends Notifier {

    private String reviewers;
    private String ownersFileName;

    @DataBoundConstructor
    public GerritPublisher(String reviewers, String ownersFileName) {
        Preconditions.checkNotNull(reviewers);
        Preconditions.checkNotNull(ownersFileName);
        this.reviewers = reviewers;
        this.ownersFileName = ownersFileName;
    }

    public String getReviewers() {
        return reviewers;
    }

    public void setReviewers(String reviewers) {
        this.reviewers = reviewers;
    }

    public String getOwnersFileName() {
        return ownersFileName;
    }

    public void setOwnersFileName(String ownersFileName) {
        this.ownersFileName = ownersFileName;
    }

    /**
     * @return a Set containing the reviewers. It splits on commas and trims the results.
     */
    public Set<String> getReviewersAsSet() {
        Preconditions.checkNotNull(reviewers);
        return Sets.newHashSet(
            Splitter.on(",")
                    .omitEmptyStrings()
                    .trimResults()
                    .split(reviewers));
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        return true;
    }

    @Extension
    public static final class Descriptor extends BuildStepDescriptor<Publisher> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Add Gerrit Reviewers";
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new GerritPublisher(
                Strings.nullToEmpty(formData.getString("reviewers")),
                Strings.nullToEmpty(formData.getString("ownersFileName"))
            );
        }
    }
}
