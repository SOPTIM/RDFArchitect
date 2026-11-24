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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.Base64;

class DesktopAccessTokenTest {

    @Test
    void generate_eachRun_drawsAnotherTokenOf32UrlSafeBytes() {
        var first = DesktopAccessToken.generate();
        var second = DesktopAccessToken.generate();

        assertThat(first.value()).isNotEqualTo(second.value()).matches("[A-Za-z0-9_-]+");
        assertThat(Base64.getUrlDecoder().decode(first.value())).hasSize(32);
    }

    @Test
    void matches_sameOrOtherValue_acceptsOnlyTheSame() {
        var token = new DesktopAccessToken("secret");

        assertThat(token.matches("secret")).isTrue();
        assertThat(token.matches("secreT")).isFalse();
        assertThat(token.matches("secret2")).isFalse();
        assertThat(token.matches("")).isFalse();
        assertThat(token.matches(null)).isFalse();
    }

    @Test
    void proof_knownTokenAndChallenge_isTheHmacSha256() {
        // HMAC-SHA256 with key "key" of "The quick brown fox jumps over the lazy dog".
        var token = new DesktopAccessToken("key");

        assertThat(token.proof("The quick brown fox jumps over the lazy dog"))
                .isEqualTo("97yD9DBThCSxMpjmqm-xQ-9NWaFJRhdZl0edvC0aPNg");
    }

    @Test
    void isProof_proofOfAnotherTokenOrChallenge_isRefused() {
        var token = DesktopAccessToken.generate();
        var other = DesktopAccessToken.generate();
        var challenge = DesktopAccessToken.challenge();

        assertThat(token.isProof(challenge, token.proof(challenge))).isTrue();
        assertThat(token.isProof(challenge, other.proof(challenge))).isFalse();
        assertThat(token.isProof(challenge, token.proof(challenge + "x"))).isFalse();
        assertThat(token.isProof(challenge, null)).isFalse();
    }

    @Test
    void toString_always_hidesTheValue() {
        var token = DesktopAccessToken.generate();

        assertThat(token.toString()).doesNotContain(token.value());
    }
}
