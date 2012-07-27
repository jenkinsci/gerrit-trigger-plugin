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

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareUtil.AntCompareUtil;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareUtil.PlainCompareUtil;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareUtil.RegExpCompareUtil;
import java.util.LinkedList;
import java.util.List;

/**
 * Enum of different ways of comparing a pattern.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public enum CompareType {

    /**
     * Plain equals comparison.
     */
    PLAIN(new PlainCompareUtil()),
    /**
     * ANT style path comparison.
     */
    ANT(new AntCompareUtil()),
    /**
     * Regular expression comparison.
     */
    REG_EXP(new RegExpCompareUtil());

    /**
     * Gets a list of all CompareType's displayNames.
     * @return a list of available displaynames.
     */
    public static List<String> getDisplayNames() {
        List<String> list = new LinkedList<String>();
        for (CompareType t : values()) {
            list.add(t.getDisplayName());
        }
        return list;
    }

    /**
     * Finds a CompareType based on displayName.
     * @param displayName the displayName
     * @return the CompareType that matches the displayName or PLAIN if none is found.
     */
    public static CompareType findByDisplayName(String displayName) {
        for (CompareType t : values()) {
            if (t.getDisplayName().equals(displayName)) {
                return t;
            }
        }
        return PLAIN;
    }

    /**
     * Finds a CompareType based on the operator.
     * @param operator the operator.
     * @return the CompareType that matches the operator or PLAIN if none is found.
     */
    public static CompareType findByOperator(char operator) {
        for (CompareType t : values()) {
            if (t.getOperator() == operator) {
                return t;
            }
        }
        return PLAIN;
    }

    private CompareUtil util;

    /**
     * Private Constructor.
     * @param util the CompareUtil to use for comparison.
     */
    private CompareType(CompareUtil util) {
        this.util = util;
    }

    /**
     * Tells if the given string matches the given pattern based on the algorithm of this CompareType instance.
     * @param pattern the pattern
     * @param str the string
     * @return true if the string matches the pattern.
     */
    public boolean matches(String pattern, String str) {
        return util.matches(pattern, str);
    }

    /**
     * Returns a "human readable" name of the instance.
     * @return the display name
     */
    public String getDisplayName() {
        return util.getName();
    }

    /**
     * Returns the operator, the one-char identifier for the CompareType.
     * @return the operator.
     */
    public char getOperator() {
        return util.getOperator();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    /*
     * Keep for now for debugging purposes.
     */
//    static class TypeConverter implements Converter {
//
//        @Override
//        public Object convert(Class type, Object value) {
//            LoggerFactory.getLogger(CompareType.class).debug("convert. type: {} value: {}", type.getName(), value);
//            throw new UnsupportedOperationException("Not supported yet.");
//        }
//
//    }

//    static {
//        Stapler.CONVERT_UTILS.register(new EnumConverter(), CompareType.class);
//    }
}
