/*
 *  The MIT License
 *
 *  Copyright 2014 rinrinne a.k.a. rin_ne All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger;

import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.doNothing;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;

/**
 * Tests for {@link GerritItemListener}.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(PluginImpl.class)
@PowerMockIgnore({"javax.crypto.*" })
public class GerritItemListenerTest {

    /**
     * Jenkins rule instance.
     */
    // CS IGNORE VisibilityModifier FOR NEXT 3 LINES. REASON: Mocks tests.
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private final String gerritServerName = "testServer";
    private GerritServer gerritServer;

    /**
     * Setup the mock'ed environment.
     */
    @Before
    public void setup() {
        gerritServer = spy(new GerritServer(gerritServerName));
        doNothing().when(gerritServer).startConnection();
        PluginImpl.getInstance().addServer(gerritServer);
    }

    /**
     * Tests {@link GerritItemListener#onLoad()} gets connection.
     *
     * @throws Exception if so.
     */
    @Test
    public void testOnLoadedWithConnection() throws Exception {
        gerritServer.setNoConnectionOnStartup(false);
        GerritItemListener listener = new GerritItemListener();
        listener.onLoaded();
        Mockito.verify(gerritServer, Mockito.times(1)).startConnection();
    }

    /**
     * Tests {@link GerritItemListener#onLoad()} does not get connection.
     *
     * @throws Exception if so.
     */
    @Test
    public void testOnLoadedWithNoConnection() throws Exception {
        gerritServer.setNoConnectionOnStartup(true);
        GerritItemListener listener = new GerritItemListener();
        listener.onLoaded();
        Mockito.verify(gerritServer, Mockito.times(0)).startConnection();
    }
}
