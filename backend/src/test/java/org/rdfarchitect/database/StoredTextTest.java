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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class StoredTextTest {

    private static final Path OFFICIAL_CONSTRAINTS =
            Path.of(
                    "../external/entsoe-application-profiles-library/CGMES/CurrentRelease/SHACL/TTL/"
                            + "61970-600-2_Dynamics-AP-Con-Simple-SHACL.ttl");

    @Test
    void givesBackExactlyTheTextItWasGiven() {
        var text = "# Grüße, ✓ and \"quotes\"\r\nex:S a sh:NodeShape .\n";

        assertThat(StoredText.of(text).text()).isEqualTo(text);
        assertThat(StoredText.of("").text()).isEmpty();
        assertThat(StoredText.of(null)).isNull();
    }

    @Test
    void equalTextsAreEqualAndDifferentOnesAreNot() {
        assertThat(StoredText.of("ex:S a sh:NodeShape ."))
                .isEqualTo(StoredText.of("ex:S a sh:NodeShape ."))
                .hasSameHashCodeAs(StoredText.of("ex:S a sh:NodeShape ."))
                .isNotEqualTo(StoredText.of("ex:S a sh:NodeShape . "));
    }

    /** What the history of a document holds per saved version. */
    @Test
    void anOfficialConstraintsFileIsKeptAtAFractionOfItsSize() throws IOException {
        assumeTrue(Files.exists(OFFICIAL_CONSTRAINTS), "submodule not initialised");
        var text = Files.readString(OFFICIAL_CONSTRAINTS, StandardCharsets.UTF_8);

        var stored = StoredText.of(text);

        assertThat(stored.storedSize()).isLessThan(text.length() / 5);
        assertThat(stored.text()).isEqualTo(text);
    }
}
