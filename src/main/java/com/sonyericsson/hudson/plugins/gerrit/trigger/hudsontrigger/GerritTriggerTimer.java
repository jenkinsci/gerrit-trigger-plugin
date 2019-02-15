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

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonymobile.tools.gerrit.gerritevents.GerritDefaultValues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages the timer that is used for each GerritTrigger TimerTask that
 * is active.
 *
 * @author Fredrik Abrahamson &lt;fredrik.abrahamson@sonymobile.com&gt;
 */
public final class GerritTriggerTimer {

    /**
     * Wait this many milliseconds before the first call to a TimerTask.
     */
    protected static final long DELAY_MILLISECONDS = 5000;
    private static final Logger logger = LoggerFactory.getLogger(GerritTriggerTimer.class);

    /**
     * The instance used by the singleton mechanism.
     */
    private static GerritTriggerTimer instance = null;


    /**
     * The private constructor (this is a singleton class).
     */
    private GerritTriggerTimer() {

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

    //CS IGNORE LineLength FOR NEXT 5 LINES. REASON: JavaDoc

    /**
     * Figures out what
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig#getDynamicConfigRefreshInterval()}
     * to use.
     *
     * @param trigger the trigger that needs a refresh interval.
     *
     * @return the refresh interval in ms.
     * @see #calculateAverageDynamicConfigRefreshInterval()
     */
    private long calculateDynamicConfigRefreshInterval(@Nonnull GerritTrigger trigger) {
        if (trigger.isAnyServer()) {
            List<GerritServer> servers = PluginImpl.getServers_();
            if (servers.isEmpty()) {
                return GerritDefaultValues.DEFAULT_DYNAMIC_CONFIG_REFRESH_INTERVAL;
            } else {
                //Do an average just for giggles
                return calculateAverageDynamicConfigRefreshInterval();
            }
        } else {
            //get the actual if it exists.
            GerritServer server = PluginImpl.getServer_(trigger.getServerName());
            if (server != null) {
                return server.getConfig().getDynamicConfigRefreshInterval();
            } else {
                //Do an average just for giggles
                return calculateAverageDynamicConfigRefreshInterval();
            }
        }
    }

    //CS IGNORE LineLength FOR NEXT 5 LINES. REASON: JavaDoc

    /**
     * Calculates the average
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig#getDynamicConfigRefreshInterval()}
     * among the configured GerritServers.
     *
     * @return the average value.
     */
    long calculateAverageDynamicConfigRefreshInterval() {
        long total = 0;
        for (GerritServer server : PluginImpl.getServers_()) {
            total += server.getConfig().getDynamicConfigRefreshInterval();
        }
        long average = total / Math.max(1, PluginImpl.getServers_().size()); //Avoid division by 0
        return Math.max(GerritDefaultValues.MINIMUM_DYNAMIC_CONFIG_REFRESH_INTERVAL, average);
    }

    /**
     * Schedule a TimerTask according to the two constants above.
     *
     * @param trigger the trigger associated with the task
     * @param timerTask the TimerTask to be scheduled
     */
    public void schedule(GerritTriggerTimerTask timerTask, @Nonnull GerritTrigger trigger) {
        long timerPeriod = TimeUnit.SECONDS.toMillis(calculateDynamicConfigRefreshInterval(trigger));
        try {
            logger.debug("Schedule task " + timerTask + " for every " + timerPeriod + "ms");
            jenkins.util.Timer.get().scheduleWithFixedDelay(timerTask, DELAY_MILLISECONDS, timerPeriod,
                                                            TimeUnit.MILLISECONDS);
        } catch (IllegalArgumentException iae) {
            logger.error("Attempted use of negative delay", iae);
        } catch (IllegalStateException ise) {
            logger.error("Attempted re-use of TimerTask", ise);
        }
    }
}
