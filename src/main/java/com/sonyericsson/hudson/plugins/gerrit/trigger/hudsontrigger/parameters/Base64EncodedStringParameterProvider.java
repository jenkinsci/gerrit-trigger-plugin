/*
 * The MIT License
 *
 * Copyright (c) 2014 Ericsson
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.parameters;

import hudson.Extension;
import hudson.model.ParameterValue;

import com.sonyericsson.rebuild.RebuildParameterPage;
import com.sonyericsson.rebuild.RebuildParameterProvider;

/**
 * An extension class to inject {@link Base64EncodedStringParameterProvider} to rebuild-plugin.
 *
 * @author Scott.Hebert &lt;Scott.Hebert@ericsson.com&gt;.
 * @author Bowen.Cheng &lt;Bowen.Cheng@ericsson.com&gt;.
 */
@Extension(optional = true)
public class Base64EncodedStringParameterProvider extends RebuildParameterProvider {
    /**
     * @param value ParameterValue to have rebuild page created for it.
     * @return rebuild page for Base64EncodedStringParameterValue
     * @see com.sonyericsson.rebuild.RebuildParameterProvider#getRebuildPage(hudson.model.ParameterValue)
     */
    @Override
    public RebuildParameterPage getRebuildPage(ParameterValue value) {
        if (value instanceof Base64EncodedStringParameterValue) {
            return new RebuildParameterPage(Base64EncodedStringParameterValue.class,
                "rebuild.jelly");
        }

        return null;
    }

}
