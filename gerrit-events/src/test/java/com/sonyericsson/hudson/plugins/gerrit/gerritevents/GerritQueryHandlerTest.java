/*
 * The MIT License
 *
 * Copyright 2011 Sony Ericsson Mobile Communications. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectionFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.Reader;
import java.io.StringReader;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GerritQueryHandler}.
 *
 * @author : slawomir.jaranowski
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(SshConnectionFactory.class)
public class GerritQueryHandlerTest {

    private GerritQueryHandler queryHandler;

    private SshConnection sshConnectionMock;

    /**
     * Prepare moc for sshConnection and create GerritQueryHandler for test.
     *
     * @throws Exception when something wrong.
     */
    @Before
    public void setUp() throws Exception {

        sshConnectionMock = mock(SshConnection.class);

        PowerMockito.mockStatic(SshConnectionFactory.class);
        PowerMockito.doReturn(sshConnectionMock).when(SshConnectionFactory.class, "getConnection",
                isA(String.class), isA(Integer.class), isA(String.class), isA(Authentication.class));


        when(sshConnectionMock.executeCommandReader(anyString())).thenAnswer(new Answer<Reader>() {

            @Override
            public Reader answer(InvocationOnMock invocationOnMock) throws Throwable {
                return new StringReader("{\"project\":\"test\"}");
            }
        });

        queryHandler = new GerritQueryHandler("", 0, "", new Authentication(null, ""));
    }

    /**
     * Test {@Link GerritQueryHandler.queryJava} and {@Link GerritQueryHandler.queryJson}
     * with only one parameter.
     *
     * @throws Exception when something wrong.
     */

    @Test
    public void testQueryStr() throws Exception {

        queryHandler.queryJava("X");
        queryHandler.queryJson("X");

        verify(sshConnectionMock, times(2))
                .executeCommandReader("gerrit query --format=JSON --patch-sets --current-patch-set \"X\"");

    }

    /**
     * Test {@Link GerritQueryHandler.queryJava} and {@Link GerritQueryHandler.queryJson}
     * with 4 parameter list.
     *
     * @throws Exception when something wrong.
     */
    @Test
    public void testQueryStr3Bool() throws Exception {

        queryHandler.queryJava("X", true, true, true);
        queryHandler.queryJson("X", true, true, true);

        verify(sshConnectionMock, times(2))
                .executeCommandReader("gerrit query --format=JSON --patch-sets --current-patch-set --files \"X\"");

        queryHandler.queryJava("X", true, true, false);
        queryHandler.queryJson("X", true, true, false);

        verify(sshConnectionMock, times(2))
                .executeCommandReader("gerrit query --format=JSON --patch-sets --current-patch-set \"X\"");

        queryHandler.queryJava("X", true, false, true);
        queryHandler.queryJson("X", true, false, true);

        verify(sshConnectionMock, times(2))
                .executeCommandReader("gerrit query --format=JSON --patch-sets --files \"X\"");

        queryHandler.queryJava("X", true, false, false);
        queryHandler.queryJson("X", true, false, false);

        verify(sshConnectionMock, times(2))
                .executeCommandReader("gerrit query --format=JSON --patch-sets \"X\"");

        queryHandler.queryJava("X", false, true, true);
        queryHandler.queryJson("X", false, true, true);

        verify(sshConnectionMock, times(2))
                .executeCommandReader("gerrit query --format=JSON --current-patch-set --files \"X\"");

        queryHandler.queryJava("X", false, true, false);
        queryHandler.queryJson("X", false, true, false);

        verify(sshConnectionMock, times(2))
                .executeCommandReader("gerrit query --format=JSON --current-patch-set \"X\"");

        queryHandler.queryJava("X", false, false, true);
        queryHandler.queryJson("X", false, false, true);

        verify(sshConnectionMock, times(2))
                .executeCommandReader("gerrit query --format=JSON --files \"X\"");

        queryHandler.queryJava("X", false, false, false);
        queryHandler.queryJson("X", false, false, false);

        verify(sshConnectionMock, times(2))
                .executeCommandReader("gerrit query --format=JSON \"X\"");

    }

    /**
     * Test {@Link GerritQueryHandler.queryJava} and {@Link GerritQueryHandler.queryJson}
     * with full parameter list.
     *
     * @throws Exception when something wrong.
     */
    @Test
    public void testQueryStr4Bool() throws Exception {

        queryHandler.queryJava("X", false, false, false, true);
        queryHandler.queryJson("X", false, false, false, true);

        verify(sshConnectionMock, times(2))
                .executeCommandReader("gerrit query --format=JSON --commit-message \"X\"");

        queryHandler.queryJava("X", false, false, false, false);
        queryHandler.queryJson("X", false, false, false, false);

        verify(sshConnectionMock, times(2))
                .executeCommandReader("gerrit query --format=JSON \"X\"");

    }

    /**
     * Test {@Link GerritQueryHandler.queryFiles}.
     *
     * @throws Exception when something wrong.
     */
    @Test
    public void testQueryFiles() throws Exception {

        queryHandler.queryFiles("X");

        verify(sshConnectionMock)
                .executeCommandReader("gerrit query --format=JSON --current-patch-set --files \"X\"");

    }
}
