/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2013 Sony Mobile Communications AB. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.trigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.rebuild.RebuildValidator;
import hudson.Extension;
import hudson.model.Run;

/**
 * Disables the rebuild button for Gerrit triggered builds.
 * This plugin provides its own "rebuild" functionality.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.actions.RetriggerAction
 */
@Extension(optional = true)
public class GerritRebuildValidator extends RebuildValidator {
    private static final long serialVersionUID = 2704238052581467905L;

    @Override
    public boolean isApplicable(Run build) {
        // Build#getActions is deprecated but the only way to prevent a stack overflow here
        return build.getActions().stream().anyMatch(it -> it instanceof GerritCause);
    }

}
