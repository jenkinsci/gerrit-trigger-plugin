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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

//CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: Testdata.
/**
 * Tests the equals and hashCode of TriggerContext.Wrap.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(Parameterized.class)
public class TriggerContextParameterizedTriggeredItemEntityTest {

    private TestParameter parameter;

    /**
     * Constructor taking the parameters.
     * @param parameter the parameters.
     */
    public TriggerContextParameterizedTriggeredItemEntityTest(TestParameter parameter) {
        this.parameter = parameter;
    }

    /**
     * Tests the equals method with the parameterized scenario.
     */
    @Test
    public void testEquals() {
        Assert.assertEquals(parameter.equal, parameter.wrap1.equals(parameter.wrap2));
    }

    /**
     * Tests the hashCode method with the parameterized scenario.
     * Except for when {@link TestParameter#wrap2} == null, when it will just pass.
     */
    @Test
    public void testHashCode() {
        if (parameter.wrap2 != null) {
            Assert.assertEquals(parameter.equal, parameter.wrap1.hashCode() == parameter.wrap2.hashCode());
        }
    }

    /**
     * Returns the parameters for the JUnit Runner to pass to the Constructor.
     * @return parameters.
     */
    @Parameters
    public static final Collection getParameters() {
        List<TestParameter[]> parameters = new LinkedList<TestParameter[]>();

        parameters.add(new TestParameter[]{new TestParameter(
                    new TriggeredItemEntity(0, "project"),
                    new TriggeredItemEntity(0, "project"),
                    true), });
        TriggeredItemEntity w = new TriggeredItemEntity(1, "project");
        parameters.add(new TestParameter[]{new TestParameter(w, w, true)});
        parameters.add(new TestParameter[]{new TestParameter(
                    new TriggeredItemEntity(Integer.MAX_VALUE, "project"),
                    new TriggeredItemEntity(Integer.MAX_VALUE, "project"),
                    true), });
        parameters.add(new TestParameter[]{new TestParameter(
                    new TriggeredItemEntity(Integer.valueOf(4), "project"),
                    new TriggeredItemEntity(Integer.valueOf(4), "project"),
                    true), });
        parameters.add(new TestParameter[]{new TestParameter(
                    new TriggeredItemEntity(Integer.valueOf(4), new String("project")),
                    new TriggeredItemEntity(Integer.valueOf(4), new String("project")),
                    true), });
        parameters.add(new TestParameter[]{new TestParameter(
                    new TriggeredItemEntity(null, "project"),
                    new TriggeredItemEntity(null, "project"),
                    true), });
        parameters.add(new TestParameter[]{new TestParameter(
                    new TriggeredItemEntity((Integer)null, null),
                    new TriggeredItemEntity((Integer)null, null),
                    true), });
        parameters.add(new TestParameter[]{new TestParameter(
                    new TriggeredItemEntity(200, "project"),
                    new TriggeredItemEntity(200, "project"),
                    true), });
        parameters.add(new TestParameter[]{new TestParameter(
                    new TriggeredItemEntity(200, new String("project")),
                    new TriggeredItemEntity(200, "project"),
                    true), });
        parameters.add(new TestParameter[]{new TestParameter(
                    new TriggeredItemEntity(200, "OneProject"),
                    new TriggeredItemEntity(200, "AnotherProject"),
                    false), });
        parameters.add(new TestParameter[]{new TestParameter(
                    new TriggeredItemEntity(100, "OneProject"),
                    new TriggeredItemEntity(200, "AnotherProject"),
                    false), });
        parameters.add(new TestParameter[]{new TestParameter(
                    new TriggeredItemEntity(200, "Project"),
                    new TriggeredItemEntity(100, "Project"),
                    false), });
        parameters.add(new TestParameter[]{new TestParameter(
                    new TriggeredItemEntity(null, "Project"),
                    new TriggeredItemEntity(100, "Project"),
                    false), });
        parameters.add(new TestParameter[]{new TestParameter(
                    new TriggeredItemEntity(null, "Project"),
                    new TriggeredItemEntity(100, null),
                    false), });
        parameters.add(new TestParameter[]{new TestParameter(
                    new TriggeredItemEntity(100, "Project"),
                    new TriggeredItemEntity(100, null),
                    false), });
        parameters.add(new TestParameter[]{new TestParameter(
                    new TriggeredItemEntity(100, "Project"),
                    "Project",
                    false), });
        parameters.add(new TestParameter[]{new TestParameter(
                    new TriggeredItemEntity(100, "Project"),
                    null,
                    false), });

        return parameters;
    }

    /**
     * Parameters for the tests.
     */
    public static class TestParameter {

        TriggeredItemEntity wrap1;
        Object wrap2;
        boolean equal;

        /**
         * Convenience constructor.
         * @param wrap1 the first object.
         * @param wrap2 the second object to pass to the first.
         * @param equal if they should be equal.
         */
        public TestParameter(TriggeredItemEntity wrap1, Object wrap2, boolean equal) {
            this.wrap1 = wrap1;
            this.wrap2 = wrap2;
            this.equal = equal;
        }
    }
}
