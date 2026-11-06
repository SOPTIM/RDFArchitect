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

package org.rdfarchitect.database.snapshots;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

class ShapesGraphNamingTest {

    @Test
    void aDocumentIdSurvivesEncoding() {
        var id = UUID.randomUUID();

        assertThat(ShapesGraphNaming.decode(ShapesGraphNaming.encode(id))).contains(id);
    }

    @Test
    void bothKindsOfGraphAreReserved() {
        assertThat(ShapesGraphNaming.isReserved(ShapesGraphNaming.encode(UUID.randomUUID())))
                .isTrue();
        assertThat(ShapesGraphNaming.isReserved(ShapesGraphNaming.metadataGraph())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "urn:rdfa:shacl:http%3A%2F%2Fexample.org%2FEQ:1fac0fd3-d607-4b1e-8e3c-0a6f3c2d9b11",
                "urn:rdfa:shacl-meta:http%3A%2F%2Fexample.org%2FEQ"
            })
    void theFormerPerSchemaNamesAreReservedButNameNoDocument(String graphUri) {
        assertThat(ShapesGraphNaming.isReserved(graphUri)).isTrue();
        assertThat(ShapesGraphNaming.decode(graphUri)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://iec.ch/TC57/CIM100", "default", "urn:rdfa:other"})
    void everyOtherGraphNameIsNotReserved(String graphUri) {
        assertThat(ShapesGraphNaming.isReserved(graphUri)).isFalse();
        assertThat(ShapesGraphNaming.decode(graphUri)).isEmpty();
    }

    @Test
    void aMissingNameIsTolerated() {
        assertThat(ShapesGraphNaming.isReserved(null)).isFalse();
        assertThat(ShapesGraphNaming.decode(null)).isEmpty();
    }
}
