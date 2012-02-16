/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeMerged;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.RefUpdated;

import java.util.LinkedList;
import java.util.List;

/**
 * Representation of the type of event, if they are interesting and what class to use to parse the JSON string.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public enum GerritEventType {

    /**
     * A patchset-created event. Intersting and usable.
     */
     PATCHSET_CREATED("patchset-created", true, PatchsetCreated.class),
     /**
      * A change-abandoned event. Interesting and usable.
      */
     CHANGE_ABANDONED("change-abandoned", true, ChangeAbandoned.class),
     /**
      * A change-merged event.
      */
     CHANGE_MERGED("change-merged", true, ChangeMerged.class),
     /**
      * A comment-added event.
      */
     COMMENT_ADDED("comment-added", true, CommentAdded.class),
     /**
      * A ref-updated event.
      */
     REF_UPDATED("ref-updated", true, RefUpdated.class);

    private String typeValue;
    private boolean interesting;
    private Class<? extends GerritJsonEvent> eventRepresentative;

    /**
     * Constructs an instance of the enum.
     * @param typeValue The value of the type property in the JSON object.
     * @param interesting If this event type is interesting from a functionality perspective.
     * @param eventRepresentative the DTO class that represents this kind of event.
     */
    private GerritEventType(String typeValue, boolean interesting,
                            Class<? extends GerritJsonEvent> eventRepresentative) {
        this.typeValue = typeValue;
        this.interesting = interesting;
        this.eventRepresentative = eventRepresentative;
    }

    /**
     * The value of the type property in the JSON object.
     * @return the type-value.
     */
    public String getTypeValue() {
        return typeValue;
    }

    /**
     * If this event type is interesting from a functionality perspective.
     * @return true if it is interesting.
     */
    public boolean isInteresting() {
        return interesting;
    }

    /**
     * Gets the DTO class that represents this kind of event.
     * @return the class object.
     */
    public Class<? extends GerritJsonEvent> getEventRepresentative() {
        return eventRepresentative;
    }

    /**
     * Finds the event type for the specified type-value.
     * @param typeValue the value of the JSON object's type property.
     * @return the event type or null if nothing was found.
     */
    public static GerritEventType findByTypeValue(String typeValue) {
        for (GerritEventType type : values()) {
            if (type.getTypeValue().equalsIgnoreCase(typeValue)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Lists all event types that are interesting.
     * @return The interesting event-types.
     * @see #isInteresting()
     */
    public static GerritEventType[] getInterestingEventTypes() {
        List<GerritEventType> list = new LinkedList<GerritEventType>();
        for (GerritEventType type : values()) {
            if (type.isInteresting()) {
                list.add(type);
            }
        }
        return list.toArray(new GerritEventType[list.size()]);
    }
}
