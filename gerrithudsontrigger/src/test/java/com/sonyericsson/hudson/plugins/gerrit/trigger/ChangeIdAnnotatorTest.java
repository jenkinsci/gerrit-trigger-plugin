package com.sonyericsson.hudson.plugins.gerrit.trigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger.DescriptorImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.MockGerritHudsonTriggerConfig;
import hudson.MarkupText;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test for {@link ChangeIdAnnotator}.
 * @author Kohsuke Kawaguchi
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ Hudson.class })
public class ChangeIdAnnotatorTest {

    /**
     * the test.
     */
    @Test
    public void testFoo() {
        Map m = new HashMap();
        m.put(new DescriptorImpl(), mock(GerritTrigger.class));

        AbstractProject p = mock(AbstractProject.class);
        when(p.getTriggers()).thenReturn(m);

        annotateAndVerify(p,
                          "test\ntest\nChange-Id: <a href='http://gerrit/r/I1234567890123456789012345678901234567890'>"
                                + "I1234567890123456789012345678901234567890</a>",
                          "test\ntest\nChange-Id: I1234567890123456789012345678901234567890");

        annotateAndVerify(p,
                          "xxxChange-Id: I1234567890123456789012345678901234567890",
                          "xxxChange-Id: I1234567890123456789012345678901234567890");

        annotateAndVerify(p,
                          "Change-Id: I1234567890123456789012345678901234567890ffff",
                          "Change-Id: I1234567890123456789012345678901234567890ffff");
    }

    /**
     * Utility method.
     * @param p p
     * @param expected expected
     * @param plain plain
     */
    private void annotateAndVerify(AbstractProject p, String expected, String plain) {
        MarkupText t = new MarkupText(plain);
        new ChangeIdAnnotator().annotate(p, t, new MockGerritHudsonTriggerConfig());
        System.out.println(t.toString(true));
        Assert.assertEquals(expected, t.toString(true));
    }
}
