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

import net.sf.json.JSONObject;

import java.util.Calendar;
import java.util.List;

/**
 * Data for when {@link StreamWatchdog} should not take action.
 *
 * @author Robert Sandell &lt;robert.sandell@sonymobile.com&gt;
 * @see StreamWatchdog
 * @see Calendar
 */
public class WatchTimeExceptionData {
    private int[] daysOfWeek;
    private List<TimeSpan> timesOfDay;

    /**
     * Standard Constructor.
     *
     * @param daysOfWeek what days of the week should be exempted.
     *                   The day numbers as specified by {@link Calendar#DAY_OF_WEEK}
     * @param timesOfDay the time spans during any day that should be exempted.
     * @see Calendar
     */
    public WatchTimeExceptionData(int[] daysOfWeek, List<TimeSpan> timesOfDay) {
        this.daysOfWeek = daysOfWeek;
        this.timesOfDay = timesOfDay;
    }

    /**
     * Default constructor.
     * <strong>Only use this if you are a serializer.</strong>
     */
    public WatchTimeExceptionData() {
    }

    /**
     * The days of the week that should be exempted.
     * As specified by {@link Calendar#DAY_OF_WEEK}.
     *
     * @return the days of the week that should be exempted.
     *
     * @see Calendar#MONDAY
     * @see Calendar#TUESDAY
     * @see Calendar#WEDNESDAY
     * @see Calendar#THURSDAY
     * @see Calendar#FRIDAY
     * @see Calendar#SATURDAY
     * @see Calendar#SUNDAY
     * @see Calendar#DAY_OF_WEEK
     */
    public int[] getDaysOfWeek() {
        return daysOfWeek;
    }

    /**
     * The time spans during any day that should be exempted.
     *
     * @return the times
     */
    public List<TimeSpan> getTimesOfDay() {
        return timesOfDay;
    }

    /**
     * If {@link #isExceptionToday()} or {@link #isExceptionAtThisTime()}.
     *
     * @return true if so.
     */
    public boolean isExceptionNow() {
        return isExceptionToday() || isExceptionAtThisTime();
    }

    /**
     * If the current time is configured as an exception.
     *
     * @return true if so.
     * @see #timesOfDay
     */
    public boolean isExceptionAtThisTime() {
        Time now = new Time();
        for (TimeSpan span : timesOfDay) {
            if (span.isWithin(now)) {
                return true;
            }
        }
        return false;
    }

    /**
     * If today is a day configured as an exception.
     *
     * @return true if so.
     * @see #daysOfWeek
     */
    public boolean isExceptionToday() {
        if (daysOfWeek != null && daysOfWeek.length > 0) {
            int dayOfWeekNow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
            for (int exceptionDay : daysOfWeek) {
                if (exceptionDay == dayOfWeekNow) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if any exception data has been added, either days or time.
     *
     * @return true if any exception data has been added, either days or time.
     */
    public boolean isEnabled() {
        if (daysOfWeek != null && daysOfWeek.length > 0) {
            return true;
        }
        if (timesOfDay != null && timesOfDay.size() > 0) {
            return true;
        }
        return false;
    }

    /**
     * Returns true if the selected day is in the list of exceptions.
     *
     * @param day the day to check for.
     * @return true if the day is in the list of exceptions.
     */
    public boolean isExceptionDay(int day) {
        for (int i : daysOfWeek) {
            if (day == i) {
                return true;
            }
        }
        return false;
    }

    /**
     * A time span from a beginning to an end.
     */
    public static class TimeSpan {
        private Time from;
        private Time to;

        /**
         * Default constructor.
         */
        TimeSpan() {
        }

        /**
         * Standard Constructor.
         * Throws an {@link IllegalArgumentException} if to is before from.
         *
         * @param from when the time span starts.
         * @param to when the time span ends.
         */
        public TimeSpan(Time from, Time to) {
            if (!from.before(to)) {
                throw new IllegalArgumentException("From should be before to.");
            }
            this.from = from;
            this.to = to;
        }

        /**
         * Creates a TimeSpan object from a JSONObject.
         *
         * @param jsonObject the JSONObject to create a TimeSpan from.
         * @return a new TimeSpan object.
         */
        public static TimeSpan createTimeSpanFromJSONObject(JSONObject jsonObject) {
            String from = jsonObject.getString("from");
            String to = jsonObject.getString("to");
            return new TimeSpan(Time.createTimeFromString(from), Time.createTimeFromString(to));
        }

        /**
         * Where the time span begins.
         *
         * @return the beginning of time.
         */
        public Time getFrom() {
            return from;
        }

        /**
         * Where the time span ends.
         *
         * @return the end of time
         */
        public Time getTo() {
            return to;
        }

        /**
         * If the provided time is within this time span.
         * <code>time &tg;= from &amp;&amp; time &lt;= to</code>
         *
         * @param time the provided time
         * @return true if <code>from &gt;= time &lt;= to</code>
         */
        public boolean isWithin(Time time) {
            return (from.before(time) || from.equals(time))
                    && (to.after(time) || to.equals(time));
        }
    }

    /**
     * A unit of time, hours and minutes in a 24h format.
     */
    public static class Time {
        /**
         * Minimum value an hour can have.
         */
        public static final int MIN_HOUR = 0;
        /**
         * Maximum value an hour can have.
         */
        public static final int MAX_HOUR = 23;
        /**
         * Minimum value an minute can have.
         */
        public static final int MIN_MINUTE = 0;
        /**
         * Maximum value an minute can have.
         */
        public static final int MAX_MINUTE = 59;

        /**
         * Minimum value a number can have that has 2 digits.
         */
        protected static final int MINIMUM_TWO_DIGIT_NUMBER = 10;
        private int hour;
        private int minute;

        /**
         * Default Constructor.
         * Constructs a new time as the time is now.
          */
        public Time() {
            Calendar now = Calendar.getInstance();
            hour = now.get(Calendar.HOUR_OF_DAY);
            minute = now.get(Calendar.MINUTE);
        }

        /**
         * Standard Constructor.
         * Will throw an {@link IllegalArgumentException} if the hours and minutes are not in 24h format.
         *
         * @param hour the hours
         * @param minute the minutes
         */
        public Time(int hour, int minute) {
            if (hour < MIN_HOUR || hour > MAX_HOUR) {
                throw new IllegalArgumentException("Hour should be in 24 hour format");
            }
            if (minute < MIN_MINUTE || minute > MAX_MINUTE) {
                throw new IllegalArgumentException("there are 60 minutes in an hour.");
            }
            this.hour = hour;
            this.minute = minute;
        }

        /**
         * Returns a new Time from a String containing both hours and minutes of from or to.
         * Will throw an {@link IllegalArgumentException} if the hours and minutes are not in 24h format.
         *
         * @param timeString the time in format hh:mm in 24 hour format.
         * @return a new Time object.
         */
        public static Time createTimeFromString(String timeString) {
            String[] split = timeString.split(":");
            if (split.length != 2) {
                throw new IllegalArgumentException("Time should be on the hh:mm format.");
            }
            int parsedHour = Integer.parseInt(split[0]);
            int parsedMinute = Integer.parseInt(split[1]);
            if (parsedHour < MIN_HOUR || parsedHour > MAX_HOUR) {
                throw new IllegalArgumentException("Hour should be in 24 hour format.");
            }
            if (parsedMinute < MIN_MINUTE || parsedMinute > MAX_MINUTE) {
                throw new IllegalArgumentException("There are 60 minutes in an hour.");
            }
            return new Time(parsedHour, parsedMinute);
        }



        /**
         * The hours 0 to 23.
         *
         * @return the hours of the time.
         */
        public int getHour() {
            return hour;
        }

        /**
         * The minutes, parts of an hour.
         *
         * @return the minutes.
         */
        public int getMinute() {
            return minute;
        }

        /**
         * String representation of hour.
         *
         * @return the hour, on the format hh.
         */
        public String getHourAsString() {
            if (hour < MINIMUM_TWO_DIGIT_NUMBER) {
                return "0" + hour;
            } else {
                return String.valueOf(hour);
            }
        }

        /**
         * String representation of minute.
         *
         * @return the hour, on the format mm.
         */
        public String getMinuteAsString() {
            if (minute < MINIMUM_TWO_DIGIT_NUMBER) {
                return "0" + minute;
            } else {
                return String.valueOf(minute);
            }
        }

        /**
         * Checks if this time is before the other time.
         *
         * @param time the other time.
         * @return true if this comes before the other
         */
        public boolean before(Time time) {
            if (hour < time.hour) {
                return true;
            } else if (hour == time.hour) {
                return minute < time.minute;
            } else {
                return false;
            }
        }

        /**
         * Checks if this time is after the other time.
         *
         * @param time the other time.
         * @return true if this comes after the other
         */
        public boolean after(Time time) {
            if (hour > time.hour) {
                return true;
            } else if (hour == time.hour) {
                return minute > time.minute;
            } else {
                return false;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Time time = (Time)o;

            if (hour != time.hour) {
                return false;
            }
            if (minute != time.minute) {
                return false;
            }

            return true;
        }

        //CS IGNORE MagicNumber FOR NEXT 6 LINES. REASON: Auto generated code.
        @Override
        public int hashCode() {
            int result = hour;
            result = 31 * result + minute;
            return result;
        }
    }
}
