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

package org.rdfarchitect.models.cim.data.dto.relations.uri;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

class URITest {

    // -------------------------------------------------------------------------
    // Valid URIs / IRIs
    // -------------------------------------------------------------------------

    static Stream<Arguments> validUris() {
        return Stream.of(
                // raw,
                //       expectedPrefix,                                              expectedSuffix
                Arguments.of(
                        "http://example.org/ontology#MyClass",
                        "http://example.org/ontology#",
                        "MyClass"),
                Arguments.of(
                        "http://example.org/ontology/MyClass",
                        "http://example.org/ontology/",
                        "MyClass"),
                Arguments.of(
                        "http://example.org/ontology#Maßeinheit",
                        "http://example.org/ontology#",
                        "Maßeinheit"),
                Arguments.of(
                        "http://example.org/ontology/Maßeinheit",
                        "http://example.org/ontology/",
                        "Maßeinheit"),
                Arguments.of(
                        "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#M:1..1",
                        "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#",
                        "M:1..1"),
                // trailing slash / empty fragment -> suffix is empty, that's ok
                Arguments.of("http://example.org/ontology/", "http://example.org/ontology/", ""),
                Arguments.of("http://example.org/ontology#", "http://example.org/ontology#", ""),
                // spaces get encoded
                Arguments.of(
                        "http://example.org/ontology#My Class",
                        "http://example.org/ontology#",
                        "My Class"),
                Arguments.of(
                        "http://example.org/ontology/My Class",
                        "http://example.org/ontology/",
                        "My Class"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("validUris")
    void validUri_isCorrect(String raw, String expectedPrefix, String expectedSuffix) {
        URI uri = new URI(raw);
        assertThat(uri.getPrefix()).isEqualTo(expectedPrefix);
        assertThat(uri.getSuffix()).isEqualTo(expectedSuffix);
        assertThat(uri).hasToString(raw);
        assertThat(uri.toNode().isURI()).isTrue();
    }

    // -------------------------------------------------------------------------
    // equals / hashCode
    // -------------------------------------------------------------------------

    static Stream<Arguments> equalPairs() {
        return Stream.of(
                Arguments.of(
                        "http://example.org/ontology#MyClass",
                        "http://example.org/ontology#MyClass"),
                Arguments.of(
                        "http://example.org/ontology/MyClass",
                        "http://example.org/ontology/MyClass"));
    }

    static Stream<Arguments> unequalPairs() {
        return Stream.of(
                Arguments.of(
                        "http://example.org/ontology#MyClass",
                        "http://example.org/ontology#OtherClass"),
                Arguments.of(
                        "http://example.org/ontology#MyClass",
                        "http://example.org/ontology/MyClass"));
    }

    @ParameterizedTest(name = "[{index}] {0} == {1}")
    @MethodSource("equalPairs")
    void equals_sameUri_returnsTrue(String a, String b) {
        assertThat(new URI(a)).isEqualTo(new URI(b));
    }

    @ParameterizedTest(name = "[{index}] {0} != {1}")
    @MethodSource("unequalPairs")
    void equals_differentUri_returnsFalse(String a, String b) {
        assertThat(new URI(a)).isNotEqualTo(new URI(b));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("equalPairs")
    void hashCode_equalUris_returnsSameHash(String a, String b) {
        assertThat(new URI(a)).hasSameHashCodeAs(new URI(b));
    }

    // -------------------------------------------------------------------------
    // Invalid inputs
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "MyClass", // relative IRI, no scheme
                "//example.org/foo" // no scheme
            })
    void invalidUri_throwsIllegalArgumentException(String raw) {
        assertThatThrownBy(() -> new URI(raw)).isInstanceOf(IllegalArgumentException.class);
    }
}
