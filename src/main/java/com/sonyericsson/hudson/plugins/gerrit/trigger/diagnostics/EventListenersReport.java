/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 CloudBees Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritProjectListUpdater;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.EventListener;
import com.sonyericsson.hudson.plugins.gerrit.trigger.playback.GerritMissedEventsPlaybackManager;
import com.sonymobile.tools.gerrit.gerritevents.GerritEventListener;
import com.sonymobile.tools.gerrit.gerritevents.GerritHandler;
import hudson.model.ModelObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * A {@link Diagnostics} page showing
 * the list of registered {@link com.sonymobile.tools.gerrit.gerritevents.GerritEventListener}s.
 *
 * @author Robert Sandell &lt;rsandell@cloudbees.com&gt;.
 */
public class EventListenersReport implements ModelObject {

    private Set<EventListener> jobs;
    private Set<GerritEventListener> others;

    /**
     * The constructor.
     *
     * @param jobs the job specific listeners
     * @param others the other non job listeners
     */
    /*package*/ EventListenersReport(Set<EventListener> jobs, Set<GerritEventListener> others) {
        this.jobs = jobs;
        this.others = others;
    }

    /**
     * The list of job specific listeners.
     *
     * An ordered set of {@link EventListener}s.
     *
     * @return the listeners for jobs
     */
    public Set<EventListener> getJobs() {
        return jobs;
    }

    /**
     * Any other listeners that are not {@link EventListener}s for jobs.
     *
     * @return the other listeners.
     */
    public Set<GerritEventListener> getOthers() {
        return others;
    }

    /**
     * A shorter/more descriptive name to display for any other listener than {@link EventListener}s.
     * The default is to use {@link Class#getSimpleName()} but for some can contain a bit more information
     * like which {@link GerritServer} the listener is targeting,
     * for example {@link GerritProjectListUpdater} and {@link GerritMissedEventsPlaybackManager}.
     *
     * @param listener the listener to display a name for.
     * @return the display name of the listener.
     */
    @Restricted(NoExternalUse.class)
    public String getName(GerritEventListener listener) {
        /*TODO this should really be up to the listener itself as to how it wants to represent itself
        Perhaps via an optional name.jelly view?
        */
        if (listener instanceof GerritProjectListUpdater) {
            String serverName = ((GerritProjectListUpdater)listener).getServerName();
            GerritServer server = PluginImpl.getServer_(serverName);
            if (server != null) {
                return Messages.GerritProjectListUpdater_For(server.getDisplayName());
            } else {
                return Messages.GerritProjectListUpdater_For(serverName);
            }
        } else if (listener instanceof GerritMissedEventsPlaybackManager) {
            String serverName = ((GerritMissedEventsPlaybackManager)listener).getServerName();
            GerritServer server = PluginImpl.getServer_(serverName);
            if (server != null) {
                return Messages.GerritMissedEventsPlaybackManager_For(server.getDisplayName());
            } else {
                return Messages.GerritMissedEventsPlaybackManager_For(serverName);
            }
        } else {
            return listener.getClass().getSimpleName();
        }
    }

    /**
     * Produces a report from the active {@link GerritHandler}.
     *
     * @return the report.
     * @see PluginImpl#getHandler_()
     */
    @CheckForNull
    public static EventListenersReport report() {
        GerritHandler handler = PluginImpl.getHandler_();
        if (handler != null) {
            Set<EventListener> jobs = new TreeSet<EventListener>(new Comparator<EventListener>() {
                @Override
                public int compare(EventListener o1, EventListener o2) {
                    return o1.getJob().compareTo(o2.getJob());
                }
            });
            Set<GerritEventListener> others = new LinkedHashSet<GerritEventListener>();

            Set<GerritEventListener> view = handler.getGerritEventListenersView();
            for (GerritEventListener listener : view) {
                if (listener instanceof EventListener) {
                    jobs.add((EventListener)listener);
                } else {
                    others.add(listener);
                }
            }
            return new EventListenersReport(jobs, others);
        } else {
            return null;
        }
    }

    @Override
    public String getDisplayName() {
        return Messages.EventListenersReport_DisplayName();
    }
}
