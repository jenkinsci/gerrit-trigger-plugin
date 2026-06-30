/*
 *  The MIT License
 *
 *  Copyright 2026 CloudBees, Inc.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.hazelcast;

import hudson.model.Run;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;

/**
 * Optional helper that checks whether a Pipeline build's CPS execution has started.
 * <p>
 * Isolated in its own class so that {@code workflow-api} classes are only loaded when
 * the workflow plugin is present. Callers must guard with a {@code try/catch} for
 * {@link NoClassDefFoundError} or check plugin availability before calling.
 * <p>
 * Interrupting a Pipeline build during CPS initialisation (before any step has started)
 * has no effect — the interrupt flag is silently lost. This helper detects whether the
 * CPS program has advanced past initialisation by checking
 * {@link FlowExecution#getCurrentHeads()}: an empty list means no {@link
 * org.jenkinsci.plugins.workflow.graph.FlowNode} has been created yet, i.e. the pipeline
 * has not started executing steps.
 */
final class PipelineAbortHelper {

    private PipelineAbortHelper() { }

    /**
     * Returns {@code true} if {@code build} is a Pipeline build whose CPS execution has
     * not yet started (i.e. it is still initialising and cannot yet receive an interrupt).
     * <p>
     * Returns {@code false} for non-Pipeline builds or when the flow execution is
     * unavailable, both of which are safe to interrupt immediately.
     *
     * @param build the build to check
     * @return true if the build is a pipeline still initialising
     */
    static boolean isPipelineNotYetStarted(Run<?, ?> build) {
        if (!(build instanceof FlowExecutionOwner.Executable)) {
            return false;
        }
        FlowExecutionOwner owner = ((FlowExecutionOwner.Executable)build).asFlowExecutionOwner();
        if (owner == null) {
            return false;
        }
        FlowExecution execution = owner.getOrNull();
        if (execution == null) {
            // Execution not yet attached — CPS is still initialising
            return true;
        }
        return execution.getCurrentHeads().isEmpty();
    }
}
