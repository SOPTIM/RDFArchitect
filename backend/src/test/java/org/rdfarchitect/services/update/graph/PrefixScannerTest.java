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
import static org.assertj.core.api.Assertions.entry;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

class PrefixScannerTest {

    @Test
    void scan_turtle_readsEveryPrefixDeclaration() {
        var turtle =
                """
                @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
                @prefix cim:  <http://iec.ch/TC57/2013/CIM-schema-cim16#> .

                cim:Gadget a rdfs:Class .
                """;

        var scanned = PrefixScanner.scan("schema.ttl", file("schema.ttl", turtle));

        assertThat(scanned.fileName()).isEqualTo("schema.ttl");
        assertThat(scanned.prefixes())
                .contains(entry("cim", "http://iec.ch/TC57/2013/CIM-schema-cim16#"));
    }

    @Test
    void scan_rdfXml_readsTheNamespaceDeclarationsOfTheDocument() {
        var rdfXml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                         xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
                         xmlns:cim="http://iec.ch/TC57/2013/CIM-schema-cim16#">
                  <rdfs:Class rdf:about="http://iec.ch/TC57/2013/CIM-schema-cim16#Gadget"/>
                </rdf:RDF>
                """;

        var scanned = PrefixScanner.scan("schema.rdf", file("schema.rdf", rdfXml));

        assertThat(scanned.prefixes())
                .contains(entry("cim", "http://iec.ch/TC57/2013/CIM-schema-cim16#"));
    }

    @Test
    void scan_fileBreakingHalfWay_isUnreadableAndBringsNoPrefixes() {
        var broken =
                """
                @prefix cim: <http://iec.ch/TC57/2013/CIM-schema-cim16#> .
                this is not turtle
                """;

        var scanned = PrefixScanner.scan("broken.ttl", file("broken.ttl", broken));

        assertThat(scanned.readable()).isFalse();
        assertThat(scanned.prefixes()).isEmpty();
    }

    @Test
    void scan_fileWithoutPrefixes_isEmpty() {
        var turtle =
                """
                <http://example.com#Gadget> a <http://www.w3.org/2000/01/rdf-schema#Class> .
                """;

        var scanned = PrefixScanner.scan("plain.ttl", file("plain.ttl", turtle));

        assertThat(scanned.prefixes()).isEmpty();
    }

    private MockMultipartFile file(String fileName, String content) {
        return new MockMultipartFile(
                "files", fileName, "text/turtle", content.getBytes(StandardCharsets.UTF_8));
    }
}
