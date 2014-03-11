/*
 *  The MIT License
 *
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Manages the timer that is used for each GerritTrigger TimerTask that
 * is active.
 *
 * @author Fredrik Abrahamson &lt;fredrik.abrahamson@sonymobile.com&gt;
 */
public final class GerritTriggerTimer {

    /**
     * Average number of milliseconds in a second.
     */
    private static final long MILLISECONDS_PER_SECOND = 1000;
    /**
     * Wait this many milliseconds before the first call to a TimerTask
     */
    private static final long DELAY_MILLISECONDS  =  5000;
    private static final Logger logger = LoggerFactory.getLogger(GerritTriggerTimer.class);

    /**
     * The instance used by the singleton mechanism.
     */
    private static GerritTriggerTimer instance = null;

    /**
     * The timer that is doing the actual scheduling.
     */
    private Timer timer = null;

    /**
     * The private constructor (this is a singleton class).
     */
    private GerritTriggerTimer() {
        timer = new Timer(true);
    }

    /**
     * Returns the instance, and creates it first if needed.
     *
     * @return the instance of this class
     */
    public static GerritTriggerTimer getInstance() {
        if (instance == null) {
            instance = new GerritTriggerTimer();
        }
        return instance;
    }

    /**
     * Schedule a TimerTask according to the two constants above.
     *
     * @param timerTask the subclass of TimerTask to be scheduled
     * @param serverName the name of the Gerrit server.
     */
    public void schedule(TimerTask timerTask, String serverName) {
        long timerPeriod = MILLISECONDS_PER_SECOND
                    * PluginImpl.getInstance().getServer(serverName).getConfig().getDynamicConfigRefreshInterval();
        try {
            timer.schedule(timerTask, DELAY_MILLISECONDS, timerPeriod);
        } catch (IllegalArgumentException iae) {
            logger.error("Attempted use of negative delay", iae);
        } catch (IllegalStateException ise) {
            logger.error("Attempted re-use of TimerTask", ise);
        }
    }
}
