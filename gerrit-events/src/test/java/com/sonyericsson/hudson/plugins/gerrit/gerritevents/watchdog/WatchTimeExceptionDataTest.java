/*
 * The MIT License
 *
 * Copyright 2013 Sony Mobile Communications AB. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.sonyericsson.hudson.plugins.gerrit.gerritevents.watchdog;

import org.junit.Test;

import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

//CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: Test data.

/**
 * Tests for {@link WatchTimeExceptionData}.
 *
 * @author Robert Sandell &lt;robert.sandell@sonymobile.com&gt;
 */
public class WatchTimeExceptionDataTest {

    /**
     * Tests {@link WatchTimeExceptionData#isExceptionNow()} returns false when data is empty.
     *
     * @throws Exception if so.
     */
    @Test
    public void testIsExceptionNowFalse() throws Exception {
        WatchTimeExceptionData data = new WatchTimeExceptionData(new int[0],
                Collections.<WatchTimeExceptionData.TimeSpan>emptyList());
        assertFalse(data.isExceptionNow());
    }

    /**
     * Tests {@link WatchTimeExceptionData#isExceptionAtThisTime()} when the hours differs in the span.
     *
     * @throws Exception if so.
     */
    @Test
    public void testIsExceptionAtThisTimeHours() throws Exception {
        List<WatchTimeExceptionData.TimeSpan> spans = new LinkedList<WatchTimeExceptionData.TimeSpan>();
        Calendar from = Calendar.getInstance();
        from.add(Calendar.HOUR_OF_DAY, -1);
        Calendar to = Calendar.getInstance();
        to.add(Calendar.HOUR_OF_DAY, 1);
        spans.add(new WatchTimeExceptionData.TimeSpan(new WatchTimeExceptionData.Time(20, 0),
                new WatchTimeExceptionData.Time(23, 59)));
        spans.add(new WatchTimeExceptionData.TimeSpan(new WatchTimeExceptionData.Time(
                from.get(Calendar.HOUR_OF_DAY), 0),
                new WatchTimeExceptionData.Time(
                        to.get(Calendar.HOUR_OF_DAY), 0)));
        spans.add(new WatchTimeExceptionData.TimeSpan(new WatchTimeExceptionData.Time(8, 0),
                new WatchTimeExceptionData.Time(9, 59)));
        WatchTimeExceptionData data = new WatchTimeExceptionData(new int[0], spans);
        assertTrue(data.isExceptionAtThisTime());
    }

    /**
     * Tests {@link WatchTimeExceptionData#isExceptionAtThisTime()} when only the minutes differs in the span.
     *
     * @throws Exception if so.
     */
    @Test
    public void testIsExceptionAtThisTimeMinutes() throws Exception {
        List<WatchTimeExceptionData.TimeSpan> spans = new LinkedList<WatchTimeExceptionData.TimeSpan>();
        Calendar from = Calendar.getInstance();
        from.add(Calendar.MINUTE, -15);
        Calendar to = Calendar.getInstance();
        to.add(Calendar.MINUTE, 15);
        spans.add(new WatchTimeExceptionData.TimeSpan(new WatchTimeExceptionData.Time(
                from.get(Calendar.HOUR_OF_DAY), from.get(Calendar.MINUTE)),
                new WatchTimeExceptionData.Time(
                        to.get(Calendar.HOUR_OF_DAY), to.get(Calendar.MINUTE))));
        WatchTimeExceptionData data = new WatchTimeExceptionData(new int[0], spans);
        assertTrue(data.isExceptionAtThisTime());
    }

    /**
     * Tests {@link WatchTimeExceptionData#isExceptionAtThisTime()} when the days of week and span are empty.
     *
     * @throws Exception if so.
     */
    @Test
    public void testIsExceptionAtThisTimeEmpty() throws Exception {
        WatchTimeExceptionData data = new WatchTimeExceptionData(new int[0],
                Collections.<WatchTimeExceptionData.TimeSpan>emptyList());
        assertFalse(data.isExceptionAtThisTime());
    }

    /**
     * Tests {@link WatchTimeExceptionData#isExceptionAtThisTime()}
     * when the days of week is non-empty and the span is empty.
     *
     * @throws Exception if so.
     */
    @Test
    public void testIsExceptionToday() throws Exception {
        int[] days = new int[]{4, Calendar.getInstance().get(Calendar.DAY_OF_WEEK), 6};
        WatchTimeExceptionData data = new WatchTimeExceptionData(days,
                Collections.<WatchTimeExceptionData.TimeSpan>emptyList());
        assertTrue(data.isExceptionToday());
    }
}
