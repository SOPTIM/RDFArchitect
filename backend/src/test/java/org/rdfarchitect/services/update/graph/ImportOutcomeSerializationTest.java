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

package org.rdfarchitect.services.update.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.rdfarchitect.services.update.graph.ImportOutcome.GraphsImported;
import org.rdfarchitect.services.update.graph.ImportOutcome.PackagesScanned;

import java.util.List;

/**
 * Pins the discriminator the client reads an outcome by. The generated OpenAPI schemas are named
 * after the classes, so the names on the wire have to be those; a mismatch would leave the contract
 * describing a shape the backend never sends, and only the browser would find out.
 */
class ImportOutcomeSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void graphsImported_carriesItsSchemaNameAsTheKind() throws Exception {
        var json =
                objectMapper.writeValueAsString(
                        (ImportOutcome)
                                new GraphsImported(
                                        List.of("http://graph#first"), List.of(), List.of()));

        assertThat(objectMapper.readTree(json).get("kind").asText()).isEqualTo("GraphsImported");
    }

    @Test
    void profilesScanned_carriesItsSchemaNameAsTheKind() throws Exception {
        var json = objectMapper.writeValueAsString((ImportOutcome) new PackagesScanned(List.of()));

        assertThat(objectMapper.readTree(json).get("kind").asText()).isEqualTo("PackagesScanned");
    }

    @Test
    void anOutcome_survivesTheRoundTripAsTheKindItWas() throws Exception {
        var written =
                new GraphsImported(List.of("http://graph#first"), List.of("broken.ttl"), List.of());

        var read =
                objectMapper.readValue(
                        objectMapper.writeValueAsString((ImportOutcome) written),
                        ImportOutcome.class);

        assertThat(read).isEqualTo(written);
    }
}
