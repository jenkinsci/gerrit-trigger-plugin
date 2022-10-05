/*
 *
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.config;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonymobile.tools.gerrit.gerritevents.watchdog.WatchTimeExceptionData;
import hudson.Extension;
import io.jenkins.plugins.casc.Attribute;
import io.jenkins.plugins.casc.BaseConfigurator;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.Configurator;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.impl.attributes.MultivaluedAttribute;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import io.jenkins.plugins.casc.model.Sequence;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configure JCasC.
 */
@Restricted(NoExternalUse.class)
@Extension(optional = true)
public class GerritJcascConfigurator extends BaseConfigurator<PluginImpl> {

    /**
     * Empty constructor.
     */
    public GerritJcascConfigurator() {
        super();
    }

    @Override
    protected PluginImpl instance(Mapping mapping, ConfigurationContext configurationContext) {
        return PluginImpl.getInstance();
    }

    @Override
    @NonNull
    public String getName() {
        return PluginImpl.SYMBOL_NAME;
    }

    @Override
    public String getDisplayName() {
        return PluginImpl.DISPLAY_NAME;
    }

    @Override
    public Class<PluginImpl> getTarget() {
        return PluginImpl.class;
    }

    @Override
    protected void configure(
        Mapping config, PluginImpl instance, boolean dryrun, ConfigurationContext context
    ) throws ConfiguratorException {
        if (!dryrun) {
            for (GerritServer oldServer : instance.getServers()) {
                oldServer.stopConnection();
                oldServer.stop();
            }
        }

        try {
            super.configure(config, instance, dryrun, context);
        } catch (IllegalArgumentException ex) {
            throw new ConfiguratorException(this, "Failed configuring gerrit trigger", ex);
        }

        if (!dryrun) {
            instance.getPluginConfig().updateEventFilter();

            for (GerritServer server : instance.getServers()) {
                server.getConfig().setNumberOfSendingWorkerThreads(
                    instance.getPluginConfig().getNumberOfSendingWorkerThreads()
                );
                server.start();
            }
        }
    }

    /**
     * Inject `config` field explicitly as BaseConfigurator cannot detect this ("type is abstract but not Describable").
     * The methods are using interface, but we have to point the `config` property to concrete class.
     */
    @Extension(optional = true)
    public static final class ServerConfigurator extends BaseConfigurator<GerritServer> {

        @Override
        protected GerritServer instance(Mapping mapping, ConfigurationContext context) {
            return new GerritServer();
        }

        @Override
        public Class<GerritServer> getTarget() {
            return GerritServer.class;
        }

        @Override
        public Set<Attribute<GerritServer, ?>> describe() {
            Set<Attribute<GerritServer, ?>> describe = super.describe();
            describe.add(new Attribute<GerritServer, IGerritHudsonTriggerConfig>("config", Config.class));
            return describe;
        }
    }

    /**
     * Cannot use BaseConfigurator as {@link WatchTimeExceptionData} is immutable.
     */
    @Extension(optional = true)
    public static final class WatchTimeExceptionDataConfigurator implements Configurator<WatchTimeExceptionData> {

        private static final String DAYS_OF_WEEK = "daysOfWeek";
        private static final String TIMES_OF_DAY = "timesOfDay";

        private static final Map<String, Integer> DAY_NAME_TO_ORDINAL = new HashMap<>();
        static {
            DAY_NAME_TO_ORDINAL.put("monday", Calendar.MONDAY);
            DAY_NAME_TO_ORDINAL.put("tuesday", Calendar.TUESDAY);
            DAY_NAME_TO_ORDINAL.put("wednesday", Calendar.WEDNESDAY);
            DAY_NAME_TO_ORDINAL.put("thursday", Calendar.THURSDAY);
            DAY_NAME_TO_ORDINAL.put("friday", Calendar.FRIDAY);
            DAY_NAME_TO_ORDINAL.put("saturday", Calendar.SATURDAY);
            DAY_NAME_TO_ORDINAL.put("sunday", Calendar.SUNDAY);
        }

        @Override
        public Class<WatchTimeExceptionData> getTarget() {
            return WatchTimeExceptionData.class;
        }

        @Override
        public Set<Attribute<WatchTimeExceptionData, ?>> describe() {
            Set<Attribute<WatchTimeExceptionData, ?>> attributes = new HashSet<>();
            Collections.addAll(attributes,
                    new MultivaluedAttribute<WatchTimeExceptionData, String>(DAYS_OF_WEEK, String.class).getter(
                            target -> Arrays.stream(target.getDaysOfWeek())
                                    .mapToObj(WatchTimeExceptionDataConfigurator.this::ordinalToName)
                                    .collect(Collectors.toList()
                    )),
                    new MultivaluedAttribute<WatchTimeExceptionData, WatchTimeExceptionData.TimeSpan>(
                            TIMES_OF_DAY, WatchTimeExceptionData.TimeSpan.class
                    ).getter(WatchTimeExceptionData::getTimesOfDay)
            );
            return Collections.unmodifiableSet(attributes);
        }

        /**
         * Translate day name into number.
         * @param dayName Day name.
         * @return Day number (1-7)
         */
        private int nameToOrdinal(String dayName) {
            Integer ordinal = DAY_NAME_TO_ORDINAL.get(dayName.toLowerCase());
            if (ordinal == null) {
                throw new IllegalArgumentException("Unknown day of week for name: " + dayName);
            }

            return ordinal;
        }

        /**
         * Translate day number into day name.
         * @param dayNumber Day number (1-7).
         * @return Day name
         */
        private String ordinalToName(int dayNumber) {
            for (Map.Entry<String, Integer> es : DAY_NAME_TO_ORDINAL.entrySet()) {
                if (es.getValue() == dayNumber) {
                    return es.getKey();
                }
            }

            throw new IllegalArgumentException("Unknown day of week for ordinal: " + dayNumber);
        }

        /**
         * Configure object.
         * @param config Text config.
         * @param context Context
         * @return New object.
         * @throws ConfiguratorException In case of problems.
         */
        @Override
        public WatchTimeExceptionData configure(
                CNode config, ConfigurationContext context
        ) throws ConfiguratorException {
            Mapping mapping;
            if (config != null) {
                mapping = config.asMapping();
            } else {
                mapping = Mapping.EMPTY;
            }

            try {
                Sequence days = mapping.get(DAYS_OF_WEEK).asSequence();
                int[] dayNumbers = new int[days.size()];
                for (int i = 0; i < days.size(); i++) {
                    dayNumbers[i] = nameToOrdinal(days.get(i).asScalar().getValue());
                }

                ArrayList<WatchTimeExceptionData.TimeSpan> timeSpans = new ArrayList<>();
                for (CNode timeSpanNode : mapping.get(TIMES_OF_DAY).asSequence()) {
                    Mapping tsm = timeSpanNode.asMapping();
                    String from = tsm.get("from").asScalar().getValue();
                    String to = tsm.get("to").asScalar().getValue();
                    WatchTimeExceptionData.TimeSpan timeSpan = new WatchTimeExceptionData.TimeSpan(
                            WatchTimeExceptionData.Time.createTimeFromString(from),
                            WatchTimeExceptionData.Time.createTimeFromString(to)
                    );
                    timeSpans.add(timeSpan);
                }
                return new WatchTimeExceptionData(dayNumbers, timeSpans);
            } catch (IllegalArgumentException ex) {
                throw new ConfiguratorException(
                        this,
                        "Failed configuring " + getTarget() + " from " + mapping,
                        ex
                );
            }
        }

        @Override
        public WatchTimeExceptionData check(CNode config, ConfigurationContext context) throws ConfiguratorException {
            // Just do what #configure does as long as #configure is a pure factory method
            return configure(config, context);
        }
    }

    /**
     * Configure {@link com.sonymobile.tools.gerrit.gerritevents.watchdog.WatchTimeExceptionData.TimeSpan}.
     */
    @Extension(optional = true)
    public static final class TimeSpanConfigurator implements Configurator<WatchTimeExceptionData.TimeSpan> {

        @Override
        public Class<WatchTimeExceptionData.TimeSpan> getTarget() {
            return WatchTimeExceptionData.TimeSpan.class;
        }

        @Override
        public Set<Attribute<WatchTimeExceptionData.TimeSpan, ?>> describe() {
            Set<Attribute<WatchTimeExceptionData.TimeSpan, ?>> attributes = new HashSet<>();
            Collections.addAll(attributes,
                    new Attribute<WatchTimeExceptionData.TimeSpan, String>("from", String.class).getter(
                            ts -> ts.getFrom().getHourAsString() + ":" + ts.getFrom().getMinuteAsString()
                    ),
                    new Attribute<WatchTimeExceptionData.TimeSpan, String>("to", String.class).getter(
                            ts -> ts.getTo().getHourAsString() + ":" + ts.getTo().getMinuteAsString()
                    )
            );
            return Collections.unmodifiableSet(attributes);
        }

        @Override
        public WatchTimeExceptionData.TimeSpan configure(CNode config, ConfigurationContext context) {
            throw new UnsupportedOperationException("Configured by " + WatchTimeExceptionDataConfigurator.class);
        }

        @Override
        public WatchTimeExceptionData.TimeSpan check(CNode config, ConfigurationContext context) {
            throw new UnsupportedOperationException("Configured by " + WatchTimeExceptionDataConfigurator.class);
        }
    }
}
