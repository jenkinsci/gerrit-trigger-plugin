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

import hudson.EnvVars;
import org.apache.tools.ant.types.selectors.SelectorUtils;

import java.io.File;

/**
 * Base interface for the compare-algorithms.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public interface CompareUtil {

    /**
     * Tells if the given pattern matches the string according to the implemented comparer/algorithm.
     * @param pattern the pattern to use.
     * @param str the string to match on.
     * @return true if the string matches the pattern.
     * @deprecated use {@link #matches(String, String, EnvVars)} instead.
     */
    boolean matches(String pattern, String str);

    /**
     * Tells if the given pattern matches the string according to the implemented comparer/algorithm.
     * @param pattern the pattern to use.
     * @param str the string to match on.
     * @param envVars the environment variables exisiting on the jenkins host. Can be {@code null}.
     * @return true if the string matches the pattern.
     */
    boolean matches(String pattern, String str, EnvVars envVars);

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

    abstract class AbstractCompareUtil implements CompareUtil {
        @Override
        public boolean matches(String pattern, String str) {
            return matches(pattern, str, null);
        }

        /**
         * Expands the pattern in case it contains tokens that can be resolved with the given envVars.
         * @param pattern the pattern to expand.
         * @param envVars the envVars to use for expansion, if {@code null} then the unmodified pattern will be returned
         * @return the expanded string, if no envVars were provided will return the initial unmodified pattern
         */
        protected String expandWithEnvVarsIfPossible(String pattern, EnvVars envVars) {
            if (envVars != null) {
                return envVars.expand(pattern);
            } else {
                return pattern;
            }
        }
    }

    /**
     * Compares based on Ant-style paths.
     * like <code>my/&#042;&#042;/something&#042;.git</code>
     */
    class AntCompareUtil extends AbstractCompareUtil {

        @Override
        public boolean matches(String pattern, String str, EnvVars envVars) {
            // Replace the Git directory separator character (always '/')
            // with the platform specific directory separator before
            // invoking Ant's platform specific path matching.
            String safePattern = pattern.replace('/', File.separatorChar);
            String expandedPattern = expandWithEnvVarsIfPossible(safePattern, envVars);
            String safeStr = str.replace('/', File.separatorChar);
            return SelectorUtils.matchPath(expandedPattern, safeStr);
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
     * Compares with pattern.equalsIgnoreCase(str).
     */
    class PlainCompareUtil extends AbstractCompareUtil {

        @Override
        public boolean matches(String pattern, String str, EnvVars envVars) {
            String expandedPattern = expandWithEnvVarsIfPossible(pattern, envVars);

            return expandedPattern.equalsIgnoreCase(str);
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
    class RegExpCompareUtil extends AbstractCompareUtil {

        @Override
        public boolean matches(String pattern, String str, EnvVars envVars) {
            String expandedPattern = expandWithEnvVarsIfPossible(pattern, envVars);
            return str.matches(expandedPattern);
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
