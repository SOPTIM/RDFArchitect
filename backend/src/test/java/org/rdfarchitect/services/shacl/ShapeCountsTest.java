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

package org.rdfarchitect.services.shacl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

class ShapeCountsTest {

    @Test
    void countsNodeShapesNamedAndInlineRulesAndSparqlConstraints() {
        var graph = GraphFactory.createDefaultGraph();
        RDFParser.fromString(
                        """
                        @prefix sh: <http://www.w3.org/ns/shacl#> .
                        @prefix ex: <http://ex.org/> .
                        ex:Typed a sh:NodeShape ; sh:property ex:Named , [ sh:path ex:p ] .
                        ex:Targeted sh:targetClass ex:C ; sh:sparql [ sh:select "SELECT $this {}" ] .
                        ex:Named a sh:PropertyShape ; sh:path ex:q .
                        """,
                        Lang.TURTLE)
                .parse(graph);

        assertThat(ShapeCounts.of(graph)).isEqualTo(new ShapeCounts(2, 2, 1));
    }

    @Test
    void aShapeWithAPathIsAPropertyShapeWithNoTypeAndNoReferenceToSaySo() {
        var graph = GraphFactory.createDefaultGraph();
        RDFParser.fromString(
                        """
                        @prefix sh: <http://www.w3.org/ns/shacl#> .
                        @prefix ex: <http://ex.org/> .
                        ex:Rule sh:targetClass ex:C ; sh:path ex:p ; sh:minCount 1 .
                        """,
                        Lang.TURTLE)
                .parse(graph);

        assertThat(ShapeCounts.of(graph)).isEqualTo(new ShapeCounts(0, 1, 0));
    }

    @Test
    void agreesWithTheFormOnAnOfficialFile() {
        var file =
                Path.of(
                        "../external/entsoe-application-profiles-library/CGMES/CurrentRelease/"
                                + "SHACL/TTL/61970-600-2_Equipment-AP-Con-Simple-SHACL.ttl");
        assumeTrue(Files.exists(file), "submodule not initialised");
        var graph = GraphFactory.createDefaultGraph();
        RDFParser.source(file.toUri().toString()).parse(graph);

        var counts = ShapeCounts.of(graph);

        assertThat(counts.nodeShapes()).isEqualTo(145);
        assertThat(counts.propertyShapes()).isEqualTo(572);
    }
}
