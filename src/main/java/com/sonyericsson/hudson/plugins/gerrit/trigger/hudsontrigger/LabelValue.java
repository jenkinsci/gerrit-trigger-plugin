package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class LabelValue implements Describable<LabelValue> {
    private String name;
    private Integer buildStartedVoteValue;
    private Integer buildSuccessfulVoteValue;
    private Integer buildFailedVoteValue;
    private Integer buildUnstableVoteValue;
    private Integer buildNotBuiltVoteValue;

    public LabelValue(){}

    public LabelValue(String name) {
        this.name = name;
        this.buildFailedVoteValue = 0;
        this.buildNotBuiltVoteValue = 0;
        this.buildStartedVoteValue = 0;
        this.buildUnstableVoteValue = 0;
        this.buildSuccessfulVoteValue = 0;
    }

    @DataBoundConstructor
    public LabelValue(String name, Integer buildStartedVoteValue, Integer buildSuccessfulVoteValue, Integer buildFailedVoteValue, Integer buildUnstableVoteValue, Integer buildNotBuiltVoteValue) {
        this.name = name;
        this.buildStartedVoteValue = buildStartedVoteValue;
        this.buildSuccessfulVoteValue = buildSuccessfulVoteValue;
        
        this.buildFailedVoteValue = buildFailedVoteValue;
        this.buildUnstableVoteValue = buildUnstableVoteValue;
        this.buildNotBuiltVoteValue = buildNotBuiltVoteValue;
    }

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = name;
    }

    public Integer getBuildStartedVoteValue() {
        return buildStartedVoteValue;
    }

    @DataBoundSetter
    public void setBuildStartedVoteValue(Integer buildStartedVoteValue) {
        this.buildStartedVoteValue = buildStartedVoteValue;
    }

    public Integer getBuildSuccessfulVoteValue() {
        return buildSuccessfulVoteValue;
    }

    @DataBoundSetter
    public void setBuildSuccessfulVoteValue(Integer buildSuccessfulVoteValue) {
        this.buildSuccessfulVoteValue = buildSuccessfulVoteValue;
    }

    public Integer getBuildFailedVoteValue() {
        return buildFailedVoteValue;
    }

    @DataBoundSetter
    public void setBuildFailedVoteValue(Integer buildFailedVoteValue) {
        this.buildFailedVoteValue = buildFailedVoteValue;
    }

    public Integer getBuildUnstableVoteValue() {
        return buildUnstableVoteValue;
    }

    @DataBoundSetter
    public void setBuildUnstableVoteValue(Integer buildUnstableVoteValue) {
        this.buildUnstableVoteValue = buildUnstableVoteValue;
    }

    public Integer getBuildNotBuiltVoteValue() {
        return buildNotBuiltVoteValue;
    }

    @DataBoundSetter
    public void setBuildNotBuiltVoteValue(Integer buildNotBuiltVoteValue) {
        this.buildNotBuiltVoteValue = buildNotBuiltVoteValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LabelValue that = (LabelValue) o;

        return name.equals(that.name);
    }

    @Override
    public Descriptor<LabelValue> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<LabelValue> {
        @Override
        public String getDisplayName() {
            return "Label value";
        }

    }
}
