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
package com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.notification;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for commends send to Gerrit.
 */
public class NotificationCommands {

    /**
     * Main commend created based on first event.
     */
    private String command;
    /**
     * List of commands for patches assigned to topic.
     */
    private List<String> commandsTopicChange;

    /**
     * Constructor.
     *
     * @param command The main command.
     */
    public NotificationCommands(String command) {
        this.command = command;
        this.commandsTopicChange = new ArrayList<>();
    }

    /**
     * Returns the main commend of the initial event.
     *
     * @return the main command.
     */
    public String getCommand() {
        return command;
    }

    /**
     * Returns list of commands for each change in case of topic.
     * Otherwise, command list is just empty.
     *
     * @return List of commands for each change in a topic.
     */
    public List<String> getCommandsTopicChanges() {
        return commandsTopicChange;
    }

    /**
     * Adds the (sub) command to the list of changes assigned to a topic.
     *
     * @param gerritCommand The command which is added to the commandsTopicChange list.
     */
    public void addTopicChangeCommand(String gerritCommand) {
        commandsTopicChange.add(gerritCommand);
    }

    /**
     * Returns true if topic change commands are available.
     *
     * @return true if commandsTopicChange contains items, otherwise false.
     */
    public boolean hasTopicChanges() {
        return commandsTopicChange.size() > 0;
    }

    /**
     * Returns true if String command is valid otherwise false.
     *
     * @param gerritCommand the string command to check.
     * @return true or false if valid.
     */
    private boolean isValidCommand(String gerritCommand) {
        if (gerritCommand == null) {
            return false;
        }
        return !gerritCommand.isEmpty();
    }

    /**
     * Returns true if all commands are valid otherwise false.
     *
     * @return true of false if all commands in list are valid or not.
     */
    public boolean isTopicChangeListCommandsValid() {
        for (final String changeCommand : commandsTopicChange) {
            if (!isValidCommand(changeCommand)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the main command is valid otherwise false.
     *
     * @return true if valid otherwise false.
     */
    public boolean isMainCommandValid() {
        return isValidCommand(command);
    }

    /**
     * Returns whether the commands are valid or not.
     *
     * @return true if valid, otherwise false.
     */
    public boolean isValid() {
        boolean isMainCommandValid = isValidCommand(command);
        if (commandsTopicChange.size() > 0) {
            return isMainCommandValid && isTopicChangeListCommandsValid();
        }
        return isMainCommandValid;
    }
}
