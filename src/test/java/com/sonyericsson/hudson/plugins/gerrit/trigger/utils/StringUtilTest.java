package com.sonyericsson.hudson.plugins.gerrit.trigger.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * this is used to test function of StringUtil.
 * @author Bruce.zu &lt;bruce.zu@sonyericsson.com&gt;
 */
class StringUtilTest {

    /**
     * test escapeQuotes() of StringUtil
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil}.
     */
    @Test
    void escapeQuotesTests() {
        String valueOfParameter = "xxx\"xxx\"xxxx";
        String escapedString = StringUtil.escapeQuotes(valueOfParameter);
        String expectedString = "xxx\\\"xxx\\\"xxxx";
        assertEquals(expectedString, escapedString);
    }
}
