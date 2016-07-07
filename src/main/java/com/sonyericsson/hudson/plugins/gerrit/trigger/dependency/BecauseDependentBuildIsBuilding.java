/*
 * The MIT License
 *
 * Copyright 2014 Smartmatic International Corporation. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.dependency;

import hudson.model.Job;
import hudson.model.queue.CauseOfBlockage;

import java.util.List;

import com.google.common.base.Joiner;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;

/**
 * Build is blocked because it depends on a currently building job.
 * @author Yannick Bréhon &lt;yannick.brehon@smartmatic.com&gt;
 */
public class BecauseDependentBuildIsBuilding extends CauseOfBlockage {

    private List<Job> blockingProjects;

    /**
     * Standard constructor.
     * @param blockingProjects The list of dependant builds which are blocking this one.
     */
    public BecauseDependentBuildIsBuilding(List<Job> blockingProjects) {
        this.blockingProjects = blockingProjects;
    }

    @Override
    public String getShortDescription() {
        return Messages.DependentBuildIsBuilding(Joiner.on(", ").join(blockingProjects));
    }
}
