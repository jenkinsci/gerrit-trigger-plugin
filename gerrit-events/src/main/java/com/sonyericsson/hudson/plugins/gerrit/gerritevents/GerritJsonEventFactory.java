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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventType;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritJsonEvent;
import java.lang.reflect.Constructor;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains utility methods for handling JSONObjects.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public final class GerritJsonEventFactory {

    private static final Logger logger = LoggerFactory.getLogger(GerritJsonEventFactory.class);

    /**
     * Empty private Constructor to hinder instantiation.
     */
    private GerritJsonEventFactory() {
        //Empty
    }

    /**
     * Creates a GerritJsonEvent DTO out of the provided JSONObject.
     * The jsonObject is assumed to be interesting and usable
     * as defined by {@link #getJsonObjectIfInterestingAndUsable(java.lang.String) }
     * @param jsonObject the parsed JSON Object
     * @return the POJO DTO representation of the jsonObject.
     */
    public static GerritJsonEvent getEvent(JSONObject jsonObject) {

        GerritEventType type = GerritEventType.findByTypeValue(jsonObject.getString("type"));
        //the type has already been verified by the method that gets the JSONObject,
        //so any NullPointerExceptions or similar problems are the caller's own fault.
        Class<? extends GerritJsonEvent> clazz = type.getEventRepresentative();
        GerritJsonEvent event = null;
        try {
            logger.debug("Interesting event with a class defined. Searching shorthand constructor.");
            Constructor<? extends GerritJsonEvent> constructor = clazz.getConstructor(JSONObject.class);
            event = constructor.newInstance(jsonObject);
            logger.trace("Event created from shorthand constructor.");
        } catch (NoSuchMethodException ex) {
            logger.debug("Constructor with JSONObject as parameter missing, trying default constructor.", ex);
        } catch (Exception ex) {
            logger.debug("Error when using event constructor with JSONObject, trying default constructor.", ex);
        }
        if (event == null) {
            logger.debug("Trying default constructor.");
            try {
                event = clazz.newInstance();
                event.fromJson(jsonObject);
                logger.trace("Event created from default constructor");
            } catch (InstantiationException ex) {
                logger.error("Could not create an interesting GerritJsonEvent via default constructor "
                        + "(DESIGN ERROR).", ex);
            } catch (IllegalAccessException ex) {
                logger.error("Could not create an interesting GerritJsonEvent via default constructor"
                        + "(DESIGN ERROR).", ex);
            }
        }
        logger.debug("Returning an event: {}", event);
        return event;
    }

    /**
     * Tells if the provided string is a valid JSON string
     * and represents an interesting and usable {@link GerritJsonEvent}
     * as defined by {@link #getJsonObjectIfInterestingAndUsable(java.lang.String) }.
     * This is the same as doing {@code (getJsonObjectIfInterestingAndUsable(jsonString) != null)}
     * @param jsonString the JSON formatted String.
     * @return true if it is so.
     */
    public static boolean isInterestingAndUsable(String jsonString) {
        return getJsonObjectIfInterestingAndUsable(jsonString) != null;
    }

    /**
     * Tries to parse the provided string into a JSONObject and returns it if it is interesting and usable.
     * If it is interesting is determined by:
     * <ol>
     *  <li>The object contains a String field named type</li>
     *  <li>The String returns a non null GerritEventType from
     *      {@link GerritEventType#findByTypeValue(java.lang.String) }</li>
     *  <li>The property {@link GerritEventType#isInteresting() } == true</li>
     * </ol>
     * It is usable if the type's {@link GerritEventType#getEventRepresentative() } is not null.
     * @param jsonString the string to parse.
     * @return an interesting and usable JSONObject, or null if it is not.
     */
    public static JSONObject getJsonObjectIfInterestingAndUsable(String jsonString) {
        logger.trace("finding event for jsonString: {}", jsonString);
        if (jsonString == null || jsonString.length() <= 0) {
            return null;
        }
        try {
            JSONObject jsonObject = (JSONObject)JSONSerializer.toJSON(jsonString);
            logger.debug("Parsed a JSONObject");
            if (jsonObject.get("type") != null) {
                logger.trace("It has a type");
                GerritEventType type = GerritEventType.findByTypeValue(jsonObject.getString("type"));
                logger.debug("Type found: {}", type);
                if (type != null && type.isInteresting() && type.getEventRepresentative() != null) {
                    logger.debug("It is interesting and usable.");
                    return jsonObject;
                }
            }
        } catch (Exception ex) {
            logger.warn("Unanticipated error when examining JSON String", ex);
        }
        return null;
    }

    /**
     * Tries to parse the provided string into a GerritJsonEvent DTO if it is interesting and usable.
     * @param jsonString the JSON formatted string.
     * @return the Event.
     * @see #getJsonObjectIfInterestingAndUsable(String)
     */
    public static GerritJsonEvent getEventIfInteresting(String jsonString) {
        logger.trace("finding event for jsonString: {}", jsonString);
        try {
            JSONObject jsonObject = getJsonObjectIfInterestingAndUsable(jsonString);
            if (jsonObject != null) {
                return getEvent(jsonObject);
            }

        } catch (Exception ex) {
            logger.warn("Unanticipated error when creating DTO representation of JSON string.", ex);
        }
        return null;
    }

    /**
     * Returns the value of a JSON property as a String if it exists otherwise returns the defaultValue.
     * @param json the JSONObject to check.
     * @param key the key
     * @param defaultValue the value to return if the key is missing
     * @return the value for the key as a string.
     */
    public static String getString(JSONObject json, String key, String defaultValue) {
        if (json.containsKey(key)) {
            return json.getString(key);
        } else {
            return defaultValue;
        }
    }

    /**
     * Returns the value of a JSON property as a String if it exists otherwise returns null.
     * Same as calling {@link #getString(net.sf.json.JSONObject, java.lang.String, java.lang.String) }
     * with null as defaultValue.
     * @param json the JSONObject to check.
     * @param key the key
     * @return the value for the key as a string.
     */
    public static String getString(JSONObject json, String key) {
        return getString(json, key, null);
    }
}
