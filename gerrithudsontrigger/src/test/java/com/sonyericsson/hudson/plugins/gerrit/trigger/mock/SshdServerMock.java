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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.PublicKey;

/**
 * The beginning of a mock of a sshd server. When it is done the idea is to use this to send in stream-events over the
 * ssh connection, and make connection related tests without running Gerrit on the local machine.
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
        sshd.setCommandFactory(new SshdServerMock());
        sshd.start();
        return sshd;
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
                throw new IOException("ssk-keygen failed!");
            }
        } else {
            System.out.println("Test key-pair seems to already exist.");
            return priv;
        }
    }

    @Override
    public Command createCommand(String s) {
        //TODO implement.
        return null;
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
            //Default implementation just waits for a disconnect
            while (!isDestroyed()) {
                try {
                    this.wait(WAIT_FOR_DESTROYED);
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
    }
}
