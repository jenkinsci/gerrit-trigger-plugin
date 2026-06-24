package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import hudson.model.Action;
import hudson.model.CauseAction;
import hudson.model.ParametersAction;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;

import static org.mockito.hamcrest.MockitoHamcrest.argThat;

/**
 * Contains Hamcrest and Mockito matchers used by {@link  GerritTriggerTest}.
 */
public final class GerritMatchers {

    /**
     * Utility constructor.
     */
    private GerritMatchers() {
    }

    /**
     * Simple instance matcher for
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritManualCause}.
     */
    static class IsAManualCause extends org.hamcrest.BaseMatcher<GerritCause> {

        private final org.mockito.internal.matchers.InstanceOf internal;
        private boolean silentMode;

        /**
         * Constructor.
         *
         * @param silentMode the silent mode value to check for
         */
        IsAManualCause(boolean silentMode) {
            internal = new org.mockito.internal.matchers.InstanceOf(GerritManualCause.class);
            this.silentMode = silentMode;
        }

        @Override
        public boolean matches(Object actual) {
            if (internal.matches(actual)) {
                GerritManualCause c = (GerritManualCause)actual;
                return c.isSilentMode() == silentMode;
            }
            return false;
        }

        @Override
        public void describeTo(org.hamcrest.Description description) {
            description.appendText(internal.toString());
            if (silentMode) {
                description.appendText(" silent ");
            } else {
                description.appendText(" loud ");
            }
        }
    }

    /**
     * Convenience method for creating a {@link IsParameterActionWithStringParameterValue}. So it is easier to read.
     *
     * @param name  the name of the parameter to check.
     * @param value the value of the parameter to check.
     * @return an argThat IsParameterActionWithStringParameterValue
     */
    static hudson.model.Action isParameterActionWithStringParameterValue(String name, String value) {
        return argThat(new IsParameterActionWithStringParameterValue(name, value));
    }

    /**
     * Convenience method for creating a {@link IsParameterActionWithStringParameterValue}. So it is easier to read.
     *
     * @param nameValues the names and values of the parameters to check.
     * @return an argThat IsParameterActionWithStringParameterValue
     */
    static hudson.model.Action isParameterActionWithStringParameterValues(
            IsParameterActionWithStringParameterValue.NameAndValue... nameValues) {
        return argThat(new IsParameterActionWithStringParameterValue(nameValues));
    }

    /**
     * Convenience method for creating a {@link IsParameterActionWithStringParameterValue}. So it is easier to read.
     *
     * @param name the name and values of the parameters to check.
     * @param val  the value of the parameters to check.
     * @return an argThat IsParameterActionWithStringParameterValue
     */
    static IsParameterActionWithStringParameterValue.NameAndValue nameVal(String name, String val) {
        return new IsParameterActionWithStringParameterValue.NameAndValue(name, val);
    }

    /**
     * An ArgumentMatcher that checks if the argument is a {@link ParametersAction}.
     * And if it contains a specific ParameterValue.
     */
    static class IsParameterActionWithStringParameterValue extends org.hamcrest.BaseMatcher<hudson.model.Action> {

        NameAndValue[] nameAndValues;

        /**
         * Standard Constructor.
         *
         * @param name  the name of the parameter to check.
         * @param value the value of the parameter to check.
         */
        IsParameterActionWithStringParameterValue(String name, String value) {
            nameAndValues = new NameAndValue[]{new NameAndValue(name, value)};
        }

        /**
         * Standard Constructor.
         *
         * @param nameVal the name and values of the parameters to check.
         */
        IsParameterActionWithStringParameterValue(NameAndValue... nameVal) {
            nameAndValues = nameVal;
        }


        @Override
        public boolean matches(Object arg) {
            hudson.model.Action action = (hudson.model.Action)arg;
            if (action instanceof ParametersAction) {
                for (NameAndValue nv : nameAndValues) {
                    hudson.model.ParameterValue parameterValue = ((ParametersAction)action).getParameter(nv.name);

                    if (parameterValue != null && parameterValue instanceof hudson.model.StringParameterValue) {
                        hudson.model.StringParameterValue param = (hudson.model.StringParameterValue)parameterValue;
                        if (!nv.name.equals(param.getName()) || !nv.value.equals(param.value)) {
                            System.err.println("Required parameter is [" + param.getName() + "=" + param.value
                                    + "] should be [" + nv.toString() + "]");
                            return false;
                        }
                    } else {
                        System.err.println("Missing required parameter " + nv.name);
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public void describeTo(final org.hamcrest.Description description) {
            description.appendText("A ParameterAction with values: [");
            for (NameAndValue nameAndValue : nameAndValues) {
                description.appendText(nameAndValue.name).appendText(" = ").appendText(nameAndValue.value);
            }
            description.appendText("]");
        }

        /**
         * Data structure for a name and a value.
         */
        static class NameAndValue {
            private String name;
            private String value;

            /**
             * Standard constructor.
             *
             * @param name  the name.
             * @param value the value.
             */
            NameAndValue(String name, String value) {
                this.name = name;
                this.value = value;
            }

            @Override
            public String toString() {
                return name + "=" + value;
            }
        }
    }

    /**
     * A {@link CoreMatchers#allOf(Iterable)} version for mockito {@link Action} matchers.
     * the list could contain more Actions than provided to check.
     *
     * @param all all the {@link Matcher}s that the list should contain.
     * @return the matcher.
     * @see #hasCauseActionContainingCauseMatcher(GerritCause)
     * @see #hasParamActionMatcher(String, String)
     */
    static java.util.List<hudson.model.Action> hasAllActions(Matcher<java.util.List<hudson.model.Action>>... all) {
        return argThat(CoreMatchers.allOf(all));
    }

    /**
     * {@link org.mockito.hamcrest.MockitoHamcrest#argThat(Matcher)} version of
     * {@link #hasCauseActionContainingCauseMatcher(GerritCause)}.
     *
     * @param cause the GerritCause to check for instance equality.
     * @return the matcher.
     * @see #hasCauseActionContainingCauseMatcher(GerritCause)
     */
    static java.util.List<hudson.model.Action> hasCauseActionContainingCause(final GerritCause cause) {
        return argThat(hasCauseActionContainingCauseMatcher(cause));
    }

    /**
     * A {@link Matcher} that checks a list of {@link Action}s for a {@link CauseAction}
     * containing the provided instance of a  {@link GerritCause}.
     *
     * @param expectedSame the GerritCause to check for instance equality.
     * @return the matcher.
     */
    static org.hamcrest.BaseMatcher<java.util.List<hudson.model.Action>>
    hasCauseActionContainingCauseMatcher(final GerritCause expectedSame) {
        return new org.hamcrest.BaseMatcher<>() {
            @Override
            public boolean matches(Object item) {
                if (item instanceof java.util.List) {
                    for (Action a : ((java.util.List<hudson.model.Action>)item)) {
                        if (a instanceof CauseAction) {
                            GerritCause cause = ((CauseAction)a).findCause(GerritCause.class);
                            if (expectedSame == null && cause != null) {
                                //Don't care about the instance just that it exists
                                return true;
                            } else if (expectedSame == null && cause == null) {
                                return false;
                            }
                            if (cause == expectedSame) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }

            @Override
            public void describeTo(org.hamcrest.Description description) {
                if (expectedSame == null) {
                    description.appendText("does not contain a CauseAction with a GerritCause.");
                } else {
                    description.appendText("does not contain a CauseAction with the valid GerritCause.");
                }
            }
        };
    }

    /**
     * A {@link org.mockito.hamcrest.MockitoHamcrest#argThat(Matcher)} version of
     * {@link #hasCauseActionContainingUserCauseMatcher()}.
     *
     * @return the matcher.
     * @see #hasCauseActionContainingUserCauseMatcher()
     */
    static java.util.List<hudson.model.Action> hasCauseActionContainingUserCause() {
        return argThat(hasCauseActionContainingUserCauseMatcher());
    }

    /**
     * A {@link Matcher} that checks a list of {@link Action}s for a {@link CauseAction}
     * containing any {@link GerritUserCause}.
     *
     * @return the matcher.
     */
    static org.hamcrest.BaseMatcher<java.util.List<hudson.model.Action>> hasCauseActionContainingUserCauseMatcher() {
        return new org.hamcrest.BaseMatcher<java.util.List<hudson.model.Action>>() {
            @Override
            public boolean matches(Object item) {
                if (item instanceof java.util.List) {
                    for (Action a : ((java.util.List<hudson.model.Action>)item)) {
                        if (a instanceof CauseAction) {
                            GerritCause cause = ((CauseAction)a).findCause(GerritUserCause.class);
                            if (cause != null) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }

            @Override
            public void describeTo(org.hamcrest.Description description) {
                description.appendText("does not contain a CauseAction with a GerritUserCause.");
            }
        };
    }


    /**
     * A {@link org.mockito.hamcrest.MockitoHamcrest#argThat(Matcher)} version of
     * {@link #hasParamActionMatcher(String, String)}.
     *
     * @param key   the key to find
     * @param value the value to compare
     * @return the matcher
     * @see #hasParamActionMatcher(String, String)
     */
    static java.util.List<hudson.model.Action> hasParamAction(final String key, final String value) {
        return argThat(hasParamActionMatcher(key, value));
    }

    /**
     * A version of {@link #hasParamActionMatcher(String, String)} using {@link GerritTriggerParameters}
     * for the key for simpler usage.
     *
     * @param key   the key
     * @param value the value to compare
     * @return the matcher
     * @see #hasParamActionMatcher(String, String)
     */
    static org.hamcrest.BaseMatcher<java.util.List<hudson.model.Action>>
    hasParamActionMatcher(final GerritTriggerParameters key, final String value) {
        return hasParamActionMatcher(key.name(), value);
    }

    /**
     * {@link Matcher} that checks a list of {@link Action}s for a {@link ParametersAction} that contains any parameter
     * with the specified key whose {@code toString()} method equals the specified value.
     *
     * @param key   the key to find
     * @param value the value to compare
     * @return the matcher
     */
    static org.hamcrest.BaseMatcher<java.util.List<hudson.model.Action>>
    hasParamActionMatcher(final String key, final String value) {
        return new org.hamcrest.BaseMatcher<>() {
            @Override
            public boolean matches(Object item) {
                if (item instanceof java.util.List) {
                    for (Action a : ((java.util.List<hudson.model.Action>)item)) {
                        if (a instanceof ParametersAction) {
                            ParametersAction parameters = (ParametersAction)a;
                            hudson.model.ParameterValue parameter = parameters.getParameter(key);
                            if (parameter != null) {
                                return value.equals(parameter.getValue());
                            }
                        }
                    }
                }

                return false;
            }

            @Override
            public void describeTo(org.hamcrest.Description description) {
                description.appendText("does not contain a parameter ").
                        appendText(key).appendText(" with value ").appendText(value);
            }
        };
    }
}
