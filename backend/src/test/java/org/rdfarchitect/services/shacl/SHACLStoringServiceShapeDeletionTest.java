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

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;

import utils.TestWorkspaces;

import java.util.UUID;

class SHACLStoringServiceShapeDeletionTest {

    private static final String EX = "http://example.org/";

    private static final String PREFIXES =
            """
            @prefix sh: <http://www.w3.org/ns/shacl#> .
            @prefix ex: <http://example.org/> .
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            """;

    private final GraphIdentifier schema = new GraphIdentifier("default", "shapes");

    private DatabasePort databasePort;

    private SHACLStoringService service;

    private static Graph graph(String body) {
        return RDFParser.fromString(PREFIXES + body, Lang.TURTLE).toGraph();
    }

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        TestWorkspaces.createGraph(databasePort, schema, GraphFactory.createDefaultGraph());
        service = new SHACLStoringService(databasePort);
    }

    private Graph customShacl() {
        try (var transaction =
                databasePort.beginTransaction(schema.workspaceName(), ReadWrite.READ)) {
            var stored = transaction.graph(schema.schemaName()).getCustomSHACL();
            var copy = GraphFactory.createDefaultGraph();
            stored.find().forEach(copy::add);
            return copy;
        }
    }

    @Test
    void deletingAShapeRemovesItsBlankNodesAndNamedSparqlConstraintsAndNothingElse() {
        service.replaceCustomSHACLGraph(
                schema,
                graph(
                        """
                        ex:Shape a sh:PropertyShape ;
                            sh:path ex:value ;
                            sh:or ( [ sh:minCount 1 ] [ sh:datatype xsd:string ] ) ;
                            sh:sparql ex:Check ;
                            sh:node ex:Neighbour .
                        ex:Check sh:select "SELECT $this WHERE { }" ;
                            sh:message [ sh:name "nested in the constraint" ] .
                        ex:Neighbour a sh:NodeShape ;
                            sh:property [ sh:path ex:other ; sh:in ( "a" "b" ) ] .
                        """));

        service.deleteSHACLShape(schema, EX + "Shape");

        assertThat(
                        customShacl()
                                .isIsomorphicWith(
                                        graph(
                                                """
                                                ex:Neighbour a sh:NodeShape ;
                                                    sh:property [ sh:path ex:other ; sh:in ( "a" "b" ) ] .
                                                """)))
                .isTrue();
    }

    @Test
    void aShapeHoldingACyclicListCanBeDeleted() {
        service.replaceCustomSHACLGraph(
                schema,
                graph(
                        """
                        ex:Shape a sh:PropertyShape ; sh:path ex:value ; sh:in _:loop .
                        _:loop rdf:first "a" ; rdf:rest _:loop .
                        ex:Other a sh:PropertyShape ; sh:path ex:other .
                        """));

        service.deleteSHACLShape(schema, EX + "Shape");

        assertThat(
                        customShacl()
                                .isIsomorphicWith(
                                        graph("ex:Other a sh:PropertyShape ; sh:path ex:other .")))
                .isTrue();
    }
}
