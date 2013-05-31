/*
 *  The MIT License
 *
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.CompareType;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.FilePath;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class handles the fetching and parsing of URLs for the Dynamic Trigger
 * Configuration.
 *
 * @author Fredrik Abrahamson &lt;fredrik.abrahamson@sonymobile.com&gt;
 */
public final class GerritDynamicUrlProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GerritDynamicUrlProcessor.class);

    private static final String SHORTNAME_PROJECT = "p";
    private static final String SHORTNAME_BRANCH = "b";
    private static final String SHORTNAME_FILE = "f";
    private static final int SOCKET_READ_TIMEOUT = 10000;

    /**
     * Private constructor.
     */
    private GerritDynamicUrlProcessor() {
    }

    /**
     * Build the regex pattern for matching lines in the config.
     * @return the pattern
     */
    private static Pattern buildLinePattern() {
      // This is what a line in the file should look like, after all comments and
      // leading and trailing whitespace have been removed:
      // item: one of the characters p (for Project), b (for Branch) or f (for FilePath)
      // optional whitespace
      // auto rebuild flag: +
      // optional whitespace
      // operator: one of the characters = (for Plain), ~ (for RegExp), or ^ (for ANT path)
      // optional whitespace
      // the pattern: everything else on the line
      String projectBranchFile = "^("
              + SHORTNAME_PROJECT
              + "|" + SHORTNAME_BRANCH
              + "|" + SHORTNAME_FILE
              + ")";
      String autoRebuildFlag = "(\\+{0,1})";
      String operators = "(";
      boolean firstoperator = true;
      for (CompareType type : CompareType.values()) {
        if (!firstoperator) {
          operators += "|";
        }
        operators += type.getOperator();
        firstoperator = false;
      }
      operators += ")";

      return Pattern.compile(projectBranchFile
              + "\\s*"
              + autoRebuildFlag
              + "\\s*"
              + operators
              + "\\s*(.+)$");
    }

    /**
     * Read and parse the dynamic trigger configuration
     * @param reader stream from which to read the config
     * @return List of Gerrit projects
     * @throws ParseException when the fetched content couldn't be parsed
     * @throws IOException for all other kinds of fetch errors
     */
    private static List<GerritProject> readAndParseTriggerConfig(BufferedReader reader)
            throws IOException, ParseException {
      Pattern linePattern = buildLinePattern();

      List<GerritProject> dynamicGerritProjects = new ArrayList<GerritProject>();
      List<Branch> branches = null;
      List<FilePath> filePaths = null;
      GerritProject dynamicGerritProject = null;

      String line = "";
      int lineNr = 0;
      while ((line = reader.readLine()) != null) {
        ++lineNr;
        // Remove any comments starting with a #
        int commentPos = line.indexOf('#');
        if (commentPos > -1) {
          line = line.substring(0, commentPos);
        }

        // Remove any comments starting with a ;
        commentPos = line.indexOf(';');
        if (commentPos > -1) {
          line = line.substring(0, commentPos);
        }

        // Trim leading and trailing whitespace
        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }

        Matcher matcher = linePattern.matcher(line);
        if (!matcher.matches()) {
          throw new ParseException("Line " + lineNr + ": cannot parse '" + line + "'", lineNr);
        }

        // CS IGNORE MagicNumber FOR NEXT 4 LINES. REASON: ConstantsNotNeeded
        String item = matcher.group(1);
        boolean rebuildFlag = !matcher.group(2).isEmpty();
        String oper = matcher.group(3);
        String text = matcher.group(4);
        if (item == null || oper == null || text == null) {
          throw new ParseException("Line " + lineNr + ": cannot parse '" + line + "'", lineNr);
        }
        char operChar = oper.charAt(0);
        CompareType type = CompareType.findByOperator(operChar);

        logger.trace("==> item:({0}) oper:({1}) text:({2})", new Object[]{item, oper, text});

        if (SHORTNAME_PROJECT.equals(item)) { // Project
          // stash previous project to the list
          if (dynamicGerritProject != null) {
            dynamicGerritProjects.add(dynamicGerritProject);
          }

          branches = new ArrayList<Branch>();
          filePaths = new ArrayList<FilePath>();
          dynamicGerritProject = new GerritProject(type, text, branches, filePaths);
        } else if (SHORTNAME_BRANCH.equals(item)) { // Branch
          if (branches == null) {
            throw new ParseException("Line " + lineNr + ": attempt to use 'Branch' before 'Project'", lineNr);
          }
          Branch branch = new Branch(type, text, rebuildFlag);
          branches.add(branch);
          dynamicGerritProject.setBranches(branches);
        } else { // FilePath (because it must be an 'f')
          if (filePaths == null) {
            throw new ParseException("Line " + lineNr + ": attempt to use 'FilePath' before 'Project'", lineNr);
          }
          FilePath filePath = new FilePath(type, text);
          filePaths.add(filePath);
          dynamicGerritProject.setFilePaths(filePaths);
        }
      }

      // Finally stash the last project to the list
      if (dynamicGerritProject != null) {
        dynamicGerritProjects.add(dynamicGerritProject);
      }

      return dynamicGerritProjects;
    }

    /**
     * This is where the actual fetching is done. If everything goes well,
     * it returns a list of GerritProjects. If the fetched content hasn't changed
     * since the last fetch, it returns null.
     *
     * @param gerritTriggerConfigUrl the URL to fetch
     * @return a list of GerritProjects if successful, or null if no change
     * @throws ParseException when the fetched content couldn't be parsed
     * @throws IOException for all other kinds of fetch errors
     */
    public static List<GerritProject> fetch(String gerritTriggerConfigUrl)
            throws IOException, ParseException {

        if (gerritTriggerConfigUrl == null) {
          throw new MalformedURLException("The gerritTriggerConfigUrl is null");
        }
        if (gerritTriggerConfigUrl.isEmpty()) {
          throw new MalformedURLException("The gerritTriggerConfigUrl is empty");
        }

        // Prepare for fetching the URL
        URL url = new URL(gerritTriggerConfigUrl);
        URLConnection connection = url.openConnection();
        connection.setReadTimeout(SOCKET_READ_TIMEOUT);
        connection.setDoInput(true);

        InputStream instream = null;
        BufferedReader reader = null;
        try {
          instream = connection.getInputStream();
          reader = new BufferedReader(new InputStreamReader(instream));
          return readAndParseTriggerConfig(reader);
        } finally {
          if (reader != null) {
            reader.close();
          } else if (instream != null) {
            instream.close();
          }
        }
    }
}
