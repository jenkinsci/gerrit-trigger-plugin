/*
 * The MIT License
 *
 * Copyright (c) 2024 Amarula Solutions. All rights reserved.
 *
 * Adapted from the gerrit-code-review-plugin:
 * Copyright (C) 2019 The Android Open Source Project
 * Copyright (C) 2019 SAP SE
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.workflow;

import java.sql.Timestamp;

/**
 * DTO for Gerrit checks REST API input.
 * Maps to the {@code CheckInput} entity in the Gerrit checks plugin.
 *
 * <p>Sent as JSON when posting to {@code /a/changes/.../revisions/.../checks/}.</p>
 *
 * @author Thomas Draebing &lt;thomas.draebing@sap.com&gt;
 */
public class CheckInput {
    /** UUID of the checker. */
    private String checkerUuid;

    /** State of the check (e.g. RUNNING, SUCCESSFUL, FAILED). */
    private String state;

    /** Short message explaining the check state. */
    private String message;

    /** Fully qualified URL to detailed result on the checker's service. */
    private String url;

    /** Date/Time at which the checker started processing this check. */
    private Timestamp started;

    /** Date/Time at which the checker finished processing this check. */
    private Timestamp finished;

    /**
     * Default constructor.
     */
    public CheckInput() {
    }

    /**
     * Constructor with required fields.
     *
     * @param checkerUuid UUID of the checker
     * @param state       state of the check
     * @param message     short message
     * @param url         URL to detailed results
     */
    public CheckInput(String checkerUuid, String state, String message, String url) {
        this.checkerUuid = checkerUuid;
        this.state = state;
        this.message = message;
        this.url = url;
    }

    /**
     * Gets the checker UUID.
     * @return the checker UUID.
     */
    public String getCheckerUuid() {
        return checkerUuid;
    }

    /**
     * Sets the checker UUID.
     * @param checkerUuid the checker UUID.
     */
    public void setCheckerUuid(String checkerUuid) {
        this.checkerUuid = checkerUuid;
    }

    /**
     * Gets the state.
     * @return the state.
     */
    public String getState() {
        return state;
    }

    /**
     * Sets the state.
     * @param state the state.
     */
    public void setState(String state) {
        this.state = state;
    }

    /**
     * Gets the message.
     * @return the message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the message.
     * @param message the message.
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Gets the URL.
     * @return the URL.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the URL.
     * @param url the URL.
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Gets the started timestamp.
     * @return the started timestamp.
     */
    public Timestamp getStarted() {
        return started;
    }

    /**
     * Sets the started timestamp.
     * @param started the started timestamp.
     */
    public void setStarted(Timestamp started) {
        this.started = started;
    }

    /**
     * Gets the finished timestamp.
     * @return the finished timestamp.
     */
    public Timestamp getFinished() {
        return finished;
    }

    /**
     * Sets the finished timestamp.
     * @param finished the finished timestamp.
     */
    public void setFinished(Timestamp finished) {
        this.finished = finished;
    }
}
