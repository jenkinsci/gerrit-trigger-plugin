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
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritQueryException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritQueryHandler;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.PatchSet;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.CHANGE;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PATCHSET;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PATCH_SET;

/**
 * Base class for  all GeritTriggeredEvents containing a Change.
 * @author Tomas Westling &lt;tomas.westling@sonymobile.com&gt;
 */
public abstract class ChangeBasedEvent extends GerritTriggeredEvent {

    private static final Logger logger = LoggerFactory.getLogger(ChangeBasedEvent.class);
    /**
     * The Gerrit change the event is related to.
     */
    protected Change change;

    /**
     * Refers to a specific patchset within a change.
     */
    protected PatchSet patchSet;

    /**
     * The Change.
     *
     * @return the change.
     */
    public Change getChange() {
        return change;
    }

    /**
     * The Change.
     *
     * @param change the change.
     */
    public void setChange(Change change) {
        this.change = change;
    }

/**
     * The changed files in this patchset.
     */
    private List<String> files;



    /**
     * Queries gerrit for the files included in this patch set.
     *
     * @param gerritQueryHandler the query handler, responsible for the queries to gerrit.
     * @return a list of files that are part of this patch set.
     */
    public List<String> getFiles(GerritQueryHandler gerritQueryHandler) {
        if (files == null) {
            files = new LinkedList<String>();
            try {
                List<JSONObject> jsonList = gerritQueryHandler.queryFiles("change:" + getChange().getId());
                for (JSONObject json : jsonList) {
                    if (json.has("type") && "stats".equalsIgnoreCase(json.getString("type"))) {
                        continue;
                    }
                    if (json.has("currentPatchSet")) {
                        JSONObject currentPatchSet = json.getJSONObject("currentPatchSet");
                        if (currentPatchSet.has("files")) {
                            JSONArray changedFiles = currentPatchSet.optJSONArray("files");
                            for (int i = 0; i < changedFiles.size(); i++) {
                                JSONObject file = changedFiles.getJSONObject(i);
                                files.add(file.getString("file"));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("IOException occured. ", e);
            } catch (GerritQueryException e) {
                logger.error("Bad query. ", e);
            }
        }
        return files;
    }


    /**
     * The patchSet.
     *
     * @return The patchSet.
     */
    public PatchSet getPatchSet() {
        return patchSet;
    }

    /**
     * The patchSet.
     *
     * @param patchset the patchSet.
     */
    public void setPatchset(PatchSet patchset) {
        this.patchSet = patchset;
    }

    /**
     * Takes a JSON object and fills its internal data-structure.
     *
     * @param json the JSON Object.
     */
    public void fromJson(JSONObject json) {
        super.fromJson(json);
        if (json.containsKey(CHANGE)) {
            change = new Change(json.getJSONObject(CHANGE));
        }
        if (json.containsKey(PATCH_SET)) {
            patchSet = new PatchSet(json.getJSONObject(PATCH_SET));
        } else if (json.containsKey(PATCHSET)) {
            patchSet = new PatchSet(json.getJSONObject(PATCHSET));
        }
    }

    //CS IGNORE MagicNumber FOR NEXT 15 LINES. REASON: Semi-autogenerated code.
    @Override
    public int hashCode() {
        int result = 0;
        if (getEventType() != null) {
            result = getEventType().hashCode() * 31;
        }
        if (change != null) {
            result += change.hashCode();
        }
        result *= 31;
        if (patchSet != null) {
            result += patchSet.hashCode();
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (!getClass().isInstance(o)) {
            return false;
        }
        ChangeBasedEvent event = (ChangeBasedEvent)o;
        if (getEventType() == null) {
            if (event.getEventType() != null) {
                return false;
            }
        } else if (!getEventType().equals(event.getEventType())) {
            return false;
        }
        if (getChange() == null) {
            if (event.getChange() != null) {
                return false;
            }
        } else if (!getChange().equals(event.getChange())) {
            return false;
        }
        if (getPatchSet() == null) {
            if (event.getPatchSet() != null) {
                return false;
            }
        } else if (!getPatchSet().equals(event.getPatchSet())) {
            return false;
        }
        return true;
    }

}
