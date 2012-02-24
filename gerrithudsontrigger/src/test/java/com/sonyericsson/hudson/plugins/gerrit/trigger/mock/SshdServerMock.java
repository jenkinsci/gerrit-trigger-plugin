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
package com.sonyericsson.hudson.plugins.gerrit.trigger.mock;

import hudson.util.StreamCopyThread;
import org.apache.sshd.SshServer;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.security.PublicKey;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The beginning of a mock of a sshd server. When it is done the idea is to use this to send in stream-events over the
 * ssh connection, and make connection related tests without running Gerrit on the local machine. There is some progress
 * but most of the predefined command types has issues.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public class SshdServerMock implements CommandFactory {


    /**
     * The default port that Gerrit usually listens to.
     */
    protected static final int GERRIT_SSH_PORT = 29418;
    /**
     * How long to sleep to let the ssh-keygen error output appear on stderr.
     */
    protected static final int WAIT_FOR_ERROR_OUTPUT = 1000;

    private static SshdServerMock instance;
    /**
     * One second in ms.
     */
    protected static final int ONE_SECOND = 1000;
    /**
     * Minimum time a thread can sleep.
     */
    protected static final int MIN_SLEEP = 200;
    private volatile CommandMock currentCommand;
    private List<CommandMock> commandHistory;
    private List<CommandLookup> commandLookups;

    @Override
    public Command createCommand(String s) {
        CommandMock command = findAndCreateCommand(s);
        return setCurrentCommand(command);
    }

    /**
     * Finds a command that matches the given line or a new {@link CommandMock} if nothing is found.
     *
     * @param s the command line to match.
     * @return a command.
     *
     * @see #createCommand(String)
     * @see CommandLookup
     */
    private CommandMock findAndCreateCommand(String s) {
        for (CommandLookup lookup : commandLookups) {
            if (lookup.isCommand(s)) {
                return lookup.newInstance(s);
            }
        }
        return new CommandMock(s);
    }

    /**
     * Sets the current command being executed and adds it to the commandHistory.
     *
     * @param command the command.
     * @return the command.
     */
    protected synchronized CommandMock setCurrentCommand(CommandMock command) {
        currentCommand = command;
        if (commandHistory == null) {
            commandHistory = new LinkedList<CommandMock>();
        }
        commandHistory.add(0, command);
        return currentCommand;
    }

    /**
     * The last started command. There could be other commands running in parallel.
     *
     * @return the current command.
     */
    public CommandMock getCurrentCommand() {
        return currentCommand;
    }

    /**
     * Gets the first running command that matches the given regular expression.
     *
     * @param commandSearch the regular expression to match.
     * @return the found command or null.
     */
    public synchronized CommandMock getRunningCommand(String commandSearch) {
        Pattern p = Pattern.compile(commandSearch);
        for (CommandMock command : commandHistory) {
            if (!command.isDestroyed() && p.matcher(command.getCommand()).find()) {
                return command;
            }
        }
        return null;
    }

    /**
     * Specifies a command type to instantiate and give to mina when a command matching the given regular expression is
     * wanted.
     *
     * @param commandPattern the regular expression
     * @param cmd            the class to create the command from. The class must have a constructor taking a single
     *                       String argument which is the command requested.
     * @throws NoSuchMethodException if there is no matching constructor.
     */
    public synchronized void returnCommandFor(String commandPattern, Class<? extends CommandMock> cmd)
            throws NoSuchMethodException {
        returnCommandFor(commandPattern, cmd, new Object[0], new Class<?>[0]);
    }

    /**
     * Specifies a command type to instantiate and give to mina when a command matching the given regular expression is
     * wanted.
     *
     * @param commandPattern the regular expression
     * @param cmd            the class to create the command from. The class must have a constructor where the first
     *                       argument is a String followed by the class types provided by the types parameter.
     * @param arguments      the other arguments to the constructor besides the command.
     * @param types          the other class types to match the constructor against.
     * @throws NoSuchMethodException if there is no matching constructor.
     */
    public synchronized void returnCommandFor(String commandPattern, Class<? extends CommandMock> cmd,
                                              Object[] arguments, Class<?>[] types) throws NoSuchMethodException {
        Class<?>[] argumentTypes = new Class<?>[types.length + 1];
        argumentTypes[0] = String.class;
        System.arraycopy(types, 0, argumentTypes, 1, types.length);
        Constructor<? extends CommandMock> constructor = cmd.getConstructor(argumentTypes);
        if (constructor != null) {
            if (commandLookups == null) {
                commandLookups = new LinkedList<CommandLookup>();
            }
            commandLookups.add(new CommandLookup(cmd, commandPattern, constructor, arguments));
        }
    }

    /**
     * Waits for a running command matching the provided regular expression to appear in the command history.
     *
     * @param commandSearch a regular expression.
     * @param timeout       the maximum time to wait for the command in ms.
     * @return the command.
     */
    public CommandMock waitForCommand(String commandSearch, int timeout) {
        long startTime = System.currentTimeMillis();
        SshdServerMock.CommandMock command = null;
        do {
            if (System.currentTimeMillis() - startTime >= timeout) {
                throw new RuntimeException("Timeout!");
            }
            command = getRunningCommand(commandSearch);
            if (command == null) {
                try {
                    Thread.sleep(MIN_SLEEP);
                    //CS IGNORE EmptyBlock FOR NEXT 2 LINES. REASON: not needed.
                } catch (InterruptedException e) {
                }
            }
        } while (command == null);
        System.out.println("Found it!!! " + command.getCommand());
        return command;
    }

    /**
     * Starts a ssh server on the provided port.
     *
     * @param port the port to listen to.
     * @return the server.
     *
     * @throws IOException if so.
     */
    public static SshServer startServer(int port) throws IOException {
        SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setPort(port);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider("hostkey.ser"));
        sshd.setPublickeyAuthenticator(new PublickeyAuthenticator() {
            @Override
            public boolean authenticate(String s, PublicKey publicKey, ServerSession serverSession) {
                return true;
            }
        });
        sshd.setCommandFactory(getInstance());
        sshd.start();
        return sshd;
    }

    /**
     * The singleton instance of the Server controller.
     *
     * @return the singleton
     */
    public static synchronized SshdServerMock getInstance() {
        if (instance == null) {
            instance = new SshdServerMock();
        }
        return instance;
    }

    /**
     * Starts a ssh server on the standard Gerrit port.
     *
     * @return the server.
     *
     * @throws IOException if so.
     * @see #GERRIT_SSH_PORT
     */
    public static SshServer startServer() throws IOException {
        return startServer(GERRIT_SSH_PORT);
    }

    /**
     * Generates a rsa key-pair in /tmp/jenkins-testkey for use with authenticating the trigger against the mock
     * server.
     *
     * @return the path to the private key file
     *
     * @throws IOException          if so.
     * @throws InterruptedException if interrupted while waiting for ssh-keygen to finish.
     */
    public static File generateKeyPair() throws IOException, InterruptedException {
        File priv = new File("/tmp/jenkins-testkey");
        File pub = new File("/tmp/jenkins-testkey.pub");
        if (!(priv.exists() && pub.exists())) {
            if (priv.exists()) {
                if (!priv.delete()) {
                    throw new IOException("Could not delete temp private key");
                }
            }
            if (pub.exists()) {
                if (!pub.delete()) {
                    throw new IOException("Could not delete temp public key");
                }
            }
            System.out.println("Generating test key-pair.");
            String[] cmd = new String[]{"ssh-keygen",
                    "-t", "rsa",
                    "-C", "testkey",
                    "-f", "/tmp/jenkins-testkey",
                    "-q", "-N", "", };
            Process p = Runtime.getRuntime().exec(cmd);
            new StreamCopyThread("ssh-keygen-out", p.getInputStream(), System.out, false).start();
            new StreamCopyThread("ssh-keygen-out", p.getErrorStream(), System.err, false).start();
            if (p.waitFor() == 0) {
                return priv;
            } else {
                Thread.sleep(WAIT_FOR_ERROR_OUTPUT);
                throw new IOException("ssh-keygen failed!");
            }
        } else {
            System.out.println("Test key-pair seems to already exist.");
            return priv;
        }
    }


    /**
     * A mocked ssh command.
     *
     * @see SshdServerMock#createCommand(String)
     */
    public static class CommandMock implements Command {

        /**
         * The max ms to wait before checking if the command is destroyed.
         */
        protected static final int WAIT_FOR_DESTROYED = 2000;
        private InputStream inputStream;
        private OutputStream outputStream;
        private OutputStream errorStream;
        private ExitCallback exitCallback;
        private boolean destroyed = false;
        private String command;

        /**
         * Standard constructor.
         *
         * @param command the command to "execute".
         */
        public CommandMock(String command) {
            this.command = command;
        }

        @Override
        public void setInputStream(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void setOutputStream(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public void setErrorStream(OutputStream errorStream) {
            this.errorStream = errorStream;
        }

        @Override
        public void setExitCallback(ExitCallback exitCallback) {
            this.exitCallback = exitCallback;
        }

        /**
         * Default implementation just waits for the command to be destroyed.
         *
         * @param environment env.
         * @throws IOException if so.
         */
        @Override
        public void start(Environment environment) throws IOException {
            System.out.println("Starting command: " + command);
            //Default implementation just waits for a disconnect
            while (!isDestroyed()) {
                try {
                    synchronized (this) {
                        this.wait(WAIT_FOR_DESTROYED);
                    }
                } catch (InterruptedException e) {
                    System.err.println("[SSHD-CommandMock] Awake.");
                }
            }
        }

        /**
         * Stops the command from running.
         *
         * @param exitCode the exitCode to return to the client.
         */
        public synchronized void stop(int exitCode) {
            exitCallback.onExit(exitCode);
        }

        @Override
        public void destroy() {
            synchronized (this) {
                destroyed = true;
                notifyAll();
            }
        }

        /**
         * Is the command destroyed.
         *
         * @return true if so.
         */
        public boolean isDestroyed() {
            synchronized (this) {
                return destroyed;
            }
        }

        /**
         * The input stream to the command.
         *
         * @return the input stream.
         */
        public InputStream getInputStream() {
            return inputStream;
        }

        /**
         * the output stream from the command.
         *
         * @return the output stream.
         */
        public OutputStream getOutputStream() {
            return outputStream;
        }

        /**
         * The error stream from the command.
         *
         * @return the error stream.
         */
        public OutputStream getErrorStream() {
            return errorStream;
        }

        /**
         * The command from the client.
         *
         * @return the command.
         */
        public String getCommand() {
            return command;
        }
    }

    /**
     * A command that immediately returns 0. There can be some timing issues with this command.
     */
    public static class EofCommandMock extends CommandMock {

        /**
         * Standard constructor.
         *
         * @param command the command.
         */
        public EofCommandMock(String command) {
            super(command);
        }

        @Override
        public void start(Environment environment) throws IOException {
            System.out.println("Starting EOF-command: " + getCommand());
            this.stop(0);
        }
    }

    /**
     * A Command that prints a given list of lines when the {@link #now()} method is called and then exits with 0. This
     * command is not working as expected yet.
     */
    public static class PrintLinesCommand extends CommandMock {

        private List<String> lines;
        private boolean doItNow = false;

        /**
         * Standard constructor.
         *
         * @param command the command
         * @param lines   the lines to print.
         */
        public PrintLinesCommand(String command, List<String> lines) {
            super(command);
            this.lines = lines;
        }

        /**
         * call this to make the command print its lines to the output.
         */
        public synchronized void now() {
            doItNow = true;
            this.notifyAll();
        }

        /**
         * If it is time to start printing. Used for synchronous reading.
         *
         * @return true if so.
         */
        private synchronized boolean isNow() {
            return doItNow;
        }

        @Override
        public void start(final Environment environment) throws IOException {
            System.out.println("Starting PL-command: " + getCommand());
            while (!isNow()) {
                synchronized (this) {
                    try {
                        this.wait(ONE_SECOND);
                    } catch (InterruptedException e) {
                        System.err.println("Interrupted while waiting.");
                    }
                }
            }
            try {
                PrintWriter out = new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(getOutputStream(), "UTF-8")));
                for (String line : lines) {
                    System.out.println("Sending: " + line);
                    out.println(line);
                    out.flush();
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Utility class for looking up and creating commands.
     *
     * @see SshdServerMock#findAndCreateCommand(String)
     */
    public static class CommandLookup {
        private Class<? extends CommandMock> cmdClass;
        private Pattern commandPattern;
        private Constructor<? extends CommandMock> constructor;
        private Object[] arguments;

        /**
         * Standard constructor.
         *
         * @param cmdClass       the class of the command to create.
         * @param commandPattern a regular expression matching a command the creation should be performed on.
         * @param constructor    the constructor of the command to call.
         * @param arguments      the arguments to the constructor except for the first actual command.
         * @see SshdServerMock#returnCommandFor(String, Class)
         * @see SshdServerMock#returnCommandFor(String, Class, Object[], Class[])
         */
        public CommandLookup(Class<? extends CommandMock> cmdClass, Pattern commandPattern,
                             Constructor<? extends CommandMock> constructor, Object... arguments) {
            this.cmdClass = cmdClass;
            this.commandPattern = commandPattern;
            this.constructor = constructor;
            this.arguments = arguments;
        }

        /**
         * Standard constructor.
         *
         * @param cmdClass       the class of the command to create.
         * @param commandPattern a regular expression matching a command the creation should be performed on.
         * @param constructor    the constructor of the command to call.
         * @param arguments      the arguments to the constructor except for the first actual command.
         * @see SshdServerMock#returnCommandFor(String, Class)
         * @see SshdServerMock#returnCommandFor(String, Class, Object[], Class[])
         */
        public CommandLookup(Class<? extends CommandMock> cmdClass, String commandPattern,
                             Constructor<? extends CommandMock> constructor, Object... arguments) {
            this(cmdClass, Pattern.compile(commandPattern), constructor, arguments);
        }

        /**
         * If the given command matches the pattern.
         *
         * @param command the command
         * @return true if so.
         */
        public boolean isCommand(String command) {
            return commandPattern.matcher(command).find();
        }

        /**
         * Creates a new instance of the command with all it's parameters.
         *
         * @param command the first parameter to the constructor.
         * @return a new instance of the command.
         */
        public CommandMock newInstance(String command) {
            try {
                if (arguments == null || arguments.length <= 0) {
                    return constructor.newInstance(command);
                } else {
                    Object[] args = new Object[arguments.length + 1];
                    args[0] = command;
                    System.arraycopy(arguments, 0, args, 1, arguments.length);
                    return constructor.newInstance(args);
                }
            } catch (Exception e) {
                throw new RuntimeException("Unpredicted reflection error. ", e);
            }
        }
    }
}
