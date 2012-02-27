/*
 *  The MIT License
 *
 *  Copyright 2011 Sony Ericsson Mobile Communications. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;

import java.util.List;

/**
 * The parameters to add to a build.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public enum GerritTriggerParameters {
    /**
     * Parameter name for the commit subject (commit message's 1st line).
     */
    GERRIT_CHANGE_SUBJECT,
    /**
     * Parameter name for the branch.
     */
    GERRIT_BRANCH,
    /**
     * Parameter name for the change-id.
     */
    GERRIT_CHANGE_ID,
    /**
     * Parameter name for the change number.
     */
    GERRIT_CHANGE_NUMBER,
    /**
     * Parameter name for the URL to the change.
     */
    GERRIT_CHANGE_URL,
    /**
     * Parameter name for the patch set number.
     */
    GERRIT_PATCHSET_NUMBER,
    /**
     * Parameter name for the patch set revision.
     */
    GERRIT_PATCHSET_REVISION,
    /**
     * Parameter name for the Gerrit project name.
     */
    GERRIT_PROJECT,
    /**
     * Parameter name for the refspec.
     */
    GERRIT_REFSPEC,
    /**
     * The name and email of the owner of the change.
     */
    GERRIT_CHANGE_OWNER,
    /**
     * The name of the owner of the change.
     */
    GERRIT_CHANGE_OWNER_NAME,
    /**
     * The email of the owner of the change.
     */
    GERRIT_CHANGE_OWNER_EMAIL,
    /**
     * The name and email of the uploader of the patch-set.
     */
    GERRIT_PATCHSET_UPLOADER,
    /**
     * The name of the uploader of the patch-set.
     */
    GERRIT_PATCHSET_UPLOADER_NAME,
    /**
     * The email of the uploader of the patch-set.
     */
    GERRIT_PATCHSET_UPLOADER_EMAIL,
    /**
     * The name and email of the person who triggered the event.
     */
    GERRIT_EVENT_ACCOUNT,
    /**
     * The name of the person who triggered the event.
     */
    GERRIT_EVENT_ACCOUNT_NAME,
    /**
     * The email of the person who triggered the event.
     */
    GERRIT_EVENT_ACCOUNT_EMAIL,
    /**
     * The refname in a ref-updated event.
     */
    GERRIT_REFNAME,
    /**
     * The old revision in a ref-updated event.
     */
    GERRIT_OLDREV,
    /**
     * The new revision in a ref-updated event.
     */
    GERRIT_NEWREV,
    /**
     * The submitter in a ref-updated event.
     */
    GERRIT_SUBMITTER,
    /**
     * The name of the submitter in a ref-updated event.
     */
    GERRIT_SUBMITTER_NAME,
    /**
     * The email of the submitter in a ref-updated event.
     */
    GERRIT_SUBMITTER_EMAIL,
    /**
     * A hashcode of the gerrit event object, to make sure every set of parameters
     * is unique (allowing jenkins to queue duplicate builds).
     */
    GERRIT_EVENT_HASH;

    /**
     * Creates a {@link hudson.model.StringParameterValue} and adds it to the provided list.
     * If the parameter with the same name already exists in the list it will be replaced by the new parameter,
     * but its description will be used, unless the parameter type is something else than a StringParameterValue.
     *
     * @param parameters   the list of existing parameters.
     * @param value        the value.
     * @param escapeQuotes if quote characters should be escaped.
     */
    public void setOrCreateStringParameterValue(List<ParameterValue> parameters, String value, boolean escapeQuotes) {
        ParameterValue parameter = null;
        for (ParameterValue p : parameters) {
            if (p.getName().toUpperCase().equals(this.name())) {
                parameter = p;
                break;
            }
        }
        String description = null;
        if (parameter != null) {
            if (parameter instanceof StringParameterValue) {
                //Perhaps it is manually added to remind the user of what it is for.
                description = parameter.getDescription();
            }
            parameters.remove(parameter);
        }
        String stringValue;
        if (escapeQuotes) {
            stringValue = StringUtil.escapeQuotes(value);
        } else {
            stringValue = value;
        }
        if (stringValue == null) {
            stringValue = "";
        }
        parameter = new StringParameterValue(this.name(), stringValue, description);
        parameters.add(parameter);
    }

    /**
     * Adds or sets all the Gerrit-parameter values to the provided list.
     * @param event the event.
     * @param parameters the default parameters
     * @param escapeQuotes if quotes should be escaped or not.
     * @see #setOrCreateStringParameterValue(java.util.List, String, boolean)
     */
    public static void setOrCreateParameters(GerritTriggeredEvent event, List<ParameterValue> parameters,
            boolean escapeQuotes) {
        GERRIT_EVENT_HASH.setOrCreateStringParameterValue(
                parameters, String.valueOf(((java.lang.Object)event).hashCode()), escapeQuotes);
        if (event.getChange() != null) {
            GERRIT_BRANCH.setOrCreateStringParameterValue(
                    parameters, event.getChange().getBranch(), escapeQuotes);
            GERRIT_CHANGE_NUMBER.setOrCreateStringParameterValue(
                    parameters, event.getChange().getNumber(), escapeQuotes);
            GERRIT_CHANGE_ID.setOrCreateStringParameterValue(
                    parameters, event.getChange().getId(), escapeQuotes);
            GERRIT_PATCHSET_NUMBER.setOrCreateStringParameterValue(
                    parameters, event.getPatchSet().getNumber(), escapeQuotes);
            GERRIT_PATCHSET_REVISION.setOrCreateStringParameterValue(
                    parameters, event.getPatchSet().getRevision(), escapeQuotes);
            GERRIT_REFSPEC.setOrCreateStringParameterValue(
                    parameters, StringUtil.makeRefSpec(event), escapeQuotes);
            GERRIT_PROJECT.setOrCreateStringParameterValue(
                    parameters, event.getChange().getProject(), escapeQuotes);
            GERRIT_CHANGE_SUBJECT.setOrCreateStringParameterValue(
                    parameters, event.getChange().getSubject(), escapeQuotes);
            String url = PluginImpl.getInstance().getConfig().getGerritFrontEndUrlFor(event.getChange().getNumber(),
                    event.getPatchSet().getNumber());
            GERRIT_CHANGE_URL.setOrCreateStringParameterValue(
                    parameters, url, escapeQuotes);
            GERRIT_CHANGE_OWNER.setOrCreateStringParameterValue(
                    parameters, getNameAndEmail(event.getChange().getOwner()), escapeQuotes);
            GERRIT_CHANGE_OWNER_NAME.setOrCreateStringParameterValue(
                    parameters, getName(event.getChange().getOwner()), escapeQuotes);
            GERRIT_CHANGE_OWNER_EMAIL.setOrCreateStringParameterValue(
                    parameters, getEmail(event.getChange().getOwner()), escapeQuotes);
            Account uploader = findUploader(event);
            GERRIT_PATCHSET_UPLOADER.setOrCreateStringParameterValue(
                    parameters, getNameAndEmail(uploader), escapeQuotes);
            GERRIT_PATCHSET_UPLOADER_NAME.setOrCreateStringParameterValue(
                    parameters, getName(uploader), escapeQuotes);
            GERRIT_PATCHSET_UPLOADER_EMAIL.setOrCreateStringParameterValue(
                    parameters, getEmail(uploader), escapeQuotes);
        }
        if (event.getRefUpdate() != null) {
            GERRIT_REFNAME.setOrCreateStringParameterValue(
                    parameters, event.getRefUpdate().getRefName(), escapeQuotes);
            GERRIT_PROJECT.setOrCreateStringParameterValue(
                    parameters, event.getRefUpdate().getProject(), escapeQuotes);
            GERRIT_OLDREV.setOrCreateStringParameterValue(
                    parameters, event.getRefUpdate().getOldRev(), escapeQuotes);
            GERRIT_NEWREV.setOrCreateStringParameterValue(
                    parameters, event.getRefUpdate().getNewRev(), escapeQuotes);
        }
        Account account = event.getAccount();
        if (account != null) {
            GERRIT_EVENT_ACCOUNT.setOrCreateStringParameterValue(
                    parameters, getNameAndEmail(account), escapeQuotes);
            GERRIT_EVENT_ACCOUNT_NAME.setOrCreateStringParameterValue(
                    parameters, getName(account), escapeQuotes);
            GERRIT_EVENT_ACCOUNT_EMAIL.setOrCreateStringParameterValue(
                    parameters, getEmail(account), escapeQuotes);
        }
    }

    /**
     * There are two uploader fields in the event, this method gets one of them if one is null.
     *
     * @param event the event to search.
     * @return the uploader if any.
     */
    private static Account findUploader(GerritTriggeredEvent event) {
        if (event.getPatchSet() != null && event.getPatchSet().getUploader() != null) {
            return event.getPatchSet().getUploader();
        } else {
            return event.getAccount();
        }
    }

    /**
     * Convenience method to avoid NPE on none existent accounts.
     *
     * @param account the account.
     * @return the name in the account or null if Account is null.
     * @see com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account#getName()
     */
    private static String getName(Account account) {
        if (account == null) {
            return "";
        } else {
            return account.getName();
        }
    }

    /**
     * Convenience method to avoid NPE on none existent accounts.
     *
     * @param account the account.
     * @return the name and email in the account or null if Account is null.
     * @see com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account#getNameAndEmail()
     */
    private static String getNameAndEmail(Account account) {
        if (account == null) {
            return "";
        } else {
            return account.getNameAndEmail();
        }
    }

    /**
     * Convenience method to avoid NPE on none existent accounts.
     *
     * @param account the account.
     * @return the email in the account or null if Account is null.
     * @see com.sonyericsson.hudson.plugins.gerrit.gerritevents.dto.attr.Account#getEmail()
     */
    private static String getEmail(Account account) {
        if (account == null) {
            return "";
        } else {
            return account.getEmail();
        }
    }
}
