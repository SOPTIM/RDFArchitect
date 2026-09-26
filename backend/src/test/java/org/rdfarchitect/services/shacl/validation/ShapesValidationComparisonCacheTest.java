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

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.GraphContext;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.services.shacl.SHACLStoringService;
import org.rdfarchitect.shacl.dto.ShapesDocumentValidationResult;
import org.rdfarchitect.shacl.dto.ShapesValidationFinding;

import java.util.UUID;

/**
 * The stored documents an unsaved buffer is compared against are cached; every change to them has
 * to be noticed, including ones that leave every triple where it was.
 */
class ShapesValidationComparisonCacheTest {

    private static final String DATASET = "cgmes";
    private static final GraphIdentifier GRAPH = new GraphIdentifier(DATASET, "http://ex.org/EQ");

    private static final String SCHEMA =
            """
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
            @prefix cim:  <http://iec.ch/TC57/CIM100#> .

            cim:ACLineSegment a rdfs:Class .
            """;

    private static final String SHAPES =
            """
            @prefix sh:  <http://www.w3.org/ns/shacl#> .
            @prefix cim: <http://iec.ch/TC57/CIM100#> .
            @prefix ex:  <http://ex.org/shapes#> .

            ex:ACLineSegmentShape a sh:NodeShape ; sh:targetClass cim:ACLineSegment .
            """;

    private final InMemoryDatabaseImpl database = new InMemoryDatabaseImpl(new SchemaConfig());
    private final InMemoryDatabaseAdapter databasePort = new InMemoryDatabaseAdapter(database);

    private SHACLStoringService documents;
    private ShapesValidationService service;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort.createDataset(DATASET);
        var schema = GraphFactory.createDefaultGraph();
        RDFParser.fromString(SCHEMA, Lang.TURTLE).parse(schema);
        databasePort.createGraph(GRAPH, schema);
        documents = new SHACLStoringService(databasePort);
        service = new ShapesValidationService(databasePort, new SchemaIndexCache(databasePort));
    }

    @AfterEach
    void tearDown() {
        database.listDatasets().forEach(database::deleteDataset);
        SessionContext.clear();
    }

    private boolean bufferCollides(UUID draft) {
        return service.validateTurtle(GRAPH, "draft.ttl", SHAPES, draft).getDocuments().stream()
                .map(ShapesDocumentValidationResult::getFindings)
                .flatMap(java.util.List::stream)
                .map(ShapesValidationFinding::getCode)
                .anyMatch("DUPLICATE_SHAPE_IRI"::equals);
    }

    @Test
    void switchingADocumentOffIsNoticed() {
        documents.replaceShapesDocumentText(GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, SHAPES);
        var draft =
                documents.createShapesDocument(GRAPH, "draft.ttl", null, "", Lang.TURTLE).getId();
        assertThat(bufferCollides(draft)).isTrue();

        documents.updateShapesDocument(
                GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, null, false, null);

        assertThat(bufferCollides(draft)).isFalse();
    }

    @Test
    void undoingADocumentChangeIsNoticed() {
        var draft =
                documents.createShapesDocument(GRAPH, "draft.ttl", null, "", Lang.TURTLE).getId();
        documents.replaceShapesDocumentText(GRAPH, GraphContext.DEFAULT_SHAPES_DOCUMENT_ID, SHAPES);
        assertThat(bufferCollides(draft)).isTrue();

        databasePort.getGraphWithContext(GRAPH).undo();

        assertThat(bufferCollides(draft)).isFalse();
    }
}
