/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved..
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

//CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: Testdata.
/**
 * Tests the equals and hashCode of TriggerContext.Wrap.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
class TriggerContextParameterizedTriggeredItemEntityTest {

    /**
     * Tests the equals method with the parameterized scenario.
     *
     * @param wrap1
     * @param wrap2
     * @param equal
     */
    @ParameterizedTest
    @MethodSource("parameters")
    void testEquals(TriggeredItemEntity wrap1, Object wrap2, boolean equal) {
        assertEquals(equal, wrap1.equals(wrap2));
    }

    /**
     * Tests the hashCode method with the parameterized scenario.
     * Except for when {@param wrap2} == null, when it will just pass.
     *
     * @param wrap1
     * @param wrap2
     * @param equal
     */
    @ParameterizedTest
    @MethodSource("parameters")
    void testHashCode(TriggeredItemEntity wrap1, Object wrap2, boolean equal) {
        if (wrap2 != null) {
            assertEquals(equal, wrap1.hashCode() == wrap2.hashCode());
        }
    }

    /**
     * Returns the parameters for the JUnit Runner to pass to the Constructor.
     * @return parameters.
     */
    static List<Arguments> parameters() {
        List<Arguments> parameters = new LinkedList<>();

        parameters.add(Arguments.of(
                    new TriggeredItemEntity(0, "project"),
                    new TriggeredItemEntity(0, "project"),
                    true));
        TriggeredItemEntity w = new TriggeredItemEntity(1, "project");
        parameters.add(Arguments.of(w, w, true));
        parameters.add(Arguments.of(
                    new TriggeredItemEntity(Integer.MAX_VALUE, "project"),
                    new TriggeredItemEntity(Integer.MAX_VALUE, "project"),
                    true));
        parameters.add(Arguments.of(
                    new TriggeredItemEntity(4, "project"),
                    new TriggeredItemEntity(4, "project"),
                    true));
        parameters.add(Arguments.of(
                    new TriggeredItemEntity(4, "project"),
                    new TriggeredItemEntity(4, "project"),
                    true));
        parameters.add(Arguments.of(
                    new TriggeredItemEntity(null, "project"),
                    new TriggeredItemEntity(null, "project"),
                    true));
        parameters.add(Arguments.of(
                    new TriggeredItemEntity((Integer)null, null),
                    new TriggeredItemEntity((Integer)null, null),
                    true));
        parameters.add(Arguments.of(
                    new TriggeredItemEntity(200, "project"),
                    new TriggeredItemEntity(200, "project"),
                    true));
        parameters.add(Arguments.of(
                    new TriggeredItemEntity(200, "project"),
                    new TriggeredItemEntity(200, "project"),
                    true));
        parameters.add(Arguments.of(
                    new TriggeredItemEntity(200, "OneProject"),
                    new TriggeredItemEntity(200, "AnotherProject"),
                    false));
        parameters.add(Arguments.of(
                    new TriggeredItemEntity(100, "OneProject"),
                    new TriggeredItemEntity(200, "AnotherProject"),
                    false));
        parameters.add(Arguments.of(
                    new TriggeredItemEntity(200, "Project"),
                    new TriggeredItemEntity(100, "Project"),
                    false));
        parameters.add(Arguments.of(
                    new TriggeredItemEntity(null, "Project"),
                    new TriggeredItemEntity(100, "Project"),
                    false));
        parameters.add(Arguments.of(
                    new TriggeredItemEntity(null, "Project"),
                    new TriggeredItemEntity(100, null),
                    false));
        parameters.add(Arguments.of(
                    new TriggeredItemEntity(100, "Project"),
                    new TriggeredItemEntity(100, null),
                    false));
        parameters.add(Arguments.of(
                    new TriggeredItemEntity(100, "Project"),
                    "Project",
                    false));
        parameters.add(Arguments.of(
                    new TriggeredItemEntity(100, "Project"),
                    null,
                    false));

        return parameters;
    }
}
