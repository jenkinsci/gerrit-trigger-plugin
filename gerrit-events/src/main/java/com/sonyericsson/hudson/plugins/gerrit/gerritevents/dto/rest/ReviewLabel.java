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

//CS IGNORE LineLength FOR NEXT 6 LINES. REASON: JavaDoc.
/**
 * Approval label and value.
 *
 * Will end up in {@link ReviewInput#labels}.
 * @see <a href="https://gerrit-documentation.storage.googleapis.com/Documentation/2.7/rest-api-changes.html#set-review">Gerrit Documentation</a>
 */
public class ReviewLabel {

    private final String name;
    private final int value;

    /**
     * Standard Constructor.
     *
     * @param name name
     * @param value value
     */
    public ReviewLabel(String name, int value) {
        this.name = name;
        this.value = value;
    }

    /**
     * Code Review Label.
     *
     * @param codeReview value
     * @return as label
     */
    public static ReviewLabel codeReview(int codeReview) {
        return new ReviewLabel("Code-Review", codeReview);
    }

    /**
     * Verified Label.
     *
     * @param verified value
     * @return as label
     */
    public static ReviewLabel verified(int verified) {
        return new ReviewLabel("Verified", verified);
    }

    /**
     * Name.
     *
     * @return name
     */
    public String getName() {
        return name;
    }

    /**
     * Value.
     *
     * @return value
     */
    public int getValue() {
        return value;
    }
}
