/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.utils;

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonymobile.tools.gerrit.gerritevents.GerritEventListener;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import java.util.regex.Pattern;

/**
 * Various string making utility methods.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public final class StringUtil {

    /**
     * What comes before the change and patch numbers in a refspec.
     */
    public static final String REFSPEC_PREFIX = "refs/changes/";

    /**
     * The base URL of this plugin. Only valid for web resources.
     */
    public static final String PLUGIN_URL = "/plugin/gerrit-trigger/";

    /**
     * the field will be used as quote "\""
     */
    private static final Pattern QUOTES_PATTERN = Pattern.compile("\"");

    /**
     * The base URL of the plugin images.
     */
    public static final String PLUGIN_IMAGES_URL = PLUGIN_URL + "images/";

    /**
     * The base URL of the plugin javascripts.
     */
    public static final String PLUGIN_JS_URL = PLUGIN_URL + "js/";

    /**
     * Private Constructor for Utility Class.
     */
    private StringUtil() {
    }

    /**
     * Creates a refspec string from the data in the event.
     * Unless the patch-set already has a refspec specified.
     * For a change with number 3456 and patchset 1 the refspec would be refs/changes/56/3456/1
     * @param event the event.
     * @return the refspec.
     * @see ChangeBasedEvent#getPatchSet()
     * @see com.sonymobile.tools.gerrit.gerritevents.dto.attr.PatchSet#getRef()
     */
    public static String makeRefSpec(ChangeBasedEvent event) {
        if (event.getPatchSet() != null && event.getPatchSet().getRef() != null) {
            if (event.getPatchSet().getRef().length() > 0) {
                return event.getPatchSet().getRef();
            }
        }
        StringBuilder str = new StringBuilder(REFSPEC_PREFIX);
        String number = event.getChange().getNumber();
        if (number.length() < 2) {
            str.append("0").append(number);
        } else if (number.length() == 2) {
            str.append(number);
        } else {
            str.append(number.substring(number.length() - 2));
        }
        str.append("/").append(number);
        str.append("/").append(event.getPatchSet().getNumber());
        return str.toString();
    }

    /**
     * Gets the path to the provided image inside this plugin.
     * The path returned is "compliant" with what for example {@link hudson.model.Action#getIconFileName()} expects.
     * @param imageName the fileName of the image.
     * @return the full path to the image.
     * @see #PLUGIN_IMAGES_URL
     */
    public static String getPluginImageUrl(String imageName) {
        return PLUGIN_IMAGES_URL + imageName;
    }

    /**
     * Gets the path to the provided javascript file inside this plugin.
     * @param jsName the name if the javascript.
     * @return the full path to the file.
     * @see #PLUGIN_JS_URL
     */
    public static String getPluginJsUrl(String jsName) {
        return PLUGIN_JS_URL + jsName;
    }

    /**
     * Escape quotes in String value.
     * @param value the name of String object having quotes.
     * @return String object as the result of escape quotes in input.
     */
    public static String escapeQuotes(String value) {
        if (value == null) {
            return null;
        } else {
            return QUOTES_PATTERN.matcher(value).replaceAll("\\\\\"");
        }
    }

    /**
     * Helper method for {@link com.sonyericsson.hudson.plugins.gerrit.trigger.NamedGerritEventListener}s.
     * To display a consistent "Server: Simple Name" pattern.
     * Usable by {@link com.sonyericsson.hudson.plugins.gerrit.trigger.diagnostics.Diagnostics}.
     *
     * @param listener the listener to get the class name from
     * @param serverName the name of the server to get the server's display name from,
     *                   null or empty string produces "Any Server"
     * @return the name to display.
     */
    public static String getDefaultDisplayNameForSpecificServer(GerritEventListener listener, String serverName) {
        StringBuilder name = new StringBuilder("");
        if (GerritServer.isAnyServer(serverName)) {
            name.append("Any Server: ");
        } else {
            GerritServer server = PluginImpl.getServer_(serverName);
            if (server != null) {
                name.append(server.getDisplayName());
            } else {
                name.append(serverName);
            }
            name.append(": ");
        }
        name.append(listener.getClass().getSimpleName());
        return name.toString();
    }
}
