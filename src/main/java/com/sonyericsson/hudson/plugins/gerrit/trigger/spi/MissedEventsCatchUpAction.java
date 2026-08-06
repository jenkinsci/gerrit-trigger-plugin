/*
 *  The MIT License
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.spi;

import java.io.IOException;
import java.util.Date;

/**
 * Fetches missed events from a given lower bound onward and feeds them into normal processing.
 * Invoked by a {@link MissedEventsCoordinationStrategy} while its coordination lock is held, so
 * implementations do not need to worry about concurrent invocation from other JVMs.
 */
@FunctionalInterface
public interface MissedEventsCatchUpAction {

    /**
     * Performs the catch-up fetch.
     *
     * @param lowerBound the point to fetch missed events from.
     * @return the new watermark plus how many fetched events were genuinely new - see
     *         {@link MissedEventsCatchUpResult}.
     * @throws IOException if the fetch or downstream processing fails. The watermark is not
     *         advanced when this is thrown.
     */
    MissedEventsCatchUpResult fetchAndTrigger(Date lowerBound) throws IOException;
}
