package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import java.util.Objects;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Gerrit label model for Gerrit Trigger.
 *
 * @author George Cimpoies &lt;george.cimpoies@itiviti.com&gt;
 */
public class LabelValue implements Describable<LabelValue> {
    private String name;
    private Integer buildStartedVoteValue;
    private Integer buildSuccessfulVoteValue;
    private Integer buildFailedVoteValue;
    private Integer buildUnstableVoteValue;
    private Integer buildNotBuiltVoteValue;

    /**
     * Default constructor.
     */
    public LabelValue() { }

    /**
     * Name-based constructor with default votes.
     *
     * @param name name of the LabelValue.
     */
    public LabelValue(String name) {
        this.name = name;
        this.buildFailedVoteValue = 0;
        this.buildNotBuiltVoteValue = 0;
        this.buildStartedVoteValue = 0;
        this.buildUnstableVoteValue = 0;
        this.buildSuccessfulVoteValue = 0;
    }

    /**
     * Constructor for LabelValue.
     *
     * @param name name of LabelValue.
     * @param buildStartedVoteValue Build started vote value of LabelValue
     * @param buildSuccessfulVoteValue Build successful vote value of LabelValue
     * @param buildFailedVoteValue Build failed vote value of LabelValue
     * @param buildUnstableVoteValue Build unstable vote value of LabelValue
     * @param buildNotBuiltVoteValue Build not built vote value of LabelValue
     */
    @DataBoundConstructor
    public LabelValue(String name,
        Integer buildStartedVoteValue,
        Integer buildSuccessfulVoteValue,
        Integer buildFailedVoteValue,
        Integer buildUnstableVoteValue,
        Integer buildNotBuiltVoteValue) {
        this.name = name;
        this.buildStartedVoteValue = buildStartedVoteValue;
        this.buildSuccessfulVoteValue = buildSuccessfulVoteValue;
        this.buildFailedVoteValue = buildFailedVoteValue;
        this.buildUnstableVoteValue = buildUnstableVoteValue;
        this.buildNotBuiltVoteValue = buildNotBuiltVoteValue;
    }

    /**
     * Standard getter for name of LabelValue.
     *
     * @return name of the LabelValue.
     */
    public String getName() {
        return name;
    }

    /**
     * Standard setter for name of LabelValue.
     *
     * @param name of the LabelValue.
     */
    @DataBoundSetter
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Standard getter for name of LabelValue.
     *
     * @return name of the LabelValue.
     */
    public Integer getBuildStartedVoteValue() {
        return buildStartedVoteValue;
    }

    /**
     * Standard setter for buildStartedVoteValue of LabelValue.
     *
     * @param buildStartedVoteValue of the LabelValue.
     */
    @DataBoundSetter
    public void setBuildStartedVoteValue(Integer buildStartedVoteValue) {
        this.buildStartedVoteValue = buildStartedVoteValue;
    }

    /**
     * Standard getter for buildSuccessfulVoteValue of LabelValue.
     *
     * @return buildSuccessfulVoteValue of the LabelValue.
     */
    public Integer getBuildSuccessfulVoteValue() {
        return buildSuccessfulVoteValue;
    }

    /**
     * Standard setter for buildSuccessfulVoteValue of LabelValue.
     *
     * @param buildSuccessfulVoteValue of the LabelValue.
     */
    @DataBoundSetter
    public void setBuildSuccessfulVoteValue(Integer buildSuccessfulVoteValue) {
        this.buildSuccessfulVoteValue = buildSuccessfulVoteValue;
    }

    /**
     * Standard getter for buildFailedVoteValue of LabelValue.
     *
     * @return buildFailedVoteValue of the LabelValue.
     */
    public Integer getBuildFailedVoteValue() {
        return buildFailedVoteValue;
    }

    /**
     * Standard setter for buildFailedVoteValue of LabelValue.
     *
     * @param buildFailedVoteValue of the LabelValue.
     */
    @DataBoundSetter
    public void setBuildFailedVoteValue(Integer buildFailedVoteValue) {
        this.buildFailedVoteValue = buildFailedVoteValue;
    }

    /**
     * Standard getter for buildUnstableVoteValue of LabelValue.
     *
     * @return buildUnstableVoteValue of the LabelValue.
     */
    public Integer getBuildUnstableVoteValue() {
        return buildUnstableVoteValue;
    }

    /**
     * Standard setter for buildUnstableVoteValue of LabelValue.
     *
     * @param buildUnstableVoteValue of the LabelValue.
     */
    @DataBoundSetter
    public void setBuildUnstableVoteValue(Integer buildUnstableVoteValue) {
        this.buildUnstableVoteValue = buildUnstableVoteValue;
    }

    /**
     * Standard getter for buildNotBuiltVoteValue of LabelValue.
     *
     * @return buildNotBuiltVoteValue of the LabelValue.
     */
    public Integer getBuildNotBuiltVoteValue() {
        return buildNotBuiltVoteValue;
    }

    /**
     * Standard setter for buildNotBuiltVoteValue of LabelValue.
     *
     * @param buildNotBuiltVoteValue of the LabelValue.
     */
    @DataBoundSetter
    public void setBuildNotBuiltVoteValue(Integer buildNotBuiltVoteValue) {
        this.buildNotBuiltVoteValue = buildNotBuiltVoteValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LabelValue that = (LabelValue)o;

        return name.equals(that.name);
    }

    @Override public int hashCode() {
        return Objects.hash(name,
            buildStartedVoteValue,
            buildSuccessfulVoteValue,
            buildFailedVoteValue,
            buildUnstableVoteValue,
            buildNotBuiltVoteValue);
    }

    @Override
    public Descriptor<LabelValue> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
    }

    /**
     * The Descriptor for a LabelValue.
     */
    @Extension
    public static final class DescriptorImpl extends Descriptor<LabelValue> {
        @Override
        public String getDisplayName() {
            return "Label value";
        }

    }
}
