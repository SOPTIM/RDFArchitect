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

package org.rdfarchitect.shacl;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.Test;

class SHACLShapesFetcherTest {

    private static final String EX = "http://example.org/";

    private static final String PREFIXES =
            """
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            @prefix ex: <http://example.org/> .
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            """;

    /** A shape holding blank nodes, a list and a named SPARQL constraint, beside two others. */
    private static final String SHAPES =
            """
            ex:Shape a sh:PropertyShape ;
                sh:path ex:value ;
                sh:or ( [ sh:minCount 1 ] [ sh:datatype xsd:string ] ) ;
                sh:sparql ex:Check ;
                sh:node ex:Neighbour .
            ex:Check sh:select "SELECT $this WHERE { }" ;
                sh:message [ sh:name "nested in the constraint" ] .
            ex:Neighbour a sh:NodeShape ; sh:targetClass ex:Thing ;
                sh:property [ sh:path ex:other ; sh:in ( "a" "b" ) ] ;
                sh:sparql ex:Check .
            ex:Unrelated a sh:PropertyShape ; sh:path ex:unrelated .
            """;

    private static final String SHAPE_CLOSURE =
            """
            ex:Shape a sh:PropertyShape ;
                sh:path ex:value ;
                sh:or ( [ sh:minCount 1 ] [ sh:datatype xsd:string ] ) ;
                sh:sparql ex:Check ;
                sh:node ex:Neighbour .
            ex:Check sh:select "SELECT $this WHERE { }" ;
                sh:message [ sh:name "nested in the constraint" ] .
            """;

    private static final String NEIGHBOUR_CLOSURE =
            """
            ex:Neighbour a sh:NodeShape ; sh:targetClass ex:Thing ;
                sh:property [ sh:path ex:other ; sh:in ( "a" "b" ) ] ;
                sh:sparql ex:Check .
            ex:Check sh:select "SELECT $this WHERE { }" ;
                sh:message [ sh:name "nested in the constraint" ] .
            """;

    /** Reads Turtle without prefixes, which is also how the fetcher hands a shape out. */
    private static Model model(String body) {
        return ModelFactory.createModelForGraph(
                RDFParser.fromString(PREFIXES + body, Lang.TURTLE).toGraph());
    }

    @Test
    void aPropertyShapeComesWithItsBlankNodesAndNamedSparqlConstraints() {
        var shape = new SHACLShapesFetcher(model(SHAPES)).getPropertyShape(EX + "Shape");

        assertThat(model(shape.getTriples()).isIsomorphicWith(model(SHAPE_CLOSURE))).isTrue();
    }

    @Test
    void thePropertyShapesOfAPropertyComeWithTheirBlankNodesAndNamedSparqlConstraints() {
        var shapes =
                new SHACLShapesFetcher(model(SHAPES))
                        .getPropertyShapesOfProperty(
                                ModelFactory.createDefaultModel(), EX + "value");

        assertThat(shapes).hasSize(1);
        assertThat(model(shapes.getFirst().getTriples()).isIsomorphicWith(model(SHAPE_CLOSURE)))
                .isTrue();
    }

    @Test
    void theNodeShapesOfAClassComeWithTheirBlankNodesAndNamedSparqlConstraints() {
        var shapes = new SHACLShapesFetcher(model(SHAPES)).getNodeShapesOfClass(EX + "Thing");

        assertThat(shapes).hasSize(1);
        assertThat(model(shapes.getFirst().getTriples()).isIsomorphicWith(model(NEIGHBOUR_CLOSURE)))
                .isTrue();
    }

    @Test
    void aShapeHoldingACyclicListCanBeRead() {
        var shapes =
                model(
                        """
                        ex:Shape a sh:PropertyShape ; sh:path ex:value ; sh:in _:loop .
                        _:loop rdf:first "a" ; rdf:rest _:loop .
                        """);

        var shape = new SHACLShapesFetcher(shapes).getPropertyShape(EX + "Shape");

        assertThat(model(shape.getTriples()).isIsomorphicWith(shapes)).isTrue();
    }
}
