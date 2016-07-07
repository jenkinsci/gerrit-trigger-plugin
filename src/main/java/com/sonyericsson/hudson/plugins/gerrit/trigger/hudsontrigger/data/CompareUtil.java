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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data;

import java.io.File;
import org.apache.tools.ant.types.selectors.SelectorUtils;

/**
 * Base interface for the compare-algorithms.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public interface CompareUtil {

    /**
     * Tells if the given pattern matches the string according to the implemented comparer/algorithm.
     * @param pattern the pattern to use.
     * @param str the string to match on.
     * @return true if the string matches the pattern.
     */
    boolean matches(String pattern, String str);

    /**
     * Returns the human-readable name of the util.
     * @return the name.
     */
    String getName();

    /**
     * Returns the operator name of the util.
     * @return the operator.
     */
    char getOperator();

    /**
     * Compares based on Ant-style paths.
     * like <code>my/&#042;&#042;/something&#042;.git</code>
     */
    static class AntCompareUtil implements CompareUtil {

        @Override
        public boolean matches(String pattern, String str) {
            // Replace the Git directory separator character (always '/')
            // with the platform specific directory separator before
            // invoking Ant's platform specific path matching.
            String safePattern = pattern.replace('/', File.separatorChar);
            String safeStr = str.replace('/', File.separatorChar);
            return SelectorUtils.matchPath(safePattern, safeStr);
        }

        @Override
        public String getName() {
            return "Path";
        }

        @Override
        public char getOperator() {
            return '^';
        }
    }

    /**
     * Compares with pattern.equals(str).
     */
    static class PlainCompareUtil implements CompareUtil {

        @Override
        public boolean matches(String pattern, String str) {
            return pattern.equalsIgnoreCase(str);
        }

        @Override
        public String getName() {
            return "Plain";
        }

        @Override
        public char getOperator() {
            return '=';
        }
    }

    /**
     * Compares with regular-expressions.
     * string.matches(pattern)
     * @see java.util.regex.Pattern
     */
    static class RegExpCompareUtil implements CompareUtil {

        @Override
        public boolean matches(String pattern, String str) {
            return str.matches(pattern);
        }

        @Override
        public String getName() {
            return "RegExp";
        }

        @Override
        public char getOperator() {
            return '~';
        }
    }
}
