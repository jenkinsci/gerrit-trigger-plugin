/*
 *  The MIT License
 *
 *  Copyright 2014 rinrinne a.k.a. rin_ne All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.parameters;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.model.StringParameterValue;

/**
 * A parameter value for Base64 encoded string.
 *
 * @author rinrinne a.k.a. rin_ne (rinrin.ne@gmail.com)
 */
public class Base64EncodedStringParameterValue extends StringParameterValue {

    private static final long serialVersionUID = 1L;
    private static final int ABBREVIATE_LENGTH = 10;

    /**
     * Default constructor.
     *
     * @param name the name.
     * @param value the value.
     */
    @DataBoundConstructor
    public Base64EncodedStringParameterValue(String name, String value) {
        super(name, value);
    }

    /**
     * Default constructor.
     * @param name the name.
     * @param value the value.
     * @param description the description.
     */
    public Base64EncodedStringParameterValue(String name, String value, String description) {
        super(name, value, description);
    }

    @Override
    public String toString() {
        return "(Base64EncodedStringParameterValue) " + getName() + "='" + value + "'";
    }

    @Override
    public String getShortDescription() {
        return name + "=<Base64 Encoded String: "
                + StringUtils.abbreviate(value, ABBREVIATE_LENGTH)
                + ">";
    }
}
