package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model;

import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeMerged;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Tests {@link BuildMemory.GerritTriggeredEventComparator}.
 *
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 */
class GerritTriggeredEventComparatorTest {

    private final Comparator<GerritTriggeredEvent> comparator = new BuildMemory.GerritTriggeredEventComparator();

    /**
     * The different scenarios.
     *
     * @return a list of parameters to the constructor.
     * @see ParameterizedTest
     */
    static Stream<Arguments> parameters() {
        PatchsetCreated event = Setup.createPatchsetCreated();
        ChangeMerged merged = Setup.createChangeMerged();
        return Stream.of(
            Arguments.of(null, null, 0),
                Arguments.of(null, event, -1),
                Arguments.of(event, null, 1),
                Arguments.of(event, event, 0),
                Arguments.of(event, merged, Integer.compare(event.hashCode(), merged.hashCode())),
                Arguments.of(merged, event, Integer.compare(merged.hashCode(), event.hashCode()))
        );
    }

    /**
     * Tests the scenario on
     * {@link BuildMemory.GerritTriggeredEventComparator#compare(GerritTriggeredEvent, GerritTriggeredEvent)}.
     *
     * @param o1
     * @param o2
     * @param expected
     *
     */
    @ParameterizedTest(name = "{index}: {0}, {1} == {2}")
    @MethodSource("parameters")
    void testCompare(GerritTriggeredEvent o1, GerritTriggeredEvent o2, int expected) {
        assertEquals(expected, comparator.compare(o1, o2));
    }
}
