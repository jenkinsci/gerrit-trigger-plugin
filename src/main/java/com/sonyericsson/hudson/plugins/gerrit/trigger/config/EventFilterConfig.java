package com.sonyericsson.hudson.plugins.gerrit.trigger.config;

import java.util.List;

public interface EventFilterConfig {
    /**
     * Get the number of events that are supported.
     *
     * @return the size of gerrit event type enum.
     */
    int getEventTypesSize();

    /**
     * Get the list of events that are filtered in.
     *
     * @return the list of events that are filtered in, if all events are included then it will return null.
     */
    List<String> getFilterIn();
}