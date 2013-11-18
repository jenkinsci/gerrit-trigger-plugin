/*
 * The MIT License
 *
 * Copyright 2013 Sony Mobile Communications AB. All rights reserved.
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

package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.thoughtworks.xstream.XStream;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.io.IOException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

/**
 * Serialization handling test for {@link Provider}.
 */
public class DeserializeProviderTest {

    /**
     * Tests that reading an XStream file with the proto attribute converts to the scheme attribute.
     * Jenkins behaves a bit differently than XStream does out of the box, so it isn't a fully comparable test.
     *
     * @throws IOException if so.
     */
    @Test
    public void testProtoToScheme() throws IOException {
        //Provider = "Default", "review", "29418", "ssh", "http://review:8080/", "2.6"
        XStream x = new XStream();
        PatchsetCreated event = (PatchsetCreated)x.fromXML(getClass()
                .getResourceAsStream("DeserializeProviderTest.xml"));
        Provider provider = event.getProvider();
        assertNotNull(provider);
        assertEquals("ssh", provider.getScheme()); //The important test
        assertEquals("Default", provider.getName());

        assertNull(Whitebox.getInternalState(provider, "proto"));
    }


}
