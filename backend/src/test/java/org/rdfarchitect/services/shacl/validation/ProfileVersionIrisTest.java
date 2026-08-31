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

package org.rdfarchitect.services.shacl.validation;

import static org.assertj.core.api.Assertions.assertThat;

import de.soptim.opencgmes.cimvocabcheck.core.VersionIri;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.Test;

/** Naming the profile a graph holds. */
class ProfileVersionIrisTest {

    private static final String WITH_VERSION =
            """
            @prefix owl: <http://www.w3.org/2002/07/owl#> .

            <http://ex.org/EQ> a owl:Ontology ;
                owl:versionIRI <http://iec.ch/TC57/ns/CIM/CoreEquipment-EU/3.0> .
            """;

    @Test
    void aDeclaredVersionIriIsFound() {
        assertThat(ProfileVersionIris.declaredIn(parse(WITH_VERSION)))
                .containsExactly(VersionIri.of("http://iec.ch/TC57/ns/CIM/CoreEquipment-EU/3.0"));
    }

    @Test
    void severalDeclaredVersionIrisAreAllFound() {
        var graph =
                parse(
                        WITH_VERSION
                                + "<http://ex.org/EQ> "
                                + "<http://www.w3.org/2002/07/owl#versionIRI> "
                                + "<http://ex.org/EQ/2.0> .");

        assertThat(ProfileVersionIris.declaredIn(graph))
                .contains(VersionIri.of("http://ex.org/EQ/2.0"));
    }

    @Test
    void aGraphWithoutOneDeclaresNothing() {
        assertThat(ProfileVersionIris.declaredIn(GraphFactory.createDefaultGraph())).isEmpty();
    }

    @Test
    void aLiteralVersionIriIsIgnored() {
        var graph =
                parse(
                        "<http://ex.org/EQ> <http://www.w3.org/2002/07/owl#versionIRI>"
                                + " \"http://ex.org/EQ/1.0\" .");

        assertThat(ProfileVersionIris.declaredIn(graph)).isEmpty();
    }

    @Test
    void aSyntheticIriCannotCollideWithAGraphUri() {
        var synthetic = ProfileVersionIris.syntheticFor("http://ex.org/EQ");

        assertThat(synthetic.iri()).doesNotContain("http://ex.org/EQ");
        assertThat(ProfileVersionIris.isSynthetic(synthetic)).isTrue();
    }

    @Test
    void twoGraphsGetTwoSyntheticIris() {
        assertThat(ProfileVersionIris.syntheticFor("http://ex.org/EQ"))
                .isNotEqualTo(ProfileVersionIris.syntheticFor("http://ex.org/TP"));
    }

    @Test
    void aDeclaredIriIsNotMistakenForASyntheticOne() {
        assertThat(
                        ProfileVersionIris.isSynthetic(
                                VersionIri.of("http://iec.ch/TC57/ns/CIM/CoreEquipment-EU/3.0")))
                .isFalse();
    }

    private static org.apache.jena.graph.Graph parse(String turtle) {
        var graph = GraphFactory.createDefaultGraph();
        RDFParser.fromString(turtle, Lang.TURTLE).parse(graph);
        return graph;
    }
}
