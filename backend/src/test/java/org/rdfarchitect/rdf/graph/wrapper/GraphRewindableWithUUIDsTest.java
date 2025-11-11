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

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.TxnType;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.cim.rdf.resources.CIMS;
import org.rdfarchitect.cim.rdf.resources.RDFA;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class GraphRewindableWithUUIDsTest {

    private static final Graph graph = GraphFactory.createDefaultGraph();

    @AfterEach
    void setUp() {
        graph.clear();
    }

    @Test
    void enhanceWithUUIDs_subjectWithoutUUID_addsUUID() {
        graph.add(NodeFactory.createURI("http://example.com/testSubject"), RDF.type.asNode(), RDFS.Class.asNode());
        GraphRewindableWithUUIDs.enhanceWithUUIDs(graph);

        assertThat(graph.size()).isEqualTo(2);
        assertThat(graph.contains(NodeFactory.createURI("http://example.com/testSubject"), RDFA.uuid.asNode(), Node.ANY)).isTrue();
    }

    @Test
    void enhanceWithUUIDs_subjectWithUUID_doesNothing() {
        var subject = NodeFactory.createURI("http://example.com/testSubject");
        var uuidNode = NodeFactory.createLiteralByValue(UUID.randomUUID());
        graph.add(subject, RDF.type.asNode(), RDFS.Class.asNode());
        graph.add(subject, RDFA.uuid.asNode(), uuidNode);

        GraphRewindableWithUUIDs.enhanceWithUUIDs(graph);

        assertThat(graph.size()).isEqualTo(2);
        assertThat(graph.contains(subject, RDFA.uuid.asNode(), uuidNode)).isTrue();
    }

    @Test
    void commit_graphWithMissingUUIDs_addsUUIDs() {
        var graphRewindable = new GraphRewindableWithUUIDs(GraphFactory.createDefaultGraph(), 20, 5);
        try {
            graphRewindable.begin(TxnType.WRITE);
            graphRewindable.add(NodeFactory.createURI("http://example.com/testSubject"), RDF.type.asNode(), RDFS.Class.asNode());
            graphRewindable.add(NodeFactory.createURI("http://example.com/testSubject2"), RDF.type.asNode(), CIMS.classCategory.asNode());
            graphRewindable.add(NodeFactory.createURI("http://example.com/testSubject3"), RDF.type.asNode(), RDF.Property.asNode());
            graphRewindable.commit();
        } finally {
            graphRewindable.end();
        }

        try {
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.size()).isEqualTo(6);
        } finally {
            graphRewindable.end();
        }
        assertAllURIsHaveUUIDs(graphRewindable);
    }

    @Test
    void constructor_GraphWithMissingUUIDs_addsUUIDs() {
        graph.add(NodeFactory.createURI("http://example.com/testSubject"), RDF.type.asNode(), RDFS.Class.asNode());
        graph.add(NodeFactory.createURI("http://example.com/testSubject2"), RDF.type.asNode(), CIMS.classCategory.asNode());
        graph.add(NodeFactory.createURI("http://example.com/testSubject3"), RDF.type.asNode(), RDF.Property.asNode());

        var graphRewindable = new GraphRewindableWithUUIDs(graph, 10, 5);

        try {
            graphRewindable.begin(TxnType.READ);
            assertThat(graphRewindable.size()).isEqualTo(6);
        } finally {
            graphRewindable.end();
        }
        assertAllURIsHaveUUIDs(graphRewindable);
    }

    private void assertAllURIsHaveUUIDs(GraphRewindableWithUUIDs graphRewindable) {
        try {
            graphRewindable.begin(TxnType.READ);
            var triples = graphRewindable.find(Node.ANY, RDF.type.asNode(), Node.ANY);
            while (triples.hasNext()) {
                assertThat(graphRewindable.contains(triples.next().getSubject(), RDFA.uuid.asNode(), Node.ANY)).isTrue();
            }
        } finally {
            graphRewindable.end();
        }
    }
}
