/*
 * The MIT License
 *
 * Copyright 2014 Ericsson.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.impls;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.doThrow;

import com.rabbitmq.client.impl.LongStringHelper;
import com.sonyericsson.hudson.plugins.gerrit.trigger.api.GerritTriggerApi;
import com.sonyericsson.hudson.plugins.gerrit.trigger.api.exception.PluginNotFoundException;
import com.sonyericsson.hudson.plugins.gerrit.trigger.api.exception.PluginStatusException;
import com.sonymobile.tools.gerrit.gerritevents.Handler;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Provider;

//CS IGNORE MagicNumber FOR NEXT 1000 LINES. REASON: testdata.
/**
 * Tests {@link com.sonyericsson.hudson.plugins.gerrit.trigger.impls.RabbitMQMessageListenerImpl}.
 *
 * @author rinrinne a.k.a. rin_ne (rinrin.ne@gmail.com)
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(GerritTriggerApi.class)
public class RabbitMQMessageListenerImplTest {

    GerritTriggerApi apiMock = mock(GerritTriggerApi.class);

    /**
     * Tests if received event.
     *
     * @throws PluginNotFoundException throw if plugin is not found.
     * @throws PluginStatusException throw if plugin status is wrong.
     */
    @Test
    public void onReceiveTest() throws PluginNotFoundException, PluginStatusException {
        Handler handlerMock = mock(Handler.class);
        doReturn(handlerMock).when(apiMock).getHandler();

        RabbitMQMessageListenerImpl listener = new RabbitMQMessageListenerImpl();
        Whitebox.setInternalState(listener, GerritTriggerApi.class, apiMock);
        listener.onBind("TEST");
        listener.onReceive("TEST", "application/json", null, "test message".getBytes());
        verify(handlerMock).post("test message", new Provider(null, null, null, null, null, null));
    }

    /**
     * Tests if received event with header.
     *
     * @throws PluginNotFoundException throw if plugin is not found.
     * @throws PluginStatusException throw if plugin status is wrong.
     */
    @Test
    public void onReceiveWithHeaderTest() throws PluginNotFoundException, PluginStatusException {
        Handler handlerMock = mock(Handler.class);
        doReturn(handlerMock).when(apiMock).getHandler();

        RabbitMQMessageListenerImpl listener = new RabbitMQMessageListenerImpl();
        Whitebox.setInternalState(listener, GerritTriggerApi.class, apiMock);

        Map<String, Object> header = new HashMap<String, Object>();
        header.put("gerrit-name", LongStringHelper.asLongString("gerrit1"));
        header.put("gerrit-host", LongStringHelper.asLongString("gerrit1.localhost"));
        header.put("gerrit-port", LongStringHelper.asLongString("29418"));
        header.put("gerrit-scheme", LongStringHelper.asLongString("ssh"));
        header.put("gerrit-front-url", LongStringHelper.asLongString("http://gerrit1.localhost"));
        header.put("gerrit-version", "2.8.4");

        listener.onBind("TEST");
        listener.onReceive("TEST", "application/json", header, "test message".getBytes());
        verify(handlerMock).post(
                "test message",
                new Provider(
                        header.get("gerrit-name").toString(),
                        header.get("gerrit-host").toString(),
                        header.get("gerrit-port").toString(),
                        header.get("gerrit-scheme").toString(),
                        header.get("gerrit-front-url").toString(),
                        header.get("gerrit-version").toString()));
    }

    /**
     * Tests if received event from unknown queue.
     *
     * @throws PluginNotFoundException throw if plugin is not found.
     * @throws PluginStatusException throw if plugin status is wrong.
     */
    @Test
    public void onReceiveFromUnknownQueueTest() throws PluginNotFoundException, PluginStatusException {
        Handler handlerMock = mock(Handler.class);
        doReturn(handlerMock).when(apiMock).getHandler();

        RabbitMQMessageListenerImpl listener = new RabbitMQMessageListenerImpl();
        Whitebox.setInternalState(listener, GerritTriggerApi.class, apiMock);

        listener.onBind("TEST");
        listener.onReceive("OTHER", "application/json", null, "test message".getBytes());
        verify(handlerMock, never()).post(anyString(), any(Provider.class));
    }

    /**
     * Tests if received message with unknown content type.
     *
     * @throws PluginNotFoundException throw if plugin is not found.
     * @throws PluginStatusException throw if plugin status is wrong.
     */
    @Test
    public void onReceiveWithUnknownContentTypeTest() throws PluginNotFoundException, PluginStatusException {
        Handler handlerMock = mock(Handler.class);
        doReturn(handlerMock).when(apiMock).getHandler();

        RabbitMQMessageListenerImpl listener = new RabbitMQMessageListenerImpl();
        Whitebox.setInternalState(listener, GerritTriggerApi.class, apiMock);

        listener.onBind("TEST");
        listener.onReceive("TEST", "text/plain", null, "test message".getBytes());
        verify(handlerMock, never()).post(anyString(), any(Provider.class));
    }

    /**
     * Tests if received message after unbind queue.
     *
     * @throws PluginNotFoundException throw if plugin is not found.
     * @throws PluginStatusException throw if plugin status is wrong.
     */
    @Test
    public void onReceiveAfterUnbindTest() throws PluginNotFoundException, PluginStatusException {
        Handler handlerMock = mock(Handler.class);
        doReturn(handlerMock).when(apiMock).getHandler();

        RabbitMQMessageListenerImpl listener = new RabbitMQMessageListenerImpl();
        Whitebox.setInternalState(listener, GerritTriggerApi.class, apiMock);

        listener.onBind("TEST");
        listener.onReceive("TEST", "application/json", null, "test message".getBytes());

        listener.onUnbind("TEST");
        listener.onReceive("TEST", "application/json", null, "test message2".getBytes());
        verify(handlerMock, times(1)).post(eq("test message"), any(Provider.class));
        verify(handlerMock, never()).post(eq("test message2"), any(Provider.class));
    }

    /**
     * Tests if received message with
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.api.exception.PluginNotFoundException}.
     *
     * @throws PluginNotFoundException throw if plugin is not found.
     * @throws PluginStatusException throw if plugin status is wrong.
     */
    @Test
    public void onReceiveWithPluginNotFoundException() throws PluginNotFoundException, PluginStatusException {
        doThrow(new PluginNotFoundException()).when(apiMock).getHandler();

        RabbitMQMessageListenerImpl listener = new RabbitMQMessageListenerImpl();
        Whitebox.setInternalState(listener, GerritTriggerApi.class, apiMock);

        listener.onBind("TEST");
        try {
            listener.onReceive("TEST", "application/json", null, "test message".getBytes());
        } catch (Exception ex) {
            fail();
        }
    }

    /**
     * Tests if received message with
     * {@link com.sonyericsson.hudson.plugins.gerrit.trigger.api.exception.PluginStatusException}.
     *
     * @throws PluginNotFoundException throw if plugin is not found.
     * @throws PluginStatusException throw if plugin status is wrong.
     */
    @Test
    public void onReceiveWithPluginStatusException() throws PluginNotFoundException, PluginStatusException {
        doThrow(new PluginStatusException()).when(apiMock).getHandler();

        RabbitMQMessageListenerImpl listener = new RabbitMQMessageListenerImpl();
        Whitebox.setInternalState(listener, GerritTriggerApi.class, apiMock);

        listener.onBind("TEST");
        try {
            listener.onReceive("TEST", "application/json", null, "test message".getBytes());
        } catch (Exception ex) {
            fail();
        }
    }
}
