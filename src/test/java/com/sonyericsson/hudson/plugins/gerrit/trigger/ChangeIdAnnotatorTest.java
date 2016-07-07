package com.sonyericsson.hudson.plugins.gerrit.trigger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import hudson.MarkupText;
import hudson.model.AbstractBuild;

import org.junit.Assert;
import org.junit.Test;

import com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritCause;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;

/**
 * Test for {@link ChangeIdAnnotator}.
 * @author Kohsuke Kawaguchi
 */
public class ChangeIdAnnotatorTest {

    /**
     * the test.
     */
    @Test
    public void testFoo() {

        AbstractBuild<?, ?> b = mock(AbstractBuild.class);
        when(b.getCause(GerritCause.class)).thenReturn(null);
        annotateAndVerify(b,
            "test\ntest\nChange-Id: I1234567890123456789012345678901234567890",
            "test\ntest\nChange-Id: I1234567890123456789012345678901234567890");

        PatchsetCreated patchsetCreated = Setup.createPatchsetCreated();
        GerritCause gerritCause = new GerritCause();
        gerritCause.setEvent(patchsetCreated);
        when(b.getCause(GerritCause.class)).thenReturn(gerritCause);
        annotateAndVerify(b,
                          "test\ntest\nChange-Id: <a href='http://gerrit/r/I1234567890123456789012345678901234567890'>"
                                + "I1234567890123456789012345678901234567890</a>",
                          "test\ntest\nChange-Id: I1234567890123456789012345678901234567890");

        annotateAndVerify(b,
                          "xxxChange-Id: I1234567890123456789012345678901234567890",
                          "xxxChange-Id: I1234567890123456789012345678901234567890");

        annotateAndVerify(b,
                          "Change-Id: I1234567890123456789012345678901234567890ffff",
                          "Change-Id: I1234567890123456789012345678901234567890ffff");
    }

    /**
     * Utility method.
     * @param b b
     * @param expected expected
     * @param plain plain
     */
    private void annotateAndVerify(AbstractBuild<?, ?> b, String expected, String plain) {
        MarkupText t = new MarkupText(plain);
        new ChangeIdAnnotator().annotate(b, null, t);
        System.out.println(t.toString(true));
        Assert.assertEquals(expected, t.toString(true));
    }
}
