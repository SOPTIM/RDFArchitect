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

import static utils.TestUtils.readMultipartFileFromFile;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rdfarchitect.config.SchemaConfig;
import org.rdfarchitect.context.SessionContext;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseAdapter;
import org.rdfarchitect.database.inmemory.InMemoryDatabaseImpl;
import org.rdfarchitect.rdf.graph.source.builder.implementations.GraphFileSourceBuilderImpl;
import org.rdfarchitect.services.ClassExtensionService;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

@SpringBootTest
class ClassExtensionServiceTest {

    private ClassExtensionService classExtensionService;
    private DatabasePort databasePort;
    private static final String PATH = "src/test/java/org/rdfarchitect/services/extension/";

    @BeforeEach
    void setUp() {
        SessionContext.setSessionId(UUID.randomUUID().toString());
        databasePort = new InMemoryDatabaseAdapter(new InMemoryDatabaseImpl(new SchemaConfig()));
        classExtensionService = new ClassExtensionService(databasePort);
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
        var results =
                classExtensionService.extendClasses(
                        sourceGraphId, List.of(classUuid), targetGraphId, true);

        // assert
        assertThat(results).hasSize(1);

        var ex = "http://example.org#";
        var cims = "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#";
        var rdfa = "http://example.org#uuid";

        try (var sourceCtx = databasePort.getGraphWithContext(sourceGraphId).begin(ReadWrite.READ);
                var targetCtx =
                        databasePort.getGraphWithContext(targetGraphId).begin(ReadWrite.WRITE)) {

            var sourceGraph = sourceCtx.getRdfGraph();
            var sourceModel = ModelFactory.createModelForGraph(sourceGraph);
            var targetGraph = targetCtx.getRdfGraph();
            var targetModel = ModelFactory.createModelForGraph(targetGraph);
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

            // copied class keeps the package it belongs to in the source graph
            assertThat(
                            targetGraph.contains(
                                    NodeFactory.createURI(ex + "Child"),
                                    NodeFactory.createURI(cims + "belongsToCategory"),
                                    NodeFactory.createURI(ex + "SourcePackage")))
                    .isTrue();

            // copied superclass keeps its package as well
            assertThat(
                            targetGraph.contains(
                                    NodeFactory.createURI(ex + "Base"),
                                    NodeFactory.createURI(cims + "belongsToCategory"),
                                    NodeFactory.createURI(ex + "SourcePackage")))
                    .isTrue();

            // the package of the target graph is left alone
            assertThat(
                            targetGraph.contains(
                                    NodeFactory.createURI(ex + "Child"),
                                    NodeFactory.createURI(cims + "belongsToCategory"),
                                    NodeFactory.createURI(ex + "CorePackage")))
                    .isFalse();
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

        String existingBaseUuidBefore;
        try (var ctx = databasePort.getGraphWithContext(targetGraphId).begin(ReadWrite.READ)) {
            var targetModel = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            existingBaseUuidBefore =
                    targetModel
                            .getProperty(
                                    targetModel.getResource(ex + "Base"),
                                    targetModel.createProperty(rdfa))
                            .getString();
        }

        // act
        var results =
                classExtensionService.extendClasses(
                        sourceGraphId, List.of(classUuid), targetGraphId, true);

        // assert
        assertThat(results).hasSize(1);

        try (var ctx = databasePort.getGraphWithContext(targetGraphId).begin(ReadWrite.READ)) {
            var targetGraph = ctx.getRdfGraph();
            var targetModel = ModelFactory.createModelForGraph(targetGraph);
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
        }
    }

    @Test
    void extendClass_whenClassHasNoPackage_addsClassWithoutBelongsToCategory() {
        // arrange
        var sourceGraphId = new GraphIdentifier("source-ds-3", "http://example.org/source3");
        var targetGraphId = new GraphIdentifier("target-ds-3", "http://example.org/target3");
        var classUuid = "2c9916ee-a33e-4a2a-a0b8-ad1ba1322ffd"; // ex:Child

        var sourceFile =
                readMultipartFileFromFile(PATH, "class-extension-source-without-package.ttl");
        var sourceGraphSource =
                new GraphFileSourceBuilderImpl()
                        .setFile(sourceFile)
                        .setGraphName(sourceGraphId.graphUri())
                        .build();
        databasePort.createGraph(sourceGraphId, sourceGraphSource.graph());

        var targetFile = readMultipartFileFromFile(PATH, "class-extension-target-no-core.ttl");
        var targetGraphSource =
                new GraphFileSourceBuilderImpl()
                        .setFile(targetFile)
                        .setGraphName(targetGraphId.graphUri())
                        .build();
        databasePort.createGraph(targetGraphId, targetGraphSource.graph());

        // act
        var results =
                classExtensionService.extendClasses(
                        sourceGraphId, List.of(classUuid), targetGraphId, true);

        // assert
        assertThat(results).hasSize(1);

        var ex = "http://example.org#";
        var cimsBelongsToCategory =
                "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#belongsToCategory";

        try (var ctx = databasePort.getGraphWithContext(targetGraphId).begin(ReadWrite.READ)) {
            var targetGraph = ctx.getRdfGraph();
            var targetModel = ModelFactory.createModelForGraph(targetGraph);

            // class still gets inserted
            assertThat(
                            targetGraph.contains(
                                    NodeFactory.createURI(ex + "Child"),
                                    RDF.type.asNode(),
                                    RDFS.Class.asNode()))
                    .isTrue();

            // without a package in the source graph there is nothing to point at
            var categoryStatements =
                    targetModel
                            .listStatements(
                                    targetModel.getResource(ex + "Child"),
                                    targetModel.createProperty(cimsBelongsToCategory),
                                    (RDFNode) null)
                            .toList();
            assertThat(categoryStatements).isEmpty();
        }
    }

    @Test
    void extendClasses_withoutInheritance_referencesSuperClassInsteadOfCreatingIt() {
        // arrange
        var sourceGraphId = new GraphIdentifier("source-ds-4", "http://example.org/source4");
        var targetGraphId = new GraphIdentifier("source-ds-4", "http://example.org/target4");
        var classUuid = "2c9916ee-a33e-4a2a-a0b8-ad1ba1322ffd"; // ex:Child

        createGraph(sourceGraphId, "class-extension-source.ttl");
        createGraph(targetGraphId, "class-extension-target-with-core.ttl");

        // act
        var results =
                classExtensionService.extendClasses(
                        sourceGraphId, List.of(classUuid), targetGraphId, false);

        // assert
        var ex = "http://example.org#";
        var cims = "http://iec.ch/TC57/1999/rdf-schema-extensions-19990926#";
        var rdfa = "http://example.org#uuid";

        assertThat(results).hasSize(1);
        var result = results.getFirst();
        assertThat(result.sourceClassUUID()).isEqualTo(UUID.fromString(classUuid));
        assertThat(result.created()).isTrue();

        try (var ctx = databasePort.getGraphWithContext(targetGraphId).begin(ReadWrite.READ)) {
            var targetGraph = ctx.getRdfGraph();
            var targetModel = ModelFactory.createModelForGraph(targetGraph);

            // the class itself is created
            assertThat(
                            targetGraph.contains(
                                    NodeFactory.createURI(ex + "Child"),
                                    RDF.type.asNode(),
                                    RDFS.Class.asNode()))
                    .isTrue();

            // the superclass is only referenced, so it stays external
            assertThat(
                            targetGraph.contains(
                                    NodeFactory.createURI(ex + "Child"),
                                    RDFS.subClassOf.asNode(),
                                    NodeFactory.createURI(ex + "Base")))
                    .isTrue();
            assertThat(
                            targetGraph.contains(
                                    NodeFactory.createURI(ex + "Base"),
                                    RDF.type.asNode(),
                                    RDFS.Class.asNode()))
                    .isFalse();

            // the identifiers of the target graph are reported back
            var targetUuid =
                    targetModel
                            .getProperty(
                                    targetModel.getResource(ex + "Child"),
                                    targetModel.createProperty(rdfa))
                            .getString();
            assertThat(result.classUUID()).isEqualTo(UUID.fromString(targetUuid));

            var packageUuid =
                    targetModel
                            .getProperty(
                                    targetModel.getResource(ex + "SourcePackage"),
                                    targetModel.createProperty(rdfa))
                            .getString();
            assertThat(result.packageUUID()).isEqualTo(UUID.fromString(packageUuid));
            assertThat(
                            targetGraph.contains(
                                    NodeFactory.createURI(ex + "Child"),
                                    NodeFactory.createURI(cims + "belongsToCategory"),
                                    NodeFactory.createURI(ex + "SourcePackage")))
                    .isTrue();
        }
    }

    @Test
    void extendClasses_withoutInheritance_whenClassAlreadyExists_reportsItAsNotCreated() {
        // arrange
        var sourceGraphId = new GraphIdentifier("source-ds-5", "http://example.org/source5");
        var targetGraphId = new GraphIdentifier("source-ds-5", "http://example.org/target5");
        var baseUuid = "22222222-2222-2222-2222-222222222222"; // ex:Base

        createGraph(sourceGraphId, "class-extension-source.ttl");
        createGraph(targetGraphId, "class-extension-target-with-existing-class.ttl");

        // act
        var results =
                classExtensionService.extendClasses(
                        sourceGraphId, List.of(baseUuid), targetGraphId, false);

        // assert
        assertThat(results).hasSize(1);
        var result = results.getFirst();
        assertThat(result.created()).isFalse();
        assertThat(result.sourceClassUUID()).isEqualTo(UUID.fromString(baseUuid));
        // the class of the target graph keeps its identifiers
        assertThat(result.classUUID())
                .isEqualTo(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        assertThat(result.packageUUID())
                .isEqualTo(UUID.fromString("99999999-9999-9999-9999-999999999999"));
    }

    private void createGraph(GraphIdentifier graphIdentifier, String fileName) {
        var file = readMultipartFileFromFile(PATH, fileName);
        var graphSource =
                new GraphFileSourceBuilderImpl()
                        .setFile(file)
                        .setGraphName(graphIdentifier.graphUri())
                        .build();
        databasePort.createGraph(graphIdentifier, graphSource.graph());
    }
}
