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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests make ref spec.
 * TODO move to StringUtilTest
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(AbstractProject.class)
public class GerritTriggerTest {

    /**
     * test.
     */
    @Test
    public void testMakeRefSpec1() {
        PatchsetCreated event = new PatchsetCreated();
        Change change = new Change();
        change.setNumber("1");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        event.setPatchset(patch);
        String expResult = StringUtil.REFSPEC_PREFIX + "01/1/1";
        String result = StringUtil.makeRefSpec(event);
        assertEquals(expResult, result);
    }

    /**
     * test.
     */
    @Test
    public void testMakeRefSpec2() {

        PatchsetCreated event = new PatchsetCreated();
        Change change = new Change();
        change.setNumber("12");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        event.setPatchset(patch);
        String expResult = StringUtil.REFSPEC_PREFIX + "12/12/1";
        String result = StringUtil.makeRefSpec(event);
        assertEquals(expResult, result);
    }

    /**
     * test.
     */
    @Test
    public void testMakeRefSpec3() {

        PatchsetCreated event = new PatchsetCreated();
        Change change = new Change();
        change.setNumber("123");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        event.setPatchset(patch);
        String expResult = StringUtil.REFSPEC_PREFIX + "23/123/1";
        String result = StringUtil.makeRefSpec(event);
        assertEquals(expResult, result);
    }

    /**
     * test.
     */
    @Test
    public void testMakeRefSpec4() {

        PatchsetCreated event = new PatchsetCreated();
        Change change = new Change();
        change.setNumber("2131");
        event.setChange(change);
        PatchSet patch = new PatchSet();
        patch.setNumber("1");
        event.setPatchset(patch);
        String expResult = StringUtil.REFSPEC_PREFIX + "31/2131/1";
        String result = StringUtil.makeRefSpec(event);
        assertEquals(expResult, result);
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild(int, hudson.model.Cause, hudson.model.Action[])}
     * gets called with correct parameters when there are some default parameters present.
     */
    @Test
    public void testScheduleWithDefaultParameters() {

        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullDisplayName()).thenReturn("MockedProject");
        ParametersDefinitionProperty parameters = mock(ParametersDefinitionProperty.class);
        List<ParameterDefinition> list = new LinkedList<ParameterDefinition>();
        list.add(new StringParameterDefinition("MOCK_PARAM", "mock_value"));
        when(parameters.getParameterDefinitions()).thenReturn(list);
        when(project.getProperty(ParametersDefinitionProperty.class)).thenReturn(parameters);

        GerritTrigger trigger = new GerritTrigger(null, 0, 0, 0, 0, 0, 0, 0, 0, true);
        trigger.start(project, true);
        PatchsetCreated event = Setup.createPatchsetCreated();
        GerritCause gerritCause = new GerritCause(event, true);
        gerritCause = spy(gerritCause);
        doReturn("http://mock.url").when(gerritCause).getUrl();
        trigger.schedule(gerritCause, event);

        verify(project).scheduleBuild(
                anyInt(),
                same(gerritCause),
                any(Action.class),
                isParameterActionWithStringParameterValue("MOCK_PARAM", "mock_value"));
        //Just to make sure the normal arguments are there as well.
        verify(project).scheduleBuild(
                anyInt(),
                same(gerritCause),
                any(Action.class),
                isParameterActionWithStringParameterValue(GerritTrigger.GERRIT_CHANGE_URL, "http://mock.url"));
    }

    /**
     * Tests the schedule method of GerritTrigger.
     * It verifies that {@link AbstractProject#scheduleBuild(int, hudson.model.Cause, hudson.model.Action[])}
     * gets called with correct parameters when there are no default parameters present.
     */
    @Test
    public void testScheduleWithNoDefaultParameters() {
        AbstractProject project = PowerMockito.mock(AbstractProject.class);
        when(project.getFullDisplayName()).thenReturn("MockedProject");
        ParametersDefinitionProperty parameters = mock(ParametersDefinitionProperty.class);
        when(parameters.getParameterDefinitions()).thenReturn(Collections.EMPTY_LIST);
        when(project.getProperty(ParametersDefinitionProperty.class)).thenReturn(parameters);

        GerritTrigger trigger = new GerritTrigger(null, 0, 0, 0, 0, 0, 0, 0, 0, true);
        trigger.start(project, true);
        PatchsetCreated event = Setup.createPatchsetCreated();
        GerritCause gerritCause = new GerritCause(event, true);
        gerritCause = spy(gerritCause);
        doReturn("http://mock.url").when(gerritCause).getUrl();
        trigger.schedule(gerritCause, event);

        verify(project).scheduleBuild(
                anyInt(),
                same(gerritCause),
                any(Action.class),
                isParameterActionWithStringParameterValue(GerritTrigger.GERRIT_CHANGE_ID, event.getChange().getId()));
        //Just to make sure one more normal arguments is there as well.
        verify(project).scheduleBuild(
                anyInt(),
                same(gerritCause),
                any(Action.class),
                isParameterActionWithStringParameterValue(GerritTrigger.GERRIT_CHANGE_URL, "http://mock.url"));
    }

    /**
     * Convenience method for creating a {@link IsParameterActionWithStringParameterValue}. So it is easier to read.
     * @param name the name of the parameter to check.
     * @param value the value of the parameter to check.
     * @return an argThat IsParameterActionWithStringParameterValue
     */
    static Action isParameterActionWithStringParameterValue(String name, String value) {
        return argThat(new IsParameterActionWithStringParameterValue(name, value));
    }

    /**
     * An ArgumentMatcher that checks if the argument is a {@link ParametersAction}.
     * And if it contains a specific ParameterValue.
     */
    static class IsParameterActionWithStringParameterValue extends ArgumentMatcher<Action> {

        private String name;
        private String value;

        /**
         * Standard Constructor.
         * @param name the name of the parameter to check.
         * @param value the value of the parameter to check.
         */
        public IsParameterActionWithStringParameterValue(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean matches(Object argument) {
            Action action = (Action)argument;
            if (action instanceof ParametersAction) {

                ParameterValue parameterValue = ((ParametersAction)action).getParameter(name);

                if (parameterValue != null && parameterValue instanceof StringParameterValue) {
                    StringParameterValue param = (StringParameterValue)parameterValue;
                    return name.equals(param.getName()) && value.equals(param.value);
                }
            }
            return false;
        }
    }
}
