/*
 * The MIT License
 *
 * Copyright (c) 2014 Ericsson
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

import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class holds events that were processed by the MissedEventPlaybackManager
 * for the most recent timestamp.
 *
 * For example, if 3 events were received at t1, they would be present in the Event Slice.
 * However, if another event is processed at t2, then the Event Slice would evict the previous
 * events and only keep the new event at t2.
 *
 * Created by scott.hebert@ericsson.com on 12/12/14.
 */
public class EventTimeSlice {

    private long timeSlice;
    /**
     * events to persist.
     */
    protected List<GerritTriggeredEvent> events = Collections.synchronizedList(new ArrayList<GerritTriggeredEvent>());

    /**
     *
     * @param ts Time slice in ms to hold events for.
     */
    public EventTimeSlice(long ts) {
        this.timeSlice = ts;
    }

    /**
     * Get the time slice in ms.
     * @return this time slice
     */
    public long getTimeSlice() {
        return timeSlice;
    }
    /**
     * Add an event to the list.
     * @param evt Event to be persisted.
     */
    public void addEvent(GerritTriggeredEvent evt) {
        events.add(evt);
    }

    /**
     * get the events for this time slice.
     * @return events that pertain to the time slice.
     */
    public List<GerritTriggeredEvent> getEvents() {
        return events;
    }

    /**
     * Creates a shallow copy of a given EventTimeSlice.
     * @param ets the EventTimeSlice to copy
     * @return the copy
     */
   public static EventTimeSlice shallowCopy(EventTimeSlice ets) {
       long initialTs = ets.getTimeSlice();
       EventTimeSlice nets = new EventTimeSlice(initialTs);
       for (GerritTriggeredEvent event : ets.getEvents()) {
           nets.addEvent(event);
       }
       return nets;
   }
}
