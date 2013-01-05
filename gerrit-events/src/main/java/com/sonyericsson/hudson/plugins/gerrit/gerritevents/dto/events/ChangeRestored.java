/*
 * The MIT License
 *
 * Copyright 2012 Intel, Inc. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events;

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.RESTORER;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventType;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritJsonEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account;
import net.sf.json.JSONObject;

/**
 * A DTO representation of the change-restored Gerrit Event.
 */
public class ChangeRestored extends ChangeBasedEvent implements GerritJsonEvent {

    /**
     * The person who triggered this event.
     */
    private Account restorer;

    /**
     * Default constructor.
     */
    public ChangeRestored() {
    }

    /**
     * Get the restorer who triggered this event.
     * @return the restorer
     */
    public Account getRestorer() {
        return this.restorer;
    }

    /**
     * Set the restorer for this event.
     * @param restorer the restorer to set
     */
    public void setRestorer(Account restorer) {
        this.restorer = restorer;
    }

    /**
     * Constructor that fills data directly.
     *
     * @param json the JSON Object
     * @see #fromJson(net.sf.json.JSONObject)
     */
    @SuppressWarnings("OverridableMethodCallInConstructor")
    public ChangeRestored(JSONObject json) {
        fromJson(json);
    }

    @Override
    public GerritEventType getEventType() {
        return GerritEventType.CHANGE_RESTORED;
    }

    @Override
    public boolean isScorable() {
        return false;
    }

    @Override
    public void fromJson(JSONObject json) {
        super.fromJson(json);

        if (json.containsKey(RESTORER)) {
            this.restorer = new Account(json.getJSONObject(RESTORER));
        }
    }
}
