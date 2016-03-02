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
package com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.EventListenersReport

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritManagement
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl
import com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.Diagnostics
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.EventListener
import com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.EventListenersReport
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger
import hudson.model.Job

EventListenersReport report = my;

def l = namespace(lib.LayoutTagLib)

l.layout(title: _("${report.getDisplayName()} - Gerrit Trigger Diagnostics"), norefresh: false, permission: Diagnostics.requiredPermission) {
    l.header {
        style {
            raw("""
.compare-type {
    font-size: smaller;
    font-style: italic;
}
.displayName {
    font-size: larger;
    font-weight: strong;
}
.compare-type:after {
    content: ":  "
}
.branches {
    list-style: none;
    margin: 2px;
    margin-left: 0;
    padding-left: 1em;

 }
.branches:before {
    content: attr(title);
    font-size: smaller;
    padding-right: 8px;
}
.branches li {
    display: inline;
    border: 1px solid silver;
    margin-left: 2px;
    margin-right: 2px;
    padding-left: 2px;
    padding-right: 2px;
}
""")
        }
    }

    l.'side-panel' {
        l.tasks {
            l.task(icon: "images/24x24/up.gif", href: "${rootURL}/${GerritManagement.URL_NAME}/", title: _("Back to Gerrit Management"))
            l.task(icon: "icon-folder icon-md", href: "${rootURL}/${GerritManagement.URL_NAME}/diagnostics", title: _("Back to Diagnostics"))
        }
    }
    l.'main-panel' {
        h1(report.getDisplayName())
        p(style: "font-size: smaller; font-style: italic;", _("blurb"))
        h3(_("Jobs"))
        table(class: "sortable pane bigtable") {
            tr {
                th(_("Job"))
                th(_("Triggers on"))
                th(_("Server"))
                th(_("Silent/Start"))
                th(_("Types"))
            }
            report.jobs.each { EventListener ev ->
                Job job = ev.findJob()
                GerritTrigger trigger = ev.getTrigger()
                tr {
                    td {
                        if (job != null) {
                            a(href: "${rootURL}/${job.shortUrl}", job.getFullDisplayName())
                        } else {
                            span(_("_unknown"))
                        }
                    }
                    if (trigger != null) {
                        td {
                            ul(class: 'interesting-projects') {
                                trigger.gerritProjects.take(4).each { def proj ->
                                    li {
                                        span(class: 'compare-type', proj.compareType.displayName)
                                        span(class: 'displayName', proj.pattern)
                                        ul(class: 'branches', title: _("Branches")) {
                                            proj.branches.take(4).each { def branch ->
                                                li {
                                                    span(class: 'compare-type', branch.compareType.displayName)
                                                    span(branch.pattern)
                                                }
                                            }
                                            if (proj.branches.size() > 4) {
                                                li("...")
                                            }
                                        }
                                    }
                                }
                                if(trigger.gerritProjects.size() > 4) {
                                    li("...")
                                }
                            }
                        }
                        td {
                            def gerritServer = PluginImpl.getServer_(trigger.serverName)
                            if (gerritServer != null) {
                                span(gerritServer.displayName)
                            } else {
                                span(trigger.serverName)
                            }
                        }
                        td(align: "center", valign: 'middle', _("_silent", trigger.silentMode ? _("_Y") : "", trigger.silentStartMode ? _("_Y") : ""))
                        td {
                            ul {
                                trigger.triggerOnEvents.each {def onEvent ->
                                    li(onEvent.descriptor.displayName)
                                }
                            }
                        }
                    } else {
                        td { raw("&nbsp;") }
                        td { raw("&nbsp;") }
                        td { raw("&nbsp;") }
                        td { raw("&nbsp;") }
                        td { raw("&nbsp;") }
                    }
                }
            }
        }
        h3(_("Others/Built In"))
        table(class: "sortable pane bigtable") {
            report.others.each {def listener ->
                tr {
                    td(report.getName(listener))
                }
            }
        }
    }
}