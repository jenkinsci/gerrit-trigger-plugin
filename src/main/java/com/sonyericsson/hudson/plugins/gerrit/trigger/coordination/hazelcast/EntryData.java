/*
 * The MIT License
 *
 *  Copyright 2026 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.hazelcast;

import edu.umd.cs.findbugs.annotations.CheckForNull;

/**
 * Serializable data for BuildMemory Entry.
 * <p>
 * Stores job and build information without Jenkins object references.
 * Uses Compact Serialization for cross-JVM compatibility.
 *
 */
public class EntryData {

    private String projectFullName;
    private String buildId;
    private boolean buildCompleted;
    private boolean cancelled;
    private String customUrl;
    private String unsuccessfulMessage;
    private long triggeredTimestamp;
    private Long completedTimestamp;
    private Long startedTimestamp;

    /**
     * Default constructor.
     */
    public EntryData() {
        this.triggeredTimestamp = System.currentTimeMillis();
    }

    /**
     * Constructor with parameters.
     *
     * @param projectFullName full job name
     * @param buildId build identifier
     * @param buildCompleted whether build is completed
     */
    public EntryData(String projectFullName, String buildId, boolean buildCompleted) {
        this.projectFullName = projectFullName;
        this.buildId = buildId;
        this.buildCompleted = buildCompleted;
        this.triggeredTimestamp = System.currentTimeMillis();
    }

    /**
     * Gets the project full name.
     *
     * @return project full name
     */
    public String getProjectFullName() {
        return projectFullName;
    }

    /**
     * Sets the project full name.
     *
     * @param projectFullName project full name
     */
    public void setProjectFullName(String projectFullName) {
        this.projectFullName = projectFullName;
    }

    /**
     * Gets the build ID.
     *
     * @return build ID
     */
    @CheckForNull
    public String getBuildId() {
        return buildId;
    }

    /**
     * Sets the build ID.
     *
     * @param buildId build ID
     */
    public void setBuildId(String buildId) {
        this.buildId = buildId;
        if (buildId != null && startedTimestamp == null) {
            this.startedTimestamp = System.currentTimeMillis();
        }
    }

    /**
     * Checks if build is completed.
     *
     * @return true if completed
     */
    public boolean isBuildCompleted() {
        return buildCompleted;
    }

    /**
     * Sets build completed status.
     *
     * @param buildCompleted completed status
     */
    public void setBuildCompleted(boolean buildCompleted) {
        this.buildCompleted = buildCompleted;
        if (buildCompleted && completedTimestamp == null) {
            this.completedTimestamp = System.currentTimeMillis();
        }
    }

    /**
     * Checks if build was cancelled.
     *
     * @return true if cancelled
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Sets cancelled status.
     *
     * @param cancelled cancelled status
     */
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /**
     * Gets custom URL.
     *
     * @return custom URL
     */
    @CheckForNull
    public String getCustomUrl() {
        return customUrl;
    }

    /**
     * Sets custom URL.
     *
     * @param customUrl custom URL
     */
    public void setCustomUrl(String customUrl) {
        this.customUrl = customUrl;
    }

    /**
     * Gets unsuccessful message.
     *
     * @return unsuccessful message
     */
    @CheckForNull
    public String getUnsuccessfulMessage() {
        return unsuccessfulMessage;
    }

    /**
     * Sets unsuccessful message.
     *
     * @param unsuccessfulMessage unsuccessful message
     */
    public void setUnsuccessfulMessage(String unsuccessfulMessage) {
        this.unsuccessfulMessage = unsuccessfulMessage;
    }

    /**
     * Gets triggered timestamp.
     *
     * @return triggered timestamp
     */
    public long getTriggeredTimestamp() {
        return triggeredTimestamp;
    }

    /**
     * Sets triggered timestamp.
     *
     * @param triggeredTimestamp triggered timestamp
     */
    public void setTriggeredTimestamp(long triggeredTimestamp) {
        this.triggeredTimestamp = triggeredTimestamp;
    }

    /**
     * Gets completed timestamp.
     *
     * @return completed timestamp
     */
    @CheckForNull
    public Long getCompletedTimestamp() {
        return completedTimestamp;
    }

    /**
     * Sets completed timestamp.
     *
     * @param completedTimestamp completed timestamp
     */
    public void setCompletedTimestamp(Long completedTimestamp) {
        this.completedTimestamp = completedTimestamp;
    }

    /**
     * Gets started timestamp.
     *
     * @return started timestamp
     */
    @CheckForNull
    public Long getStartedTimestamp() {
        return startedTimestamp;
    }

    /**
     * Sets started timestamp.
     *
     * @param startedTimestamp started timestamp
     */
    public void setStartedTimestamp(Long startedTimestamp) {
        this.startedTimestamp = startedTimestamp;
    }
}
