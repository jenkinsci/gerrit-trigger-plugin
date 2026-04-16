package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import jenkins.model.CauseOfInterruption;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests for {@link NewPatchSetInterruption}.
 *
 * @author Ignacio Roncero &lt;ironcero@cloudbees.com&gt;
 */
public class NewPatchSetInterruptionTest {

    /**
     * Tests that it's a CauseOfInterruption.
     */
    @Test
    public void testIsCauseOfInterruption() {
        NewPatchSetInterruption interruption = new NewPatchSetInterruption();
        assertNotNull(interruption instanceof CauseOfInterruption);
    }

    /**
     * Tests that getShortDescription() returns the correct message.
     */
    @Test
    public void testGetShortDescription() {
        NewPatchSetInterruption interruption = new NewPatchSetInterruption();
        String description = interruption.getShortDescription();

        assertNotNull(description);
        assertEquals(Messages.AbortedByNewPatchSet(), description);
    }

    /**
     * Tests that the description is not empty.
     */
    @Test
    public void testDescriptionNotEmpty() {
        NewPatchSetInterruption interruption = new NewPatchSetInterruption();
        String description = interruption.getShortDescription();

        assertNotNull(description);
        assertNotNull(!description.isEmpty());
    }

    /**
     * Tests that multiple instances return the same description.
     */
    @Test
    public void testMultipleInstancesSameDescription() {
        NewPatchSetInterruption interruption1 = new NewPatchSetInterruption();
        NewPatchSetInterruption interruption2 = new NewPatchSetInterruption();

        assertEquals(interruption1.getShortDescription(), interruption2.getShortDescription());
    }
}
