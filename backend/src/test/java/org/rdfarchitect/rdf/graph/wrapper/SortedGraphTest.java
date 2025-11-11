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

package org.rdfarchitect.rdf.graph.wrapper;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.assertj.core.api.Assertions.*;

class SortedGraphTest {

    private SortedGraph sortedGraph;

    private final Node subject = NodeFactory.createURI("http://example.org/subject");
    private final Node predicate1 = NodeFactory.createURI("http://example.org/p1");
    private final Node predicate2 = NodeFactory.createURI("http://example.org/p2");
    private final Node object1 = NodeFactory.createLiteralString("B");
    private final Node object2 = NodeFactory.createLiteralString("A");

    @BeforeEach
    void setup() {
        // Sort triples by object lexical form ascending
        Comparator<Triple> comparator = Comparator.comparing(t -> t.getObject().toString());

        sortedGraph = new SortedGraph(comparator);
        sortedGraph.add(Triple.create(subject, predicate1, object1)); // "B"
        sortedGraph.add(Triple.create(subject, predicate2, object2)); // "A"
    }

    @Test
    void find_byTriplePattern_shouldReturnSortedTriples() {
        // Act
        var result = sortedGraph.find(Triple.ANY);
        var triples = result.toList();

        // Assert
        assertThat(triples).hasSize(2);
        assertThat(triples.get(0).getObject().getLiteralLexicalForm()).isEqualTo("A");
        assertThat(triples.get(1).getObject().getLiteralLexicalForm()).isEqualTo("B");
    }

    @Test
    void find_byNodePattern_shouldReturnSortedTriples() {
        // Act
        var result = sortedGraph.find(subject, Node.ANY, Node.ANY);
        var triples = result.toList();

        // Assert
        assertThat(triples).hasSize(2);
        assertThat(triples.get(0).getObject().getLiteralLexicalForm()).isEqualTo("A");
        assertThat(triples.get(1).getObject().getLiteralLexicalForm()).isEqualTo("B");
    }
}
