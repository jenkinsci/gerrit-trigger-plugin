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
package com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.BuildMemoryReport

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritManagement
import com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.BuildMemoryReport
import com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.Diagnostics
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent
import com.sonymobile.tools.gerrit.gerritevents.dto.events.RefUpdated
import hudson.model.Job
import hudson.model.Run

import java.text.DateFormat

def l = namespace(lib.LayoutTagLib)

BuildMemoryReport report = my
DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)

l.layout(title: _("Build Coordination - Gerrit Trigger Diagnostics"), norefresh: false, permission: Diagnostics.requiredPermission) {
    l.'side-panel' {
        l.tasks {
            l.task(icon: "images/24x24/up.gif", href: "${rootURL}/${GerritManagement.URL_NAME}/", title: _("Back to Gerrit Management"))
            l.task(icon: "icon-folder icon-md", href: "${rootURL}/${GerritManagement.URL_NAME}/diagnostics", title: _("Back to Diagnostics"))
        }
    }
    l.'main-panel' {
        h1(report.getDisplayName())
        p(style: "font-size: smaller; font-style: italic;", _("blurb"))
        table(class: "sortable pane bigtable") {
            tr {
                th(id: 'hJob', align: "left", _('Job'))
                th(id: 'hRun', align: "left", _('Run #'))
                th(id: 'hCompleted', align: "left", _('Completed'))
                th(id: 'hResult', align: "left", _('Result'))
                th(id: 'hTriggeredTs', align: "left", _('Triggered@'))
                th(id: 'hStartedTs', align: "left", _('Started@'))
                th(id: 'hCompletedTs', align: "left", _('Completed@'))
            }
            report.getSortedEntrySet().each {event ->
                def eventHeaderId = "event${event.key.hashCode()}"
                tr {
                    def display = report.getDisplayNameFor(event.key)
                    th(id: eventHeaderId, colspan: "7", scope: "colgroup", align: "left", display)
                }
                event.value.each {entry ->
                    tr {
                        Job job = entry.project
                        Run run = entry.build
                        td(headers: "hJob ${eventHeaderId}") {
                            if (job != null) {
                                a(href: "${rootURL}/${job.shortUrl}", job.fullDisplayName)
                            } else {
                                raw("&nbsp;")
                            }
                        }
                        td(headers: "hRun ${eventHeaderId}") {
                            if (run != null) {
                                a(href: "${rootURL}/${run.url}", run.displayName)
                            } else {
                                raw("&nbsp;")
                            }
                        }
                        td(headers: "hCompleted ${eventHeaderId}") {
                            if (entry.buildCompleted) {
                                strong(_('Y'))
                            } else {
                                raw('&nbsp;')
                            }
                        }
                        td(headers: "hResult ${eventHeaderId}") {
                            if (run != null && run.result != null) {
                                strong(run.result.toString())
                            } else {
                                raw('&nbsp;')
                            }
                        }
                        td(headers: "hTriggeredTs ${eventHeaderId}",
                                dateFormat.format(new Date(entry.triggeredTimestamp)))
                        td(headers: "hStartedTs ${eventHeaderId}", entry.startedTimestamp != null ?
                                dateFormat.format(new Date(entry.startedTimestamp)) : raw('&nbsp;'))
                        td(headers: "hCompletedTs ${eventHeaderId}", entry.completedTimestamp != null ?
                                dateFormat.format(new Date(entry.completedTimestamp)) : raw('&nbsp;'))
                    }
                }
            }
        }
    }
}
