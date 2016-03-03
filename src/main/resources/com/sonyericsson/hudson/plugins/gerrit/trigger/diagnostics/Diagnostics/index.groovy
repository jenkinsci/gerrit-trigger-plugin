/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 CloudBees Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.Diagnostics

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritManagement
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages
import com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.Diagnostics

Diagnostics diag = my

def l = namespace(lib.LayoutTagLib)

l.layout(title: _("Gerrit Trigger Diagnostics"), norefresh: false, permission: Diagnostics.requiredPermission) {
    l.'side-panel' {
        l.tasks {
            l.task(icon: "images/24x24/up.gif", href: "${rootURL}/${GerritManagement.URL_NAME}/", title: _("Back to Gerrit Management"))
            l.task(icon: "icon-clipboard icon-md", href: "buildMemory", title: Messages.BuildMemoryReport_DisplayName())
            l.task(icon: "icon-clipboard icon-md", href: "eventListeners", title: Messages.EventListenersReport_DisplayName())
            if (diag.isDebugMode()) {
                l.task(icon: "icon-warning icon-md", href: "triggerDebugEvent", title: "Trigger Event", post: false, requiresConfirmation: true)
            }
        }
    }
    l.'main-panel' {
        h1(_("Gerrit Trigger Diagnostics"))
        p(_("blurb"))
    }
}
