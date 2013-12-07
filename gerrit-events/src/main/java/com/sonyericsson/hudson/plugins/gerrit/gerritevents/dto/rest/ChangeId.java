/*
 *  The MIT License
 *
 *  Copyright 2013 Jyrki Puttonen. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.rest;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * A changeId.
 */
public class ChangeId {
    private static final Logger logger = LoggerFactory.getLogger(ChangeId.class);

    private final String projectName;
    private final String branchName;
    private final String id;

    /**
     * Constructor.
     *
     * @param projectName project name
     * @param branchName branch name
     * @param id id
     */
    public ChangeId(String projectName, String branchName, String id) {
        this.projectName = projectName;
        this.branchName = branchName;
        this.id = id;
    }

    /**
     * Constructor getting values from a change object.
     *
     * @param change the change
     * @see #ChangeId(String, String, String)
     */
    public ChangeId(Change change) {
        this(change.getProject(), change.getBranch(), change.getId());
    }

    /**
     * As the part of the URL.
     *
     * @return the url part.
     */
    public String asUrlPart() {
        try {
            return encode(projectName) + "~" + encode(branchName) + "~" + id;
        } catch (UnsupportedEncodingException e) {
            String parameter = projectName + "~" + branchName + "~" + id;
            logger.error("Failed to encode ChangeId {}, falling back to unencoded {}", this, parameter);
            return parameter;
        }
    }

    /**
     * Encode given String for usage in URL. UTF-8 is used as per recommendation
     * at http://www.w3.org/TR/html40/appendix/notes.html#non-ascii-chars
     * @param s String to be encoded
     * @return Encoded string
     * @throws UnsupportedEncodingException if UTF-8 is unsupported, handled in caller for better log message
     */
    private String encode(final String s) throws UnsupportedEncodingException {
        return URLEncoder.encode(s, "UTF-8");
    }

    @Override
    public String toString() {
        return "ChangeId{"
                + "projectName='" + projectName + '\''
                + ", branchName='" + branchName + '\''
                + ", id='" + id + '\''
                + '}';
    }
}
