/*
 *  The MIT License
 *
 *  Copyright 2013 rinrinne All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.runner;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.Handler;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEvent;

/**
 * Deliver arrived event to handler.
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public class GerritEventRunner implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(GerritEventRunner.class);

    Handler handler;
    GerritEvent event;

    /**
     * Default constructor.
     *
     * @param handler a handler.
     * @param event an event.
     * @throws IOException raised if handler is null.
     */
    public GerritEventRunner(Handler handler, GerritEvent event) throws IOException {
        if (handler == null) {
            throw(new IOException("handler should not be null."));
        }
        this.handler = handler;
        this.event = event;
    }

    /**
     * Sets event.
     *
     * @param event an event.
     */
    public void setEvent(GerritEvent event) {
        this.event = event;
    }

    @Override
    public void run() {
        if (event != null) {
            logger.debug("Event is: {}", event);
            handler.handleEvent(event);
        } else {
            logger.debug("No event extracted!");
        }
    }
}
