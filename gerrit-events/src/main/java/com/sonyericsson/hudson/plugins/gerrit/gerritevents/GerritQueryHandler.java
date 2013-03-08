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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.Authentication;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnection;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshConnectionFactory;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.ssh.SshException;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class helps you call gerrit query to search for patch-sets.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class GerritQueryHandler {

    private static final Logger logger = LoggerFactory.getLogger(GerritQueryHandler.class);
    /**
     * The base of the query ssh command to send to Gerrit.
     */
    public static final String QUERY_COMMAND = "gerrit query";
    private final String gerritHostName;
    private final int gerritSshPort;
    private final String gerritProxy;
    private final Authentication authentication;

    /**
     * Creates a GerritQueryHandler with the specified values.
     * @param gerritHostName the hostName
     * @param gerritSshPort  the ssh port that the gerrit server listens to.
     * @param gerritProxy    the ssh Proxy url
     * @param authentication the authentication credentials.
     */
    public GerritQueryHandler(String gerritHostName,
                              int gerritSshPort,
                              String gerritProxy,
                              Authentication authentication) {
        this.gerritHostName = gerritHostName;
        this.gerritSshPort = gerritSshPort;
        this.gerritProxy = gerritProxy;
        this.authentication = authentication;

    }

    /**
     * Creates a GerritQueryHandler with the specified config.
     * @param config the config.
     */
    public GerritQueryHandler(GerritConnectionConfig config) {
        this(config.getGerritHostName(),
                config.getGerritSshPort(),
                GerritDefaultValues.DEFAULT_GERRIT_PROXY,
                config.getGerritAuthentication());
    }

    /**
     * Creates a GerritQueryHandler with the specified config.
     *
     * @param config the config.
     */
    public GerritQueryHandler(GerritConnectionConfig2 config) {
        this(config.getGerritHostName(),
                config.getGerritSshPort(),
                config.getGerritProxy(),
                config.getGerritAuthentication());
    }

    //CS IGNORE RedundantThrows FOR NEXT 18 LINES. REASON: Informative.
    //CS IGNORE JavadocMethod FOR NEXT 17 LINES. REASON: It is there.

    /**
     * Runs the query and returns the result as a list of Java JSONObjects.
     * It is the equivalent of calling queryJava(queryString, true, true).
     * @param queryString the query.
     * @return the query result as a List of JSONObjects.
     * @throws GerritQueryException if Gerrit reports an error with the query.
     * @throws SshException if there is an error in the SSH Connection.
     * @throws IOException for some other IO problem.
     */
    public List<JSONObject> queryJava(String queryString) throws SshException, IOException, GerritQueryException {
        return queryJava(queryString, true, true, false);
    }

    //CS IGNORE RedundantThrows FOR NEXT 18 LINES. REASON: Informative.
    //CS IGNORE JavadocMethod FOR NEXT 17 LINES. REASON: It is there.

    /**
     * Runs the query and returns the result as a list of Java JSONObjects.
     * @param queryString the query.
     * @param getPatchSets getPatchSets if all patch-sets of the projects found should be included in the result.
     *                      Meaning if --patch-sets should be appended to the command call.
     * @param getCurrentPatchSet if the current patch-set for the projects found should be included in the result.
     *                          Meaning if --current-patch-set should be appended to the command call.
     * @return the query result as a List of JSONObjects.
     * @throws GerritQueryException if Gerrit reports an error with the query.
     * @throws SshException if there is an error in the SSH Connection.
     * @throws IOException for some other IO problem.
     */
    public List<JSONObject> queryJava(String queryString, boolean getPatchSets, boolean getCurrentPatchSet,
                                      boolean getFiles) throws SshException, IOException, GerritQueryException {

        final List<JSONObject> list = new LinkedList<JSONObject>();

        runQuery(queryString, getPatchSets, getCurrentPatchSet, getFiles, new LineVisitor() {

            @Override
            public void visit(String line) throws GerritQueryException {
                JSONObject json = (JSONObject)JSONSerializer.toJSON(line.trim());
                if (json.has("type") && "error".equalsIgnoreCase(json.getString("type"))) {
                    throw new GerritQueryException(json.getString("message"));
                }
                list.add(json);
            }
        });
        return list;
    }


    //CS IGNORE RedundantThrows FOR NEXT 18 LINES. REASON: Informative.
    //CS IGNORE JavadocMethod FOR NEXT 17 LINES. REASON: It is there.

    /**
     * Runs the query and returns the result as a list of Java JSONObjects.
     * @param queryString the query.
     * @return the query result as a List of JSONObjects.
     * @throws GerritQueryException if Gerrit reports an error with the query.
     * @throws SshException if there is an error in the SSH Connection.
     * @throws IOException for some other IO problem.
     */
    public List<JSONObject> queryFiles(String queryString) throws
            SshException, IOException, GerritQueryException {
        return queryJava(queryString, false, true, true);
    }



    //CS IGNORE RedundantThrows FOR NEXT 17 LINES. REASON: Informative.

    /**
     * Runs the query and returns the result as a list of JSON formatted strings.
     * This is the equivalent of calling queryJava(queryString, true, true).
     * @param queryString the query.
     * @return a List of JSON formatted strings.
     * @throws SshException if there is an error in the SSH Connection.
     * @throws IOException for some other IO problem.
     */
    public List<String> queryJson(String queryString) throws SshException, IOException {
        return queryJson(queryString, true, true, false);
    }

    //CS IGNORE RedundantThrows FOR NEXT 17 LINES. REASON: Informative.

    /**
     * Runs the query and returns the result as a list of JSON formatted strings.
     * @param queryString the query.
     * @param getPatchSets if all patch-sets of the projects found should be included in the result.
     *                      Meaning if --patch-sets should be appended to the command call.
     * @param getCurrentPatchSet if the current patch-set for the projects found should be included in the result.
     *                          Meaning if --current-patch-set should be appended to the command call.
     * @param getFiles if the files of the patch sets should be included in the result.
     *                          Meaning if --files should be appended to the command call.
     * @return a List of JSON formatted strings.
     * @throws SshException if there is an error in the SSH Connection.
     * @throws IOException for some other IO problem.
     */
    public List<String> queryJson(String queryString, boolean getPatchSets, boolean getCurrentPatchSet, boolean getFiles)
            throws SshException, IOException {
        final List<String> list = new LinkedList<String>();
        try {
            runQuery(queryString, getPatchSets, getCurrentPatchSet, getFiles, new LineVisitor() {

                @Override
                public void visit(String line) {
                    list.add(line.trim());
                }
            });
        } catch (GerritQueryException gqe) {
            logger.error("This should not have happened!", gqe);
        }
        return list;
    }

    //CS IGNORE RedundantThrows FOR NEXT 18 LINES. REASON: Informative.
    //CS IGNORE JavadocMethod FOR NEXT 17 LINES. REASON: It is there.

    /**
     * Runs the query on the Gerrit server and lets the provided visitor handle each line in the result.
     * @param queryString the query.
     * @param getPatchSets if all patch-sets of the projects found should be included in the result.
     *                      Meaning if --patch-sets should be appended to the command call.
     * @param getCurrentPatchSet if the current patch-set for the projects found should be included in the result.
     *                          Meaning if --current-patch-set should be appended to the command call.
     * @param visitor the visitor to handle each line in the result.
     * @throws GerritQueryException if a visitor finds that Gerrit reported an error with the query.
     * @throws SshException if there is an error in the SSH Connection.
     * @throws IOException for some other IO problem.
     */
    private void runQuery(String queryString, boolean getPatchSets, boolean getCurrentPatchSet, boolean getFiles,
                          LineVisitor visitor) throws GerritQueryException, SshException, IOException {
        StringBuilder str = new StringBuilder(QUERY_COMMAND);
        str.append(" --format=JSON");
        if (getPatchSets) {
            str.append(" --patch-sets");
        }
        if (getCurrentPatchSet) {
            str.append(" --current-patch-set");
        }
        if (getFiles) {
            str.append(" --files");
        }
        str.append(" \"").append(queryString.replace((CharSequence)"\"", (CharSequence)"\\\"")).append("\"");

        SshConnection ssh = null;
        try {
            ssh = SshConnectionFactory.getConnection(gerritHostName, gerritSshPort, gerritProxy, authentication);
            BufferedReader reader = new BufferedReader(ssh.executeCommandReader(str.toString()));
            String incomingLine = null;
            while ((incomingLine = reader.readLine()) != null) {
                logger.trace("Incoming line: {}", incomingLine);
                visitor.visit(incomingLine);
            }
            logger.trace("Closing reader.");
            reader.close();
        } finally {
            if (ssh != null) {
                ssh.disconnect();
            }
        }
    }

    /**
     * Internal visitor for handling a line of text.
     * Used by {@link #runQuery(java.lang.String, boolean, boolean, boolean, LineVisitor)}.
     */
    interface LineVisitor {
        /**
         * Visits a line of query result.
         * @param line the line.
         * @throws GerritQueryException if you want to.
         */
        void visit(String line) throws GerritQueryException;
    }
}
