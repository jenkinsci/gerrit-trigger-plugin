/*
 * The MIT License
 *
 *  Copyright 2026 CloudBees, Inc.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.coordination.hazelcast;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;

import java.lang.reflect.Type;

/**
 * Custom Gson type adapter for polymorphic GerritTriggeredEvent serialization.
 * <p>
 * Handles serialization and deserialization of GerritTriggeredEvent subclasses
 * by including type information in the JSON. This allows proper reconstruction
 * of the correct concrete event type when deserializing from Hazelcast.
 * <p>
 * JSON format:
 * <pre>
 * {
 *   "@type": "com.sonymobile.tools.gerrit.gerritevents.dto.events.PatchsetCreated",
 *   "data": { ... actual event properties ... }
 * }
 * </pre>
 *
 */
public class PolymorphicEventTypeAdapter
        implements JsonSerializer<GerritTriggeredEvent>, JsonDeserializer<GerritTriggeredEvent> {

    private static final String TYPE_FIELD = "@type";
    private static final String DATA_FIELD = "data";

    @Override
    public JsonElement serialize(GerritTriggeredEvent src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        result.addProperty(TYPE_FIELD, src.getClass().getName());
        result.add(DATA_FIELD, context.serialize(src, src.getClass()));
        return result;
    }

    @Override
    public GerritTriggeredEvent deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        if (!jsonObject.has(TYPE_FIELD)) {
            throw new JsonParseException("Missing type field '" + TYPE_FIELD + "' in JSON");
        }

        String className = jsonObject.get(TYPE_FIELD).getAsString();
        JsonElement data = jsonObject.get(DATA_FIELD);

        try {
            Class<?> clazz = Class.forName(className);
            return context.deserialize(data, clazz);
        } catch (ClassNotFoundException e) {
            throw new JsonParseException("Unknown event type: " + className, e);
        }
    }
}
