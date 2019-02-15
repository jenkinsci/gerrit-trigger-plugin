/*
 *  The MIT License
 *
 *  Copyright 2011 Sony Ericsson Mobile Communications. All rights reserved.
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

import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.Messages;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.parameters.Base64EncodedStringParameterValue;
import com.sonyericsson.hudson.plugins.gerrit.trigger.utils.StringUtil;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Account;
import com.sonymobile.tools.gerrit.gerritevents.dto.attr.Provider;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeAbandoned;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeBasedEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeMerged;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.ChangeRestored;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.CommentAdded;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.RefUpdated;
import com.sonymobile.tools.gerrit.gerritevents.dto.events.TopicChanged;
import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import hudson.model.TextParameterValue;
import org.apache.commons.codec.binary.Base64;
import org.jvnet.localizer.Localizable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The parameters to add to a build.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public enum GerritTriggerParameters {
    /**
     * Parameter name for change private state.
     */
    GERRIT_CHANGE_PRIVATE_STATE,
    /**
     * Parameter name for change wip state.
     */
    GERRIT_CHANGE_WIP_STATE,
    /**
     * Parameter name for the commit subject (commit message's 1st line).
     */
    GERRIT_CHANGE_SUBJECT,
    /**
     * Parameter name for the full commit message.
     */
    GERRIT_CHANGE_COMMIT_MESSAGE,
    /**
     * Parameter name for the branch.
     */
    GERRIT_BRANCH,
    /**
     * Parameter name for the topic.
     */
    GERRIT_TOPIC,
    /**
     * Parameter name for the old topic (in case of topic was changed).
     */
    GERRIT_OLD_TOPIC,
    /**
     * The name and email of the changer of the topic.
     */
    GERRIT_TOPIC_CHANGER,
    /**
     * The name of the changer of the topic.
     */
    GERRIT_TOPIC_CHANGER_NAME,
    /**
     * The email of the changer of the topic.
     */
    GERRIT_TOPIC_CHANGER_EMAIL,
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
     * The name and email of the abandoner of the change.
     */
    GERRIT_CHANGE_ABANDONER,
    /**
     * The name of the abandoner of the change.
     */
    GERRIT_CHANGE_ABANDONER_NAME,
    /**
     * The email of the abandoner of the change.
     */
    GERRIT_CHANGE_ABANDONER_EMAIL,
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
     * The name and email of the restorer of the change.
     */
    GERRIT_CHANGE_RESTORER,
    /**
     * The name of the restorer of the change.
     */
    GERRIT_CHANGE_RESTORER_NAME,
    /**
     * The email of the restorer of the change.
     */
    GERRIT_CHANGE_RESTORER_EMAIL,
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
     * The new revision in a ref-updated or change-merged event.
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
     * The name of the Gerrit instance.
     */
    GERRIT_NAME,
    /**
     * The host of the Gerrit instance.
     */
    GERRIT_HOST,
    /**
     * The port number of the Gerrit instance.
     */
    GERRIT_PORT,
    /**
     * The protocol scheme of the Gerrit instance.
     */
    GERRIT_SCHEME,
    /**
     * The version of the Gerrit instance.
     */
    GERRIT_VERSION,
    /**
     * A hashcode of the Gerrit event object, to make sure every set of parameters
     * is unique (allowing jenkins to queue duplicate builds).
     */
    GERRIT_EVENT_HASH,
    /**
     * The type of the event.
     */
    GERRIT_EVENT_TYPE,
    /**
     * Comment posted to Gerrit in a comment-added event.
     */
    GERRIT_EVENT_COMMENT_TEXT;

    private static final Logger logger = LoggerFactory.getLogger(GerritTriggerParameters.class);

    /**
     * A set of all the declared parameter names.
     * @return the names of the parameters
     * @see #values()
     * @see #name()
     */
    public static Set<String> getNamesSet() {
        Set<String> names = new TreeSet<String>();
        for (GerritTriggerParameters p : GerritTriggerParameters.values()) {
            names.add(p.name());
        }
        return names;
    }

    /**
     * Creates a {@link hudson.model.ParameterValue} and adds it to the provided list.
     * If the parameter with the same name already exists in the list it will be replaced by the new parameter,
     * but its description will be used, unless the parameter type is something else than a StringParameterValue.
     *
     * @param parameters   the list of existing parameters.
     * @param value        the value.
     * @param escapeQuotes if quote characters should be escaped.
     * @param clazz        the class which extends {@link hudson.model.ParameterValue}.
     */
    private void setOrCreateParameterValue(List<ParameterValue> parameters, String value, boolean escapeQuotes,
            Class<? extends StringParameterValue> clazz) {
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

        Class<?>[] types = { String.class, String.class, String.class };
        Object[] args = { this.name(), stringValue, description };
        Constructor<? extends StringParameterValue> constructor;
        try {
            constructor = clazz.getConstructor(types);
            parameter = constructor.newInstance(args);
            parameters.add(parameter);
        } catch (Exception ex) {
            parameter = null;
        }
    }

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
        setOrCreateParameterValue(parameters, value, escapeQuotes, StringParameterValue.class);
    }

    /**
     * Creates a {@link hudson.model.TextParameterValue} and adds it to the provided list.
     * If the parameter with the same name already exists in the list it will be replaced by the new parameter,
     * but its description will be used, unless the parameter type is something else than a TextParameterValue.
     *
     * @param parameters   the list of existing parameters.
     * @param value        the value.
     * @param escapeQuotes if quote characters should be escaped.
     */
    public void setOrCreateTextParameterValue(List<ParameterValue> parameters, String value, boolean escapeQuotes) {
        setOrCreateParameterValue(parameters, value, escapeQuotes, TextParameterValue.class);
    }

    /**
     * Creates a {@link Base64EncodedStringParameterValue} and adds it to the provided list.
     * If the parameter with the same name already exists in the list it will be replaced by the new parameter,
     * but its description will be used, unless the parameter type is something else
     * than a Base64EncodedStringParameterValue.
     *
     * @param parameters   the list of existing parameters.
     * @param value        the value.
     * @param escapeQuotes if quote characters should be escaped.
     */
    public void setOrCreateBase64EncodedStringParameterValue(
            List<ParameterValue> parameters,
            String value,
            boolean escapeQuotes) {
        setOrCreateParameterValue(parameters, value, escapeQuotes, Base64EncodedStringParameterValue.class);
    }

    /**
     * Adds or sets all the Gerrit-parameter values to the provided list.
     * @param gerritEvent the event.
     * @param parameters the default parameters
     * @see #setOrCreateStringParameterValue(java.util.List, String, boolean)
     */
    public static void setOrCreateParameters(GerritTriggeredEvent gerritEvent,
                                             List<ParameterValue> parameters) {
        setOrCreateParameters(gerritEvent, null, parameters);
    }

    /**
     * Adds or sets all the Gerrit-parameter values to the provided list.
     * @param gerritEvent the event.
     * @param project the project for which the parameters are being set
     * @param parameters the default parameters
     * @see #setOrCreateStringParameterValue(java.util.List, String, boolean)
     */
    public static void setOrCreateParameters(GerritTriggeredEvent gerritEvent, Job project,
            List<ParameterValue> parameters) {

        ParameterMode nameAndEmailParameterMode = ParameterMode.PLAIN;
        boolean escapeQuotes = false;
        ParameterMode commitMessageMode = ParameterMode.BASE64;
        ParameterMode changeSubjectMode = ParameterMode.PLAIN;
        ParameterMode commentTextMode = ParameterMode.BASE64;
        if (project != null) {
            GerritTrigger trigger = GerritTrigger.getTrigger(project);
            if (trigger != null) {
                nameAndEmailParameterMode = trigger.getNameAndEmailParameterMode();
                escapeQuotes = trigger.isEscapeQuotes();
                commitMessageMode = trigger.getCommitMessageParameterMode();
                changeSubjectMode = trigger.getChangeSubjectParameterMode();
                commentTextMode = trigger.getCommentTextParameterMode();
            }
        }

        GERRIT_EVENT_TYPE.setOrCreateStringParameterValue(
                parameters, gerritEvent.getEventType().getTypeValue(), escapeQuotes);
        GERRIT_EVENT_HASH.setOrCreateStringParameterValue(
                parameters, String.valueOf(((java.lang.Object)gerritEvent).hashCode()), escapeQuotes);
        if (gerritEvent instanceof ChangeBasedEvent) {
            ChangeBasedEvent event = (ChangeBasedEvent)gerritEvent;
            GERRIT_CHANGE_WIP_STATE.setOrCreateStringParameterValue(
                    parameters, String.valueOf(event.getChange().isWip()), escapeQuotes);
            GERRIT_CHANGE_PRIVATE_STATE.setOrCreateStringParameterValue(
                    parameters, String.valueOf(event.getChange().isPrivate()), escapeQuotes);
            GERRIT_BRANCH.setOrCreateStringParameterValue(
                    parameters, event.getChange().getBranch(), escapeQuotes);
            GERRIT_TOPIC.setOrCreateStringParameterValue(
                    parameters, event.getChange().getTopic(), escapeQuotes);
            GERRIT_CHANGE_NUMBER.setOrCreateStringParameterValue(
                    parameters, event.getChange().getNumber(), escapeQuotes);
            GERRIT_CHANGE_ID.setOrCreateStringParameterValue(
                    parameters, event.getChange().getId(), escapeQuotes);
            String pNumber = null;
            if (null != event.getPatchSet()) {
                pNumber = event.getPatchSet().getNumber();
                GERRIT_PATCHSET_NUMBER.setOrCreateStringParameterValue(
                        parameters, pNumber, escapeQuotes);
                GERRIT_PATCHSET_REVISION.setOrCreateStringParameterValue(
                        parameters, event.getPatchSet().getRevision(), escapeQuotes);
                GERRIT_REFSPEC.setOrCreateStringParameterValue(
                        parameters, StringUtil.makeRefSpec(event), escapeQuotes);
            }
            GERRIT_PROJECT.setOrCreateStringParameterValue(
                    parameters, event.getChange().getProject(), escapeQuotes);
            if (event instanceof ChangeRestored) {
                nameAndEmailParameterMode.setOrCreateParameterValue(GERRIT_CHANGE_RESTORER, parameters,
                        getNameAndEmail(((ChangeRestored)event).getRestorer()),
                        ParameterMode.PlainMode.STRING, escapeQuotes);
                GERRIT_CHANGE_RESTORER_NAME.setOrCreateStringParameterValue(
                        parameters, getName(((ChangeRestored)event).getRestorer()), escapeQuotes);
                GERRIT_CHANGE_RESTORER_EMAIL.setOrCreateStringParameterValue(
                        parameters, getEmail(((ChangeRestored)event).getRestorer()), escapeQuotes);
            }
            changeSubjectMode.setOrCreateParameterValue(GERRIT_CHANGE_SUBJECT, parameters,
                    event.getChange().getSubject(), ParameterMode.PlainMode.STRING, escapeQuotes);

            String url = getURL(event, project);

            String commitMessage = event.getChange().getCommitMessage();
            if (commitMessage != null) {
                commitMessageMode.setOrCreateParameterValue(GERRIT_CHANGE_COMMIT_MESSAGE,
                        parameters, commitMessage, ParameterMode.PlainMode.TEXT, escapeQuotes);
            }
            GERRIT_CHANGE_URL.setOrCreateStringParameterValue(
                    parameters, url, escapeQuotes);
            if (event instanceof ChangeAbandoned) {
                nameAndEmailParameterMode.setOrCreateParameterValue(GERRIT_CHANGE_ABANDONER, parameters,
                        getNameAndEmail(((ChangeAbandoned)event).getAbandoner()),
                        ParameterMode.PlainMode.STRING, escapeQuotes);
                GERRIT_CHANGE_ABANDONER_NAME.setOrCreateStringParameterValue(
                        parameters, getName(((ChangeAbandoned)event).getAbandoner()), escapeQuotes);
                GERRIT_CHANGE_ABANDONER_EMAIL.setOrCreateStringParameterValue(
                        parameters, getEmail(((ChangeAbandoned)event).getAbandoner()), escapeQuotes);
            }
            if (event instanceof TopicChanged) {
                GERRIT_OLD_TOPIC.setOrCreateStringParameterValue(parameters,
                        ((TopicChanged)event).getOldTopic(),
                        escapeQuotes);
                nameAndEmailParameterMode.setOrCreateParameterValue(GERRIT_TOPIC_CHANGER, parameters,
                        getNameAndEmail(((TopicChanged)event).getChanger()),
                        ParameterMode.PlainMode.STRING, escapeQuotes);
                GERRIT_TOPIC_CHANGER_NAME.setOrCreateStringParameterValue(
                        parameters, getName(((TopicChanged)event).getChanger()), escapeQuotes);
                GERRIT_TOPIC_CHANGER_EMAIL.setOrCreateStringParameterValue(
                        parameters, getEmail(((TopicChanged)event).getChanger()), escapeQuotes);
            }
            if (event instanceof ChangeMerged) {
                GERRIT_NEWREV.setOrCreateStringParameterValue(
                        parameters, ((ChangeMerged)event).getNewRev(), escapeQuotes);
            }
            nameAndEmailParameterMode.setOrCreateParameterValue(GERRIT_CHANGE_OWNER, parameters,
                    getNameAndEmail(event.getChange().getOwner()), ParameterMode.PlainMode.STRING, escapeQuotes);
            GERRIT_CHANGE_OWNER_NAME.setOrCreateStringParameterValue(
                    parameters, getName(event.getChange().getOwner()), escapeQuotes);
            GERRIT_CHANGE_OWNER_EMAIL.setOrCreateStringParameterValue(
                    parameters, getEmail(event.getChange().getOwner()), escapeQuotes);
            Account uploader = findUploader(event);
            nameAndEmailParameterMode.setOrCreateParameterValue(GERRIT_PATCHSET_UPLOADER, parameters,
                    getNameAndEmail(uploader), ParameterMode.PlainMode.STRING, escapeQuotes);
            GERRIT_PATCHSET_UPLOADER_NAME.setOrCreateStringParameterValue(
                    parameters, getName(uploader), escapeQuotes);
            GERRIT_PATCHSET_UPLOADER_EMAIL.setOrCreateStringParameterValue(
                    parameters, getEmail(uploader), escapeQuotes);
            if (event instanceof CommentAdded) {
                String comment = ((CommentAdded)event).getComment();
                if (comment != null) {
                    commentTextMode.setOrCreateParameterValue(GERRIT_EVENT_COMMENT_TEXT,
                            parameters, comment, ParameterMode.PlainMode.TEXT, escapeQuotes);
                }
            }
        } else if (gerritEvent instanceof RefUpdated) {
            RefUpdated event = (RefUpdated)gerritEvent;
            GERRIT_REFNAME.setOrCreateStringParameterValue(
                    parameters, event.getRefUpdate().getRefName(), escapeQuotes);
            GERRIT_PROJECT.setOrCreateStringParameterValue(
                    parameters, event.getRefUpdate().getProject(), escapeQuotes);
            GERRIT_OLDREV.setOrCreateStringParameterValue(
                    parameters, event.getRefUpdate().getOldRev(), escapeQuotes);
            GERRIT_NEWREV.setOrCreateStringParameterValue(
                    parameters, event.getRefUpdate().getNewRev(), escapeQuotes);
        }
        Account account = gerritEvent.getAccount();
        if (account != null) {
            nameAndEmailParameterMode.setOrCreateParameterValue(GERRIT_EVENT_ACCOUNT, parameters,
                    getNameAndEmail(account), ParameterMode.PlainMode.STRING, escapeQuotes);
            GERRIT_EVENT_ACCOUNT_NAME.setOrCreateStringParameterValue(
                    parameters, getName(account), escapeQuotes);
            GERRIT_EVENT_ACCOUNT_EMAIL.setOrCreateStringParameterValue(
                    parameters, getEmail(account), escapeQuotes);
        }
        Provider provider = gerritEvent.getProvider();
        if (provider != null) {
            GERRIT_NAME.setOrCreateStringParameterValue(
                    parameters, provider.getName(), escapeQuotes);
            GERRIT_HOST.setOrCreateStringParameterValue(
                    parameters, provider.getHost(), escapeQuotes);
            GERRIT_PORT.setOrCreateStringParameterValue(
                    parameters, provider.getPort(), escapeQuotes);
            GERRIT_SCHEME.setOrCreateStringParameterValue(
                    parameters, provider.getScheme(), escapeQuotes);
            GERRIT_VERSION.setOrCreateStringParameterValue(
                    parameters, provider.getVersion(), escapeQuotes);
        }
    }

    /**
     * Get the front end url from a ChangeBasedEvent.
     *
     * @param event the event
     * @param project the project for which the parameters are being set
     * @return the front end url
     */
    private static String getURL(ChangeBasedEvent event, Job project) {
        String url = "";
        String serverName = null;
        //Figure out what serverName to use
        if (event.getProvider() != null) {
            serverName = event.getProvider().getName();
        } else if (project != null) {
            String name = GerritTrigger.getTrigger(project).getServerName();
            if (!GerritServer.ANY_SERVER.equals(name)) {
                serverName = name;
            }
        }
        GerritServer firstServer = PluginImpl.getFirstServer_();
        if (serverName == null && firstServer != null) {
            logger.warn("No server could be determined from event or project config, "
                    + "defaulting to the first configured server. Event: [{}] Project: [{}]", event, project);
            serverName = firstServer.getName();
        } else if (serverName == null) {
            //We have exhausted all possibilities, time to fail horribly
            throw new IllegalStateException("Cannot determine a Gerrit server to link to. Have you configured one?");
        }

        GerritServer server = PluginImpl.getServer_(serverName);
        if (server != null) {
            IGerritHudsonTriggerConfig config = server.getConfig();
            if (config != null) {
                url = config.getGerritFrontEndUrlFor(event);
            } else {
                logger.error("Could not find config for Gerrit server {}", serverName);
            }
        } else {
            logger.error("Could not find Gerrit server {}", serverName);
        }
        return url;
    }

    /**
     * There are two uploader fields in the event, this method gets one of them if one is null.
     *
     * @param event the event to search.
     * @return the uploader if any.
     */
    private static Account findUploader(ChangeBasedEvent event) {
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
     * @see com.sonymobile.tools.gerrit.gerritevents.dto.attr.Account#getName()
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
     * @see com.sonymobile.tools.gerrit.gerritevents.dto.attr.Account#getNameAndEmail()
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
     * @see com.sonymobile.tools.gerrit.gerritevents.dto.attr.Account#getEmail()
     */
    private static String getEmail(Account account) {
        if (account == null) {
            return "";
        } else {
            return account.getEmail();
        }
    }

    /**
     * How a parameter should be added to the triggered build.
     *
     * Some parameters like {@link #GERRIT_CHANGE_COMMIT_MESSAGE} and {@link #GERRIT_CHANGE_SUBJECT} can be bulky
     * and not often used in the build. This allows the user to specify how those specific parameters should be added.
     * {@link #NONE} means that they are not added at all.
     */
    public enum ParameterMode {
        /**
         * The parameter will be added as a human readable {@link TextParameterValue}.
         */
        PLAIN(Messages._ParameterMode_PLAIN()) {
            @Override
            void setOrCreateParameterValue(GerritTriggerParameters parameter,
                                           List<ParameterValue> parameters,
                                           String value, PlainMode mode, boolean escapeQuotes) {
                parameter.setOrCreateParameterValue(
                        parameters, value, escapeQuotes, mode.clazz);
            }
        },
        /**
         * The parameter will be added as a {@link Base64EncodedStringParameterValue}.
         */
        BASE64(Messages._ParameterMode_BASE64()) {
            @Override
            void setOrCreateParameterValue(GerritTriggerParameters parameter,
                                           List<ParameterValue> parameters,
                                           String value, PlainMode mode, boolean escapeQuotes) {
                try {
                    parameter.setOrCreateBase64EncodedStringParameterValue(
                            parameters, encodeBase64(value), escapeQuotes);
                } catch (UnsupportedEncodingException uee) {
                    logger.error("Failed to encode " + parameter.name() + " as Base64: ", uee);
                }

            }
        },
        /**
         * The parameter will not be added.
         */
        NONE(Messages._ParameterMode_NONE()) {
            @Override
            void setOrCreateParameterValue(GerritTriggerParameters parameter,
                                           List<ParameterValue> parameters,
                                           String value, PlainMode mode, boolean escapeQuotes) {
                //Do nothing
            }
        };

        /**
         * If plain text parameters should be added as {@link StringParameterValue} or {@link TextParameterValue}.
         */
        enum PlainMode {
            /**
             * {@link StringParameterValue}.
             */
            STRING(StringParameterValue.class),
            /**
             * {@link TextParameterValue}.
             */
            TEXT(TextParameterValue.class);

            final Class<? extends StringParameterValue> clazz;

            /**
             * Constructor.
             *
             * @param clazz what parameter value class to use for plain text.
             */
            PlainMode(Class<? extends StringParameterValue> clazz) {
                this.clazz = clazz;
            }
        }

        /**
         * Encodes the string as Base64.
         * @param original the string to encode
         * @return a new encoded string for the original.
         *
         * @throws UnsupportedEncodingException if so
         */
        public static String encodeBase64(String original) throws UnsupportedEncodingException {
            byte[] encodedBytes = Base64.encodeBase64(original.getBytes("UTF-8"));
            return new String(encodedBytes, Charset.forName("UTF-8"));
        }

        private final Localizable displayName;

        /**
         * Constructor.
         *
         * @param displayName the name to show on the config page
         */
        ParameterMode(Localizable displayName) {
            this.displayName = displayName;
        }

        /**
         * Adds the parameter in accordance with the mode and adds it to the provided list.
         *
         * If the parameter with the same name already exists in the list it will be replaced by the new parameter,
         * but its description will be used, unless the parameter type is something else than a TextParameterValue.
         *
         * @param parameter    the parameter to add
         * @param parameters   the list of already added parameters
         * @param value        the value to set (in plain text)
         * @param mode         what plain text parameters should be added as
         * @param escapeQuotes if quote characters should be escaped.
         * @see #setOrCreateBase64EncodedStringParameterValue(List, String, boolean)
         * @see #setOrCreateTextParameterValue(List, String, boolean)
         */
        abstract void setOrCreateParameterValue(GerritTriggerParameters parameter, List<ParameterValue> parameters,
                                                String value, PlainMode mode, boolean escapeQuotes);

        @Override
        public String toString() {
            return this.displayName.toString();
        }
    }
}
