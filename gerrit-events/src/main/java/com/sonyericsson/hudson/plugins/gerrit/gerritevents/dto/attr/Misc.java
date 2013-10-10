/*
 *  The MIT License
 *
 *  Copyright 2013 rinrinne. All rights reserved.
 *  Copyright 2013 Sony Mobile Communications AB. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr;

import net.sf.json.JSONObject;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritJsonEventFactory.getString;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.EVENT_ID;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritJsonDTO;

/**
 * Represents a Gerrit JSON Provider DTO.
 * An Misc that is related to an event or attribute.
 * Used for additional information.
 *
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public class Misc implements GerritJsonDTO {

    /**
     * The event id.
     * Suppose SHA-256 hash from a line of stream-events.
     */
    private String eventId;

    /**
     * Default constructor.
     */
    public Misc() {
    }

    /**
     * Constructor that fills with data directly.
     *
     * @param json the JSON Object with data.
     * @see #fromJson(net.sf.json.JSONObject)
     */
    public Misc(JSONObject json) {
        fromJson(json);
    }

    /**
     * For easier testing.
     * @param eventId the event id.
     */
    public Misc(String eventId) {
        this.eventId = eventId;
    }

    @Override
    public void fromJson(JSONObject json) {
        eventId = getString(json, EVENT_ID);
    }

    /**
     * Get event id.
     * @return the event id.
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Set event id.
     * @param eventId the event id.
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
}
