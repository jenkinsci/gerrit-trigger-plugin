package com.sonyericsson.hudson.plugins.gerrit.trigger.utils;

import org.junit.Assert;
import org.junit.Test;

/**
 * this is used to test function of StringUtil.
 * @author Bruce.zu &lt;bruce.zu@sonyericsson.com&gt;
 */
public class StringUtilTest {

    /**
     * test escapeQuotes() of StringUtil
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil}.
     */
    @Test
    public void escapeQuotesTests() {

        String valueOfParameter = "xxx\"xxx\"xxxx";
        String escapedString = StringUtil.escapeQuotes(valueOfParameter);
        String expectedString = "xxx\\\"xxx\\\"xxxx";
        Assert.assertEquals(expectedString, escapedString);


    }
}
