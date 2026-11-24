/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package org.rdfarchitect.desktop;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The secret that lets only the user who started the desktop app use it.
 *
 * <p>The backend listens on the loopback interface, which every user of the computer can reach. So
 * every run draws a random token, which its API asks for (see {@link DesktopAccessFilter}). Only
 * files in the user's data directory hold it: the page that takes a browser window into the app
 * (see {@link LaunchPages}) and the file that tells a later start where the app runs (see {@link
 * RunningInstance}).
 */
public final class DesktopAccessToken {

    private static final int TOKEN_BYTES = 32;
    private static final int CHALLENGE_BYTES = 24;
    private static final String PROOF_ALGORITHM = "HmacSHA256";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String value;

    DesktopAccessToken(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("No access token");
        }
        this.value = value;
    }

    /** Draws a new token. */
    static DesktopAccessToken generate() {
        return new DesktopAccessToken(randomString(TOKEN_BYTES));
    }

    /** Draws a challenge for {@link #proof}, a new one for every check. */
    static String challenge() {
        return randomString(CHALLENGE_BYTES);
    }

    String value() {
        return value;
    }

    /** Whether the candidate is this token. Takes the same time wherever the two differ. */
    boolean matches(String candidate) {
        return candidate != null && MessageDigest.isEqual(bytes(value), bytes(candidate));
    }

    /**
     * Answers a challenge in a way only a holder of the token can, without giving the token away.
     *
     * @param challenge the challenge
     * @return the HMAC-SHA256 of the challenge, keyed with the token, base64url-encoded
     */
    String proof(String challenge) {
        try {
            var mac = Mac.getInstance(PROOF_ALGORITHM);
            mac.init(new SecretKeySpec(bytes(value), PROOF_ALGORITHM));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(bytes(challenge)));
        } catch (GeneralSecurityException e) {
            // Every Java runtime has to provide HmacSHA256.
            throw new IllegalStateException(e);
        }
    }

    /** Whether the proof answers the challenge for this token. */
    boolean isProof(String challenge, String proof) {
        return proof != null && MessageDigest.isEqual(bytes(proof(challenge)), bytes(proof));
    }

    @Override
    public String toString() {
        return "DesktopAccessToken[hidden]";
    }

    private static String randomString(int length) {
        var bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
