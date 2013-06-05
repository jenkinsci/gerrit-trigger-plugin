/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritJsonEventFactory;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Provider;
import net.sf.json.JSONObject;

/**
 * Top of the hierarchies of work, converts the string to JSON if it is interesting and usable.
 * And then hands the work over to {@link AbstractJsonObjectWork}.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class StreamEventsStringWork extends AbstractJsonObjectWork {

    private String line;
    private Provider provider;

    /**
     * Default constructor.
     *
     * @param line a line of text from the stream-events stream of events.
     */
    public StreamEventsStringWork(String line) {
        this.line = line;
    }

    /**
     * Default constructor.
     *
     * @param line     a line of text from the stream-events stream of events.
     * @param provider the Gerrit server info.
     */
    public StreamEventsStringWork(String line, Provider provider) {
        this.line = line;
        this.provider = provider;
    }

    @Override
    public void perform(Coordinator coordinator) {
        JSONObject obj = GerritJsonEventFactory.getJsonObjectIfInterestingAndUsable(line);
        if (obj != null) {
            perform(obj, coordinator, provider);
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
