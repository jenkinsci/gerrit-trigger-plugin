/*
 *  The MIT License
 *
 *  Copyright 2014 Sony Mobile Communications AB. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.TriggerContext;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import jenkins.model.Jenkins;

/**
 * GerritCause indicated that the build was triggered for Gerrit by an upstream build.
 */
public class UpstreamGerritCause extends GerritCause {
    private transient AbstractBuild upstreamBuild;
    private String upstreamBuildParent;
    private int upstreamBuildNumber;

    public UpstreamGerritCause(GerritTriggeredEvent event, boolean silentMode, AbstractBuild upstreamBuild) {
        super(event, silentMode);
        setUpstreamBuild(upstreamBuild);
    }

    public UpstreamGerritCause(GerritTriggeredEvent event, boolean silentMode, TriggerContext context,
                               AbstractBuild upstreamBuild) {
        super(event, silentMode, context);
        setUpstreamBuild(upstreamBuild);
    }

    public UpstreamGerritCause() {
    }

    public AbstractBuild getUpstreamBuild() {
        if (upstreamBuild == null) {
            AbstractProject p = Jenkins.getInstance().getItemByFullName(upstreamBuildParent, AbstractProject.class);
            upstreamBuild = (AbstractBuild) p.getBuildByNumber(upstreamBuildNumber);
        }
        return upstreamBuild;
    }

    void setUpstreamBuild(AbstractBuild upstreamBuild) {
        this.upstreamBuild = upstreamBuild;
        this.upstreamBuildParent = upstreamBuild.getParent().getFullName();
        this.upstreamBuildNumber = upstreamBuild.getNumber();
    }
}
