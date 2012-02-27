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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto;

/**
 * Contains constants that represent the names of Gerrit Event JSON properties.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public abstract class GerritEventKeys {
    /**
     * abandoner.
     */
    public static final String ABANDONER = "abandoner";
    /**
     * change.
     */
    public static final String CHANGE = "change";
    /**
     * patchset.
     */
    public static final String PATCHSET = "patchset";
    /**
     * patchSet.
     */
    public static final String PATCH_SET = "patchSet";

    /**
     * email.
     */
    public static final String EMAIL = "email";
    /**
     * name.
     */
    public static final String NAME = "name";
    /**
     * branch.
     */
    public static final String BRANCH = "branch";
    /**
     * id.
     */
    public static final String ID = "id";
    /**
     * number.
     */
    public static final String NUMBER = "number";
    /**
     * owner.
     */
    public static final String OWNER = "owner";
    /**
     * project.
     */
    public static final String PROJECT = "project";
    /**
     * subject.
     */
    public static final String SUBJECT = "subject";
    /**
     * url.
     */
    public static final String URL = "url";
    /**
     * revision.
     */
    public static final String REVISION = "revision";
    /**
     * ref.
     */
    public static final String REF = "ref";
    /**
     * uploader.
     */
    public static final String UPLOADER = "uploader";
    /**
     * submitter.
     */
    public static final String SUBMITTER = "submitter";
    /**
     * refupdate.
     */
    public static final String REFUPDATE = "refUpdate";
    /**
     * refname.
     */
    public static final String REFNAME = "refName";
    /**
     * oldrev.
     */
    public static final String OLDREV = "oldRev";
    /**
     * newrev.
     */
    public static final String NEWREV = "newRev";
    /**
     * submitter.
     */
    public static final String AUTHOR = "author";
    /**
     * review approvals.
     */
    public static final String APPROVALS = "approvals";
    /**
     * approval category.
     */
    public static final String TYPE = "type";
    /**
     * approval value.
     */
    public static final String VALUE = "value";

    /**
     * Empty default constructor to hinder instantiation.
     */
    private GerritEventKeys() {
    }
}
