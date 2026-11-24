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
import org.rdfarchitect.database.DatabaseFixtures;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.WorkspaceTransaction;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.services.shacl.SHACLStoringService;
import org.rdfarchitect.shacl.dto.ShapesDocumentValidationResult;
import org.rdfarchitect.shacl.dto.ShapesValidationFinding;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The stored documents an unsaved buffer is compared against are cached; every change to them has
 * to be noticed, including ones that leave every triple where it was.
 */
class ShapesValidationComparisonCacheTest {

    private static final String WORKSPACE = "cgmes";
    private static final GraphIdentifier SCHEMA_ID = new GraphIdentifier(WORKSPACE, "EQ");

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
    private UUID custom;

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort.createDataset(WORKSPACE);
        var schema = GraphFactory.createDefaultGraph();
        RDFParser.fromString(SCHEMA, Lang.TURTLE).parse(schema);
        DatabaseFixtures.createGraph(databasePort, SCHEMA_ID, schema);
        documents = new SHACLStoringService(databasePort);
        service = new ShapesValidationService(databasePort, new SchemaIndexCache(databasePort));
        custom =
                documents
                        .createShapesDocument(
                                SCHEMA_ID.workspaceName(),
                                List.of(SCHEMA_ID.schemaName()),
                                "custom.ttl",
                                null,
                                "",
                                Lang.TURTLE)
                        .getId();
    }

    @AfterEach
    void tearDown() {
        database.listDatasets().forEach(database::deleteDataset);
        SessionContext.clear();
    }

    private boolean bufferCollides(UUID draft) {
        return service
                .validateTurtle(SCHEMA_ID.workspaceName(), "draft.ttl", SHAPES, draft)
                .getDocuments()
                .stream()
                .map(ShapesDocumentValidationResult::getFindings)
                .flatMap(java.util.List::stream)
                .map(ShapesValidationFinding::getCode)
                .anyMatch("DUPLICATE_SHAPE_IRI"::equals);
    }

    @Test
    void switchingADocumentOffIsNoticed() {
        documents.replaceShapesDocumentText(SCHEMA_ID.workspaceName(), custom, SHAPES);
        var draft =
                documents
                        .createShapesDocument(
                                SCHEMA_ID.workspaceName(),
                                List.of(SCHEMA_ID.schemaName()),
                                "draft.ttl",
                                null,
                                "",
                                Lang.TURTLE)
                        .getId();
        assertThat(bufferCollides(draft)).isTrue();

        documents.updateShapesDocument(SCHEMA_ID.workspaceName(), custom, null, false, null, null);

        assertThat(bufferCollides(draft)).isFalse();
    }

    /** The port, counting how often a transaction hands out the workspace's documents. */
    private DatabasePort counting(AtomicInteger reads) {
        return (DatabasePort)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {DatabasePort.class},
                        (proxy, method, args) -> {
                            var result = method.invoke(databasePort, args);
                            if (!(result instanceof WorkspaceTransaction transaction)) {
                                return result;
                            }
                            return Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] {WorkspaceTransaction.class},
                                    (inner, call, callArgs) -> {
                                        if (call.getName().equals("shapesDocuments")) {
                                            reads.incrementAndGet();
                                        }
                                        return call.invoke(transaction, callArgs);
                                    });
                        });
    }

    @Test
    void sessionsTypingAtOnceKeepTheirOwnCopy() {
        documents.replaceShapesDocumentText(SCHEMA_ID.workspaceName(), custom, SHAPES);
        var reads = new AtomicInteger();
        var port = counting(reads);
        service = new ShapesValidationService(port, new SchemaIndexCache(port));
        var first = SessionContext.getSessionId();
        var second = UUID.randomUUID().toString();
        bufferCollides(custom);
        SessionContext.setSessionId(second);
        databasePort.createDataset(WORKSPACE);
        DatabaseFixtures.createGraph(databasePort, SCHEMA_ID, GraphFactory.createDefaultGraph());
        bufferCollides(UUID.randomUUID());
        SessionContext.setSessionId(first);
        reads.set(0);

        bufferCollides(custom);

        // One read for the schemas the document applies to, none for the copies to compare with.
        assertThat(reads).hasValue(1);
    }

    @Test
    void undoingADocumentChangeIsNoticed() {
        var draft =
                documents
                        .createShapesDocument(
                                SCHEMA_ID.workspaceName(),
                                List.of(SCHEMA_ID.schemaName()),
                                "draft.ttl",
                                null,
                                "",
                                Lang.TURTLE)
                        .getId();
        documents.replaceShapesDocumentText(SCHEMA_ID.workspaceName(), custom, SHAPES);
        assertThat(bufferCollides(draft)).isTrue();

        databasePort.undo(SCHEMA_ID.workspaceName());

        assertThat(bufferCollides(draft)).isFalse();
    }
}
