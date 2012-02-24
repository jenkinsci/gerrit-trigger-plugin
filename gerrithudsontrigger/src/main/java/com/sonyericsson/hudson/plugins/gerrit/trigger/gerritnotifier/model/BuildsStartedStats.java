/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;

/**
 * Bean for holding statistics of started builds for a specific event.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class BuildsStartedStats {
    private GerritTriggeredEvent event;
    private int totalBuildsToStart;
    private int startedBuilds;

    /**
     * The Constructor.
     * @param event the event that started the build(s).
     * @param totalBuildsToStart the total amount of builds that have been triggered.
     * @param startedBuilds the amount of builds that have been started so far.
     */
    public BuildsStartedStats(GerritTriggeredEvent event, int totalBuildsToStart, int startedBuilds) {
        this.event = event;
        this.totalBuildsToStart = totalBuildsToStart;
        this.startedBuilds = startedBuilds;
    }

    /**
     * The event that started the build(s).
     * @return the event that started the build(s)
     */
    public GerritTriggeredEvent getEvent() {
        return event;
    }

    /**
     * The event that started the build(s).
     * @param event the event that started the build(s).
     */
    public void setEvent(GerritTriggeredEvent event) {
        this.event = event;
    }

    /**
     * The amount of builds that have been started so far.
     * @return the amount of builds that have been started so far.
     */
    public int getStartedBuilds() {
        return startedBuilds;
    }

    /**
     * The amount of builds that have been started so far.
     * @param startedBuilds the amount of builds that have been started so far.
     */
    void setStartedBuilds(int startedBuilds) {
        this.startedBuilds = startedBuilds;
    }

    /**
     * The total amount of builds that have been triggered.
     * @return the total amount of builds that have been triggered.
     */
    public int getTotalBuildsToStart() {
        return totalBuildsToStart;
    }

    /**
     * The total amount of builds that have been triggered.
     * @param totalBuildsToStart the total amount of builds that have been triggered.
     */
    void setTotalBuildsToStart(int totalBuildsToStart) {
        this.totalBuildsToStart = totalBuildsToStart;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("(");
        str.append(getStartedBuilds()).append("/").append(getTotalBuildsToStart()).append(")");
        return str.toString();
    }


}
