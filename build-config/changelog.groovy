#!/usr/bin/env groovy
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

/*
 A hacked together script that generates a changelog (as markdown) with the format:

     JENKINS-xxxx - Title of pull request _(link to pr)_

 Since the last commit from maven-release-plugin
 */

 import groovy.json.JsonSlurper
 import groovy.transform.Field

 import java.util.concurrent.TimeUnit


@Field
def mvnInfo = fetchMavenInfo()
@Field
def prInfos = []
@Field
StringBuilder changelogEntry = new StringBuilder("")

String[] changelog = "git log --oneline --after=1year".execute().text.split("\n")
for(String commit : changelog) {
    def m = commit =~ /([0-9a-f]+)\s+Merge pull request #(\d+).*/
    if (m.find()) {
        String sha1 = m.group(1)
        int pr = Integer.valueOf(m.group(2))
        println("${sha1} -> ${pr}")
        def prInfo = fetchPr(pr)
        prInfos << prInfo
        changelogEntry.append("\n")
        changelogEntry.append(generateChangeLogEntry(prInfo))
    } else if (commit.contains("[maven-release-plugin]")) {
        break
    }
}

println(changelogEntry.toString())

String generateChangeLogEntry(def prInfo) {
    StringBuilder issues = new StringBuilder("")
    prInfo.issues.each { def issue ->
        if (issues.length() > 0) {
            issues.append(", ")
        }
        issues.append("[${issue.id}](${issue.url})")
    }
    String txt = ""
    if (issues.length() > 0) {
        txt = "* ${issues} -"
    } else {
        txt = "*"
    }

    return "${txt} ${prInfo.cleanTitle} _([PR #${prInfo.number}](${prInfo.url}))_"
}

/**
 * Performs an API call to github to fetch the info about a particular PR on the repository
 *
 * @param pr the pr number
 * @return a map with relevant information
 */
def fetchPr(int pr) {
    def json = new JsonSlurper().parseText(new URL(mvnInfo.github.pr + pr).text)
    def m = [:]
    m.url = json.html_url
    m.number = json.number
    m.title = json.title.toString()
    m.body = json.body.toString()

    def issues = []
    issues.addAll(findIssues(m.title))
    issues.addAll(findIssues(m.body))
    m.issues = issues

    m.cleanTitle = clearIssues(m.title).trim()
    m.cleanBody = clearIssues(m.body).trim()

    return m
}

/**
 * Removes any strings of the form "[JENKINS-xxxx]" or "JENKINS-xxxx" from the string
 * @param txt the string to clean
 * @return a string with the matches removed
 */
String clearIssues(String txt) {
    txt = txt.replaceAll(~/\[JENKINS-\d+\]/, "")
    return txt.replaceAll(~/JENKINS-\d+/, "")
}

/**
 * Finds all "JENKINS-xxxx" strings in the text
 *
 * @param text the text to find issue bumbers in
 * @return a list of maps with the keys "id" and "url"
 */
def findIssues(String text) {
    return text.findAll(~/JENKINS-\d+/).collect { String num ->
        def n = [:]
        n.id = num
        n.url = "https://issues.jenkins-ci.org/browse/" + num
        return n
    }
}

/**
 * Generates an effective-pom of the maven project in the current working directory and picks some relevant data.
 *
 * @return a map with the relevant data
 */
def fetchMavenInfo() {
    File ep = File.createTempFile("pom", ".xml");
    ep.deleteOnExit()
    def process = "mvn help:effective-pom -Doutput=${ep.absolutePath}".execute()
    if (!process.waitFor(10, TimeUnit.SECONDS)) {
        println "Cannot get effective pom in a timely manner."
        return null
    }
    def xml = new XmlSlurper().parse(ep)
    def m = [:]
    m.groupId = xml.groupId
    m.artifactId = xml.artifactId
    m.version = xml.version
    m.scmUrl = xml.scm.url.toString()
    int pos = m.scmUrl.indexOf("github.com/")
    if (pos > 0) {
        String[] rest = m.scmUrl.substring(pos + 11).trim().split('/')
        m.github = [:]
        m.github.org = rest[0]
        m.github.repo = rest[1]
        m.github.pr = "https://api.github.com/repos/${m.github.org}/${m.github.repo}/pulls/"
    } else {
        throw new IllegalStateException("Malformed scm url")
    }
    return m
}
