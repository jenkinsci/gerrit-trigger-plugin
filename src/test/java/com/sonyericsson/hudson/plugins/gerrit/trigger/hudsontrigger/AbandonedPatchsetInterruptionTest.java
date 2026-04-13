package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import jenkins.model.CauseOfInterruption;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests for {@link AbandonedPatchsetInterruption}.
 *
 * @author Ignacio Roncero &lt;ironcero@cloudbees.com&gt;
 */
public class AbandonedPatchsetInterruptionTest {

    /**
     * Tests that the class can be instantiated.
     */
    @Test
    public void testInstantiation() {
        AbandonedPatchsetInterruption interruption = new AbandonedPatchsetInterruption();
        assertNotNull(interruption);
    }

    /**
     * Tests that it's a CauseOfInterruption.
     */
    @Test
    public void testIsCauseOfInterruption() {
        AbandonedPatchsetInterruption interruption = new AbandonedPatchsetInterruption();
        assertNotNull(interruption instanceof CauseOfInterruption);
    }

    /**
     * Tests that getShortDescription() returns the correct message.
     */
    @Test
    public void testGetShortDescription() {
        AbandonedPatchsetInterruption interruption = new AbandonedPatchsetInterruption();
        String description = interruption.getShortDescription();

        assertNotNull(description);
        assertEquals(Messages.AbortedByAbandonedPatchset(), description);
    }

    /**
     * Tests that the description is not empty.
     */
    @Test
    public void testDescriptionNotEmpty() {
        AbandonedPatchsetInterruption interruption = new AbandonedPatchsetInterruption();
        String description = interruption.getShortDescription();

        assertNotNull(description);
        assertNotNull(!description.isEmpty());
    }

    /**
     * Tests that multiple instances return the same description.
     */
    @Test
    public void testMultipleInstancesSameDescription() {
        AbandonedPatchsetInterruption interruption1 = new AbandonedPatchsetInterruption();
        AbandonedPatchsetInterruption interruption2 = new AbandonedPatchsetInterruption();

        assertEquals(interruption1.getShortDescription(), interruption2.getShortDescription());
    }
}
