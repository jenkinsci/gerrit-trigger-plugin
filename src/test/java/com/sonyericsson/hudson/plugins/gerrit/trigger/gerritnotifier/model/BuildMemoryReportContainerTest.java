package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model;

import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import hudson.model.AbstractProject;
import hudson.model.ItemGroup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * Tests {@link BuildMemory} internal container.
 * The main requirement is not merging non-equal events with same hash code.
 *
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 */
@RunWith(Parameterized.class)
public class BuildMemoryReportContainerTest {

    private final GerritTriggeredEvent o1;
    private final GerritTriggeredEvent o2;
    private final int expected;
    private final BuildMemory memory;

    static class PatchsetCreatedWithPrefinedHash extends PatchsetCreated {
        private final int hashCode;

        private PatchsetCreatedWithPrefinedHash(int hashCode) {
            this.hashCode = hashCode;
        }

        @Override
        public boolean equals(Object o) {
            return false;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    /**
     * The different scenarios.
     *
     * @return a list of parameters to the constructor.
     * @see Parameterized
     */
    @Parameterized.Parameters(name = "{index}: {0}, {1} == {2}")
    public static Iterable<Object[]> permutations() {
        ChangeBasedEvent event1 = new PatchsetCreatedWithPrefinedHash(777);
        ChangeBasedEvent event2 = new PatchsetCreatedWithPrefinedHash(777);
        return Arrays.asList(new Object[][]{
                {null, null, 1},
                {null, event1, 2},
                {event1, null, 2},
                {event1, event1, 1},
                {event2, event2, 1},
                {event1, event2, 2},
                {event2, event1, 2},
        });
    }

    /**
     * Constructor.
     * @param o1 first argument
     * @param o2 second argument
     * @param expected the expected result
     */
    public BuildMemoryReportContainerTest(GerritTriggeredEvent o1, GerritTriggeredEvent o2, int expected) {
        this.o1 = o1;
        this.o2 = o2;
        this.expected = expected;
        memory = new BuildMemory();
    }

    /**
     * Tests the scenario on {@link BuildMemory}.
     *
     * @throws Exception if so
     */
    @Test
    public void testCompare() throws Exception {
        AbstractProject mock = Mockito.mock(AbstractProject.class);
        ItemGroup itemGroup = Mockito.mock(ItemGroup.class);
        Mockito.when(itemGroup.getFullName()).thenReturn("Parent");
        Mockito.when(mock.getParent()).thenReturn(itemGroup);
        memory.triggered(o1, mock);
        memory.triggered(o2, mock);

        assertEquals(expected, memory.getMemory().size());
    }
}
