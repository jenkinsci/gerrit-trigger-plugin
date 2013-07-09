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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.runners;

import java.io.IOException;

import net.sf.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritJsonEventFactory;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.Handler;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Provider;

/**
 * A runner for a line of gerrit stream-events, converts the string to JSON
 * if it is interesting and usable. Then hands GerritEvent instance over
 * to {@link GerritEventRunner}.
 * @author rinrinne &lt;rinrin.ne@gmail.om&gt;
 */
public class StreamEventsStringRunner extends GerritEventRunner {

    private static final Logger logger = LoggerFactory.getLogger(StreamEventsStringRunner.class);
    private String line;

    /**
     * Default constructor.
     *
     * @param handler the handler.
     * @param line a line of text from the stream-events stream of events.
     * @throws IOException raised if handler is null.
     */
    public StreamEventsStringRunner(Handler handler, String line) throws IOException {
        this(handler, line, null);
    }

    /**
     * Default constructor.
     *
     * @param handler the handler.
     * @param line a line of text from the stream-events stream of events.
     * @param provider the Gerrit server info.
     * @throws IOException raised if handler is null.
     */
    public StreamEventsStringRunner(Handler handler, String line, Provider provider) throws IOException {
        super(handler, null, provider);
        this.line = line;
    }

    @Override
    public void run() {
        JSONObject obj = GerritJsonEventFactory.getJsonObjectIfInterestingAndUsable(line);
        if (obj != null) {
            setEvent(GerritJsonEventFactory.getEvent(obj));
            super.run();
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("[");
        str.append(getClass().getSimpleName());
        str.append(": \"");
        str.append(line);
        str.append("\"]");
        return str.toString();
    }
}
