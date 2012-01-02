/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
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
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventType;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritJsonEvent;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Change;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.PatchSet;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.lifecycle.GerritEventLifecycle;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.CHANGE;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.PATCH_SET;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.GerritEventKeys.UPLOADER;

/**
 * A DTO representation of the patchset-created Gerrit Event.
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class PatchsetCreated extends GerritEventLifecycle implements GerritJsonEvent {


   private static final Logger logger = LoggerFactory.getLogger(PatchsetCreated.class);
    /**
     * The Gerrit change the event is related to.
     */
    private Change change;

    /**
     * Refers to a specific patchset within a change.
     */
    private PatchSet patchSet;

    /**
     * The uploader of the patch-set.
     */
    private Account uploader;

    /**
     * The changed files in this patchset.
     */
    private List<String> files;

    @Override
    public GerritEventType getEventType() {
        return GerritEventType.PATCHSET_CREATED;
    }

    @Override
    public void fromJson(JSONObject json) {
        if (json.containsKey(CHANGE)) {
            change = new Change(json.getJSONObject(CHANGE));
        }
        if (json.containsKey(PATCH_SET)) {
            this.patchSet = new PatchSet(json.getJSONObject(PATCH_SET));
        }
        if (json.containsKey(UPLOADER)) {
            this.uploader = new Account(json.getJSONObject(UPLOADER));
        }
    }

    /**
     * The Gerrit change the event is related to.
     * @return the change.
     */
    public Change getChange() {
        return change;
    }

    /**
     * The Gerrit change the event is related to.
     * @param change the change.
     */
    public void setChange(Change change) {
        this.change = change;
    }

    /**
     * Refers to a specific patchset within a change.
     * @return the patchSet.
     */
    public PatchSet getPatchSet() {
        return patchSet;
    }

    /**
     * Refers to a specific patchset within a change.
     * @param patchset the patchSet.
     */
    public void setPatchset(PatchSet patchset) {
        this.patchSet = patchset;
    }

    /**
     * The uploader of the patch-set.
     * @return the uploader.
     */
    public Account getUploader() {
        return uploader;
    }

    /**
     * The uploader of the patch-set.
     * @param uploader the uploader.
     */
    public void setUploader(Account uploader) {
        this.uploader = uploader;
    }

    /**
     * Queries gerrit for the files included in this patch set.
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

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PatchsetCreated) {
            return equals((PatchsetCreated)obj);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        //CS IGNORE MagicNumber FOR NEXT 5 LINES. REASON: Autogenerated Code.
        //CS IGNORE AvoidInlineConditionals FOR NEXT 5 LINES. REASON: Autogenerated Code.
        int hash = 5;
        hash = 83 * hash + (this.change != null ? this.change.hashCode() : 0);
        hash = 83 * hash + (this.patchSet != null ? this.patchSet.hashCode() : 0);
        return hash;
    }

    /**
     * Implementation specific equals.
     * @param obj the object to compare.
     * @see #equals(java.lang.Object)
     * @return true if equals
     */
    public boolean equals(PatchsetCreated obj) {
        return change.equals(obj.change) && patchSet.equals(obj.patchSet);
    }

    @Override
    public String toString() {
        return "PatchsetCreated: " + change + " " + patchSet;
    }
}
