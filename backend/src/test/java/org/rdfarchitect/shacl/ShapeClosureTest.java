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
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.shacl.vocabulary.SHACL;
import org.junit.jupiter.api.Test;

class ShapeClosureTest {

    private static final String EX = "http://example.org/";

    private static Model model(String body) {
        var document =
                """
                @prefix sh: <http://www.w3.org/ns/shacl#> .
                @prefix ex: <http://example.org/> .
                @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                """
                        + body;
        return ModelFactory.createModelForGraph(
                RDFParser.fromString(document, Lang.TURTLE).toGraph());
    }

    private static Model closureOf(Model source, Resource subject) {
        var target = ModelFactory.createDefaultModel();
        ShapeClosure.copy(source, target, subject);
        return target;
    }

    @Test
    void copiesAShapeWithWhatItHoldsInlineAndLeavesItsNeighboursOut() {
        var source =
                model(
                        """
                        ex:Shape sh:name "kept" ;
                            sh:property [ sh:path ex:value ; sh:or ( [ sh:minCount 1 ] ) ] ;
                            sh:node ex:Neighbour .
                        ex:Neighbour sh:name "left out" .
                        """);

        var closure = closureOf(source, source.createResource(EX + "Shape"));

        assertThat(closure.listObjectsOfProperty(source.createProperty(SHACL.NS + "name")).toList())
                .extracting(node -> node.asLiteral().getString())
                .containsExactly("kept");
        assertThat(closure.contains(null, source.createProperty(SHACL.NS + "minCount"))).isTrue();
    }

    @Test
    void followsANamedSparqlConstraint() {
        var source =
                model(
                        """
                        ex:Shape sh:sparql ex:Query .
                        ex:Query sh:select "SELECT ?this WHERE { }" .
                        """);

        var closure = closureOf(source, source.createResource(EX + "Shape"));

        assertThat(closure.contains(null, source.createProperty(SHACL.NS + "select"))).isTrue();
    }

    @Test
    void endsOnACyclicList() {
        var source =
                model(
                        """
                        ex:Shape sh:in _:loop .
                        _:loop rdf:first "a" ; rdf:rest _:loop .
                        """);

        assertThat(closureOf(source, source.createResource(EX + "Shape")).size()).isEqualTo(3);
    }

    @Test
    void copesWithDeepNesting() {
        // Deep enough to overflow the stack of a recursive copy.
        var depth = 50_000;
        var source = ModelFactory.createDefaultModel();
        var or = ResourceFactory.createProperty(SHACL.NS + "or");
        var shape = source.createResource(EX + "Shape");
        var current = shape;
        for (int i = 0; i < depth; i++) {
            var next = source.createResource();
            source.add(current, or, next);
            current = next;
        }

        assertThat(closureOf(source, shape).size()).isEqualTo(depth);
    }
}
