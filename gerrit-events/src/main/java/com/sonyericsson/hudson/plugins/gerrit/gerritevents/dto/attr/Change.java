/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritJsonDTO;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritJsonEventFactory.getString;
import net.sf.json.JSONObject;

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PROJECT;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.BRANCH;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.ID;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.NUMBER;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.SUBJECT;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.OWNER;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.URL;

/**
 * Represents a Gerrit JSON Change DTO.
 * The Gerrit change the event is related to.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class Change implements GerritJsonDTO {

    /**
     * Project path in Gerrit.
     */
    private String project;
    /**
     * Branch name within project.
     */
    private String branch;
    /**
     * Change identifier.
     */
    private String id;
    /**
     * Change number (deprecated).
     */
    private String number;
    /**
     * Description of change.
     */
    private String subject;
    /**
     * Owner in account attribute.
     */
    private Account owner;
    /**
     * Canonical URL to reach this change.
     */
    private String url;

    /**
     * Default constructor.
     */
    public Change() {
    }

    /**
     * Constructor that fills with data directly.
     * @param json the JSON Object with corresponding data.
     * @see #fromJson(net.sf.json.JSONObject)
     */
    public Change(JSONObject json) {
        this.fromJson(json);
    }

    @Override
    public void fromJson(JSONObject json) {
        project = getString(json, PROJECT);
        branch = getString(json, BRANCH);
        id = getString(json, ID);
        number = getString(json, NUMBER);
        subject = getString(json, SUBJECT);
        if (json.containsKey(OWNER)) {
            owner = new Account(json.getJSONObject(OWNER));
        }
        url = getString(json, URL);
    }

    /**
     * Branch name within project.
     * @return the branch.
     */
    public String getBranch() {
        return branch;
    }

    /**
     * Branch name within project.
     * @param branch the branch.
     */
    public void setBranch(String branch) {
        this.branch = branch;
    }

    /**
     * Change identifier.
     * @return the identifier.
     */
    public String getId() {
        return id;
    }

    /**
     * Change identifier.
     * @param id the identifier.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Change number (deprecated).
     * @return the change number.
     * @deprecated because the Gerrit documentation says so.
     */
    public String getNumber() {
        return number;
    }

    /**
     * Change number (deprecated).
     * @param number the change number.
     * @deprecated because the Gerrit documentation says so.
     */
    public void setNumber(String number) {
        this.number = number;
    }

    /**
     * Owner in account attribute.
     * @return the owner.
     */
    public Account getOwner() {
        return owner;
    }

    /**
     * Owner in account attribute.
     * @param owner the owner.
     */
    public void setOwner(Account owner) {
        this.owner = owner;
    }

    /**
     * Project path in Gerrit.
     * @return the project.
     */
    public String getProject() {
        return project;
    }

    /**
     * Project path in Gerrit.
     * @param project the project.
     */
    public void setProject(String project) {
        this.project = project;
    }

    /**
     * Description of change.
     * @return the description.
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Description of change.
     * @param subject the description.
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * Canonical URL to reach this change.
     * @return the URL.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Canonical URL to reach this change.
     * @param url the URL.
     */
    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public boolean equals(Object obj) {
        //CS IGNORE MagicNumber FOR NEXT 14 LINES. REASON: Autogenerated Code.
        //CS IGNORE AvoidInlineConditionals FOR NEXT 14 LINES. REASON: Autogenerated Code.
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Change other = (Change)obj;
        if ((this.project == null) ? (other.project != null) : !this.project.equals(other.project)) {
            return false;
        }
        if ((this.branch == null) ? (other.branch != null) : !this.branch.equals(other.branch)) {
            return false;
        }
        if ((this.number == null) ? (other.number != null) : !this.number.equals(other.number)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        //CS IGNORE MagicNumber FOR NEXT 5 LINES. REASON: Autogenerated Code.
        //CS IGNORE AvoidInlineConditionals FOR NEXT 5 LINES. REASON: Autogenerated Code.
        int hash = 7;
        hash = 29 * hash + (this.project != null ? this.project.hashCode() : 0);
        hash = 29 * hash + (this.branch != null ? this.branch.hashCode() : 0);
        hash = 29 * hash + (this.number != null ? this.number.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        return "Change: " + getNumber();
    }

    /**
     * Returns change's info in string format.
     * @param preText the text before change info.
     * @return change info.
     */
    public String getChangeInfo(String preText) {
        StringBuilder s = new StringBuilder();
        s.append(preText + "\n");
        s.append("Subject: " + getSubject() + "\n");
        s.append("Project: " + getProject() + "  " + getBranch() + "  " + getId() + "\n");
        s.append("Link:    " + getUrl() + "\n");
        return s.toString();
    }
}
