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

package com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.rest;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.PatchsetCreated;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.rest.ReviewInput;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.rest.RestConnectionConfig;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests {@link AbstractRestCommandJob}.
 *
 * @author <a href="mailto:robert.sandell@sonymobile.com">Robert Sandell</a>
 */
public class AbstractRestCommandJobTest {
    /**
     * Tests a standard run with a Jetty Server accepting the request.
     *
     * @throws Exception if so.
     */
    @Test
    public void testRun() throws Exception {
        PatchsetCreated event = new PatchsetCreated();
        Change change = new Change();
        change.setId("oneIdToRuleThemAll");
        change.setProject("project");
        change.setBranch("mastah");
        event.setChange(change);
        PatchSet patchSet = new PatchSet();
        patchSet.setRevision("theOneAndOnly");
        event.setPatchset(patchSet);

        System.out.println("Creating server");
        final Server server = new Server(0);


        final String assertTarget = "/a/changes/project~mastah~oneIdToRuleThemAll/revisions/theOneAndOnly/review";
        final String expectedMessage = "Hello Gerrit";
        final String expectedLabelName = "code-review";
        final int expectedLabelValue = 1;

        TestHandler handler = new TestHandler(assertTarget);
        server.setHandler(handler);
        System.out.println("Starting server");
        server.start();

        AbstractRestCommandJob job = setupRestCommandJob(server, event,
                expectedMessage, expectedLabelName, expectedLabelValue);
        try {
            System.out.println("Running job");
            job.run();
            assertTrue("Invalid target: " + handler.actualTarget, handler.targetOk);

            JSONObject json = JSONObject.fromObject(handler.requestContent);
            System.out.println("JSON: " + json.toString());
            assertEquals(expectedMessage, json.getString("message"));
            JSONObject labels = json.getJSONObject("labels");
            assertEquals("Bad label value", expectedLabelValue, labels.getInt(expectedLabelName));
        } finally {
            server.stop();
        }
    }

    /**
     * Creates an implementation fof a job to use for testing.
     *
     * @param server             the server
     * @param event              the event to send.
     * @param expectedMessage    the message for the {@link ReviewInput}
     * @param expectedLabelName  the label name for the {@link ReviewInput}
     * @param expectedLabelValue the label value for the {@link ReviewInput}
     * @return the job
     */
    private AbstractRestCommandJob setupRestCommandJob(final Server server, final ChangeBasedEvent event,
                                                       final String expectedMessage, final String expectedLabelName,
                                                       final int expectedLabelValue) {
        return new AbstractRestCommandJob(new RestConnectionConfig() {
            @Override
            public String getGerritFrontEndUrl() {
                return "http://localhost:" + server.getConnectors()[0].getLocalPort();
            }

            @Override
            public Credentials getHttpCredentials() {
                return new UsernamePasswordCredentials("user", "password");
            }

            @Override
            public String getGerritProxy() {
                return null;
            }
        }, null, event) {
            @Override
            protected ReviewInput createReview() {
                return new ReviewInput(expectedMessage, expectedLabelName, expectedLabelValue);
            }
        };
    }


    /**
     * A Jetty handler to accept or deny a request.
     */
    static class TestHandler extends AbstractHandler {
        String assertTarget;
        String requestContent;
        boolean targetOk = false;
        String actualTarget;

        /**
         * The Constructor.
         *
         * @param assertTarget the target url to expect and fail if not.
         */
        TestHandler(String assertTarget) {
            this.assertTarget = assertTarget;
        }

        @Override
        public void handle(String target, Request request, HttpServletRequest httpServletRequest,
                           HttpServletResponse response)
                throws IOException, ServletException {
            requestContent = IOUtils.toString(httpServletRequest.getReader());
            System.out.println("requestContent = " + requestContent);
            actualTarget = target;
            if (target.equals(assertTarget)) {
                response.setContentType("application/xml;charset=utf-8");
                response.setStatus(HttpServletResponse.SC_OK);
                request.setHandled(true);
                response.getWriter().println("<response>OK</response>");
                targetOk = true;
            } else {
                response.setContentType("application/xml;charset=utf-8");
                response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
                request.setHandled(true);
                response.getWriter().println("<response>This is not the resource you are looking for</response>");
                targetOk = false;
            }
        }


    }
}
