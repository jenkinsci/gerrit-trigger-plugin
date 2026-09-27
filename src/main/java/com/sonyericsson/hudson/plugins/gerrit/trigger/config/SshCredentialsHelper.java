/*
 * The MIT License
 *
 * Copyright 2026 Jenkins project contributors.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.config;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.sonymobile.tools.gerrit.gerritevents.ssh.Authentication;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Resolves SSH private keys stored in the Jenkins credentials store
 * (SSH Credentials Plugin) for the connection to Gerrit.
 *
 * Only credentials from the global (Jenkins root) scope are considered,
 * since the Gerrit server configuration itself is global.
 *
 * See JENKINS-21637 / GitHub issue #664.
 */
@Restricted(NoExternalUse.class)
public final class SshCredentialsHelper {

    /**
     * Utility class.
     */
    private SshCredentialsHelper() {
    }

    /**
     * Looks up an SSH private key credential by id in the global scope.
     *
     * @param credentialsId the credentials id, may be null or empty.
     * @return the credential or null if the id is empty or no such credential exists.
     */
    @CheckForNull
    public static SSHUserPrivateKey lookup(@CheckForNull String credentialsId) {
        String id = Util.fixEmptyAndTrim(credentialsId);
        if (id == null) {
            return null;
        }
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return null;
        }
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentialsInItemGroup(
                        SSHUserPrivateKey.class,
                        jenkins,
                        ACL.SYSTEM2,
                        Collections.emptyList()),
                CredentialsMatchers.withId(id));
    }

    /**
     * Creates an {@link Authentication} holding the private key in memory instead of a key file.
     *
     * The Gerrit user name configured on the server takes precedence, because the plugin also uses it
     * to recognize its own review comments. The credential's user name is only used when the server
     * configuration has none.
     *
     * @param credential     the SSH credential.
     * @param gerritUserName the user name from the server configuration, may be empty.
     * @return the authentication.
     * @throws IllegalStateException if the credential holds no private key.
     */
    @NonNull
    public static Authentication toAuthentication(@NonNull SSHUserPrivateKey credential,
                                                  @CheckForNull String gerritUserName) {
        String userName = Util.fixEmptyAndTrim(gerritUserName);
        if (userName == null) {
            userName = credential.getUsername();
        }
        // gerrit-events calls getPrivateKeyFilePassword().getBytes() on the in-memory path without a
        // null check, so an unencrypted key must get an empty passphrase rather than null.
        return new CredentialsAuthentication(userName, getPassphrase(credential),
                Secret.fromString(getPrivateKey(credential)));
    }

    /**
     * Checks that the credential holds a private key that JSch can parse and,
     * if the key is encrypted, that the stored passphrase decrypts it.
     *
     * @param credential the SSH credential.
     * @return true if the key is usable.
     */
    public static boolean isKeyUsable(@NonNull SSHUserPrivateKey credential) {
        KeyPair keyPair = null;
        try {
            keyPair = KeyPair.load(new JSch(), getPrivateKey(credential).getBytes(StandardCharsets.UTF_8), null);
            return !keyPair.isEncrypted() || keyPair.decrypt(getPassphrase(credential));
        } catch (JSchException | IllegalStateException e) {
            return false;
        } finally {
            if (keyPair != null) {
                keyPair.dispose();
            }
        }
    }

    /**
     * Lists the SSH private key credentials a Gerrit server can use.
     *
     * @param currentValue the currently configured id, kept in the list even if it no longer resolves.
     * @return the list box model.
     */
    @NonNull
    public static ListBoxModel fillCredentialsIdItems(@CheckForNull String currentValue) {
        StandardListBoxModel result = new StandardListBoxModel();
        Jenkins jenkins = Jenkins.get();
        if (!jenkins.hasPermission(Jenkins.ADMINISTER)) {
            return result.includeCurrentValue(Util.fixNull(currentValue));
        }
        return result
                .includeEmptyValue()
                .includeMatchingAs(
                        ACL.SYSTEM2,
                        jenkins,
                        SSHUserPrivateKey.class,
                        Collections.emptyList(),
                        CredentialsMatchers.always())
                .includeCurrentValue(Util.fixNull(currentValue));
    }

    /**
     * The first private key of the credential.
     *
     * @param credential the credential.
     * @return the key.
     * @throws IllegalStateException if the credential holds no private key.
     */
    private static String getPrivateKey(SSHUserPrivateKey credential) {
        List<String> keys = credential.getPrivateKeys();
        if (keys == null || keys.isEmpty() || Util.fixEmptyAndTrim(keys.get(0)) == null) {
            throw new IllegalStateException("SSH credential '" + credential.getId() + "' contains no private key");
        }
        return keys.get(0);
    }

    /**
     * The passphrase of the credential, or an empty string if there is none.
     *
     * @param credential the credential.
     * @return the passphrase, never null.
     */
    private static String getPassphrase(SSHUserPrivateKey credential) {
        Secret passphrase = credential.getPassphrase();
        return passphrase == null ? "" : passphrase.getPlainText();
    }

    /**
     * An {@link Authentication} holding the private key in memory.
     *
     * gerrit-events keeps and reuses the same {@link Authentication} instance for every reconnect
     * (GerritConnection, query handlers) and hands the array straight to JSch. Returning a fresh copy
     * on every call means a caller that clears or modifies the array cannot break later connections,
     * and the plain-text key is not kept around as a long-lived byte array.
     */
    static final class CredentialsAuthentication extends Authentication {
        private final Secret privateKey;

        /**
         * Constructor.
         *
         * @param username   the user name.
         * @param passphrase the passphrase, never null.
         * @param privateKey the private key.
         */
        CredentialsAuthentication(String username, String passphrase, Secret privateKey) {
            super(null, username, passphrase, new byte[0]);
            this.privateKey = privateKey;
        }

        @Override
        public byte[] getPrivateKeyPhrase() {
            return privateKey.getPlainText().getBytes(StandardCharsets.UTF_8);
        }
    }
}
