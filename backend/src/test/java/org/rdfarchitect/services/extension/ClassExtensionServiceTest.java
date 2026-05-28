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

package org.rdfarchitect.services.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import static utils.TestUtils.readMultipartFileFromFile;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.TxnType;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.api.dto.ClassMapper;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.ChangeLogService;
import org.rdfarchitect.services.ClassExtensionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class ClassExtensionServiceTest {

    private ClassExtensionService classExtensionService;
    private DatabasePort databasePort;
    @Autowired private ClassMapper classMapper;

    private static final String PATH = "src/test/java/org/rdfarchitect/services/extension/";

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        var schemaConfig = new SchemaConfig();
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(), schemaConfig);
        var mockChangeLogService = mock(ChangeLogService.class);
        classExtensionService =
                new ClassExtensionService(databasePort, classMapper, mockChangeLogService);
    }

    @Test
    void extendClass_classWithSuperClass_copiesClasses() {
        // arrange
        var sourceGraphId = new GraphIdentifier("source-ds", "http://example.org/source");
        var targetGraphId = new GraphIdentifier("target-ds", "http://example.org/target");
        var classUuid = "2c9916ee-a33e-4a2a-a0b8-ad1ba1322ffd"; // UUID of ex:Child in source ttl

        var sourceFile = readMultipartFileFromFile(PATH, "class-extension-source.ttl");
        var sourceGraphSource =
                new GraphFileSourceBuilderImpl()
                        .setFile(sourceFile)
                        .setGraphName(sourceGraphId.graphUri())
                        .build();
        databasePort.createGraph(sourceGraphId, sourceGraphSource.graph());

        var targetFile = readMultipartFileFromFile(PATH, "class-extension-target-with-core.ttl");
        var targetGraphSource =
                new GraphFileSourceBuilderImpl()
                        .setFile(targetFile)
                        .setGraphName(targetGraphId.graphUri())
                        .build();
        databasePort.createGraph(targetGraphId, targetGraphSource.graph());

        // act
        var result = classExtensionService.extendClass(sourceGraphId, classUuid, targetGraphId);

        // assert DTO returned
        assertThat(result).isNotNull();

        var targetGraph = databasePort.getGraphWithContext(targetGraphId).getRdfGraph();
        var sourceGraph = databasePort.getGraphWithContext(sourceGraphId).getRdfGraph();
        var targetModel = ModelFactory.createModelForGraph(targetGraph);
        var sourceModel = ModelFactory.createModelForGraph(sourceGraph);

        var ex = "http://example.org#";
        var cims = "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#";
        var rdfa = "http://example.org#uuid";

        try {
            sourceGraph.begin(TxnType.READ);
            targetGraph.begin(TxnType.READ);

            // class exists in target
            assertThat(
                            targetGraph.contains(
                                    NodeFactory.createURI(ex + "Child"),
                                    RDF.type.asNode(),
                                    RDFS.Class.asNode()))
                    .isTrue();

            // superclass relation exists => superclass copied/usable
            assertThat(
                            targetGraph.contains(
                                    NodeFactory.createURI(ex + "Child"),
                                    RDFS.subClassOf.asNode(),
                                    NodeFactory.createURI(ex + "Base")))
                    .isTrue();

            // superclass exists in target graph
            assertThat(
                            targetGraph.contains(
                                    NodeFactory.createURI(ex + "Base"),
                                    RDF.type.asNode(),
                                    RDFS.Class.asNode()))
                    .isTrue();

            // UUID changed for copied class
            var sourceUuid =
                    sourceModel
                            .getProperty(
                                    sourceModel.getResource(ex + "Child"),
                                    sourceModel.createProperty(rdfa))
                            .getString();

            var targetUuid =
                    targetModel
                            .getProperty(
                                    targetModel.getResource(ex + "Child"),
                                    targetModel.createProperty(rdfa))
                            .getString();

            assertThat(targetUuid).isNotBlank();
            assertThat(targetUuid).isNotEqualTo(sourceUuid);

            // UUID changed for copied superclass
            var sourceBaseUuid =
                    sourceModel
                            .getProperty(
                                    sourceModel.getResource(ex + "Base"),
                                    sourceModel.createProperty(rdfa))
                            .getString();

            var targetBaseUuid =
                    targetModel
                            .getProperty(
                                    targetModel.getResource(ex + "Base"),
                                    targetModel.createProperty(rdfa))
                            .getString();

            assertThat(targetBaseUuid).isNotBlank();
            assertThat(targetBaseUuid).isNotEqualTo(sourceBaseUuid);

            // concrete stereotype removed from copied class
            assertThat(
                            targetGraph.contains(
                                    NodeFactory.createURI(ex + "Child"),
                                    NodeFactory.createURI(cims + "stereotype"),
                                    NodeFactory.createURI(
                                            "http://iec.ch/TC57/NonStandard/UML#concrete")))
                    .isFalse();

            // copied class belongs to Core package in target
            assertThat(
                            targetGraph.contains(
                                    NodeFactory.createURI(ex + "Child"),
                                    NodeFactory.createURI(cims + "belongsToCategory"),
                                    NodeFactory.createURI(ex + "CorePackage")))
                    .isTrue();

            // copied superclass belongs to Core package as well
            assertThat(
                            targetGraph.contains(
                                    NodeFactory.createURI(ex + "Base"),
                                    NodeFactory.createURI(cims + "belongsToCategory"),
                                    NodeFactory.createURI(ex + "CorePackage")))
                    .isTrue();

        } finally {
            targetGraph.end();
            sourceGraph.end();
        }
    }

    @Test
    void extendClass_whenSuperclassAlreadyPresent_skipsSuperclassInsert_butAddsClass() {
        // arrange
        var sourceGraphId = new GraphIdentifier("source-ds-2", "http://example.org/source2");
        var targetGraphId = new GraphIdentifier("target-ds-2", "http://example.org/target2");
        var classUuid = "2c9916ee-a33e-4a2a-a0b8-ad1ba1322ffd"; // ex:Child

        var sourceFile = readMultipartFileFromFile(PATH, "class-extension-source.ttl");
        var sourceGraphSource =
                new GraphFileSourceBuilderImpl()
                        .setFile(sourceFile)
                        .setGraphName(sourceGraphId.graphUri())
                        .build();
        databasePort.createGraph(sourceGraphId, sourceGraphSource.graph());

        // target already contains Base class with existing UUID; should not be replaced
        var targetFile =
                readMultipartFileFromFile(PATH, "class-extension-target-with-existing-class.ttl");
        var targetGraphSource =
                new GraphFileSourceBuilderImpl()
                        .setFile(targetFile)
                        .setGraphName(targetGraphId.graphUri())
                        .build();
        databasePort.createGraph(targetGraphId, targetGraphSource.graph());

        var ex = "http://example.org#";
        var rdfa = "http://example.org#uuid";

        var targetGraph = databasePort.getGraphWithContext(targetGraphId).getRdfGraph();
        var targetModel = ModelFactory.createModelForGraph(targetGraph);

        String existingBaseUuidBefore;
        try {
            targetGraph.begin(TxnType.READ);
            existingBaseUuidBefore =
                    targetModel
                            .getProperty(
                                    targetModel.getResource(ex + "Base"),
                                    targetModel.createProperty(rdfa))
                            .getString();
        } finally {
            targetGraph.end();
        }

        // act
        var result = classExtensionService.extendClass(sourceGraphId, classUuid, targetGraphId);

        // assert
        assertThat(result).isNotNull();

        try {
            targetGraph.begin(TxnType.READ);

            // class got added
            assertThat(
                            targetGraph.contains(
                                    NodeFactory.createURI(ex + "Child"),
                                    RDF.type.asNode(),
                                    RDFS.Class.asNode()))
                    .isTrue();

            // relation to existing superclass remains
            assertThat(
                            targetGraph.contains(
                                    NodeFactory.createURI(ex + "Child"),
                                    RDFS.subClassOf.asNode(),
                                    NodeFactory.createURI(ex + "Base")))
                    .isTrue();

            // superclass was skipped for reinsertion: UUID unchanged
            var existingBaseUuidAfter =
                    targetModel
                            .getProperty(
                                    targetModel.getResource(ex + "Base"),
                                    targetModel.createProperty(rdfa))
                            .getString();

            assertThat(existingBaseUuidAfter).isEqualTo(existingBaseUuidBefore);

            // and still exactly one UUID triple for Base
            var baseUuidStatements =
                    targetModel
                            .listStatements(
                                    targetModel.getResource(ex + "Base"),
                                    targetModel.createProperty(rdfa),
                                    (RDFNode) null)
                            .toList();
            assertThat(baseUuidStatements).hasSize(1);

        } finally {
            targetGraph.end();
        }
    }

    @Test
    void extendClass_whenNoCorePackagePresent_addsClassWithoutBelongsToCategory() {
        // arrange
        var sourceGraphId = new GraphIdentifier("source-ds-3", "http://example.org/source3");
        var targetGraphId = new GraphIdentifier("target-ds-3", "http://example.org/target3");
        var classUuid = "2c9916ee-a33e-4a2a-a0b8-ad1ba1322ffd"; // ex:Child

        var sourceFile = readMultipartFileFromFile(PATH, "class-extension-source.ttl");
        var sourceGraphSource =
                new GraphFileSourceBuilderImpl()
                        .setFile(sourceFile)
                        .setGraphName(sourceGraphId.graphUri())
                        .build();
        databasePort.createGraph(sourceGraphId, sourceGraphSource.graph());

        // target has no package containing "Core"
        var targetFile = readMultipartFileFromFile(PATH, "class-extension-target-no-core.ttl");
        var targetGraphSource =
                new GraphFileSourceBuilderImpl()
                        .setFile(targetFile)
                        .setGraphName(targetGraphId.graphUri())
                        .build();
        databasePort.createGraph(targetGraphId, targetGraphSource.graph());

        // act
        var result = classExtensionService.extendClass(sourceGraphId, classUuid, targetGraphId);

        // assert
        assertThat(result).isNotNull();

        var targetGraph = databasePort.getGraphWithContext(targetGraphId).getRdfGraph();
        var targetModel = ModelFactory.createModelForGraph(targetGraph);

        var ex = "http://example.org#";
        var cimsBelongsToCategory =
                "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#belongsToCategory";

        try {
            targetGraph.begin(TxnType.READ);

            // class still gets inserted
            assertThat(
                            targetGraph.contains(
                                    NodeFactory.createURI(ex + "Child"),
                                    RDF.type.asNode(),
                                    RDFS.Class.asNode()))
                    .isTrue();

            // with no core package, service should not attach belongsToCategory
            var categoryStatements =
                    targetModel
                            .listStatements(
                                    targetModel.getResource(ex + "Child"),
                                    targetModel.createProperty(cimsBelongsToCategory),
                                    (RDFNode) null)
                            .toList();
            assertThat(categoryStatements).isEmpty();

        } finally {
            targetGraph.end();
        }
    }
}
