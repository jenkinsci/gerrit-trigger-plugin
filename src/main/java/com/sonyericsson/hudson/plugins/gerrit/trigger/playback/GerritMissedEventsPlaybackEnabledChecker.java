/*
 * The MIT License
 *
 * Copyright (c) 2017 Red Hat
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

package com.sonyericsson.hudson.plugins.gerrit.trigger.playback;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * Utility class to periodically verify whether MissedEventsPlaybackManager
 * can be enabled/disabled.
 */
@Extension
public class GerritMissedEventsPlaybackEnabledChecker extends AsyncPeriodicWork {

    // default check period in seconds
    private static final long DEFAULTCHECKPERIOD = 2;
    private final Long recurrencePeriod;
    private static final Logger logger =
            LoggerFactory.getLogger(GerritMissedEventsPlaybackEnabledChecker.class);


    /**
     * Default constructor.
     */
    public GerritMissedEventsPlaybackEnabledChecker() {
        super("GerritMissedEventsPlaybackEnabledChecker");
        recurrencePeriod =
                Long.getLong("com.sonyericsson.hudson.plugins.gerrit.trigger.playback.checkEnabledPeriod",
                        TimeUnit.SECONDS.toMillis(DEFAULTCHECKPERIOD));
        logger.debug("checkIfEventsLogPluginSupported check period is {0}ms",
                recurrencePeriod);
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        List<GerritServer> servers = PluginImpl.getServers_();
        for (GerritServer gs: servers) {
            if (gs != null && gs.getMissedEventsPlaybackManager() != null) {
                logger.debug("Performing plugin check for server: {0}", gs.getName());
                gs.getMissedEventsPlaybackManager().performCheck();
            } else {
                logger.debug("Skip plugin check, because server is not completely initialised");
            }
        }
    }

    @Override
    public long getRecurrencePeriod() {
        return recurrencePeriod;
    }
}
