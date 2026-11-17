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

package org.rdfarchitect.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.rdfarchitect.exception.graph.InvalidSchemaNameException;

import java.util.List;

class SchemaNamesTest {

    @ParameterizedTest
    @ValueSource(
            strings = {"my-profile", "my profile", "a%b#c&d+e", "Équipement", "default", "3.0"})
    void validate_validName_returnsIt(String name) {
        assertThat(SchemaNames.validate(name)).isEqualTo(name);
    }

    @Test
    void validate_surroundingWhitespace_isStripped() {
        assertThat(SchemaNames.validate("  my-profile \t")).isEqualTo("my-profile");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "   ",
                "a\\b",
                "a/b",
                "a:b",
                "a*b",
                "a?b",
                "a\"b",
                "a<b",
                "a>b",
                "a|b",
                "a;b",
                "a\u0001b",
                "CON",
                ".",
                ".."
            })
    void validate_invalidName_isRejected(String name) {
        assertThatExceptionOfType(InvalidSchemaNameException.class)
                .isThrownBy(() -> SchemaNames.validate(name));
    }

    @Test
    void validate_nameLongerThanAFileNameMayBe_isRejected() {
        var name = "a".repeat(256);

        assertThatExceptionOfType(InvalidSchemaNameException.class)
                .isThrownBy(() -> SchemaNames.validate(name));
    }

    @Test
    void unique_nameNotTaken_returnsIt() {
        assertThat(SchemaNames.unique("EQ", List.of("SSH"))).isEqualTo("EQ");
    }

    @Test
    void unique_nameTaken_appendsTheFirstFreeNumber() {
        assertThat(SchemaNames.unique("EQ", List.of("EQ", "EQ-2"))).isEqualTo("EQ-3");
    }
}
