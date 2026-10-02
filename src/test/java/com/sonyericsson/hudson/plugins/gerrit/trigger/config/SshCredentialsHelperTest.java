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
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.sonymobile.tools.gerrit.gerritevents.ssh.Authentication;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SshCredentialsHelper} and the credentials handling in {@link Config}.
 */
@WithJenkins
class SshCredentialsHelperTest {

    private static final String ID = "gerrit-ssh";
    private static final String ENCRYPTED_ID = "gerrit-ssh-encrypted";
    private static final String PASSPHRASE = "s3cret";
    private static final int RECONNECTS = 3;
    private static final int KEY_SIZE = 2048;

    private String plainKey;

    /**
     * Stores an unencrypted and an encrypted SSH key in the global credentials store.
     *
     * @param j the jenkins rule.
     * @throws Exception if so.
     */
    @BeforeEach
    void setUp(JenkinsRule j) throws Exception {
        plainKey = generateKey(null);
        String encryptedKey = generateKey(PASSPHRASE);
        SystemCredentialsProvider store = SystemCredentialsProvider.getInstance();
        store.getCredentials().add(credential(ID, "credential-user", plainKey, null));
        store.getCredentials().add(credential(ENCRYPTED_ID, "credential-user", encryptedKey, PASSPHRASE));
        store.save();
    }

    /**
     * Lookup finds existing ids and ignores unknown or empty ones.
     */
    @Test
    void lookup() {
        assertNotNull(SshCredentialsHelper.lookup(ID));
        assertNotNull(SshCredentialsHelper.lookup("  " + ID + " "));
        assertNull(SshCredentialsHelper.lookup("does-not-exist"));
        assertNull(SshCredentialsHelper.lookup(""));
        assertNull(SshCredentialsHelper.lookup(null));
    }

    /**
     * The key is passed in memory, with an empty (not null) passphrase for an unencrypted key.
     */
    @Test
    void toAuthenticationUsesKeyInMemory() {
        SSHUserPrivateKey credential = SshCredentialsHelper.lookup(ID);
        Authentication auth = SshCredentialsHelper.toAuthentication(credential, "gerrit-user");
        assertNull(auth.getPrivateKeyFile());
        assertArrayEquals(keyBytes(credential), auth.getPrivateKeyPhrase());
        assertTrue(new String(auth.getPrivateKeyPhrase(), StandardCharsets.UTF_8).contains(plainKey.trim()));
        assertEquals("", auth.getPrivateKeyFilePassword());
        assertEquals("gerrit-user", auth.getUsername());
    }

    /**
     * gerrit-events reuses the same Authentication for every reconnect, so each call must return a
     * fresh copy that JSch can load, unaffected by what a previous caller did with its array.
     *
     * @throws Exception if so.
     */
    @Test
    void authenticationSurvivesReconnects() throws Exception {
        Authentication auth = SshCredentialsHelper.toAuthentication(
                SshCredentialsHelper.lookup(ENCRYPTED_ID), "gerrit-user");
        for (int i = 0; i < RECONNECTS; i++) {
            // The call SshConnectionImpl makes for an in-memory key.
            byte[] key = auth.getPrivateKeyPhrase();
            JSch jsch = new JSch();
            jsch.addIdentity(auth.getUsername(), key, null,
                    auth.getPrivateKeyFilePassword().getBytes(StandardCharsets.UTF_8));
            assertEquals(1, jsch.getIdentityNames().size());
            // Clobber the array like a caller clearing it would; the next call must be unaffected.
            Arrays.fill(key, (byte)0);
        }
    }

    /**
     * The credential's user name is only used when the server configuration has none.
     */
    @Test
    void toAuthenticationFallsBackToCredentialUserName() {
        SSHUserPrivateKey credential = SshCredentialsHelper.lookup(ID);
        assertEquals("credential-user", SshCredentialsHelper.toAuthentication(credential, "").getUsername());
        assertEquals("credential-user", SshCredentialsHelper.toAuthentication(credential, null).getUsername());
    }

    /**
     * The passphrase of an encrypted key is passed on.
     */
    @Test
    void toAuthenticationPassesPassphrase() {
        Authentication auth = SshCredentialsHelper.toAuthentication(
                SshCredentialsHelper.lookup(ENCRYPTED_ID), "gerrit-user");
        assertEquals(PASSPHRASE, auth.getPrivateKeyFilePassword());
    }

    /**
     * Key validation detects wrong passphrases and garbage.
     *
     * @throws Exception if so.
     */
    @Test
    void isKeyUsable() throws Exception {
        assertTrue(SshCredentialsHelper.isKeyUsable(SshCredentialsHelper.lookup(ID)));
        assertTrue(SshCredentialsHelper.isKeyUsable(SshCredentialsHelper.lookup(ENCRYPTED_ID)));
        assertFalse(SshCredentialsHelper.isKeyUsable(
                credential("wrong-pass", "u", generateKey(PASSPHRASE), "wrong")));
        assertFalse(SshCredentialsHelper.isKeyUsable(credential("garbage", "u", "not a key", null)));
    }

    /**
     * The configured credential takes precedence over the key file.
     */
    @Test
    void configUsesCredentialWhenSet() {
        Config config = new Config();
        config.setGerritUserName("gerrit-user");
        config.setGerritAuthKeyFile(new File("/does/not/exist"));
        config.setGerritCredentialsId(ID);

        Authentication auth = config.getGerritAuthentication();
        assertNull(auth.getPrivateKeyFile());
        assertArrayEquals(keyBytes(SshCredentialsHelper.lookup(ID)), auth.getPrivateKeyPhrase());
    }

    /**
     * Without a credential, and with a credential id that no longer resolves, the key file is used.
     */
    @Test
    void configFallsBackToKeyFile() {
        File keyFile = new File("/some/key");
        Config config = new Config();
        config.setGerritAuthKeyFile(keyFile);

        assertSame(keyFile, config.getGerritAuthentication().getPrivateKeyFile());
        assertNull(config.getGerritAuthentication().getPrivateKeyPhrase());

        config.setGerritCredentialsId("does-not-exist");
        assertSame(keyFile, config.getGerritAuthentication().getPrivateKeyFile());
    }

    /**
     * Empty ids are normalized to null, and the copy constructor keeps the id.
     */
    @Test
    void configCredentialsIdHandling() {
        Config config = new Config();
        config.setGerritCredentialsId("  ");
        assertNull(config.getGerritCredentialsId());

        config.setGerritCredentialsId(ID);
        assertEquals(ID, new Config(config).getGerritCredentialsId());
    }

    /**
     * The drop-down lists the SSH credentials plus an empty entry.
     */
    @Test
    void fillItems() {
        ListBoxModel items = SshCredentialsHelper.fillCredentialsIdItems(null);
        assertTrue(items.stream().anyMatch(o -> o.value.isEmpty()));
        assertTrue(items.stream().anyMatch(o -> ID.equals(o.value)));
        assertTrue(items.stream().anyMatch(o -> ENCRYPTED_ID.equals(o.value)));
    }

    /**
     * The configuration page submits the selected authentication method as a radio block.
     * Selecting credentials keeps the key file, selecting the key file clears the credential.
     */
    @Test
    void configAuthMethodFromForm() {
        Config config = new Config();
        config.setGerritAuthKeyFile(new File("/some/key"));

        JSONObject credentials = new JSONObject();
        credentials.put("value", Config.AUTH_METHOD_CREDENTIALS);
        credentials.put("gerritCredentialsId", ID);
        JSONObject form = new JSONObject();
        form.put("gerritAuthMethod", credentials);
        config.setValues(form);
        assertEquals(ID, config.getGerritCredentialsId());
        assertEquals(new File("/some/key"), config.getGerritAuthKeyFile());

        JSONObject keyFile = new JSONObject();
        keyFile.put("value", Config.AUTH_METHOD_KEY_FILE);
        keyFile.put("gerritAuthKeyFile", "/other/key");
        form.put("gerritAuthMethod", keyFile);
        config.setValues(form);
        assertNull(config.getGerritCredentialsId());
        assertEquals(new File("/other/key"), config.getGerritAuthKeyFile());
    }

    /**
     * The key as stored by the credential (DirectEntryPrivateKeySource normalizes the trailing newline).
     *
     * @param credential the credential.
     * @return the key bytes.
     */
    private static byte[] keyBytes(SSHUserPrivateKey credential) {
        return credential.getPrivateKeys().get(0).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Creates a credential with a directly entered private key.
     *
     * @param id         the id.
     * @param user       the user name.
     * @param key        the private key.
     * @param passphrase the passphrase or null.
     * @return the credential.
     */
    private static BasicSSHUserPrivateKey credential(String id, String user, String key, String passphrase) {
        return new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, id, user,
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(key), passphrase, "test");
    }

    /**
     * Generates an RSA private key in PEM format.
     *
     * @param passphrase the passphrase to encrypt it with, or null.
     * @return the key.
     * @throws Exception if so.
     */
    private static String generateKey(String passphrase) throws Exception {
        KeyPair keyPair = KeyPair.genKeyPair(new JSch(), KeyPair.RSA, KEY_SIZE);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (passphrase == null) {
                keyPair.writePrivateKey(out);
            } else {
                keyPair.writePrivateKey(out, passphrase.getBytes(StandardCharsets.UTF_8));
            }
            return out.toString(StandardCharsets.UTF_8);
        } finally {
            keyPair.dispose();
        }
    }
}
