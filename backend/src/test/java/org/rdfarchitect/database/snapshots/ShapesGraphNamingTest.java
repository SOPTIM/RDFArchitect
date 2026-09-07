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

class ShapesGraphNamingTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://iec.ch/TC57/ns/CIM/CoreEquipment-EU/3.0",
                // Colons and hashes in the owner uri must not confuse the id separator.
                "http://example.org/profile#EQ",
                "urn:uuid:1fac0fd3-d607-42e5-931f-850d3361caab",
                "default"
            })
    void encode_thenDecode_returnsTheOwnerUnchanged(String ownerGraphUri) {
        var name = ShapesGraphNaming.encode(ownerGraphUri, ShapesGraphNaming.DEFAULT_DOCUMENT_ID);

        assertThat(ShapesGraphNaming.isShapesGraph(name)).isTrue();
        assertThat(ShapesGraphNaming.decode(name))
                .contains(
                        new ShapesGraphNaming.ShapesGraphName(
                                ownerGraphUri, ShapesGraphNaming.DEFAULT_DOCUMENT_ID));
    }

    @Test
    void encode_keepsTheDocumentIdRecoverable() {
        var name = ShapesGraphNaming.encode("http://example.org/EQ", "a-second-document");

        assertThat(ShapesGraphNaming.decode(name))
                .map(ShapesGraphNaming.ShapesGraphName::documentId)
                .contains("a-second-document");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://iec.ch/TC57/ns/CIM/CoreEquipment-EU/3.0",
                "default",
                "urn:rdfa:diagrams:something"
            })
    void isShapesGraph_isFalseForEveryOtherGraphName(String graphUri) {
        assertThat(ShapesGraphNaming.isShapesGraph(graphUri)).isFalse();
        assertThat(ShapesGraphNaming.decode(graphUri)).isEmpty();
    }

    @Test
    void decode_isEmptyForAMalformedName() {
        assertThat(ShapesGraphNaming.decode("urn:rdfa:shacl:")).isEmpty();
        assertThat(ShapesGraphNaming.decode("urn:rdfa:shacl:no-id-separator")).isEmpty();
        assertThat(ShapesGraphNaming.decode("urn:rdfa:shacl:trailing-separator:")).isEmpty();
    }

    @Test
    void isShapesGraph_toleratesNull() {
        assertThat(ShapesGraphNaming.isShapesGraph(null)).isFalse();
    }
}
