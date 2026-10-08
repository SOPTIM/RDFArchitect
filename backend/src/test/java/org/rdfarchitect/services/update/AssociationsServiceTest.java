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

package org.rdfarchitect.services.update;

import static org.assertj.core.api.Assertions.assertThat;

import static utils.TestUtils.readMultipartFileFromFile;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.DataTypeDTO;
import org.rdfarchitect.api.dto.association.AssociationDTO;
import org.rdfarchitect.api.dto.association.AssociationPairDTO;
import org.rdfarchitect.api.dto.association.AssociationPairMapper;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.models.cim.rdf.resources.CIMS;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.update.classes.associations.AssociationsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

/**
 * The graph these tests run on is a <em>named</em> graph, as every schema in the running app is
 * (`{@link org.rdfarchitect.models.cim.rdf.resources.RDFA#GRAPH_URI}`). An update built for a named
 * graph has to be executed against a dataset that knows that name, so writing to the default graph
 * instead silently discards it.
 */
@SpringBootTest
class AssociationsServiceTest {

    private static final String PATH = "src/test/java/org/rdfarchitect/services/update/";
    private static final String PREFIX = "http://example.org#";
    private static final String GRAPH_URI = "http://graph#EQ";

    private static final Node CLASS = NodeFactory.createURI(PREFIX + "class");
    private static final Node SUB_CLASS = NodeFactory.createURI(PREFIX + "subClass");
    private static final Node ASSOCIATION = NodeFactory.createURI(PREFIX + "class.subClass");
    private static final Node INVERSE_ASSOCIATION =
            NodeFactory.createURI(PREFIX + "subClass.class");

    private final GraphIdentifier graphIdentifier = new GraphIdentifier("default", GRAPH_URI);

    @Autowired private AssociationPairMapper associationPairMapper;

    private DatabasePort databasePort;
    private AssociationsService associationsService;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        associationsService = new AssociationsService(databasePort, associationPairMapper);
        var graphSource =
                new GraphFileSourceBuilderImpl()
                        .setFile(readMultipartFileFromFile(PATH, "class.ttl"))
                        .setGraphName(GRAPH_URI)
                        .build();
        databasePort.createGraph(graphIdentifier, graphSource.graph());
    }

    @Test
    @DisplayName("Creating an association writes both directions into the named graph")
    void createAssociation_namedGraph_persistsBothDirections() {
        var uuids =
                associationsService.createAssociation(graphIdentifier, associationPair(null, null));

        assertThat(uuids.fromUUID()).isNotNull();
        assertThat(uuids.toUUID()).isNotNull();
        assertBothDirectionsArePresent();
    }

    @Test
    @DisplayName("Replacing all associations of a class writes them into the named graph")
    void replaceAllAssociations_namedGraph_persistsBothDirections() {
        var classUUID = UUID.fromString("43836908-c7f7-4749-bb8b-3ac9250de655");

        associationsService.replaceAllAssociations(
                graphIdentifier,
                classUUID,
                List.of(associationPair(UUID.randomUUID(), UUID.randomUUID())));

        assertBothDirectionsArePresent();
    }

    private void assertBothDirectionsArePresent() {
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var graph = ctx.getRdfGraph();
            assertThat(graph.contains(ASSOCIATION, RDF.type.asNode(), RDF.Property.asNode()))
                    .isTrue();
            assertThat(graph.contains(ASSOCIATION, RDFS.domain.asNode(), CLASS)).isTrue();
            assertThat(graph.contains(ASSOCIATION, RDFS.range.asNode(), SUB_CLASS)).isTrue();
            assertThat(
                            graph.contains(
                                    ASSOCIATION,
                                    CIMS.inverseRoleName.asNode(),
                                    INVERSE_ASSOCIATION))
                    .isTrue();

            assertThat(
                            graph.contains(
                                    INVERSE_ASSOCIATION, RDF.type.asNode(), RDF.Property.asNode()))
                    .isTrue();
            assertThat(graph.contains(INVERSE_ASSOCIATION, RDFS.domain.asNode(), SUB_CLASS))
                    .isTrue();
            assertThat(graph.contains(INVERSE_ASSOCIATION, RDFS.range.asNode(), CLASS)).isTrue();
            assertThat(
                            graph.contains(
                                    INVERSE_ASSOCIATION,
                                    CIMS.inverseRoleName.asNode(),
                                    ASSOCIATION))
                    .isTrue();
        }
    }

    private AssociationPairDTO associationPair(UUID fromUUID, UUID toUUID) {
        var from =
                AssociationDTO.builder()
                        .uuid(fromUUID)
                        .prefix(PREFIX)
                        .label("subClass")
                        .multiplicity("M:0..n")
                        .domain(PREFIX + "class")
                        .range(new DataTypeDTO("subClass", PREFIX))
                        .associationUsed(true)
                        .build();
        var to =
                AssociationDTO.builder()
                        .uuid(toUUID)
                        .prefix(PREFIX)
                        .label("class")
                        .multiplicity("M:1..1")
                        .domain(PREFIX + "subClass")
                        .range(new DataTypeDTO("class", PREFIX))
                        .associationUsed(true)
                        .build();
        return new AssociationPairDTO(from, to);
    }
}
